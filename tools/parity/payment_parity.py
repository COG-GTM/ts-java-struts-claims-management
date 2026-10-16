#!/usr/bin/env python3
"""Replay the payment requests against the payment service.

The legacy behaviour is recorded in two places: the payment transcripts under
``transcripts/payment_*.json`` and the numbered live requests of
``docs/specs/SPEC-PAY-001-live.json``. This script replays both against the
service run with the parity profile on port 8084
(``make payment-service-run-parity``) and compares what
docs/decisions/ADR-002-payment-boundary.md ("How parity is judged") defines as
parity: status class (and redirect target), business fields, validation keys,
history links, payee and method as displayed, and database state.

The rules each scenario is evidence for come from ``transcript_rules`` in
``parity/routes.json``. Approved differences there apply to payment scenarios as
they do to settlement ones; there are none today.

Usage: python3 tools/parity/payment_parity.py [--base http://localhost:8084]
Writes parity/evidence/payment.json, then parity/report.md, parity/report.json
and parity/report.html through tools/parity/report.py, and exits non-zero on any
FAIL. Nothing written carries a revision or a timestamp.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import report  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIVE = os.path.join(ROOT, "docs", "specs", "SPEC-PAY-001-live.json")

# legacy path -> service path (ADR-002, "How each Struts piece maps")
PATHS = {
    "/claims/payment/issue.do": "/payment/issue",
    "/claims/payment/history.do": "/payment/history",
    "/claims/payment/detail.do": "/payment/detail",
    "/claims/payment/remittance.do": "/payment/remittance",
}

# Live steps that are setup rather than payment behaviour (ADR-002): signing in
# is the user parameter, and the settlement save belongs to the settlement
# slice, so parity applies the row it writes instead.
SIGN_IN = {"L02", "L35"}
PRECONDITIONS = {"L05": "settlement-save"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


OPENER = urllib.request.build_opener(NoRedirect)


def http(method, url, data=None):
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        response = OPENER.open(req)
    except urllib.error.HTTPError as error:
        response = error
    body = response.read().decode()
    return response.status, response.headers.get("Location"), (json.loads(body) if body else {})


def reset(base):
    http("POST", base + "/parity/reset", b"")


def apply(base, script):
    http("POST", base + "/parity/apply?" + urllib.parse.urlencode({"script": script}), b"")


def query(base, sql):
    status, unused, rows = http("GET", base + "/parity/query?" + urllib.parse.urlencode({"sql": sql}))
    if status != 200:
        raise SystemExit("parity query failed (%s): %s" % (status, sql))
    return rows


def service_request(base, request, actor):
    """The service request equivalent to a legacy one: same method, same
    parameters, the session user as ``user`` (ADR-002, "Sign-in")."""
    path, unused, query_string = request["path"].partition("?")
    params = urllib.parse.parse_qsl(query_string, keep_blank_values=True)
    params += sorted(request.get("form", {}).items())
    if actor != "anonymous":
        params.append(("user", actor))
    method = request["method"]
    encoded = urllib.parse.urlencode(params)
    url = base + PATHS[path]
    if method == "GET":
        return method, url + ("?" + encoded if encoded else ""), None, params
    return method, url, encoded.encode(), params


def history_db_state(base, keys):
    """The transcript db_state keys, read the way tools/capture/capture.py reads
    them from the legacy app: through the claim 119 history screen."""
    if not keys:
        return {}
    unused, unused2, body = http("GET", base + "/payment/history?user=supervisor")
    fields = body.get("fields", {})
    actual = {}
    for key in keys:
        parts = key.split(".")
        if key.startswith("payment.count.claim."):
            actual[key] = str(len([n for n in fields if n.startswith("paymentAmount_")]))
        else:
            actual[key] = fields.get("payment" + parts[2].title() + "_" + parts[1], "")
    return actual


def compare(expected, status, location, body, actual_db):
    """Checks per ADR-002 "How parity is judged"; returns them and the diffs."""
    checks = []
    result = expected.get("result", "")
    if expected["status"] == 302:
        checks.append(report.check("status", 302, status, status == 302))
        checks.append(report.check("location", expected.get("location"), location,
                                   location == expected.get("location")))
    elif result.endswith("error.jsp"):
        ok = status >= 500 and body.get("screen") == "error"
        checks.append(report.check("status class", "200 forward to error.jsp (5xx in the service)",
                                   "%s screen %s" % (status, body.get("screen")), ok))
    else:
        checks.append(report.check("status class", report.status_class(expected["status"]),
                                   report.status_class(status),
                                   report.status_class(status) == report.status_class(expected["status"])))
    checks += report.field_checks(expected.get("business_fields", {}), body.get("fields", {}))
    errors = body.get("errors", [])
    checks.append(report.check("validation errors", expected.get("validation_errors", []), errors,
                               errors == expected.get("validation_errors", [])))
    if "/history.jsp" in result and "links" in expected:
        links = body.get("links", [])
        checks.append(report.check("links", expected.get("links", []), links,
                                   links == expected.get("links", [])))
    text = expected.get("text")
    for name, value in sorted(body.get("display", {}).items()):
        if text is None:
            continue
        shown = value == "" or any(value in line for line in text)
        checks.append(report.check("display " + name, "shown on the legacy page", value, shown))
    for key, value in sorted(expected.get("db_state", {}).items()):
        checks.append(report.check("db " + key, value, actual_db.get(key), actual_db.get(key) == value))
    diffs = ["%s %r != %r" % (c["name"], c["actual"], c["expected"]) for c in checks if not c["ok"]]
    return checks, diffs


def scenario(name, description, source, rules, legacy_request, actor, sent, expected, actual, checks,
             diffs, approvals):
    approval = approvals.get(name)
    if approval:
        raise SystemExit("approved differences for payment scenarios are not supported yet: " + name)
    method, url, unused, params = sent
    return {
        "scenario": name,
        "slice": "payment",
        "description": description,
        "source": source,
        "rules": rules,
        "request": {"method": legacy_request["method"], "path": legacy_request["path"],
                    "form": legacy_request.get("form", {}), "actor": actor},
        "service_request": {"method": method, "path": urllib.parse.urlsplit(url).path,
                            "params": [list(p) for p in params]},
        "expected": expected,
        "actual": actual,
        "checks": checks,
        "verdict": "PASS" if not diffs else "FAIL",
        "change": None,
        "record": None,
        "difference": "; ".join(diffs) or "-",
    }


def replay(base, name, description, source, rules, legacy_request, actor, expected, approvals,
           db_keys=()):
    sent = service_request(base, legacy_request, actor)
    method, url, data, unused = sent
    status, location, body = http(method, url, data)
    actual_db = history_db_state(base, db_keys)
    actual = {"status": status, "location": location, "body": body}
    if actual_db:
        actual["db_state"] = actual_db
    checks, diffs = compare(expected, status, location, body, actual_db)
    print("%-28s %s %s" % (name, "PASS" if not diffs else "FAIL", "; ".join(diffs)))
    return scenario(name, description, source, rules, legacy_request, actor, sent, expected, actual,
                    checks, diffs, approvals)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("PAYMENT_SERVICE_BASE", "http://localhost:8084"))
    args = parser.parse_args()
    base = args.base

    with open(os.path.join(ROOT, "parity", "routes.json")) as handle:
        routes = json.load(handle)
    rules = routes.get("transcript_rules", {})
    approvals = {entry["scenario"]: entry for entry in routes.get("approved_differences", [])}
    rows = []

    # Transcripts, in recorded order, from the seed plus the settlement save
    # that precedes them in transcripts/index.json.
    with open(os.path.join(ROOT, "transcripts", "index.json")) as handle:
        order = [entry["scenario"] for entry in json.load(handle)]
    reset(base)
    apply(base, "settlement-save")
    for name in [n for n in order if n.startswith("payment_")]:
        with open(os.path.join(ROOT, "transcripts", name + ".json")) as handle:
            transcript = json.load(handle)
        rows.append(replay(base, name, transcript["description"], "transcripts/%s.json" % name,
                           rules.get(name, []), transcript["request"], transcript["actor"],
                           transcript["expected"], approvals, sorted(transcript["expected"]["db_state"])))

    # The live requests of SPEC-PAY-001, in order from the seed.
    with open(LIVE) as handle:
        live = json.load(handle)
    reset(base)
    for step in live["steps"]:
        if step["id"] in SIGN_IN:
            continue
        if step["id"] in PRECONDITIONS:
            apply(base, PRECONDITIONS[step["id"]])
            continue
        name = "payment_live_" + step["id"]
        rows.append(replay(base, name, step["purpose"],
                           "docs/specs/SPEC-PAY-001-live.json %s" % step["id"],
                           rules.get(name, []), step["request"], step["actor"], step["response"],
                           approvals))

    # The database after the live replay, against the db_after queries.
    name = "payment_live_db_after"
    checks = []
    actual = {}
    for key, recorded in sorted(live["db_after"].items()):
        rows_now = query(base, recorded["sql"])
        actual[key] = {"sql": recorded["sql"], "rows": rows_now}
        checks.append(report.check("db_after " + key, recorded["rows"], rows_now,
                                   rows_now == recorded["rows"]))
    diffs = [c["name"] + " differs" for c in checks if not c["ok"]]
    print("%-28s %s %s" % (name, "PASS" if not diffs else "FAIL", "; ".join(diffs)))
    request = {"method": "GET", "path": "/parity/query", "form": {}}
    rows.append({
        "scenario": name,
        "slice": "payment",
        "description": "PAYMENT and SETTLEMENT rows after the live replay",
        "source": "docs/specs/SPEC-PAY-001-live.json db_after",
        "rules": rules.get(name, []),
        "request": dict(request, actor="parity"),
        "service_request": {"method": "GET", "path": "/parity/query",
                            "params": [["sql", r["sql"]] for r in live["db_after"].values()]},
        "expected": live["db_after"],
        "actual": actual,
        "checks": checks,
        "verdict": "PASS" if not diffs else "FAIL",
        "change": None,
        "record": None,
        "difference": "; ".join(diffs) or "-",
    })

    report.write_evidence("payment", rows)
    report.build()
    raise SystemExit(1 if any(row["verdict"] == "FAIL" for row in rows) else 0)


if __name__ == "__main__":
    main()

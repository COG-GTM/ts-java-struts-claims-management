#!/usr/bin/env python3
"""Replay the payment scenarios against the payment service and compare.

The legacy side is what the Struts app answered to the same requests:
``transcripts/payment_*.json`` (written by ``make capture``) and the live proof
``docs/specs/SPEC-PAY-001-proof.json`` (written by ``make payment-spec-proof``
against the running legacy app). This script sends the same requests, in the
same order and as the same operator, to the payment service on port 8084
(``make payment-run-parity``) after resetting its database to the scenario's
explicit precondition (``parity/preconditions/payment.sql``), and compares
what ADR-002 defines as parity (docs/decisions/ADR-002-payment-boundary.md,
"How parity is judged"): status and redirect target, screen, the
``<ns:field>`` values in screen order, the payee and method texts, the
history links, the operator, the validation keys, and the database rows the
legacy case read afterwards.

Scenario-to-rule mapping: ``payment_scenarios`` in ``parity/routes.json``. It
must equal the ``Observed:`` citations of docs/specs/SPEC-PAY-001.md; the script
fails when they drift. A scenario in ``approved_differences`` is judged
against its CHG record; any other difference is a FAIL. An approval lists, per
step number, the answer the service must give instead of the legacy one
(``{"cap": true}`` is CHG-002's rejection: status 200, screen issue, no
fields, one key ``payment.amount.aboveSettlement``), and the database rows or
transcript keys it must leave instead; every unlisted step and query is still
judged against legacy. A ``cap`` step also fails unless CHG-002's condition
holds before it is sent: the parsed amount, with the legacy fallbacks, is
greater than the newest settlement amount of the claim in the service's
database (docs/changes/CHG-002-payment-amount-cap.md).

Every verdict carries a mark: ``automated`` when the service answer equals the
legacy answer on every compared item, ``reviewer`` when the verdict also rests
on a recorded human decision (a CHG record, or ADR-002's rule that a 5xx
answers the legacy error.jsp forward).

Usage: python3 tools/parity/payment_parity.py [--base http://localhost:8084]
Writes the payment rows of parity/report.json, report.md and report.html via
tools/parity/report.py and exits non-zero on any FAIL.
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import report  # noqa: E402

ROOT = report.ROOT
SPEC = os.path.join("docs", "specs", "SPEC-PAY-001.md")
PROOF = os.path.join("docs", "specs", "SPEC-PAY-001-proof.json")
PRECONDITIONS = os.path.join("parity", "preconditions", "payment.sql")
TRANSCRIPTS = ["payment_issue", "payment_history"]
HEADER = "X-NorthStar-Operator"
RULE = re.compile(r"^\*\*(PAY-R\d+) \(v(\d+)")
EQUIVALENCE = ("ADR-002 \"How parity is judged\": the legacy error.jsp forward (200) "
               "is answered by a 5xx with screen error")


def read(path):
    with open(os.path.join(ROOT, path)) as handle:
        return handle.read()


def spec_mapping():
    """scenario -> ["PAY-Rnn (vN)", ...] from the Observed: lines of the spec."""
    mapping = {}
    rule = None
    observed = None
    for line in read(SPEC).splitlines() + [""]:
        heading = RULE.match(line)
        if heading:
            rule = "%s (v%s)" % heading.groups()
            observed = None
            continue
        if rule and line.startswith("Observed:"):
            observed = line[len("Observed:"):]
            continue
        if rule and observed is not None and line.strip() and not line.startswith(("Read:", "**")):
            observed += " " + line
            continue
        if rule and observed is not None:
            for token in observed.split(";"):
                token = token.strip()
                if not token:
                    continue
                name = token[len("live:"):].split("#")[0] if token.startswith("live:") else token
                mapping.setdefault(name, [])
                if rule not in mapping[name]:
                    mapping[name].append(rule)
            rule = None
            observed = None
    return mapping


def all_rules():
    return ["%s (v%s)" % m.groups() for m in map(RULE.match, read(SPEC).splitlines()) if m]


def routes():
    return json.loads(read(os.path.join("parity", "routes.json")))


def precondition_sections():
    sections = {}
    current = None
    for line in read(PRECONDITIONS).splitlines():
        line = line.strip()
        if line.startswith("-- scenario: "):
            current = line[len("-- scenario: "):].strip()
            sections[current] = {"name": None, "sql": []}
        elif current and line.startswith("--") and sections[current]["name"] is None:
            sections[current]["name"] = line[2:].strip()
        elif current and line and not line.startswith("--"):
            sections[current]["sql"].append(line.rstrip(";"))
    return sections


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


OPENER = urllib.request.build_opener(NoRedirect)


def http(method, url, data=None, headers=None):
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with OPENER.open(req) as response:
            return response.status, dict(response.headers), response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode()


def parity_call(base, path, body=b"", params=None):
    url = base + path + ("?" + urllib.parse.urlencode(params) if params else "")
    status, unused, text = http("POST", url, body, {"X-Parity-Reset": "1",
                                                    "Content-Type": "text/plain"})
    if status != 200:
        raise SystemExit("%s failed (%s): %s - is make payment-run-parity running?"
                         % (path, status, text))
    return json.loads(text)


def service_request(base, request, actor):
    path = request["path"].replace("/claims/payment/", "/payment/").replace(".do", "")
    params = request.get("query") or {}
    form = request.get("form") or {}
    url = base + path + ("?" + urllib.parse.urlencode(params) if params else "")
    headers = {HEADER: actor} if actor else {}
    data = None
    if request["method"] == "POST":
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    status, response_headers, text = http(request["method"], url, data, headers)
    body = json.loads(text) if text.strip().startswith("{") else {}
    return {"status": status, "location": response_headers.get("Location"), "body": body}


def legacy_screen(view):
    return None if view is None else os.path.basename(view).replace(".jsp", "")


def legacy_display(response):
    """The non-field texts the screen prints: text cells minus the audit table."""
    if legacy_screen(response.get("view")) == "error":
        return []
    cells = list(response.get("text_cells", []))
    audit = list(response.get("audit", {}).values())
    if audit and cells[-len(audit):] == [response["audit"][k] for k in
                                         ("Screen context", "Operator", "As of date")]:
        cells = cells[:-len(audit)]
    return cells


def page_links(links):
    """Links printed by the screen itself, without the menu link every screen has."""
    return [link for link in links if not link.startswith("/claims/")]


def check(checks, what, expected, actual, basis=None):
    ok = expected == actual
    checks.append({"what": what, "expected": expected, "actual": actual, "ok": ok,
                   "basis": basis})
    return ok


def compare_live_step(response, actual):
    checks = []
    reviewer = False
    body = actual["body"]
    screen = legacy_screen(response.get("view"))
    if response["status"] == 302:
        check(checks, "status", 302, actual["status"])
        check(checks, "redirect", urllib.parse.urlparse(response["location"]).path,
              urllib.parse.urlparse(actual["location"] or "").path)
        return checks, reviewer
    if screen == "error":
        reviewer = True
        check(checks, "status class", "5xx (legacy 200 error.jsp)",
              "%dxx" % (actual["status"] // 100) + (" (service %d)" % actual["status"]),
              EQUIVALENCE)
        checks[-1]["ok"] = actual["status"] >= 500
        check(checks, "screen", "error", body.get("screen"))
        check(checks, "fields", [], [list(p) for p in body.get("fields", {}).items()])
        return checks, reviewer
    check(checks, "status", response["status"], actual["status"])
    check(checks, "screen", screen, body.get("screen"))
    check(checks, "fields", [list(p) for p in response["fields"]],
          [list(p) for p in body.get("fields", {}).items()])
    check(checks, "payee/method text", legacy_display(response),
          list(body.get("display", {}).values()))
    check(checks, "links", page_links(response["links"]), body.get("links", []))
    check(checks, "operator", response["audit"].get("Operator"), body.get("operator"))
    check(checks, "validation keys", [], body.get("errors", []))
    return checks, reviewer


ISSUE_PATH = "/claims/payment/issue.do"
CAP_KEY = "payment.amount.aboveSettlement"
JAVA_INT = re.compile(r"^[+-]?\d+$")
JAVA_DOUBLE = re.compile(r"^[+-]?(NaN|Infinity|(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)[fFdD]?$")


def java_int(value, fallback):
    """LegacyParameters.integer: Integer.parseInt or the fallback."""
    if value is None or not JAVA_INT.match(value) or not -2 ** 31 <= int(value) < 2 ** 31:
        return fallback
    return int(value)


def java_double(value, fallback):
    """LegacyParameters.decimal: Double.parseDouble (which trims) or the fallback.
    Hexadecimal literals are not modelled; none is sent by any scenario."""
    text = (value or "").strip(" \t\n\r\x00")
    if not JAVA_DOUBLE.match(text):
        return fallback
    return float(text.rstrip("fFdD"))


def cap_applies(base, request):
    """CHG-002's condition for an issue request, read from the service database
    as it stands just before the request is sent."""
    params = dict(request.get("query") or {}, **(request.get("form") or {}))
    claim = java_int(params.get("claimId"), 119)
    rows = parity_call(base, "/parity/query", (
        "select settlement_amount from SETTLEMENT where claim_id = %d "
        "order by settlement_id desc limit 1" % claim).encode())["rows"]
    if not rows:
        return False
    settlement = float(rows[0][0])
    return java_double(params.get("amount"), settlement) > settlement


def approved_checks(expected, actual, actor, change):
    """Checks of one step against the answer an approval states for it."""
    if "cap" in expected:
        if list(expected) != ["cap"] or expected["cap"] is not True:
            raise SystemExit("a cap approval is exactly {\"cap\": true}, got %s" % json.dumps(expected))
        expected = {"status": 200, "screen": "issue", "fields": [], "payee/method text": [],
                    "links": [], "operator": actor, "validation keys": [CAP_KEY]}
    unknown = set(expected) - {"status", "screen", "fields", "payee/method text", "links",
                               "operator", "validation keys"}
    if unknown:
        raise SystemExit("approved step names unknown items %s" % sorted(unknown))
    body = actual["body"]
    got = {"status": actual["status"], "screen": body.get("screen"),
           "fields": [list(p) for p in body.get("fields", {}).items()],
           "payee/method text": list(body.get("display", {}).values()),
           "links": body.get("links", []), "operator": body.get("operator"),
           "validation keys": body.get("errors", [])}
    checks = []
    for what in ("status", "screen", "fields", "payee/method text", "links", "operator",
                 "validation keys"):
        if what in expected:
            check(checks, what, expected[what], got[what], "approved by " + change)
    return checks


def approved_step(base, n, request, actor, approval):
    """The approved answer for step n, or None; sends nothing."""
    if not approval:
        return None
    return approval.get("expected", {}).get("steps", {}).get(str(n))


def cap_check(base, request, approved, change):
    """Before a step the approval marks as CHG-002's rejection, check that the
    condition holds; return the check list (empty when not a cap step)."""
    if not (approved and approved.get("cap")):
        return []
    checks = []
    holds = request["path"] == ISSUE_PATH and cap_applies(base, request)
    check(checks, "CHG-002 condition (amount > newest settlement amount)", True, holds,
          "approved by " + change)
    return checks


def service_summary(actual):
    """What the report keeps of the service answer besides the checks."""
    return {"status": actual["status"], "location": actual["location"],
            "screen": actual["body"].get("screen")}


def transcript_db(base, key):
    parts = key.split(".")
    if parts[0] == "payment" and parts[1] == "count":
        sql = "select count(*) from PAYMENT where claim_id = %d" % int(parts[3])
        return parity_call(base, "/parity/query", sql.encode())["rows"][0][0]
    if parts[0] == "payment":
        column = {"amount": "amount", "status": "status"}[parts[2]]
        sql = "select %s from PAYMENT where payment_id = %d" % (column, int(parts[1]))
        rows = parity_call(base, "/parity/query", sql.encode())["rows"]
        value = rows[0][0] if rows else None
        return "%.2f" % float(value) if column == "amount" and value is not None else value
    raise SystemExit("unknown db_state key %s" % key)


def replay_transcript(base, name, approval=None):
    transcript = json.loads(read(os.path.join("transcripts", name + ".json")))
    expected = transcript["expected"]
    request = transcript["request"]
    if request["method"] == "GET" and request.get("form"):
        request = dict(request, query=request["form"], form={})
    approved = approved_step(base, 1, request, transcript.get("actor"), approval)
    pre = cap_check(base, request, approved, approval and approval["change"])
    actual = service_request(base, request, transcript.get("actor"))
    body = actual["body"]
    checks = []
    reviewer = False
    if approved:
        checks = pre + approved_checks(approved, actual, transcript.get("actor"),
                                       approval["change"])
    elif expected["result"].endswith("error.jsp"):
        reviewer = True
        check(checks, "status class", "5xx", "%dxx" % (actual["status"] // 100), EQUIVALENCE)
    else:
        check(checks, "status", expected["status"], actual["status"])
    if not approved:
        check(checks, "screen", legacy_screen(expected["result"].split(":", 1)[1]), body.get("screen"))
        check(checks, "business fields", expected["business_fields"], body.get("fields", {}))
        check(checks, "validation keys", expected["validation_errors"], body.get("errors", []))
    approved_db = (approval or {}).get("expected", {}).get("db_state", {})
    db = []
    for key, value in sorted(expected["db_state"].items()):
        got = transcript_db(base, key)
        entry = {"sql": key, "expected": approved_db.get(key, value), "actual": got}
        if key in approved_db:
            entry["legacy"] = value
            entry["approved"] = approval["change"]
        entry["ok"] = got == entry["expected"]
        db.append(entry)
    step = {"n": 1, "request": request, "actor": transcript.get("actor"),
            "legacy": {"status": expected["status"], "result": expected["result"],
                       "fields": expected["business_fields"]},
            "service": service_summary(actual), "checks": checks}
    if approved:
        step["approved"] = approval["change"]
    return transcript["description"], [step], db, reviewer, "transcripts/%s.json" % name


def replay_case(base, case, approval=None):
    steps = []
    reviewer = False
    for n, exchange in enumerate(case["exchanges"], 1):
        approved = approved_step(base, n, exchange["request"], case["actor"], approval)
        pre = cap_check(base, exchange["request"], approved, approval and approval["change"])
        actual = service_request(base, exchange["request"], case["actor"])
        if approved:
            checks = pre + approved_checks(approved, actual, case["actor"], approval["change"])
        else:
            checks, needs_review = compare_live_step(exchange["response"], actual)
            reviewer = reviewer or needs_review
        legacy = {k: v for k, v in exchange["response"].items() if k not in ("body", "text_cells")}
        steps.append({"n": n, "request": exchange["request"], "actor": case["actor"],
                      "legacy": legacy, "service": service_summary(actual), "checks": checks})
        if approved:
            steps[-1]["approved"] = approval["change"]
    approved_db = {q["sql"]: q["rows"] for q in
                   (approval or {}).get("expected", {}).get("db_after", [])}
    db = []
    for query in case["db_after"]:
        got = parity_call(base, "/parity/query", query["sql"].encode())
        entry = {"sql": query["sql"], "columns": query["columns"],
                 "expected": approved_db.get(query["sql"], query["rows"]), "actual": got["rows"]}
        if query["sql"] in approved_db:
            entry["legacy"] = query["rows"]
            entry["approved"] = approval["change"]
        entry["ok"] = got["rows"] == entry["expected"]
        db.append(entry)
    return case["title"], steps, db, reviewer, "%s#%s" % (PROOF, case["id"])


def mapping_problems(mapping, scenarios):
    declared = routes().get("payment_scenarios", {})
    problems = []
    for name in scenarios:
        if declared.get(name, {}).get("rules") != mapping.get(name, []):
            problems.append("parity/routes.json payment_scenarios.%s.rules %r != spec %r"
                            % (name, declared.get(name, {}).get("rules"), mapping.get(name, [])))
    for name in set(declared) - set(scenarios):
        problems.append("parity/routes.json payment_scenarios.%s has no scenario" % name)
    return problems


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("PAYMENT_BASE", "http://localhost:8084"))
    args = parser.parse_args()

    proof = json.loads(read(PROOF))
    cases = {case["id"]: case for case in proof["cases"]}
    scenarios = TRANSCRIPTS + list(cases)
    mapping = spec_mapping()
    problems = mapping_problems(mapping, scenarios)
    sections = precondition_sections()
    for name, case in cases.items():
        if sections.get(name, {}).get("sql") != case["precondition"]["sql"]:
            problems.append("%s section %s differs from the proof precondition" % (PRECONDITIONS, name))
    if problems:
        raise SystemExit("\n".join(problems))
    approvals = {e["scenario"]: e for e in routes().get("approved_differences", [])
                 if e["scenario"] in scenarios}

    rows = []
    failed = False
    for name in scenarios:
        parity_call(args.base, "/parity/reset", params={"scenario": name})
        approval = approvals.get(name)
        if name in cases:
            description, steps, db, reviewer, source = replay_case(args.base, cases[name], approval)
        else:
            description, steps, db, reviewer, source = replay_transcript(args.base, name, approval)
        diffs = ["step %d %s: expected %s, got %s" % (s["n"], c["what"],
                                                     json.dumps(c["expected"]), json.dumps(c["actual"]))
                 for s in steps for c in s["checks"] if not c["ok"]]
        diffs += ["db %s: expected %s, got %s" % (d["sql"], json.dumps(d["expected"]), json.dumps(d["actual"]))
                  for d in db if not d["ok"]]
        if approval:
            verdict = "CHANGED (%s)" % approval["change"] if not diffs else "FAIL"
            note = approval["summary"] if not diffs else "; ".join(diffs)
        else:
            verdict = "PASS" if not diffs else "FAIL"
            note = "; ".join(diffs) or "-"
        failed = failed or verdict == "FAIL"
        basis = [EQUIVALENCE] if reviewer else []
        rows.append({
            "slice": "payment",
            "scenario": name,
            "description": description,
            "rules": mapping.get(name, []),
            "precondition": {"file": PRECONDITIONS, "name": sections[name]["name"],
                             "sql": sections[name]["sql"]},
            "source": source,
            "verdict": verdict,
            "check": "reviewer" if reviewer or approval else "automated",
            "basis": basis,
            "change": approval["change"] if approval else None,
            "record": approval["record"] if approval else None,
            "difference": note,
            "steps": steps,
            "db": db,
        })
        print("%-28s %-7s %-9s %s" % (name, verdict, rows[-1]["check"], "; ".join(diffs)[:300]))

    report.write_slice("payment", rows)
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()

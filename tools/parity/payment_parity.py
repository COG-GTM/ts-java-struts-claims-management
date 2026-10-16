#!/usr/bin/env python3
"""Replay the payment parity scenarios against the payment service.

The scenarios are the ``payment_scenarios`` of ``parity/routes.json``. Each one
is either a recorded transcript (``transcripts/payment_*.json``) or a request
copied verbatim, with its legacy response, from the live run recorded in
``docs/specs/SPEC-PAY-001-live.json``. Every scenario names its database
precondition: the scripts under ``parity/preconditions/payment`` that
``POST /parity/reset`` on the service (``make payment-run-parity``, port 8084,
Spring profile ``parity``) applies on top of the legacy seed before the request
is sent. Scenarios therefore do not depend on the order they run in.

The comparison is the one ADR-002 ("How parity is judged") defines: status
class, business field values, validation keys, and the database state read
back through the service's own read endpoints (and, for stored amounts and
settlement ids, the parity profile's raw row read). The legacy forward name is
compared with the response's ``screen``. A scenario listed under
``approved_differences`` in parity/routes.json is judged against the response
its ``docs/changes/CHG-nnn`` record approves and reported as ``CHANGED``
(ADR-002, "Behaviour freeze"); any other difference is a FAIL.

Usage: python3 tools/parity/payment_parity.py [--base http://localhost:8084]
Writes its rows into parity/report.json, parity/report.md and
parity/report.html through tools/parity/report.py, next to the settlement rows,
and exits non-zero on any FAIL.
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

# legacy path -> service path (ADR-002, "Endpoints")
PATHS = {
    "/claims/payment/issue.do": "/payment/issue",
    "/claims/payment/history.do": "/payment/history",
    "/claims/payment/detail.do": "/payment/detail",
    "/claims/payment/remittance.do": "/payment/remittance",
}


def routes():
    with open(os.path.join(ROOT, "parity", "routes.json")) as handle:
        return json.load(handle)


def scenarios(config):
    """Every payment scenario with its request and expected block resolved.

    A scenario with a ``transcript`` takes both from ``transcripts/<name>.json``
    and its rules from ``transcript_rules``; one with its own ``request`` is a
    live-run step (``observed``).
    """
    result = []
    for entry in config.get("payment_scenarios", []):
        scenario = dict(entry)
        if "transcript" in entry:
            with open(os.path.join(ROOT, "transcripts", entry["transcript"] + ".json")) as handle:
                transcript = json.load(handle)
            scenario["request"] = transcript["request"]
            scenario["expected"] = transcript["expected"]
            scenario["description"] = transcript["description"]
            scenario["rules"] = config.get("transcript_rules", {}).get(entry["transcript"], [])
            scenario["source"] = {"transcript": entry["transcript"]}
        else:
            scenario["source"] = {"observed": entry["observed"]}
        result.append(scenario)
    return result


def status_class(status):
    return "%dxx" % (status // 100)


def reset(base, preconditions):
    """PAY scenarios state their precondition; the parity profile applies it."""
    query = urllib.parse.urlencode([("precondition", name) for name in preconditions])
    req = urllib.request.Request(base + "/parity/reset?" + query, data=b"", method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        raise SystemExit("reset with %s refused by the service: %s %s" % (
            preconditions, error.code, error.read().decode()))
    except urllib.error.URLError as error:
        raise SystemExit("payment service not reachable at %s (make payment-run-parity): %s" % (base, error))


def call(base, request):
    form = dict(request.get("form", {}))
    data = urllib.parse.urlencode(form)
    url = base + PATHS[request["path"]]
    if request["method"] == "GET":
        url += "?" + data if form else ""
        req = urllib.request.Request(url, method="GET")
    else:
        req = urllib.request.Request(url, data=data.encode(), method=request["method"])
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req) as response:
            status, body = response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        status, body = error.code, json.loads(error.read().decode())
    return status, body, {"method": request["method"], "url": url, "form": form}


def fetch_fields(base, path):
    try:
        with urllib.request.urlopen(base + path) as response:
            return json.loads(response.read().decode())["fields"]
    except urllib.error.HTTPError as error:
        return json.loads(error.read().decode()).get("fields", {})


def fetch_json(base, path):
    try:
        with urllib.request.urlopen(base + path) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        return {}


def expected_screen(result):
    """The forward name of a legacy result: ``forward:/WEB-INF/jsp/payment/issue.jsp``
    (transcript) or ``forward /WEB-INF/jsp/payment/issue.jsp`` (live run) is
    ``issue``; any ``error.jsp`` result is ``error`` (PAY-R29)."""
    if "error.jsp" in result:
        return "error"
    return result.rsplit("/", 1)[-1].replace(".jsp", "")


def db_state(base, expected):
    """Read the db_state keys out of the service database.

    ``payment.<id>.amount`` and ``payment.<id>.status`` are ``detailPaymentAmount``
    and ``detailPaymentStatus`` of ``GET /payment/detail?paymentId=<id>``
    (PAY-R22, PAY-R24; src/main/java/com/northstar/claims/dao/PaymentDAO.java:26-44).
    ``payment.count.claim.<id>`` is ``remitCount`` of
    ``GET /payment/remittance?claimId=<id>`` (PAY-R27;
    src/main/java/com/northstar/claims/dao/PaymentDAO.java:100-119). A missing
    payment reads as None.

    ``payment.<id>.stored_amount`` and ``payment.<id>.settlement_id`` are the raw
    PAYMENT columns from the parity profile's ``GET /parity/payment/<id>``
    (``amount`` as Double.toString), so PAY-R09 (12.345 stored unrounded) and
    PAY-R05 (the newest settlement's id) are checked against the live run's
    JDBC probe rather than through the rounding screens.
    """
    actual = {}
    for key in expected:
        parts = key.split(".")
        if parts[0] == "payment" and parts[2] == "stored_amount":
            actual[key] = fetch_json(base, "/parity/payment/" + parts[1]).get("amount")
        elif parts[0] == "payment" and parts[2] == "settlement_id":
            actual[key] = fetch_json(base, "/parity/payment/" + parts[1]).get("settlement_id")
        elif parts[:3] == ["payment", "count", "claim"]:
            actual[key] = fetch_fields(base, "/payment/remittance?claimId=" + parts[3]).get("remitCount")
        elif parts[0] == "payment" and parts[2] == "amount":
            actual[key] = fetch_fields(base, "/payment/detail?paymentId=" + parts[1]).get("detailPaymentAmount")
        elif parts[0] == "payment" and parts[2] == "status":
            actual[key] = fetch_fields(base, "/payment/detail?paymentId=" + parts[1]).get("detailPaymentStatus")
        else:
            raise SystemExit("unknown db_state key %s" % key)
    return actual


def compare(expected, status, body, actual_db):
    diffs = []
    if status_class(status) != status_class(expected["status"]) and not (
            "error.jsp" in expected["result"] and status >= 500):
        diffs.append("status class %s != %s" % (status_class(status), status_class(expected["status"])))
    if body.get("screen") != expected_screen(expected["result"]):
        diffs.append("screen %r != %r" % (body.get("screen"), expected_screen(expected["result"])))
    fields = body.get("fields", {})
    if fields != expected["business_fields"]:
        for key, value in expected["business_fields"].items():
            got = fields.get(key)
            if got != value:
                diffs.append("%s %r != %r" % (key, got, value))
        for key in fields.keys() - expected["business_fields"].keys():
            diffs.append("unexpected field %s=%r" % (key, fields[key]))
    if body.get("errors", []) != expected["validation_errors"]:
        diffs.append("validation %r != %r" % (body.get("errors"), expected["validation_errors"]))
    for key, value in expected["db_state"].items():
        if actual_db[key] != value:
            diffs.append("db %s %r != %r" % (key, actual_db[key], value))
    return diffs


def compare_approved(approval, status, body, actual_db):
    """Compare against the response a CHG-nnn record approves, not the legacy one."""
    expected = approval["expected"]
    diffs = []
    if status != expected["status"]:
        diffs.append("status %s != %s" % (status, expected["status"]))
    if "screen" in expected and body.get("screen") != expected["screen"]:
        diffs.append("screen %r != %r" % (body.get("screen"), expected["screen"]))
    if body.get("fields", {}) != expected["business_fields"]:
        diffs.append("fields %r != %r" % (body.get("fields"), expected["business_fields"]))
    if body.get("errors", []) != expected["validation_errors"]:
        diffs.append("validation %r != %r" % (body.get("errors"), expected["validation_errors"]))
    for key, value in expected["db_state"].items():
        if actual_db[key] != value:
            diffs.append("db %s %r != %r" % (key, actual_db[key], value))
    return diffs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("PAYMENT_BASE", "http://localhost:8084"))
    args = parser.parse_args()
    config = routes()
    approvals = {entry["scenario"]: entry for entry in config.get("approved_differences", [])}

    rows = []
    failed = False
    for scenario in scenarios(config):
        name = scenario["scenario"]
        preconditions = scenario.get("precondition", [])
        reset(args.base, preconditions)
        status, body, service_request = call(args.base, scenario["request"])
        approval = approvals.get(name)
        expected = approval["expected"] if approval else scenario["expected"]
        actual_db = db_state(args.base, expected["db_state"])
        if approval:
            diffs = compare_approved(approval, status, body, actual_db)
            verdict = "CHANGED (%s)" % approval["change"] if not diffs else "FAIL"
            note = approval["summary"] if not diffs else "; ".join(diffs)
        else:
            diffs = compare(expected, status, body, actual_db)
            verdict = "PASS" if not diffs else "FAIL"
            note = "; ".join(diffs) or "-"
        failed = failed or bool(diffs)
        rows.append({
            "scenario": name,
            "slice": "payment",
            "description": scenario["description"],
            "verdict": verdict,
            "change": approval["change"] if approval else None,
            "record": approval["record"] if approval else None,
            "difference": note,
            "source": scenario["source"],
            "precondition": preconditions,
            "rules": scenario.get("rules", []),
            "request": scenario["request"],
            "service_request": service_request,
            "expected": scenario["expected"],
            "approved_expected": approval["expected"] if approval else None,
            "actual": {
                "status": status,
                "screen": body.get("screen"),
                "fields": body.get("fields", {}),
                "errors": body.get("errors", []),
                "db_state": actual_db,
            },
        })
        print("%-44s %s %s" % (name, verdict, "; ".join(diffs)))
    report.write("payment_", rows)
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()

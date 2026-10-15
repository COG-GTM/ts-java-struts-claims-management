#!/usr/bin/env python3
"""Replay the payment transcripts against the payment service.

Each transcript under ``transcripts/payment_*.json`` records what the legacy
Struts screens do. This script replays the same request against the service on
port 8084 (``make payment-run``) and compares the four things ADR-002 defines
as parity (docs/decisions/ADR-002-payment-boundary.md, "How parity is
judged"): status class, the business field values, the validation keys, and
the database state.

Scenarios are replayed in the order of ``transcripts/index.json``, because
``payment_history`` expects the payment that ``payment_issue`` creates. The
legacy capture replays every scenario against one database, so the service
has to start with the state the capture had reached before the payment
scenarios (``make payment-run-parity``, which loads
parity/preconditions/payment.sql on top of the seed).

A scenario listed under ``approved_differences`` in parity/routes.json is an
approved difference (ADR-002, "Behaviour freeze", as amended by CHG-002): it is
compared against the response its ``docs/changes/CHG-nnn`` record approves and
reported as ``CHANGED (CHG-nnn)``; anything else is still a FAIL. An approved
difference that no transcript exercises carries its own ``request`` block and
is replayed after the transcripts, so the approved behaviour is still checked
on every run even though transcripts/ (written only by ``make capture``) has
no recording of it.

Usage: python3 tools/parity/payment_parity.py [--base http://localhost:8084]
Writes its rows into parity/report.md and parity/report.json through
tools/parity/report.py, next to the settlement rows, and exits non-zero on any
FAIL.
"""

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import report  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# transcript path -> service path (ADR-002, "Endpoints")
PATHS = {
    "/claims/payment/issue.do": "/payment/issue",
    "/claims/payment/history.do": "/payment/history",
    "/claims/payment/detail.do": "/payment/detail",
    "/claims/payment/remittance.do": "/payment/remittance",
}


def payment_scenarios():
    """The payment scenarios in transcripts/index.json order (issue before history).

    Selected by scenario name: transcripts/index.json files the two payment
    transcripts under module ``settlement``, and transcripts/ is generated.
    """
    with open(os.path.join(ROOT, "transcripts", "index.json")) as handle:
        index = json.load(handle)
    return [entry["scenario"] for entry in index if entry["scenario"].startswith("payment_")]


def approved_differences():
    """The payment approved differences of parity/routes.json, keyed by scenario."""
    path = os.path.join(ROOT, "parity", "routes.json")
    if not os.path.exists(path):
        return {}
    with open(path) as handle:
        routes = json.load(handle)
    return {entry["scenario"]: entry for entry in routes.get("approved_differences", [])
            if entry["scenario"].startswith("payment_")}


PRECONDITION_HINT = ("payment_issue tripped the PAY-R05 (v2) cap: the service was started "
                     "without the legacy capture's preconditions; use make payment-run-parity")


def status_class(status):
    return "%dxx" % (status // 100)


def call(base, scenario):
    request = scenario["request"]
    path = PATHS[request["path"]]
    form = dict(request.get("form", {}))
    data = urllib.parse.urlencode(form).encode()
    url = base + path
    if request["method"] == "GET":
        req = urllib.request.Request(url + ("?" + data.decode() if form else ""), method="GET")
    else:
        req = urllib.request.Request(url, data=data, method=request["method"])
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode())


def fetch_fields(base, path):
    with urllib.request.urlopen(base + path) as response:
        return json.loads(response.read().decode())["fields"]


def db_state(base, expected):
    """Read the db_state keys the payment transcripts use out of the service database.

    ``payment.<id>.amount`` and ``payment.<id>.status`` are read back through
    ``GET /payment/detail?paymentId=<id>`` (PAY-R21), which selects the row by
    primary key (src/main/java/com/northstar/claims/dao/PaymentDAO.java:26-44).
    ``payment.count.claim.<id>`` is ``remitCount`` of
    ``GET /payment/remittance?claimId=<id>`` (PAY-R26), the size of the claim's
    payment list (src/main/java/com/northstar/claims/dao/PaymentDAO.java:100-119).
    """
    actual = {}
    for key in expected:
        parts = key.split(".")
        if parts[0] == "payment" and parts[1] == "count" and parts[2] == "claim":
            actual[key] = fetch_fields(base, "/payment/remittance?claimId=" + parts[3])["remitCount"]
        elif parts[0] == "payment" and parts[2] == "amount":
            actual[key] = fetch_fields(base, "/payment/detail?paymentId=" + parts[1])["detailPaymentAmount"]
        elif parts[0] == "payment" and parts[2] == "status":
            actual[key] = fetch_fields(base, "/payment/detail?paymentId=" + parts[1])["detailPaymentStatus"]
        else:
            raise SystemExit("unknown db_state key %s" % key)
    return actual


def compare(base, transcript, status, body):
    expected = transcript["expected"]
    diffs = []
    if status_class(status) != status_class(expected["status"]) and not (
            expected["result"].endswith("error.jsp") and status >= 500):
        diffs.append("status class %s != %s" % (status_class(status), status_class(expected["status"])))
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
    actual_db = db_state(base, expected["db_state"])
    for key, value in expected["db_state"].items():
        if actual_db[key] != value:
            diffs.append("db %s %r != %r" % (key, actual_db[key], value))
    return diffs


def compare_approved(base, approval, status, body):
    """Compare against the response a CHG-nnn record approves, not the transcript."""
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
    actual_db = db_state(base, expected["db_state"])
    for key, value in expected["db_state"].items():
        if actual_db[key] != value:
            diffs.append("db %s %r != %r" % (key, actual_db[key], value))
    return diffs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("PAYMENT_BASE", "http://localhost:8084"))
    args = parser.parse_args()
    approvals = approved_differences()
    rows = []
    failed = False

    def record(name, description, verdict, approval, note):
        rows.append({
            "scenario": name,
            "description": description,
            "verdict": verdict,
            "change": approval["change"] if approval else None,
            "record": approval["record"] if approval else None,
            "difference": note,
        })
        print("%-32s %s %s" % (name, verdict, note if verdict == "FAIL" else ""))

    transcripts = payment_scenarios()
    for name in transcripts:
        with open(os.path.join(ROOT, "transcripts", name + ".json")) as handle:
            transcript = json.load(handle)
        status, body = call(args.base, transcript)
        approval = approvals.get(name)
        if approval:
            diffs = compare_approved(args.base, approval, status, body)
            verdict = "CHANGED (%s)" % approval["change"] if not diffs else "FAIL"
            note = approval["summary"] if not diffs else "; ".join(diffs)
        else:
            diffs = compare(args.base, transcript, status, body)
            if name == "payment_issue" and cap_tripped(body):
                diffs.append(PRECONDITION_HINT)
            verdict = "PASS" if not diffs else "FAIL"
            note = "; ".join(diffs) or "-"
        failed = failed or bool(diffs)
        record(name, transcript["description"], verdict, approval, note)

    # Approved differences with no transcript: replayed from their own request.
    for name, approval in approvals.items():
        if name in transcripts:
            continue
        if "request" not in approval:
            raise SystemExit("%s is not a transcript and has no request block in parity/routes.json" % name)
        status, body = call(args.base, approval)
        diffs = compare_approved(args.base, approval, status, body)
        verdict = "CHANGED (%s)" % approval["change"] if not diffs else "FAIL"
        note = approval["summary"] if not diffs else "; ".join(diffs)
        failed = failed or bool(diffs)
        record(name, approval["description"], verdict, approval, note)

    report.write("payment_", rows)
    raise SystemExit(1 if failed else 0)


def cap_tripped(body):
    return body.get("errors") == ["payment.amount.exceedsSettlement"]


if __name__ == "__main__":
    main()

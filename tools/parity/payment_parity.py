#!/usr/bin/env python3
"""Replay the payment transcripts against the payment service.

Each transcript under ``transcripts/payment_*.json`` records what the legacy
Struts screens do. This script replays the same request against the service on
port 8084 (``make payment-run``) and compares the four things ADR-002 defines
as parity (docs/decisions/ADR-002-payment-boundary.md, "How parity is
judged"): status class, the business field values, the validation keys, and
the database state.

Scenarios are replayed in the order of ``transcripts/index.json``, because
``payment_history`` expects the payment that ``payment_issue`` creates. A
scenario listed in ``payment_preconditions`` of parity/routes.json first resets
the service database to the seed plus that script from
parity/preconditions/payment (``POST /parity/reset``, available under
``make payment-run-parity``); an unlisted one continues from the previous
scenario's database.

Approved differences (ADR-002 as amended by CHG-002, "Behaviour freeze") are
judged against the response their ``docs/changes/CHG-nnn`` record approves and
reported as ``CHANGED (CHG-nnn)``; anything else is still a FAIL. An approved
difference with its own ``request`` has no transcript: it is replayed after the
transcripts, on its own ``precondition``.

Usage: python3 tools/parity/payment_parity.py [--base http://localhost:8084]
Writes its rows into parity/report.md, parity/report.json and
parity/report.html through tools/parity/report.py, next to the settlement
rows, and exits non-zero on any FAIL.
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


def routes():
    path = os.path.join(ROOT, "parity", "routes.json")
    if not os.path.exists(path):
        return {}
    with open(path) as handle:
        return json.load(handle)


def reset(base, precondition):
    """Rebuild the service database: seed plus one precondition script."""
    query = urllib.parse.urlencode({"precondition": precondition})
    req = urllib.request.Request(base + "/parity/reset?" + query, data=b"", method="POST")
    req.add_header("X-Parity-Reset", "1")
    try:
        with urllib.request.urlopen(req) as response:
            response.read()
    except urllib.error.HTTPError as error:
        raise SystemExit("POST /parity/reset?%s answered %d: start the payment service "
                         "with make payment-run-parity" % (query, error.code))


def status_class(status):
    return "%dxx" % (status // 100)


def call(base, request):
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
    """Judge the response against what the CHG-nnn record approves."""
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
    return diffs, actual_db


def row(name, description, approval, diffs, precondition, request, status, body, db):
    if approval and not diffs:
        verdict = "CHANGED (%s)" % approval["change"]
        difference = approval["summary"]
    else:
        verdict = "PASS" if not diffs else "FAIL"
        difference = "; ".join(diffs) or "-"
    print("%-32s %s %s" % (name, verdict, "; ".join(diffs)))
    return {
        "scenario": name,
        "description": description,
        "verdict": verdict,
        "change": approval["change"] if approval else None,
        "record": approval["record"] if approval else None,
        "difference": difference,
        "precondition": precondition,
        "service_request": request,
        "actual": {"status": status, "body": body, "db_state": db},
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("PAYMENT_BASE", "http://localhost:8084"))
    args = parser.parse_args()
    config = routes()
    preconditions = config.get("payment_preconditions", {})
    approvals = {entry["scenario"]: entry for entry in config.get("approved_differences", [])
                 if entry["scenario"].startswith("payment_")}
    rows = []
    transcripts = payment_scenarios()
    for name in transcripts:
        with open(os.path.join(ROOT, "transcripts", name + ".json")) as handle:
            transcript = json.load(handle)
        precondition = preconditions.get(name)
        if precondition:
            reset(args.base, precondition)
        status, body = call(args.base, transcript["request"])
        approval = approvals.get(name)
        if approval:
            diffs, db = compare_approved(args.base, approval, status, body)
        else:
            db = db_state(args.base, transcript["expected"]["db_state"])
            diffs = compare(args.base, transcript, status, body)
        rows.append(row(name, transcript["description"], approval, diffs, precondition,
                        transcript["request"], status, body, db))
    for name, approval in approvals.items():
        if name in transcripts:
            continue
        if "request" not in approval or "precondition" not in approval:
            raise SystemExit("%s has no transcript, so parity/routes.json must give it a "
                             "request and a precondition" % name)
        reset(args.base, approval["precondition"])
        status, body = call(args.base, approval["request"])
        diffs, db = compare_approved(args.base, approval, status, body)
        rows.append(row(name, approval.get("description", approval["summary"]), approval,
                        diffs, approval["precondition"], approval["request"], status, body, db))
    report.write("payment_", rows)
    raise SystemExit(1 if any(item["verdict"] == "FAIL" for item in rows) else 0)


if __name__ == "__main__":
    main()

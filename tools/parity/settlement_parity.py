#!/usr/bin/env python3
"""Replay the settlement transcripts against the settlement service.

Each transcript under ``transcripts/settlement_*.json`` records what the legacy
Struts screens do. This script replays the same request against the service on
port 8083 (``make service-run``) and compares the four things ADR-001 defines as
parity (docs/decisions/ADR-001-settlement-boundary.md:82-90): status class, the
business field values, the validation keys, and the database state.

Usage: python3 tools/parity/settlement_parity.py [--base http://localhost:8083]
Writes parity/settlement-parity.md and exits non-zero on any FAIL.
"""

import argparse
import glob
import json
import os
import subprocess
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# transcript path -> service path (ADR-001:40-47)
PATHS = {
    "/claims/settlement/calculate.do": "/settlement/calculate",
    "/claims/settlement/save.do": "/settlement/save",
    "/claims/settlement/detail.do": "/settlement/detail",
}


def status_class(status):
    return "%dxx" % (status // 100)


def call(base, transcript):
    request = transcript["request"]
    path = PATHS[request["path"]]
    form = dict(request.get("form", {}))
    if path == "/settlement/save":
        # ADR-001:56-63 - the session operator becomes an explicit parameter.
        form["user"] = transcript.get("actor", "supervisor")
    data = urllib.parse.urlencode(form).encode()
    url = base + path
    req = urllib.request.Request(url, data=data, method=request["method"])
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode())


def db_state(base, expected):
    """Read the db_state keys the transcripts use out of the service database.

    ``settlement.claim.<id>.amount`` is read back through
    ``GET /settlement/detail``, which selects the highest ``settlement_id`` for
    the claim (src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-114).
    ``claim.<id>.status`` is read from the seed the service loads: the settlement
    slice never writes to CLAIM (src/main/java/com/northstar/claims/web/
    SettlementSaveAction.java:29-45).
    """
    actual = {}
    for key in expected:
        parts = key.split(".")
        if parts[0] == "settlement" and parts[1] == "claim":
            url = base + "/settlement/detail?claimId=" + parts[2]
            with urllib.request.urlopen(url) as response:
                actual[key] = json.loads(response.read().decode())["fields"]["detailAmount"]
        elif parts[0] == "claim":
            actual[key] = seeded_claim_status(parts[1])
        else:
            raise SystemExit("unknown db_state key %s" % key)
    return actual


def seeded_claim_status(claim_id):
    seed = os.path.join(ROOT, "src", "main", "resources", "db", "seed.sql")
    prefix = "INSERT INTO CLAIM VALUES (%s," % claim_id
    with open(seed) as handle:
        for line in handle:
            if line.startswith(prefix):
                return line.split(",")[8].strip().strip("'")
    raise SystemExit("claim %s not seeded" % claim_id)


def compare(name, transcript, status, body):
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
    actual_db = db_state(BASE, expected["db_state"])
    for key, value in expected["db_state"].items():
        if actual_db[key] != value:
            diffs.append("db %s %r != %r" % (key, actual_db[key], value))
    return diffs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("SERVICE_BASE", "http://localhost:8083"))
    args = parser.parse_args()
    global BASE
    BASE = args.base

    rows = []
    failed = False
    for path in sorted(glob.glob(os.path.join(ROOT, "transcripts", "settlement_*.json"))):
        name = os.path.splitext(os.path.basename(path))[0]
        with open(path) as handle:
            transcript = json.load(handle)
        status, body = call(args.base, transcript)
        diffs = compare(name, transcript, status, body)
        verdict = "PASS" if not diffs else "FAIL"
        failed = failed or bool(diffs)
        rows.append((name, transcript["description"], verdict, "; ".join(diffs) or "-"))
        print("%-32s %s %s" % (name, verdict, "; ".join(diffs)))

    revision = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT).decode().strip()
    out = os.path.join(ROOT, "parity", "settlement-parity.md")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as handle:
        handle.write("# Settlement parity\n\n")
        handle.write("Generated by `make parity` (tools/parity/settlement_parity.py) at %s.\n" % revision)
        handle.write("Each row replays a `transcripts/settlement_*.json` scenario against the\n")
        handle.write("settlement service on 8083 and compares the four things ADR-001:82-90\n")
        handle.write("calls parity: status class, business fields, validation keys, db state.\n\n")
        handle.write("| Scenario | Legacy behaviour | Verdict | Difference |\n")
        handle.write("| --- | --- | --- | --- |\n")
        for row in rows:
            handle.write("| `%s` | %s | %s | %s |\n" % row)
        handle.write("\nStatus class: the legacy error path forwards to `error.jsp` with a 200;\n")
        handle.write("ADR-001:51,84 replaces that forward with the equivalent error status\n")
        handle.write("class, so a 5xx there is parity, not a change.\n")
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()

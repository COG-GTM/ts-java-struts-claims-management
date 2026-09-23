#!/usr/bin/env python3
"""Replay the captured legacy transcripts against the settlement service.

Each transcript under transcripts/ records what the Struts application did
for one request: HTTP status, the JSP it forwarded to, the business fields
the JSP rendered (the <span id="f_..."> values), the validation error keys
and a few database probes. This script sends the same request to the new
service, which answers {"screen": ..., "fields": {...}, "errors": [...]},
and compares (ADR-001, decision 6):

  status     must be equal
  result     forward:/WEB-INF/jsp/<screen>.jsp  <->  "screen": "<screen>"
  fields     every legacy field with the same value, and no extra field
  errors     the same validation keys, order ignored
  db_state   each probe re-read through the service's own read screen

Database probes mirror tools/capture/capture.py. The save scenario probes
GET /settlement/detail?claimId=N and reads detailAmount, the amount of the
row that was actually written (SETTLE-R12, SETTLE-R13). A probe whose legacy
read screen is outside the slice (claim.N.status is read through
/workbench/view.do) cannot be re-read through this service; it is listed in
the Note column as not probed and does not decide the verdict.

Verdicts: PASS, FAIL, SKIP (scenario belongs to a screen this service does
not serve). Exit code 1 when any scenario FAILs.

Usage: python3 parity/replay.py --base-url http://localhost:8083
           [--module settlement] [--report parity/report.md]
           [--json parity/report.json]
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TRANSCRIPTS = os.path.join(ROOT, "transcripts")
LEGACY_CONTEXT = "/claims"

# Legacy screens served by the settlement service, and the rules each
# transcript exercises (SPEC-SETTLE-001).
ROUTED = {
    "/settlement/calculate": ["SETTLE-R01", "SETTLE-R02", "SETTLE-R03 v2",
                              "SETTLE-R04", "SETTLE-R05", "SETTLE-R06",
                              "SETTLE-R07", "SETTLE-R08", "SETTLE-R09 v2",
                              "SETTLE-R10", "SETTLE-R11"],
    "/settlement/save": ["SETTLE-R12"],
    "/settlement/detail": ["SETTLE-R13"],
}
SCENARIO_RULES = {
    "settlement_calculate": ["SETTLE-R01", "SETTLE-R05", "SETTLE-R07",
                             "SETTLE-R08", "SETTLE-R10", "SETTLE-R11"],
    "settlement_save": ["SETTLE-R12", "SETTLE-R13"],
    "settlement_blank_deductible": ["SETTLE-R04", "SETTLE-R07"],
    "settlement_deductible_floor": ["SETTLE-R07"],
    "settlement_policy_cap": ["SETTLE-R08"],
    "settlement_half_cent": ["SETTLE-R09 v2", "SETTLE-R10"],
    "settlement_bad_deductible": ["SETTLE-R06"],
}


# db_state keys whose legacy read screen is not served by this service.
UNROUTED_PROBES = {"claim.": "/workbench/view"}


def probe_for(key):
    """Maps a capture.py db_state key to (service path, field name)."""
    parts = key.split(".")
    if key.startswith("settlement.claim.") and key.endswith(".detail_amount"):
        return "/settlement/detail?claimId=" + parts[2], "detailAmount"
    return None


def unrouted_screen(key):
    for prefix, screen in UNROUTED_PROBES.items():
        if key.startswith(prefix):
            return screen
    return None


def load_transcripts(module):
    with open(os.path.join(TRANSCRIPTS, "index.json"), encoding="utf-8") as stream:
        index = json.load(stream)
    transcripts = []
    for entry in index:
        if entry["module"] != module:
            continue
        path = os.path.join(TRANSCRIPTS, entry["scenario"] + ".json")
        with open(path, encoding="utf-8") as stream:
            transcripts.append(json.load(stream))
    return transcripts


def service_path(legacy_path):
    if legacy_path.startswith(LEGACY_CONTEXT):
        legacy_path = legacy_path[len(LEGACY_CONTEXT):]
    path, _, query = legacy_path.partition("?")
    if path.endswith(".do"):
        path = path[:-3]
    return path, query


def expected_screen(result):
    prefix, suffix = "forward:/WEB-INF/jsp/", ".jsp"
    if result.startswith(prefix) and result.endswith(suffix):
        return result[len(prefix):-len(suffix)]
    if result.startswith("error:"):
        return "error"
    return result


def call(base_url, method, path, form):
    url = base_url + path
    data = None
    if method == "POST":
        data = urllib.parse.urlencode(form).encode("utf-8")
    elif form:
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode(form)
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as failure:
        body = failure.read().decode("utf-8")
        try:
            return failure.code, json.loads(body)
        except ValueError:
            return failure.code, {"screen": "http-%d" % failure.code,
                                  "fields": {}, "errors": [body[:200]]}


def compare(transcript, base_url):
    request = transcript["request"]
    expected = transcript["expected"]
    path, query = service_path(request["path"])
    status, body = call(base_url, request["method"],
                        path + ("?" + query if query else ""),
                        request.get("form", {}))
    differences = []
    if status != expected["status"]:
        differences.append(("status", expected["status"], status))
    want_screen = expected_screen(expected["result"])
    if body.get("screen") != want_screen:
        differences.append(("result", want_screen, body.get("screen")))
    got_fields = body.get("fields", {})
    for name, value in sorted(expected["business_fields"].items()):
        if got_fields.get(name) != value:
            differences.append(("field " + name, value, got_fields.get(name)))
    for name in sorted(got_fields):
        if name not in expected["business_fields"]:
            differences.append(("field " + name, None, got_fields[name]))
    if sorted(body.get("errors", [])) != sorted(expected["validation_errors"]):
        differences.append(("validation_errors", expected["validation_errors"],
                            body.get("errors", [])))
    unprobed = []
    for key, value in sorted(expected.get("db_state", {}).items()):
        probe = probe_for(key)
        if probe is None:
            screen = unrouted_screen(key)
            if screen is None:
                differences.append(("db_state " + key, value,
                                    "no probe defined"))
            else:
                unprobed.append("%s (read through %s, not served)"
                                % (key, screen))
            continue
        probe_path, field = probe
        _, probed = call(base_url, "GET", probe_path, {})
        got = probed.get("fields", {}).get(field)
        if got != value:
            differences.append(("db_state " + key, value, got))
    return differences, unprobed


def replay(base_url, module):
    rows = []
    for transcript in load_transcripts(module):
        scenario = transcript["scenario"]
        path, _ = service_path(transcript["request"]["path"])
        if path not in ROUTED:
            rows.append({"scenario": scenario, "rules": [], "verdict": "SKIP",
                         "differences": [], "unprobed": [],
                         "note": "screen not served by this service"})
            continue
        differences, unprobed = compare(transcript, base_url)
        rows.append({
            "scenario": scenario,
            "rules": SCENARIO_RULES.get(scenario, ROUTED[path]),
            "verdict": "PASS" if not differences else "FAIL",
            "differences": [{"what": what, "legacy": legacy, "service": got}
                            for what, legacy, got in differences],
            "unprobed": unprobed,
            "note": ("db_state not probed: " + "; ".join(unprobed))
                    if unprobed else "",
        })
    return rows


def render(rows, base_url, module, counts):
    summary = ", ".join("%s %d" % item for item in sorted(counts.items()))
    lines = [
        "# Parity report: %s" % module,
        "",
        "Service: `%s`. Fixtures: `transcripts/*.json` with module `%s`."
        % (base_url, module),
        "Generated by `make parity` (`parity/replay.py`).",
        "",
        "Result: %s." % summary,
        "",
        "| Scenario | Rules | Verdict | Differences (legacy -> service) | Note |",
        "| --- | --- | --- | --- | --- |",
    ]
    for row in rows:
        diff_text = "<br>".join(
            "%s: `%s` -> `%s`" % (d["what"], d["legacy"], d["service"])
            for d in row["differences"])
        lines.append("| %s | %s | %s | %s | %s |" % (
            row["scenario"], ", ".join(row["rules"]), row["verdict"],
            diff_text, row["note"]))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--module", default="settlement")
    parser.add_argument("--report", default=None)
    parser.add_argument("--json", dest="json_report", default=None)
    args = parser.parse_args()

    rows = replay(args.base_url.rstrip("/"), args.module)
    counts = {}
    for row in rows:
        counts[row["verdict"]] = counts.get(row["verdict"], 0) + 1
    report = render(rows, args.base_url, args.module, counts)
    sys.stdout.write(report)
    if args.report:
        with open(os.path.join(ROOT, args.report), "w", encoding="utf-8") as stream:
            stream.write(report)
    if args.json_report:
        with open(os.path.join(ROOT, args.json_report), "w",
                  encoding="utf-8") as stream:
            json.dump({"module": args.module, "base_url": args.base_url,
                       "summary": counts, "scenarios": rows},
                      stream, indent=2, sort_keys=True)
            stream.write("\n")
    return 1 if counts.get("FAIL") else 0


if __name__ == "__main__":
    sys.exit(main())

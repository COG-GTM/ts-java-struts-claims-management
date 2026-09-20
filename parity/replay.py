#!/usr/bin/env python3
"""Replay captured legacy transcripts against a modernised service.

Each transcript in transcripts/ records what the Struts application did for
one request: status, the JSP it forwarded to, the business fields the JSP
rendered, the validation error keys, and a few database probes. This script
sends the same request to the new service, which answers with
{"screen": ..., "fields": {...}, "errors": [...]}, and compares.

Verdicts:
  PASS     every compared value matches the transcript
  FAIL     at least one value differs and no change record approves it
  CHANGED  values differ exactly as an approved change record says they should
  SKIP     scenario belongs to a module that is not routed yet

Exit code is 1 when any scenario FAILs.
"""

import argparse
import glob
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TRANSCRIPTS = os.path.join(ROOT, "transcripts")
ROUTES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "routes.json")

# The legacy capture probes database state through read screens. The only
# probe family the settlement module uses recalculates from defaults for the
# claim (tools/capture/capture.py, SCENARIOS entry for settlement_save).
PROBES = {
    "settlement.claim.": lambda key: (
        "/settlement/calculate?claimId=" + key.split(".")[2], "settlementAmount"),
}


def load_routes():
    with open(ROUTES) as handle:
        return json.load(handle)


def load_transcripts(module):
    items = []
    with open(os.path.join(TRANSCRIPTS, "index.json")) as handle:
        index = json.load(handle)
    for entry in index:
        if entry["module"] != module:
            continue
        with open(os.path.join(TRANSCRIPTS, entry["scenario"] + ".json")) as handle:
            items.append(json.load(handle))
    return items


def service_path(legacy_path, context):
    if legacy_path.startswith(context):
        legacy_path = legacy_path[len(context):]
    path, _, query = legacy_path.partition("?")
    if path.endswith(".do"):
        path = path[:-3]
    return path + ("?" + query if query else "")


def expected_screen(result):
    if result.startswith("forward:/WEB-INF/jsp/") and result.endswith(".jsp"):
        return result[len("forward:/WEB-INF/jsp/"):-len(".jsp")]
    if result.startswith("error:"):
        return "error"
    return result


def call(base_url, method, path, form):
    url = base_url + path
    data = None
    if method == "POST":
        data = urllib.parse.urlencode(form).encode()
    elif form:
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode(form)
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as failure:
        body = failure.read().decode()
        try:
            return failure.code, json.loads(body)
        except ValueError:
            return failure.code, {"screen": "http-" + str(failure.code),
                                  "fields": {}, "errors": [body[:200]]}


def compare(transcript, base_url, context, unrouted_probes):
    request = transcript["request"]
    expected = transcript["expected"]
    status, body = call(base_url, request["method"],
                        service_path(request["path"], context),
                        request.get("form", {}))
    differences = []
    if status != expected["status"]:
        differences.append(("status", expected["status"], status))
    want_screen = expected_screen(expected["result"])
    if body.get("screen") != want_screen:
        differences.append(("result", want_screen, body.get("screen")))
    got_fields = body.get("fields", {})
    for name, value in expected["business_fields"].items():
        if got_fields.get(name) != value:
            differences.append(("field " + name, value, got_fields.get(name)))
    for name in got_fields:
        if name not in expected["business_fields"]:
            differences.append(("field " + name, None, got_fields[name]))
    if sorted(body.get("errors", [])) != sorted(expected["validation_errors"]):
        differences.append(("validation_errors", expected["validation_errors"],
                            body.get("errors", [])))
    unprobed = []
    for key, value in expected.get("db_state", {}).items():
        for prefix, resolve in PROBES.items():
            if key.startswith(prefix):
                probe_path, field = resolve(key)
                _, probe = call(base_url, "GET", probe_path, {})
                got = probe.get("fields", {}).get(field)
                if got != value:
                    differences.append(("db_state " + key, value, got))
                break
        else:
            if any(key.startswith(p) for p in unrouted_probes):
                unprobed.append(key)
            else:
                differences.append(("db_state " + key, value, "no probe defined"))
    return differences, unprobed


def approved(differences, approval):
    """A change record approves a difference when every differing item is
    listed with the exact new value the record predicts."""
    predicted = approval.get("expect", {})
    if len(differences) != len(predicted):
        return False
    for what, _, got in differences:
        if what not in predicted or predicted[what] != got:
            return False
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--module", default="settlement")
    parser.add_argument("--report", default=None)
    parser.add_argument("--json", dest="json_report", default=None)
    args = parser.parse_args()

    routes = load_routes()
    module = routes["modules"].get(args.module)
    if module is None:
        print("module %s is not routed" % args.module)
        return 2
    context = routes["legacy_context"]
    rows = []
    for transcript in load_transcripts(args.module):
        scenario = transcript["scenario"]
        if scenario not in module["scenarios"]:
            rows.append({"scenario": scenario, "rules": [], "verdict": "SKIP",
                         "differences": [], "unprobed": [],
                         "note": "screen not routed to this service"})
            continue
        differences, unprobed = compare(transcript, args.base_url, context,
                                        module.get("unrouted_probes", []))
        approval = module["approved_differences"].get(scenario)
        if not differences:
            verdict = "PASS"
        elif approval and approved(differences, approval):
            verdict = "CHANGED (%s)" % approval["change"]
        else:
            verdict = "FAIL"
        rows.append({
            "scenario": scenario,
            "rules": module["scenarios"].get(scenario, []),
            "verdict": verdict,
            "differences": [{"what": w, "legacy": e, "service": g}
                            for w, e, g in differences],
            "unprobed": unprobed,
            "note": ("db_state not checked (screen outside the slice): "
                     + ", ".join(unprobed)) if unprobed else "",
        })

    counts = {}
    for row in rows:
        counts[row["verdict"].split(" ")[0]] = counts.get(
            row["verdict"].split(" ")[0], 0) + 1
    summary = ", ".join("%s %d" % (k, v) for k, v in sorted(counts.items()))

    lines = ["# Parity report: %s" % args.module, "",
             "Service: `%s`. Fixtures: `transcripts/%s_*.json`." % (
                 args.base_url, args.module),
             "", "Result: %s." % summary, "",
             "| Scenario | Rules | Verdict | Differences (legacy -> service) | Note |",
             "| --- | --- | --- | --- | --- |"]
    for row in rows:
        diff_text = "<br>".join(
            "%s: `%s` -> `%s`" % (d["what"], d["legacy"], d["service"])
            for d in row["differences"]) or ""
        lines.append("| %s | %s | %s | %s | %s |" % (
            row["scenario"], ", ".join(row["rules"]), row["verdict"],
            diff_text, row["note"]))
    report = "\n".join(lines) + "\n"
    print(report)
    if args.report:
        with open(os.path.join(ROOT, args.report), "w") as handle:
            handle.write(report)
    if args.json_report:
        with open(os.path.join(ROOT, args.json_report), "w") as handle:
            json.dump({"module": args.module, "base_url": args.base_url,
                       "summary": counts, "scenarios": rows}, handle,
                      indent=2, sort_keys=True)
            handle.write("\n")
    return 1 if counts.get("FAIL") else 0


if __name__ == "__main__":
    sys.exit(main())

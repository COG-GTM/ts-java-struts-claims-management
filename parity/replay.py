#!/usr/bin/env python3
"""Replay recorded transcripts against an extracted service and judge parity.

Every scenario in ``transcripts/index.json`` that belongs to ``--module`` is
replayed against ``--base-url``. ``parity/routes.json`` says which legacy path
each service path answers, which scenarios this service handles at all, and
which differences are already approved by a ``docs/changes/CHG-nnn`` record.

What is compared (ADR-001 "How parity is judged"):

* the status class (2xx / 4xx / 5xx), not the exact Struts-forwarded 200,
* the business field values the transcript recorded,
* the validation error keys,
* the ``db_state`` probes, read back through the service.

HTML is not compared: the legacy markup, span ids, forward paths and the
message bundle are presentation (ADR-001).

Verdicts: PASS, FAIL (with each legacy -> service difference), SKIP (a screen
this service does not handle), CHANGED (an approved CHG-nnn difference).

Usage: python3 parity/replay.py --module settlement [--base-url URL]
Writes parity/report.md and parity/report.json; exits non-zero on any FAIL.
"""

import argparse
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def load_json(path):
    with open(path) as handle:
        return json.load(handle)


def status_class(status):
    return "%dxx" % (status // 100)


def request_json(url, data=None, method="GET"):
    request = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as failure:
        body = failure.read().decode()
        try:
            return failure.code, json.loads(body)
        except ValueError:
            return failure.code, {}


def replay(base_url, routes, transcript):
    """Send the recorded request to the service path that answers for it."""
    recorded = transcript["request"]
    path = routes["routes"][recorded["path"]]
    form = dict(recorded.get("form", {}))
    rewrite = routes.get("request_rewrites", {}).get(path)
    if rewrite and "actor_parameter" in rewrite:
        form[rewrite["actor_parameter"]] = transcript.get("actor", "")
    url = base_url + path
    if recorded["method"] == "GET":
        if form:
            url += "?" + urllib.parse.urlencode(form)
        return request_json(url)
    return request_json(url, urllib.parse.urlencode(form).encode(), recorded["method"])


def probe(base_url, routes, key):
    """Read one db_state probe key back out of the service.

    Returns (value, None) when the probe ran, or (None, reason) when the key
    names state outside this service's boundary.
    """
    parts = key.split(".")
    if len(parts) == 4 and parts[0] == "settlement" and parts[1] == "claim" and parts[3] == "amount":
        url = base_url + "/settlement/detail?claimId=" + parts[2]
        _, body = request_json(url)
        return body.get("fields", {}).get("detailAmount"), None
    for pattern, reason in routes.get("unprobeable", {}).items():
        if probe_matches(pattern, parts):
            return None, reason
    return None, "no probe defined for %s" % key


def probe_matches(pattern, parts):
    fields = pattern.split(".")
    if len(fields) != len(parts):
        return False
    return all(f.startswith("<") or f == p for f, p in zip(fields, parts))


def differences(base_url, routes, transcript, status, body):
    """Every legacy -> service difference, as (kind, description) pairs."""
    expected = transcript["expected"]
    found = []

    legacy_class = status_class(expected["status"])
    service_class = status_class(status)
    if legacy_class != service_class:
        found.append(("status_class", "status class: %s -> %s" % (legacy_class, service_class)))

    fields = body.get("fields", {})
    for name, value in sorted(expected["business_fields"].items()):
        if fields.get(name) != value:
            found.append(("field:%s" % name,
                          "%s: %r -> %r" % (name, value, fields.get(name))))
    for name in sorted(set(fields) - set(expected["business_fields"])):
        found.append(("field:%s" % name,
                      "%s: (not recorded) -> %r" % (name, fields[name])))

    errors = list(body.get("errors", []))
    if errors != list(expected["validation_errors"]):
        found.append(("validation",
                      "validation keys: %r -> %r" % (expected["validation_errors"], errors)))

    notes = []
    for key, value in sorted(expected["db_state"].items()):
        actual, reason = probe(base_url, routes, key)
        if reason is not None:
            notes.append("db %s not probed: %s" % (key, reason))
        elif actual != value:
            found.append(("db:%s" % key, "db %s: %r -> %r" % (key, value, actual)))
    return found, notes


def approvals(routes, scenario, kind):
    for approved in routes.get("approved_differences", []):
        if scenario in approved["scenarios"] and approved["kind"] == kind:
            return approved
    return None


def judge(routes, scenario, found):
    """PASS, CHANGED or FAIL, with the failing and approved differences split."""
    failures, changes = [], []
    for kind, description in found:
        approved = approvals(routes, scenario, kind)
        if approved:
            changes.append((approved["id"], description))
        else:
            failures.append(description)
    if failures:
        return "FAIL", failures, changes
    if changes:
        return "CHANGED", failures, changes
    return "PASS", failures, changes


def write_report(results, module, base_url, routes, revision):
    counts = {verdict: 0 for verdict in ("PASS", "CHANGED", "SKIP", "FAIL")}
    for result in results:
        counts[result["verdict"]] += 1
    overall = "FAIL" if counts["FAIL"] else "PASS"

    lines = ["# Parity report — %s module vs %s" % (module, routes["service"]), ""]
    lines.append("Result: %s — %d PASS, %d CHANGED, %d FAIL, %d SKIP "
                 "(%s, %s at %s)." % (overall, counts["PASS"], counts["CHANGED"],
                                      counts["FAIL"], counts["SKIP"], base_url,
                                      "parity/replay.py", revision))
    lines += ["",
              "Compared per ADR-001: status class, business fields, validation keys and",
              "the `db_state` probes read back through the service. HTML is not compared.",
              "",
              "| Scenario | Verdict | SETTLE-R rules exercised | Detail |",
              "| --- | --- | --- | --- |"]
    for result in results:
        rules = ", ".join(result["rules"]) or "-"
        detail = result["detail"] or "-"
        lines.append("| `%s` | %s | %s | %s |" % (result["scenario"], result["verdict"], rules, detail))
    changed = [approved for approved in routes.get("approved_differences", [])
               if any(r["verdict"] == "CHANGED" and approved["id"] in r["change_ids"] for r in results)]
    if changed:
        lines += ["", "## Approved differences"]
        for approved in changed:
            lines.append("* **%s** (%s) — %s: legacy %s -> service %s. %s"
                         % (approved["id"], approved["record"], approved["kind"],
                            approved["legacy"], approved["service"], approved["why"]))
    notes = [(r["scenario"], note) for r in results for note in r["notes"]]
    if notes:
        lines += ["", "## Probes not run"]
        for scenario, note in notes:
            lines.append("* `%s` — %s" % (scenario, note))
    lines.append("")

    with open(os.path.join(HERE, "report.md"), "w") as handle:
        handle.write("\n".join(lines))
    with open(os.path.join(HERE, "report.json"), "w") as handle:
        json.dump({"module": module, "service": routes["service"], "base_url": base_url,
                   "revision": revision, "result": overall, "counts": counts,
                   "scenarios": results}, handle, indent=2, sort_keys=True)
        handle.write("\n")
    return overall


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--module", required=True, help="transcript module to replay")
    parser.add_argument("--base-url", help="service base url (default: routes.json)")
    parser.add_argument("--routes", default=os.path.join(HERE, "routes.json"))
    args = parser.parse_args()

    routes = load_json(args.routes)
    base_url = (args.base_url or os.environ.get("SERVICE_BASE") or routes["base_url"]).rstrip("/")
    index = load_json(os.path.join(ROOT, "transcripts", "index.json"))

    results = []
    for entry in index:
        if entry["module"] != args.module:
            continue
        scenario = entry["scenario"]
        rules = routes.get("rules", {}).get(scenario, [])
        if scenario not in routes["handles"]:
            reason = routes.get("skips", {}).get(scenario, "not handled by this service")
            results.append({"scenario": scenario, "description": entry["description"],
                            "verdict": "SKIP", "rules": rules, "detail": reason,
                            "differences": [], "change_ids": [], "notes": []})
            print("%-30s SKIP %s" % (scenario, reason))
            continue

        transcript = load_json(os.path.join(ROOT, "transcripts", scenario + ".json"))
        status, body = replay(base_url, routes, transcript)
        found, notes = differences(base_url, routes, transcript, status, body)
        verdict, failures, changes = judge(routes, scenario, found)
        if verdict == "FAIL":
            detail = "; ".join(failures)
        elif verdict == "CHANGED":
            detail = "; ".join("%s: %s" % (change_id, text) for change_id, text in changes)
        else:
            detail = ""
        results.append({"scenario": scenario, "description": entry["description"],
                        "verdict": verdict, "rules": rules, "detail": detail,
                        "differences": failures,
                        "change_ids": sorted({change_id for change_id, _ in changes}),
                        "notes": notes})
        print("%-30s %-7s %s" % (scenario, verdict, detail))
        for note in notes:
            print("%-30s note    %s" % ("", note))

    revision = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"],
                                       cwd=ROOT).decode().strip()
    overall = write_report(results, args.module, base_url, routes, revision)
    print("Result: %s" % overall)
    raise SystemExit(1 if overall == "FAIL" else 0)


if __name__ == "__main__":
    main()

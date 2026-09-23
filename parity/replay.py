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


def pattern_matches(pattern, parts):
    """Match a dotted probe pattern, where `<name>` is a placeholder.

    Returns the placeholder bindings, or None when the pattern does not match.
    """
    fields = pattern.split(".")
    if len(fields) != len(parts):
        return None
    bindings = {}
    for field, part in zip(fields, parts):
        if field.startswith("<") and field.endswith(">"):
            bindings[field] = part
        elif field != part:
            return None
    return bindings


def probe(base_url, routes, key):
    """Read one db_state probe key back out of the service.

    Returns ``(outcome, value, identity)``, where outcome is ``"read"``, or the
    routes.json ``unprobeable`` reason for a key naming state outside this
    service's boundary, or ``"unconfigured"`` for a key with no probe at all —
    which is a parity failure, not a note. ``identity`` is the identity of the
    row the probe read, which tells a row this request wrote from an identical
    row an earlier run left behind.
    """
    parts = key.split(".")
    for pattern, spec in routes.get("probes", {}).items():
        bindings = pattern_matches(pattern, parts)
        if bindings is None:
            continue
        query = {name: bindings.get(value, value) for name, value in spec["query"].items()}
        _, body = request_json(base_url + spec["path"] + "?" + urllib.parse.urlencode(query))
        fields = body.get("fields", {})
        return "read", fields.get(spec["field"]), fields.get(spec.get("identity_field"))
    for pattern, reason in routes.get("unprobeable", {}).items():
        if pattern_matches(pattern, parts) is not None:
            return reason, None, None
    return "unconfigured", None, None


def probe_identities(base_url, routes, keys):
    """The identity each probe reads now, to compare with after the replay."""
    return {key: probe(base_url, routes, key)[2] for key in keys}


def difference(kind, legacy, service, text):
    return {"kind": kind, "legacy": legacy, "service": service, "text": text}


def differences(base_url, routes, transcript, status, body, before, writes):
    """Every legacy -> service difference, with both values kept apart.

    ``before`` holds the row identity each probe read before the request, and
    ``writes`` says whether the replayed path is a write, in which case the
    probed row has to be a new one.
    """
    expected = transcript["expected"]
    found = []

    legacy_class = status_class(expected["status"])
    service_class = status_class(status)
    if legacy_class != service_class:
        found.append(difference("status_class", legacy_class, service_class,
                                "status class: %s -> %s" % (legacy_class, service_class)))

    fields = body.get("fields", {})
    for name, value in sorted(expected["business_fields"].items()):
        if fields.get(name) != value:
            found.append(difference("field:%s" % name, value, fields.get(name),
                                    "%s: %r -> %r" % (name, value, fields.get(name))))
    for name in sorted(set(fields) - set(expected["business_fields"])):
        found.append(difference("field:%s" % name, None, fields[name],
                                "%s: (not recorded) -> %r" % (name, fields[name])))

    errors = list(body.get("errors", []))
    if errors != list(expected["validation_errors"]):
        found.append(difference("validation", expected["validation_errors"], errors,
                                "validation keys: %r -> %r"
                                % (expected["validation_errors"], errors)))

    notes = []
    for key, value in sorted(expected["db_state"].items()):
        outcome, actual, identity = probe(base_url, routes, key)
        if outcome == "unconfigured":
            found.append(difference("db:%s" % key, value, "no probe configured",
                                    "db %s: %r -> no probe is configured for this key"
                                    % (key, value)))
        elif outcome != "read":
            notes.append("db %s not probed: %s" % (key, outcome))
        elif actual != value:
            found.append(difference("db:%s" % key, value, actual,
                                    "db %s: %r -> %r" % (key, value, actual)))
        elif writes and identity is not None and identity == before.get(key):
            found.append(difference("db:%s" % key, "a row written by this request",
                                    "the row of an earlier run",
                                    "db %s: %r is the row that was already there "
                                    "(identity %s unchanged), so this request wrote nothing"
                                    % (key, value, identity)))
    return found, notes


def approvals(routes, scenario, found):
    """The CHG record approving exactly this difference, if there is one."""
    for approved in routes.get("approved_differences", []):
        if (scenario in approved["scenarios"] and approved["kind"] == found["kind"]
                and str(approved["legacy"]) == str(found["legacy"])
                and str(approved["service"]) == str(found["service"])):
            return approved
    return None


def judge(routes, scenario, found):
    """PASS, CHANGED or FAIL, with the failing and approved differences split."""
    failures, changes = [], []
    for entry in found:
        approved = approvals(routes, scenario, entry)
        if approved:
            changes.append((approved["id"], entry["text"]))
        else:
            failures.append(entry["text"])
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
              "| Scenario | Verdict | %s rules exercised | Detail |"
              % routes["rule_family"],
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
    if args.module != routes["module"]:
        parser.error("%s answers for the %s module, not %s"
                     % (args.routes, routes["module"], args.module))
    base_url = (args.base_url or os.environ.get("SERVICE_BASE") or routes["base_url"]).rstrip("/")
    index = load_json(os.path.join(ROOT, "transcripts", "index.json"))

    entries = [entry for entry in index if entry["module"] == args.module]
    if not entries:
        parser.error("transcripts/index.json has no %s scenarios to replay" % args.module)
    handled = [entry for entry in entries if entry["scenario"] in routes["handles"]]
    if not handled:
        parser.error("%s handles none of the %d recorded %s scenarios"
                     % (routes["service"], len(entries), args.module))

    results = []
    for entry in entries:
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
        before = probe_identities(base_url, routes, transcript["expected"]["db_state"])
        writes = routes["routes"][transcript["request"]["path"]] in routes.get("writes", [])
        status, body = replay(base_url, routes, transcript)
        found, notes = differences(base_url, routes, transcript, status, body, before, writes)
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

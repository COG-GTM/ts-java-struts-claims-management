#!/usr/bin/env python3
"""Replay the captured legacy transcripts against the settlement service.

Each transcript under transcripts/ records what the Struts application did
for one request: HTTP status, the JSP it forwarded to, the business fields
the JSP rendered (the <span id="f_..."> values), the validation error keys
and a few database probes. This script reads transcripts/index.json, takes
every scenario in --module, sends the same request to the service named by
--base-url, which answers {"screen": ..., "fields": {...}, "errors": [...]},
and compares (ADR-001, decision 6):

  status     same class (2xx, 4xx, 5xx)
  result     forward:/WEB-INF/jsp/<screen>.jsp  <->  "screen": "<screen>"
  fields     every legacy business field with the same value, no extra field
  errors     the same validation keys, order ignored
  db_state   each probe re-read through the service's own read screen

HTML is never compared. parity/routes.json holds the mapping:

  routes               legacy /x/y.do  ->  service /x/y; a scenario whose
                       request path has no route is SKIP
  probes               db_state key pattern -> service path and field, e.g.
                       settlement.claim.<id>.amount ->
                       GET /settlement/detail?claimId=<id>, field detailAmount
  unrouted_probes      keys read through a screen outside the slice; listed
                       in the Note column, do not decide the verdict
  modules.<m>.scenarios            the SETTLE-R rules each scenario exercises
  modules.<m>.approved_differences scenario -> {"change": "CHG-nnn",
                       "expect": {...}} describing the service behaviour a
                       change record approved

Verdicts:
  PASS     no difference
  FAIL     differences listed as legacy -> service
  SKIP     request path not routed to this service
  CHANGED  every difference matches the approved CHG-nnn expectation

Exit code 1 when any scenario FAILs.

Usage: python3 parity/replay.py --base-url http://localhost:8083
           [--module settlement] [--routes parity/routes.json]
           [--report parity/report.md] [--json parity/report.json]
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TRANSCRIPTS = os.path.join(ROOT, "transcripts")


class Routes:
    """The parity/routes.json contract between the transcripts and one service."""

    def __init__(self, data, module):
        self.legacy_context = data.get("legacy_context", "")
        self.routes = data["routes"]
        self.probes = [(self._pattern(key), spec)
                       for key, spec in data.get("probes", {}).items()]
        self.unrouted_probes = [(self._pattern(key), screen)
                                for key, screen in
                                data.get("unrouted_probes", {}).items()]
        section = data.get("modules", {}).get(module, {})
        self.scenario_rules = section.get("scenarios", {})
        self.approved = section.get("approved_differences", {})

    @staticmethod
    def _pattern(key):
        """settlement.claim.<id>.amount -> regex with a named group per <name>."""
        parts = re.split(r"(<[a-z_]+>)", key)
        regex = "".join(
            "(?P%s[^.]+)" % part if part.startswith("<") else re.escape(part)
            for part in parts)
        return re.compile("^" + regex + "$")

    def service_path(self, legacy_path):
        """Returns (service path or None when unrouted, query string)."""
        if legacy_path.startswith(self.legacy_context):
            legacy_path = legacy_path[len(self.legacy_context):]
        path, _, query = legacy_path.partition("?")
        return self.routes.get(path), query

    def probe_for(self, key):
        """Maps a capture.py db_state key to (service path, field name)."""
        for pattern, spec in self.probes:
            match = pattern.match(key)
            if match:
                path = spec["path"]
                for name, value in match.groupdict().items():
                    path = path.replace("<%s>" % name, value)
                return path, spec["field"]
        return None

    def unrouted_screen(self, key):
        for pattern, screen in self.unrouted_probes:
            if pattern.match(key):
                return screen
        return None


def load_routes(path, module):
    with open(os.path.join(ROOT, path), encoding="utf-8") as stream:
        return Routes(json.load(stream), module)


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


def expected_screen(result):
    prefix, suffix = "forward:/WEB-INF/jsp/", ".jsp"
    if result.startswith(prefix) and result.endswith(suffix):
        return result[len(prefix):-len(suffix)]
    if result.startswith("error:"):
        return "error"
    return result


def status_class(status):
    return "%dxx" % (status // 100)


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


def compare(transcript, base_url, routes):
    """Returns (differences as (what, legacy, service), unprobed notes)."""
    request = transcript["request"]
    expected = transcript["expected"]
    path, query = routes.service_path(request["path"])
    status, body = call(base_url, request["method"],
                        path + ("?" + query if query else ""),
                        request.get("form", {}))
    differences = []
    if status_class(status) != status_class(expected["status"]):
        differences.append(("status", status_class(expected["status"]),
                            status_class(status)))
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
        probe = routes.probe_for(key)
        if probe is None:
            screen = routes.unrouted_screen(key)
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


def approved_value(expect, what):
    """The service value a change record approves for one compared item."""
    if what.startswith("field "):
        return expect.get("fields", {}).get(what[len("field "):])
    if what.startswith("db_state "):
        return expect.get("db_state", {}).get(what[len("db_state "):])
    if what == "result":
        return expected_screen(expect["result"]) if "result" in expect else None
    if what == "validation_errors":
        value = expect.get(what)
        return sorted(value) if value is not None else None
    return expect.get(what)


def verdict_for(differences, approval):
    """PASS, FAIL or CHANGED; every difference must be covered by the approval."""
    if not differences:
        return "PASS"
    if not approval:
        return "FAIL"
    expect = approval.get("expect", {})
    for what, _, got in differences:
        want = approved_value(expect, what)
        if what == "validation_errors":
            got = sorted(got)
        if want is None or want != got:
            return "FAIL"
    return "CHANGED"


def replay(base_url, module, routes):
    rows = []
    for transcript in load_transcripts(module):
        scenario = transcript["scenario"]
        path, _ = routes.service_path(transcript["request"]["path"])
        rules = routes.scenario_rules.get(scenario, [])
        if path is None:
            rows.append({"scenario": scenario, "rules": rules,
                         "verdict": "SKIP", "change": None,
                         "differences": [], "unprobed": [],
                         "note": "screen not routed to this service"})
            continue
        differences, unprobed = compare(transcript, base_url, routes)
        approval = routes.approved.get(scenario)
        verdict = verdict_for(differences, approval)
        change = approval["change"] if verdict == "CHANGED" else None
        notes = []
        if change:
            notes.append("approved by " + change)
        if unprobed:
            notes.append("db_state not probed: " + "; ".join(unprobed))
        rows.append({
            "scenario": scenario,
            "rules": rules,
            "verdict": verdict,
            "change": change,
            "differences": [{"what": what, "legacy": legacy, "service": got}
                            for what, legacy, got in differences],
            "unprobed": unprobed,
            "note": "; ".join(notes),
        })
    return rows


def render(rows, base_url, module, counts):
    summary = ", ".join("%s %d" % item for item in sorted(counts.items()))
    lines = [
        "# Parity report: %s" % module,
        "",
        "Service: `%s`. Fixtures: `transcripts/*.json` with module `%s`,"
        " routed by `parity/routes.json`." % (base_url, module),
        "Generated by `make parity` (`parity/replay.py`).",
        "",
        "Result: %s." % summary,
        "",
        "| Scenario | Rules | Verdict | Differences (legacy -> service) | Note |",
        "| --- | --- | --- | --- | --- |",
    ]
    for row in rows:
        verdict = row["verdict"]
        if row["change"]:
            verdict = "%s (%s)" % (verdict, row["change"])
        diff_text = "<br>".join(
            "%s: `%s` -> `%s`" % (d["what"], d["legacy"], d["service"])
            for d in row["differences"])
        lines.append("| %s | %s | %s | %s | %s |" % (
            row["scenario"], ", ".join(row["rules"]), verdict,
            diff_text, row["note"]))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--module", default="settlement")
    parser.add_argument("--routes", default="parity/routes.json")
    parser.add_argument("--report", default=None)
    parser.add_argument("--json", dest="json_report", default=None)
    args = parser.parse_args()

    routes = load_routes(args.routes, args.module)
    rows = replay(args.base_url.rstrip("/"), args.module, routes)
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
                       "routes": args.routes, "summary": counts,
                       "scenarios": rows},
                      stream, indent=2, sort_keys=True)
            stream.write("\n")
    return 1 if counts.get("FAIL") else 0


if __name__ == "__main__":
    sys.exit(main())

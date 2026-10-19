"""Shared writer of parity/report.json, parity/report.md and parity/report.html.

Each slice's parity tool (tools/parity/settlement_parity.py,
tools/parity/payment_parity.py) hands over its rows; the writer keeps the rows
of the other slices from the committed report.json unchanged and regenerates
the three files. The settlement rows keep the six keys and the Markdown text
they had when the settlement slice was the only one.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PARITY = os.path.join(ROOT, "parity")
TEMPLATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "report_template.html")

sys.path.insert(0, os.path.join(ROOT, "tools"))
import traceability  # noqa: E402  (rule list, javadoc citations, scenario mapping)

SLICES = ("settlement", "payment")
SLICE_OF_PREFIX = {"SETTLE": "settlement", "PAY": "payment"}
SUPERSEDED = re.compile(r"^\*\*([A-Z]+-R\d+) \(v(\d+), superseded by v(\d+)\)")
TABLE_ROW = re.compile(r"^\|(.*)\|\s*$")


def verdict_source(row):
    """Who decided the verdict: the comparator (PASS/FAIL, also when the scenario
    has an approval record that the comparison then failed) or a reviewer, when
    a CHANGED verdict rests on an approved docs/changes record."""
    if row["verdict"].startswith("CHANGED"):
        return "reviewer", row.get("change") or row["verdict"]
    return "automated", None


def superseded_versions():
    found = {}
    for path, _ in traceability.specs():
        for line in traceability.read(path).splitlines():
            match = SUPERSEDED.match(line)
            if match:
                found[(match.group(1), match.group(2))] = match.group(3)
    return found


def documented_reason(rule, superseded):
    """Why a rule has no automated coverage, read from the documents that say so:
    the spec (superseded version), an ADR mapping-table row that names the rule,
    or an open-question row; otherwise the plain fact."""
    if rule in superseded:
        return "superseded by v%s; the current text is covered under that version" % superseded[rule]
    for path in sorted(glob.glob(os.path.join(ROOT, "docs", "decisions", "ADR-*.md"))):
        for line in open(path).read().splitlines():
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) >= 2 and traceability.refers_to(line, rule) and TABLE_ROW.match(line.strip()):
                return "%s, per %s" % (cells[-1], os.path.basename(path)[:-3])
    questions = os.path.join(ROOT, "docs", "specs", "OPEN-QUESTIONS.md")
    if os.path.exists(questions):
        for line in open(questions).read().splitlines():
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if cells and cells[0].startswith("OQ-") and traceability.refers_to(line, rule):
                return "open question %s" % cells[0]
    return "no parity scenario and no test javadoc cites it"


def coverage(routes):
    """Per slice: rule versions with a parity scenario, with tests only, with
    nothing, from the specs, the test javadocs and parity/routes.json (the
    same sources docs/TRACEABILITY.md is built from)."""
    rules, _ = traceability.rules_and_history()
    citations = traceability.javadoc_citations()
    superseded = superseded_versions()
    result = {}
    for rule in rules:
        slice_name = SLICE_OF_PREFIX.get(rule[0].split("-")[0], rule[0].split("-")[0].lower())
        entry = result.setdefault(slice_name, {
            "spec": None, "total": 0, "parity": [], "tests_only": [], "none": []})
        entry["spec"] = next(path for path, prefix in traceability.specs()
                             if rule[0].startswith(prefix + "-"))
        entry["total"] += 1
        label = "%s (v%s)" % rule
        scenarios = traceability.transcripts_for(rule, routes)
        tests = traceability.tests_for(rule, citations)
        if scenarios:
            entry["parity"].append(label)
        elif tests:
            entry["tests_only"].append({"rule": label, "tests": tests})
        else:
            entry["none"].append({"rule": label, "reason": documented_reason(rule, superseded)})
    return result


def slice_of(row):
    return row.get("slice") or row["scenario"].split("_")[0]


def write(slice_name, rows):
    path = os.path.join(PARITY, "report.json")
    existing = []
    if os.path.exists(path):
        with open(path) as handle:
            existing = json.load(handle).get("scenarios", [])
    kept = [row for row in existing if slice_of(row) != slice_name]
    scenarios = []
    for name in SLICES:
        scenarios += [row for row in kept if slice_of(row) == name]
        if name == slice_name:
            scenarios += rows
    os.makedirs(PARITY, exist_ok=True)
    with open(path, "w") as handle:
        json.dump({"scenarios": scenarios}, handle, indent=2, sort_keys=True)
        handle.write("\n")
    with open(os.path.join(PARITY, "routes.json")) as handle:
        routes = json.load(handle)
    cover = coverage(routes)
    write_markdown(scenarios, cover)
    write_html(scenarios, cover, routes)


def source_cell(row):
    source, change = verdict_source(row)
    return "reviewer (%s)" % change if source == "reviewer" else "automated"


def coverage_lines(entry):
    spec = os.path.basename(entry["spec"])[:-3]
    lines = ["**Coverage** (%s, %d rule versions, from the spec, the test javadocs and"
             " `parity/routes.json`):" % (spec, entry["total"]), ""]
    lines.append("- Rules with at least one parity scenario: %d / %d." % (len(entry["parity"]), entry["total"]))
    lines.append("- Rules covered only by unit tests: %s." % (
        ", ".join(item["rule"] for item in entry["tests_only"]) or "none"))
    lines.append("- Rules with no automated coverage: %s." % (
        "; ".join("%s (%s)" % (item["rule"], item["reason"]) for item in entry["none"]) or "none"))
    count = len(entry["tests_only"])
    lines.append("- Not exercised live: %s." % (
        "%d rule version%s (the unit-test-only ones above) have no legacy request replayed"
        " against the service; a scenario in `parity/routes.json` closes each"
        % (count, "" if count == 1 else "s") if count else
        "none; every rule with automated coverage has a legacy request replayed against the service"))
    lines.append("")
    lines.append("Verdict source: `automated` = the comparator's PASS/FAIL; `reviewer` = a CHANGED"
                 " verdict accepted on the strength of its `docs/changes/CHG-nnn` record.")
    return lines


def write_markdown(scenarios, cover):
    settlement = [row for row in scenarios if slice_of(row) == "settlement"]
    payment = [row for row in scenarios if slice_of(row) == "payment"]
    with open(os.path.join(PARITY, "report.md"), "w") as handle:
        handle.write("# Settlement parity\n\n")
        handle.write("Generated by `make parity` (tools/parity/settlement_parity.py); the same\n")
        handle.write("verdicts are in parity/report.json.\n")
        handle.write("Each row replays a `transcripts/settlement_*.json` scenario against the\n")
        handle.write("settlement service on 8083 and compares the four things ADR-001:82-90\n")
        handle.write("calls parity: status class, business fields, validation keys, db state.\n\n")
        handle.write("Scenarios listed in `parity/routes.json` are approved differences: they\n")
        handle.write("are judged against their `docs/changes/CHG-nnn` record and reported as\n")
        handle.write("CHANGED, and still fail on anything the record does not approve.\n\n")
        if "settlement" in cover:
            handle.write("\n".join(coverage_lines(cover["settlement"])) + "\n\n")
        handle.write("| Scenario | Legacy behaviour | Verdict | Source | Difference |\n")
        handle.write("| --- | --- | --- | --- | --- |\n")
        for row in settlement:
            handle.write("| `%s` | %s | %s | %s | %s |\n" % (
                row["scenario"], row["description"], row["verdict"], source_cell(row), row["difference"]))
        handle.write("\nStatus class: the legacy error path forwards to `error.jsp` with a 200;\n")
        handle.write("ADR-001:51,84 replaces that forward with the equivalent error status\n")
        handle.write("class, so a 5xx there is parity, not a change.\n")
        if payment:
            handle.write("\n# Payment parity\n\n")
            handle.write("Generated by `make parity` (tools/parity/payment_parity.py); the same\n")
            handle.write("verdicts, with each request, its expected and actual response and the\n")
            handle.write("rules it covers, are in parity/report.json and parity/report.html.\n")
            handle.write("Each row replays one scenario of `parity/routes.json` (`payment_scenarios`:\n")
            handle.write("the two `transcripts/payment_*.json` transcripts and the live requests of\n")
            handle.write("`docs/specs/SPEC-PAY-001-live.json`) against the payment service on 8084\n")
            handle.write("after resetting its database to the named precondition\n")
            handle.write("(`parity/preconditions/payment/<name>.sql` on top of the legacy seed), and\n")
            handle.write("compares what ADR-002 \"How parity is judged\" calls parity: status class,\n")
            handle.write("business fields (exact set), display texts and links, validation keys, and\n")
            handle.write("the PAYMENT rows from 61 up.\n\n")
            if "payment" in cover:
                handle.write("\n".join(coverage_lines(cover["payment"])) + "\n\n")
            handle.write("| Scenario | Precondition | Legacy behaviour | Rules | Verdict | Source | Difference |\n")
            handle.write("| --- | --- | --- | --- | --- | --- | --- |\n")
            for row in payment:
                handle.write("| `%s` | `%s` | %s | %s | %s | %s | %s |\n" % (
                    row["scenario"], row["precondition"], row["description"],
                    ", ".join(row["rules"]), row["verdict"], source_cell(row), row["difference"]))
            handle.write("\nLive L01, L38, L39 and L40 (PAY-R13, the unauthenticated redirects) and the\n")
            handle.write("sign-in and settlement set-up steps L02, L05 and L35 are not scenarios: the\n")
            handle.write("service has no session and L05 is reproduced as the `settlement-121`\n")
            handle.write("precondition (ADR-002, \"Out of scope\", \"Database\").\n")


def write_html(scenarios, cover=None, routes=None):
    with open(TEMPLATE) as handle:
        template = handle.read()
    if routes is None:
        with open(os.path.join(PARITY, "routes.json")) as handle:
            routes = json.load(handle)
    if cover is None:
        cover = coverage(routes)
    # Settlement rows carry no "rules" key (their shape is frozen); the view
    # takes their rule mapping from routes.json transcript_rules instead.
    data = json.dumps({"scenarios": scenarios, "coverage": cover,
                       "transcript_rules": routes.get("transcript_rules", {})},
                      sort_keys=True).replace("</", "<\\/")
    with open(os.path.join(PARITY, "report.html"), "w") as handle:
        handle.write(template.replace("/*REPORT_JSON*/", data))

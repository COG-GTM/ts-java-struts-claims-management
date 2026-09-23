#!/usr/bin/env python3
"""Generate docs/TRACEABILITY.md from the repository, one row per spec rule.

Nothing in the matrix is typed by hand. For every rule version declared in
docs/specs/SPEC-SETTLE-001.md the row is assembled from:

* the spec version that last changed the rule, from the "Rule history" table of
  the spec;
* the last commit whose diff mentions that exact rule version, from
  ``git log -G`` over the tracked sources, re-reading the changed lines so that
  a line about ``SETTLE-R06 (v2)`` is not counted as a change to ``SETTLE-R06
  (v1)``;
* the tests whose javadoc cites the rule, under ``src/test`` and
  ``services/*/src/test``;
* the transcripts mapped to the rule in ``parity/routes.json``;
* the parity verdict of each of those transcripts, from ``parity/report.json``
  (written by ``make parity``);
* the ``docs/changes/CHG-nnn`` records that name the rule.

A mention without a version (``SETTLE-R05``) belongs to every version of that
rule; a qualified mention (``SETTLE-R06 (v2)``, ``| SETTLE-R06 | v2 |``) belongs
to that version only.

The output holds no commit of its own and no timestamp, so running it twice on
the same commit writes the same bytes. The generated files are excluded from the
git history search for the same reason: a commit that only refreshes them is not
a change to any rule.

Usage: python3 tools/traceability.py  (see `make traceability`)
"""

import glob
import json
import os
import re
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = os.path.join("docs", "specs", "SPEC-SETTLE-001.md")
OUTPUT = os.path.join("docs", "TRACEABILITY.md")

GENERATED = [OUTPUT, os.path.join("parity", "report.md"),
             os.path.join("parity", "report.json")]
SEARCHED = ["docs", "parity", "services", "src", "tools", "Makefile"]

# "SETTLE-R06", "SETTLE-R06 (v2)", "SETTLE-R06 (v1, superseded by v2)",
# "| SETTLE-R06 | v2 |": the id, and the version it is qualified with if any.
MENTION = re.compile(r"(SETTLE-R\d+)[ \t]*[|(,]?[ \t]*(?:v(\d+))?")
HEADING = re.compile(r"^\*\*(SETTLE-R\d+) \(v(\d+)")
HISTORY = re.compile(r"^\| (SETTLE-R\d+) \| v(\d+) \| (.*?) \|\s*$")
SPEC_VERSION = re.compile(r"version (\d+\.\d+)", re.I)
JAVADOC_END = re.compile(r"\*/")
MEMBER = re.compile(r"(\w+)\s*\(")


def mentions(text):
    """Every (rule id, version or None) the text refers to."""
    return {(match.group(1), match.group(2)) for match in MENTION.finditer(text)}


def refers_to(text, rule):
    rule_id, version = rule
    return any(found == rule_id and (found_version in (None, version))
               for found, found_version in mentions(text))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


def git(*args):
    return subprocess.check_output(["git"] + list(args), cwd=ROOT).decode("utf-8")


def rules_and_history():
    """The rule versions the spec declares, in spec order, and their history."""
    rules = []
    history = {}
    for line in read(SPEC).splitlines():
        heading = HEADING.match(line)
        if heading:
            rules.append((heading.group(1), heading.group(2)))
            continue
        row = HISTORY.match(line)
        if row:
            change = row.group(3)
            version = SPEC_VERSION.search(change)
            history[(row.group(1), row.group(2))] = (
                version.group(1) if version else "-", change)
    return rules, history


def searched_paths():
    return SEARCHED + [":(exclude)" + path for path in GENERATED]


def last_commit(rule):
    """The last commit whose changed lines mention this exact rule version."""
    rule_id = rule[0]
    commits = git("log", "--no-merges", "--format=%H", "-G" + rule_id, "--",
                  *searched_paths()).split()
    for commit in commits:
        diff = git("show", "--no-merges", "--unified=0", "--format=", commit,
                   "--", *searched_paths())
        for line in diff.splitlines():
            if line[:1] not in ("+", "-") or line[:3] in ("+++", "---"):
                continue
            if refers_to(line[1:], rule):
                subject = git("log", "-1", "--format=%s", commit).strip()
                return "`%s` %s" % (commit[:7], subject)
    return "-"


def test_files():
    roots = [os.path.join(ROOT, "src", "test")]
    roots += glob.glob(os.path.join(ROOT, "services", "*", "src", "test"))
    for source in roots:
        for directory, unused, names in os.walk(source):
            for name in sorted(names):
                if name.endswith(".java"):
                    yield os.path.join(directory, name)


def javadoc_citations():
    """Map each rule version to the tests whose javadoc cites it."""
    citations = {}
    for path in sorted(test_files()):
        class_name = os.path.basename(path)[: -len(".java")]
        lines = read(os.path.relpath(path, ROOT)).splitlines()
        index = 0
        while index < len(lines):
            if lines[index].strip().startswith("/**"):
                block = []
                while index < len(lines):
                    block.append(lines[index])
                    if JAVADOC_END.search(lines[index]):
                        break
                    index += 1
                member = member_after(lines, index + 1)
                for rule in mentions("\n".join(block)):
                    name = class_name + ("#" + member if member else "")
                    citations.setdefault(rule, set()).add(name)
            index += 1
    return citations


def member_after(lines, index):
    """The name of the method a javadoc block documents, if it documents one."""
    for line in lines[index:index + 6]:
        stripped = line.strip()
        if not stripped or stripped.startswith("@") or stripped.startswith("//"):
            continue
        member = MEMBER.search(stripped)
        return member.group(1) if member else None
    return None


def tests_for(rule, citations):
    names = set()
    for cited, cited_names in citations.items():
        if cited[0] == rule[0] and cited[1] in (None, rule[1]):
            names |= cited_names
    return sorted(names)


def transcripts_for(rule, routes):
    found = []
    for scenario, cited in sorted(routes.get("transcript_rules", {}).items()):
        if any(refers_to(entry, rule) for entry in cited):
            found.append(scenario)
    return found


def change_records_for(rule):
    records = []
    for path in sorted(glob.glob(os.path.join(ROOT, "docs", "changes", "*.md"))):
        if refers_to(read(os.path.relpath(path, ROOT)), rule):
            records.append(os.path.basename(path))
    return records


def verdicts():
    path = os.path.join(ROOT, "parity", "report.json")
    if not os.path.exists(path):
        return {}
    report = json.loads(read(os.path.relpath(path, ROOT)))
    return {row["scenario"]: row["verdict"] for row in report["scenarios"]}


def cell(values):
    return ", ".join(values) if values else "-"


def require_full_history():
    """A shallow clone has no history to read, and would fill the commit column
    with the single commit it carries."""
    if git("rev-parse", "--is-shallow-repository").strip() == "true":
        raise SystemExit(
            "tools/traceability.py needs the full history: run git fetch "
            "--unshallow (in CI, check out with fetch-depth: 0)")


def main():
    require_full_history()
    rules, history = rules_and_history()
    citations = javadoc_citations()
    routes = json.loads(read(os.path.join("parity", "routes.json")))
    parity = verdicts()

    rows = []
    for rule in rules:
        spec_version, change = history.get(rule, ("-", ""))
        scenarios = transcripts_for(rule, routes)
        rows.append({
            "rule": "SETTLE-R%s (v%s)" % (rule[0].split("-R")[1], rule[1]),
            "spec_version": spec_version,
            "history": change or "-",
            "commit": last_commit(rule),
            "tests": cell(["`%s`" % name for name in tests_for(rule, citations)]),
            "transcripts": cell(["`%s`" % name for name in scenarios]),
            "parity": cell(["%s: %s" % (name, parity.get(name, "not replayed"))
                            for name in scenarios]),
            "changes": cell(["`docs/changes/%s`" % name
                             for name in change_records_for(rule)]),
        })

    lines = [
        "# Traceability matrix",
        "",
        "Generated by `make traceability` (tools/traceability.py). Do not edit:",
        "every cell is read out of the spec, the git history, the tests,",
        "`parity/routes.json` and `parity/report.json`, and the file is rewritten",
        "byte for byte on every run.",
        "",
        "One row per rule version of `docs/specs/SPEC-SETTLE-001.md`. *Spec version*",
        "is the version of the spec that last changed the rule, from its Rule",
        "history row. *Last commit* is the last commit whose changed lines mention",
        "that rule version. *Tests* are the tests whose javadoc cites it, *Transcripts*",
        "the scenarios `parity/routes.json` maps to it, and *Parity* their verdicts in",
        "`parity/report.json`.",
        "",
        "| Rule | Spec version | Last commit | Tests | Transcripts | Parity | Change record |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in rows:
        lines.append("| %s | %s | %s | %s | %s | %s | %s |" % (
            row["rule"], row["spec_version"], row["commit"], row["tests"],
            row["transcripts"], row["parity"], row["changes"]))
    lines += ["", "## Rule history", "", "| Rule | Change |", "| --- | --- |"]
    for row in rows:
        lines.append("| %s | %s |" % (row["rule"], row["history"]))
    lines.append("")

    with open(os.path.join(ROOT, OUTPUT), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
    print("wrote %s (%d rules)" % (OUTPUT, len(rows)))


if __name__ == "__main__":
    main()

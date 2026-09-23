#!/usr/bin/env python3
"""Generates docs/TRACEABILITY.md: one row per SPEC-SETTLE-001 rule.

Standard library only. Nothing in the output is typed by hand; every cell is
derived from the files and git history below, and a cell without evidence
reads "none".

A rule identifier is matched exactly. "SETTLE-R06" and "SETTLE-R06 v2" are
different rules: a mention without a version suffix belongs to the
unversioned rule only, a mention with a suffix belongs to that version only,
and "SETTLE-R1" never matches "SETTLE-R10". Lists and ranges written the way
the code comments write them ("SETTLE-R02, R03, R04", "SETTLE-R07 to R09")
expand to the rules they name, always at version 1.

Sources, per rule:

* rule, title, status: the "### SETTLE-Rnn[ vN] Title" headings of
  docs/specs/SPEC-SETTLE-001.md; status is Observed when the rule text says
  so, otherwise Read; Superseded when the rule body opens with
  "Superseded by".
* transcripts: scenario names in the rule text that exist as
  transcripts/<scenario>.json.
* service code / tests: files under services/*/src/main and
  services/*/src/test whose text mentions the rule, as file:line.
* legacy tests: files under src/test that mention the rule.
* parity: verdicts in parity/report.json for scenarios whose "rules" list
  contains the rule, when that file exists.
* decisions: docs/decisions/*.md and docs/specs/OPEN-QUESTIONS.md entries
  that mention the rule.
* history: rows of the spec's "Rule history" table for the rule.
* commits: commits reachable from HEAD (excluding merges) whose message
  mentions the rule.

Mentions of a rule version that the spec does not define are listed at the
end so that nothing is silently dropped.

Usage: python3 tools/traceability.py [--check] [--self-test]
  --check      exit 1 if docs/TRACEABILITY.md differs from what would be written
  --self-test  exercise the identifier matcher and exit
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "specs" / "SPEC-SETTLE-001.md"
OUTPUT = ROOT / "docs" / "TRACEABILITY.md"
TRANSCRIPTS = ROOT / "transcripts"
DECISIONS = [ROOT / "docs" / "decisions", ROOT / "docs" / "specs" / "OPEN-QUESTIONS.md"]
SERVICES = ROOT / "services"
LEGACY_TESTS = ROOT / "src" / "test"
PARITY = ROOT / "parity" / "report.json"

PREFIX = "SETTLE-R"
NONE = "none"

RuleKey = tuple[int, int]  # (number, version); the unversioned rule is version 1

HEADING_RE = re.compile(r"^###\s+SETTLE-R(\d+)(?:\s+v(\d+))?\s+(.*?)\s*$", re.M)
# A full identifier, optionally followed by a list of short identifiers or a
# range in the same style: "SETTLE-R02, R03 and R04", "SETTLE-R07 to R09".
FULL_RE = re.compile(r"\bSETTLE-R(\d+)\b(?:\s+v(\d+)\b)?")
SHORT_RE = re.compile(r"\bR(\d+)\b(?:\s+v(\d+)\b)?")
LIST_RE = re.compile(
    r"\bSETTLE-R\d+\b(?:\s+v\d+\b)?"
    r"(?:\s*(?:,|and|to|-|\u2013)\s*(?:SETTLE-)?R\d+\b(?:\s+v\d+\b)?)+")
RANGE_RE = re.compile(r"\b(?:SETTLE-)?R(\d+)\b\s*(?:to|-|\u2013)\s*(?:SETTLE-)?R(\d+)\b")


def mentions(text: str) -> set[RuleKey]:
    """Every rule key the text names, matched exactly."""
    found: set[RuleKey] = set()
    for low, high in RANGE_RE.findall(text):
        low, high = int(low), int(high)
        if low <= high <= low + 100:
            found.update((number, 1) for number in range(low, high + 1))
    for group in LIST_RE.finditer(text):
        for number, version in SHORT_RE.findall(group.group(0)):
            found.add((int(number), int(version) if version else 1))
    for number, version in FULL_RE.findall(text):
        found.add((int(number), int(version) if version else 1))
    return found


def label(key: RuleKey) -> str:
    number, version = key
    return "%s%02d%s" % (PREFIX, number, " v%d" % version if version > 1 else "")


def spec_rules(text: str) -> list[tuple[RuleKey, str, str, str]]:
    """(key, title, status, body) for each rule heading in the spec."""
    rules = []
    headings = list(HEADING_RE.finditer(text))
    for index, heading in enumerate(headings):
        end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
        body = text[heading.end():end]
        body = body.split("\n## ")[0]
        key = (int(heading.group(1)), int(heading.group(2) or 1))
        if re.search(r"^Superseded by\b", body.strip()):
            status = "Superseded"
        elif re.search(r"\bObserved\b", body):
            status = "Observed"
        else:
            status = "Read"
        rules.append((key, heading.group(3), status, body))
    return rules


def spec_version(text: str) -> str:
    match = re.search(r"^Version:\s*(\S+)", text, re.M)
    return match.group(1) if match else NONE


def spec_history(text: str) -> dict[RuleKey, list[str]]:
    history: dict[RuleKey, list[str]] = {}
    section = text.split("## Rule history", 1)
    if len(section) < 2:
        return history
    for line in section[1].splitlines():
        if not line.startswith("|") or set(line.replace("|", "").strip()) <= {"-", " "}:
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 3 or cells[0] == "Rule":
            continue
        for key in mentions(cells[0]):
            history.setdefault(key, []).append("%s: %s" % (cells[1], cells[2]))
    return history


def transcripts_in(body: str) -> list[str]:
    names = sorted(set(re.findall(r"\b([a-z]+(?:_[a-z]+)+)\b", body)))
    return [name for name in names if (TRANSCRIPTS / (name + ".json")).exists()]


def file_mentions(paths: list[Path]) -> dict[RuleKey, list[str]]:
    found: dict[RuleKey, list[str]] = {}
    for path in sorted(paths):
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(lines, 1):
            for key in mentions(line):
                found.setdefault(key, []).append(
                    "%s:%d" % (path.relative_to(ROOT).as_posix(), number))
    return found


def files_under(*roots: Path, pattern: str) -> list[Path]:
    paths: list[Path] = []
    for root in roots:
        if root.is_file():
            paths.append(root)
        elif root.is_dir():
            paths.extend(p for p in root.rglob(pattern) if p.is_file())
    return paths


def service_sources(kind: str) -> list[Path]:
    if not SERVICES.is_dir():
        return []
    return [p for p in SERVICES.rglob("*") if p.is_file()
            and "/src/%s/" % kind in p.as_posix() and p.suffix in (".java", ".yml", ".properties")]


def parity_verdicts() -> dict[RuleKey, list[str]]:
    verdicts: dict[RuleKey, list[str]] = {}
    if not PARITY.exists():
        return verdicts
    report = json.loads(PARITY.read_text(encoding="utf-8"))
    for row in report.get("scenarios", []):
        for rule in row.get("rules", []):
            for key in mentions(rule):
                verdicts.setdefault(key, []).append(
                    "%s %s" % (row["scenario"], row["verdict"]))
    return verdicts


def commits() -> dict[RuleKey, list[str]]:
    result = subprocess.run(
        ["git", "log", "--no-merges", "--format=%h%x00%s%x00%b%x01", "HEAD"],
        cwd=ROOT, capture_output=True, text=True, check=True)
    found: dict[RuleKey, list[str]] = {}
    for record in result.stdout.split("\x01"):
        record = record.strip("\n")
        if not record:
            continue
        short, subject, body = record.split("\x00", 2)
        for key in mentions(subject + "\n" + body):
            found.setdefault(key, []).append("%s %s" % (short, subject))
    return found


def cell(values: list[str] | None) -> str:
    if not values:
        return NONE
    return "<br>".join(v.replace("|", "\\|") for v in values)


def build() -> str:
    text = SPEC.read_text(encoding="utf-8")
    rules = spec_rules(text)
    defined = {key for key, *_ in rules}
    history = spec_history(text)
    code = file_mentions(service_sources("main"))
    tests = file_mentions(service_sources("test"))
    legacy = file_mentions(files_under(LEGACY_TESTS, pattern="*.java"))
    decisions = file_mentions(files_under(*DECISIONS, pattern="*.md"))
    parity = parity_verdicts()
    log = commits()

    lines = [
        "# Traceability: SPEC-SETTLE-001 version %s" % spec_version(text),
        "",
        "Generated by `make traceability` (`tools/traceability.py`); do not edit.",
        "Identifiers are matched exactly: `SETTLE-R06` and `SETTLE-R06 v2` are",
        "different rules and never share a row. `none` means no evidence found.",
        "",
        "| Rule | Title | Status | Transcripts | Service code | Service tests | "
        "Legacy tests | Parity | Decisions | History | Commits |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for key, title, status, body in rules:
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            label(key), title, status,
            cell(transcripts_in(body)),
            cell(code.get(key)), cell(tests.get(key)), cell(legacy.get(key)),
            cell(parity.get(key)), cell(decisions.get(key)),
            cell(history.get(key)), cell(log.get(key))))

    unmatched: dict[RuleKey, list[str]] = {}
    for source, found in (("service code", code), ("service tests", tests),
                          ("legacy tests", legacy), ("decisions", decisions),
                          ("parity", parity), ("history", history),
                          ("commits", log)):
        for key, values in found.items():
            if key not in defined:
                unmatched.setdefault(key, []).extend(
                    "%s: %s" % (source, value) for value in values)
    lines += ["", "## Mentions of rules the spec does not define", ""]
    if unmatched:
        lines += ["| Rule | Where |", "| --- | --- |"]
        for key in sorted(unmatched):
            lines.append("| %s | %s |" % (label(key), cell(unmatched[key])))
    else:
        lines.append(NONE)
    return "\n".join(lines) + "\n"


def self_test() -> None:
    assert mentions("Narrow SETTLE-R06 to query string") == {(6, 1)}
    assert mentions("Implement CHG-001 / SETTLE-R06 v2: validation error") == {(6, 2)}
    assert mentions("SETTLE-R06 and SETTLE-R06 v2 differ") == {(6, 1), (6, 2)}
    assert mentions("SETTLE-R1 is not R10") == {(1, 1)}
    assert mentions("SETTLE-R10") == {(10, 1)}
    assert mentions("SETTLE-R02, R03, R04, R06") == {(2, 1), (3, 1), (4, 1), (6, 1)}
    assert mentions("rules SETTLE-R02 to R09") == {(n, 1) for n in range(2, 10)}
    assert mentions("SETTLE-R07 to SETTLE-R09") == {(7, 1), (8, 1), (9, 1)}
    assert mentions("SETTLE-R12, SETTLE-R13: save then detail") == {(12, 1), (13, 1)}
    assert mentions("R06 alone is not a rule id") == set()
    assert mentions("SETTLE-R06v2 without a space is not a version") == set()
    assert mentions("SETTLE_R06 is not the spec spelling") == set()
    assert label((6, 1)) == "SETTLE-R06" and label((6, 2)) == "SETTLE-R06 v2"
    print("traceability self-test: ok")


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    rendered = build()
    if args.check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
        if current != rendered:
            print("%s is out of date; run make traceability" % OUTPUT.relative_to(ROOT))
            return 1
        print("%s is up to date" % OUTPUT.relative_to(ROOT))
        return 0
    OUTPUT.write_text(rendered, encoding="utf-8")
    print("wrote %s" % OUTPUT.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())

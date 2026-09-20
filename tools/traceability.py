#!/usr/bin/env python3
"""Generates docs/TRACEABILITY.md for the SETTLE-R rules from repository contents.

Standard library only, Python 3.10 or later. Nothing in the output is typed by
hand: every cell is derived from the files and git history listed below, and a
cell with no evidence reads "none".

Sources, per rule SETTLE-Rnn:

* rule, spec version, status: docs/specs/SPEC-SETTLE-001.md (rule tables and
  the header "Version" row).
* transcript: the transcript scenario names cited in the rule's Evidence cell
  that exist as transcripts/<scenario>.json. The phrases "all N scenarios" and
  "all N `calculate` scenarios" expand to the spec's section 2.1 inventory,
  the latter restricted to transcripts whose request path ends in
  calculate.do.
* decision record: docs/decisions/*.md files that mention the rule.
* commit: commits on the current branch (git log HEAD, excluding merges) whose
  message mentions the rule, including ranges such as R49-R51.
* test: test method names under services/*/src/test that mention the rule,
  when that directory exists.
* parity result: entries in parity/report.json that mention the rule, when
  that file exists; the result is taken from a result/status/outcome/passed
  field of the enclosing object.
* reviewer: reviewers named as "reviewer: NAME" in the Author column of the
  spec's Change log rows whose Change text mentions the rule. A range that
  spans the whole rule set (for example "SETTLE-R01 to SETTLE-R51", a count
  of rules) does not count as a mention of every rule.

Usage: python3 tools/traceability.py [--check]
  --check  exit 1 if docs/TRACEABILITY.md differs from what would be written.
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
DECISIONS = ROOT / "docs" / "decisions"
SERVICES = ROOT / "services"
PARITY = ROOT / "parity" / "report.json"

RULE_PREFIX = "SETTLE-R"
STATUSES = ("Observed", "Inferred", "Open")
NONE = "none"

FULL_ID_RE = re.compile(r"\bSETTLE-R(\d{2,})\b")
SHORT_ID_RE = re.compile(r"(?<![A-Za-z0-9_-])R(\d{2,})\b")
RANGE_RE = re.compile(
    r"(?:SETTLE-)?R(\d{2,})\s*(?:-|–|to)\s*(?:SETTLE-)?R(\d{2,})\b")
WORDS = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
         "seven": 7, "eight": 8, "nine": 9, "ten": 10}
ALL_SCENARIOS_RE = re.compile(
    r"\bAll\s+(\w+)\s+(?:`(\w+)`\s+)?scenarios\b", re.IGNORECASE)


def split_row(line: str) -> list[str]:
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def is_separator(line: str) -> bool:
    return bool(re.fullmatch(r"\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?", line.strip()))


def parse_tables(text: str) -> list[list[list[str]]]:
    """Returns every markdown pipe table as a list of rows (header first)."""
    tables: list[list[list[str]]] = []
    current: list[list[str]] = []
    for line in text.splitlines():
        if line.lstrip().startswith("|"):
            if is_separator(line):
                continue
            current.append(split_row(line))
        else:
            if current:
                tables.append(current)
            current = []
    if current:
        tables.append(current)
    return tables


def rule_numbers_in(text: str,
                    ignore_span: tuple[int, int] | None = None) -> set[int]:
    """All rule numbers mentioned in text: SETTLE-Rnn, bare Rnn, and ranges.

    A range equal to ignore_span (the whole rule set) is not a mention of
    any rule, its endpoints included.
    """
    found: set[int] = set()

    def expand(match: re.Match[str]) -> str:
        low, high = int(match.group(1)), int(match.group(2))
        if (low, high) == ignore_span:
            return " "
        if low <= high <= low + 100:
            found.update(range(low, high + 1))
        return match.group(0)

    text = RANGE_RE.sub(expand, text)
    for match in FULL_ID_RE.findall(text):
        found.add(int(match))
    for match in SHORT_ID_RE.findall(text):
        found.add(int(match))
    return found


def rule_id(number: int) -> str:
    return f"{RULE_PREFIX}{number:02d}"


def spec_version(text: str) -> str:
    for table in parse_tables(text):
        for row in table:
            if len(row) >= 2 and row[0] == "Version":
                return row[1]
    return NONE


def spec_rules(text: str) -> list[tuple[str, str, str]]:
    """(rule id, status, evidence) for each rule row, in document order."""
    rules: list[tuple[str, str, str]] = []
    for table in parse_tables(text):
        header = [cell.lower() for cell in table[0]]
        if "id" not in header or "status" not in header:
            continue
        id_col = header.index("id")
        status_col = header.index("status")
        evidence_col = header.index("evidence") if "evidence" in header else None
        for row in table[1:]:
            if len(row) <= max(id_col, status_col):
                continue
            if not FULL_ID_RE.fullmatch(row[id_col]):
                continue
            status = row[status_col]
            if status not in STATUSES:
                status = f"unrecognised ({status})"
            evidence = row[evidence_col] if evidence_col is not None else ""
            rules.append((row[id_col], status, evidence))
    return rules


def spec_scenarios(text: str) -> list[str]:
    """Scenario names from the spec's transcript inventory that exist on disk."""
    names: list[str] = []
    for table in parse_tables(text):
        header = [cell.lower() for cell in table[0]]
        if header[:2] != ["scenario", "file"]:
            continue
        for row in table[1:]:
            name = row[0].strip("`")
            if (TRANSCRIPTS / f"{name}.json").is_file():
                names.append(name)
    return names


def transcript_path(name: str) -> str:
    try:
        with (TRANSCRIPTS / f"{name}.json").open(encoding="utf-8") as handle:
            return json.load(handle)["request"]["path"]
    except (OSError, KeyError, ValueError):
        return ""


def transcripts_for(evidence: str, inventory: list[str]) -> list[str]:
    cited: list[str] = []
    for name in re.findall(r"`([a-z0-9_]+)`", evidence):
        if name in inventory and name not in cited:
            cited.append(name)
    for count, qualifier in ALL_SCENARIOS_RE.findall(evidence):
        number = WORDS.get(count.lower())
        if number is None and count.isdigit():
            number = int(count)
        pool = inventory
        if qualifier:
            pool = [name for name in inventory
                    if transcript_path(name).endswith(f"/{qualifier}.do")]
        if number is None or number != len(pool):
            continue
        for name in pool:
            if name not in cited:
                cited.append(name)
    return cited


def change_log_reviewers(text: str,
                         span: tuple[int, int] | None) -> dict[int, list[str]]:
    """Rule number -> reviewers named in change log rows mentioning the rule."""
    reviewers: dict[int, list[str]] = {}
    for table in parse_tables(text):
        header = [cell.lower() for cell in table[0]]
        if header[:3] != ["version", "date", "author"]:
            continue
        for row in table[1:]:
            if len(row) < 4:
                continue
            names = re.findall(r"reviewer:\s*([^);|]+)", row[2])
            if not names:
                continue
            for number in rule_numbers_in(row[3], ignore_span=span):
                for name in names:
                    name = name.strip()
                    if name not in reviewers.setdefault(number, []):
                        reviewers[number].append(name)
    return reviewers


def decision_records() -> dict[int, list[str]]:
    records: dict[int, list[str]] = {}
    if not DECISIONS.is_dir():
        return records
    for path in sorted(DECISIONS.glob("*.md")):
        text = path.read_text(encoding="utf-8")
        for number in rule_numbers_in(text):
            records.setdefault(number, []).append(path.name)
    return records


def commits() -> dict[int, list[str]]:
    result: dict[int, list[str]] = {}
    try:
        log = subprocess.run(
            ["git", "log", "--no-merges", "--reverse", "--format=%h%x1f%B%x1e",
             "HEAD"],
            cwd=ROOT, check=True, capture_output=True, text=True).stdout
    except (OSError, subprocess.CalledProcessError):
        return result
    for entry in log.split("\x1e"):
        if "\x1f" not in entry:
            continue
        sha, message = entry.strip().split("\x1f", 1)
        for number in rule_numbers_in(message):
            result.setdefault(number, []).append(sha.strip())
    return result


METHOD_RE = re.compile(
    r"\b(?:void|def|fun|public|private|protected|static|it|test)\b[^\n(]*?"
    r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(")


def tests() -> dict[int, list[str]]:
    found: dict[int, list[str]] = {}
    if not SERVICES.is_dir():
        return found
    for test_dir in sorted(SERVICES.glob("*/src/test")):
        for path in sorted(p for p in test_dir.rglob("*") if p.is_file()):
            try:
                text = path.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                continue
            for name in METHOD_RE.findall(text):
                for number in rule_numbers_in(name.replace("_", "-")):
                    label = f"{path.relative_to(ROOT).as_posix()}::{name}"
                    if label not in found.setdefault(number, []):
                        found[number].append(label)
    return found


RESULT_KEYS = ("result", "status", "outcome", "verdict")


def parity_results() -> dict[int, list[str]]:
    found: dict[int, list[str]] = {}
    if not PARITY.is_file():
        return found
    try:
        with PARITY.open(encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return found

    def outcome(obj: dict) -> str:
        for key in RESULT_KEYS:
            if key in obj and isinstance(obj[key], (str, int, float)):
                return str(obj[key])
        if isinstance(obj.get("passed"), bool):
            return "pass" if obj["passed"] else "fail"
        return "recorded"

    def walk(node: object) -> None:
        if isinstance(node, dict):
            mentioned: set[int] = set()
            for key, value in node.items():
                mentioned |= rule_numbers_in(str(key))
                if isinstance(value, str):
                    mentioned |= rule_numbers_in(value)
            if mentioned:
                verdict = outcome(node)
                for number in mentioned:
                    if verdict not in found.setdefault(number, []):
                        found[number].append(verdict)
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(data)
    return found


def cell(values: list[str]) -> str:
    return ", ".join(f"`{value}`" for value in values) if values else NONE


def render(rows: list[dict[str, str]], summary: str, version: str) -> str:
    columns = ["rule", "spec version", "status", "transcript",
               "decision record", "commit", "test", "parity result",
               "reviewer"]
    lines = [
        "# Settlement traceability matrix",
        "",
        f"Generated by `python3 tools/traceability.py` from "
        f"`docs/specs/SPEC-SETTLE-001.md` (version {version}), `transcripts/`, "
        f"`docs/decisions/`, the git log of the current branch, "
        f"`services/*/src/test` and `parity/report.json`. Do not edit by hand; "
        f"re-run the script. A cell reading `none` means no evidence of that "
        f"kind was found.",
        "",
        summary,
        "",
        "| " + " | ".join(columns) + " |",
        "|" + "|".join("---" for _ in columns) + "|",
    ]
    for row in rows:
        lines.append("| " + " | ".join(row[column] for column in columns) + " |")
    lines.append("")
    return "\n".join(lines)


def build() -> tuple[str, str]:
    text = SPEC.read_text(encoding="utf-8")
    version = spec_version(text)
    rules = spec_rules(text)
    inventory = spec_scenarios(text)
    numbers = [int(identifier[len(RULE_PREFIX):]) for identifier, _, _ in rules]
    span = (min(numbers), max(numbers)) if numbers else None
    reviewers = change_log_reviewers(text, span)
    decisions = decision_records()
    commit_index = commits()
    test_index = tests()
    parity_index = parity_results()

    rows: list[dict[str, str]] = []
    with_transcript = with_test = with_parity = 0
    for identifier, status, evidence in rules:
        number = int(identifier[len(RULE_PREFIX):])
        transcript = transcripts_for(evidence, inventory)
        test_names = test_index.get(number, [])
        parity = parity_index.get(number, [])
        with_transcript += bool(transcript)
        with_test += bool(test_names)
        with_parity += bool(parity)
        rows.append({
            "rule": identifier,
            "spec version": version,
            "status": status,
            "transcript": cell(transcript),
            "decision record": cell(decisions.get(number, [])),
            "commit": cell(commit_index.get(number, [])),
            "test": cell(test_names),
            "parity result": cell(parity),
            "reviewer": cell(reviewers.get(number, [])),
        })
    summary = (f"Summary: {len(rows)} rules; {with_transcript} with a "
               f"transcript, {with_test} with a test, {with_parity} with a "
               f"parity result.")
    return render(rows, summary, version), summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="exit 1 if docs/TRACEABILITY.md is out of date")
    args = parser.parse_args()
    if sys.version_info < (3, 10):
        print("Python 3.10 or later is required", file=sys.stderr)
        return 2
    content, summary = build()
    if args.check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.is_file() else ""
        if current != content:
            print(f"{OUTPUT.relative_to(ROOT)} is out of date", file=sys.stderr)
            return 1
        print(summary)
        return 0
    OUTPUT.write_text(content, encoding="utf-8")
    print(summary)
    return 0


if __name__ == "__main__":
    sys.exit(main())

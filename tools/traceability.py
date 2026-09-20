#!/usr/bin/env python3
"""Generates docs/TRACEABILITY.md for the SETTLE-R rules from repository contents.

Standard library only, Python 3.10 or later. Nothing in the output is typed by
hand: every cell is derived from the files and git history listed below, and a
cell with no evidence reads "none".

A rule is identified by its number and version. The unversioned row in the
spec ("SETTLE-R18") is version 1 and describes current behaviour; a row with a
version suffix ("SETTLE-R18 v2") is an approved future state. A mention
without a suffix ("SETTLE-R18", "R18") counts for version 1 only; a mention
with a suffix ("SETTLE-R18 v2", "R18 v2", "SETTLE_R18_V2") counts for that
version only.

Sources, per rule row:

* rule, spec version, status: docs/specs/SPEC-SETTLE-001.md (rule tables and
  the header "Version" row).
* transcript: the transcript scenario names cited in the rule's Evidence cell
  that exist as transcripts/<scenario>.json. The phrases "all N scenarios" and
  "all N `calculate` scenarios" expand to the spec's section 2.1 inventory,
  the latter restricted to transcripts whose request path ends in
  calculate.do. Execution evidence ("E:" citations) is not a transcript and
  is listed separately under the summary so that an Observed rule with no
  transcript is not misread as unevidenced.
* decision record: docs/decisions/*.md files that mention the rule.
* commit: commits on the current branch (git log HEAD, excluding merges) whose
  message mentions the rule, including ranges such as R49-R51. Hashes are
  those reachable from HEAD, so a squash merge or a shallow clone changes
  this column; merge with a merge commit, or regenerate after squashing, and
  run --check from a full clone.
* test: under services/*/src/test, when that directory exists, either a test
  method whose name contains the rule id (settleR18..., SETTLE_R18_...,
  settle_r18_v2_...) or any other mention of the full id (SETTLE-R18,
  SETTLE-R18 v2) in a test source file, for example in a @DisplayName or a
  parameterized-test label; the latter is reported as file:line.
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

Key = tuple[int, int]  # (rule number, version); version 1 is the unversioned rule

VERSION = r"(?:[\s_-]?[vV](\d+)\b)?"
ID_CELL_RE = re.compile(r"SETTLE-R(\d{2,})" + VERSION)
FULL_ID_RE = re.compile(r"\bSETTLE[-_]R(\d{2,})" + VERSION)
SHORT_ID_RE = re.compile(r"(?<![A-Za-z0-9_-])R(\d{2,})" + VERSION)
RANGE_RE = re.compile(
    r"(?:SETTLE-)?R(\d{2,})\s*(?:-|–|to)\s*(?:SETTLE-)?R(\d{2,})\b")
WORDS = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
         "seven": 7, "eight": 8, "nine": 9, "ten": 10}
ALL_SCENARIOS_RE = re.compile(
    r"\bAll\s+(\w+)\s+(?:`(\w+)`\s+)?scenarios\b", re.IGNORECASE)
EXECUTION_RE = re.compile(r"`E:`|\bE:\s")


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


def version_of(suffix: str | None) -> int:
    return int(suffix) if suffix else 1


def rule_keys_in(text: str, ignore_span: tuple[int, int] | None = None) -> set[Key]:
    """All (number, version) keys mentioned in text.

    Recognises SETTLE-Rnn, bare Rnn, optional version suffixes, and ranges
    such as R49-R51 (ranges are always version 1). A range equal to
    ignore_span (the whole rule set) is not a mention of any rule, its
    endpoints included.
    """
    found: set[Key] = set()

    def expand(match: re.Match[str]) -> str:
        low, high = int(match.group(1)), int(match.group(2))
        if (low, high) == ignore_span:
            return " "
        if low <= high <= low + 100:
            found.update((number, 1) for number in range(low, high + 1))
        return match.group(0)

    text = RANGE_RE.sub(expand, text)
    for number, suffix in FULL_ID_RE.findall(text):
        found.add((int(number), version_of(suffix)))
    for number, suffix in SHORT_ID_RE.findall(text):
        found.add((int(number), version_of(suffix)))
    return found


def rule_label(key: Key) -> str:
    number, version = key
    return f"{RULE_PREFIX}{number:02d}" + (f" v{version}" if version > 1 else "")


def spec_version(text: str) -> str:
    for table in parse_tables(text):
        for row in table:
            if len(row) >= 2 and row[0] == "Version":
                return row[1]
    return NONE


def spec_rules(text: str) -> list[tuple[Key, str, str]]:
    """(key, status, evidence) for each rule row, in document order."""
    rules: list[tuple[Key, str, str]] = []
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
            match = ID_CELL_RE.fullmatch(row[id_col])
            if not match:
                continue
            key = (int(match.group(1)), version_of(match.group(2)))
            status = row[status_col]
            if status not in STATUSES:
                status = f"unrecognised ({status})"
            evidence = row[evidence_col] if evidence_col is not None else ""
            rules.append((key, status, evidence))
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


def add(index: dict[Key, list[str]], key: Key, value: str) -> None:
    values = index.setdefault(key, [])
    if value not in values:
        values.append(value)


def change_log_reviewers(text: str,
                         span: tuple[int, int] | None) -> dict[Key, list[str]]:
    """Rule key -> reviewers named in change log rows mentioning the rule."""
    reviewers: dict[Key, list[str]] = {}
    for table in parse_tables(text):
        header = [cell.lower() for cell in table[0]]
        if header[:3] != ["version", "date", "author"]:
            continue
        for row in table[1:]:
            if len(row) < 4:
                continue
            names = [name.strip()
                     for name in re.findall(r"reviewer:\s*([^);|]+)", row[2])]
            if not names:
                continue
            for key in rule_keys_in(row[3], ignore_span=span):
                for name in names:
                    add(reviewers, key, name)
    return reviewers


def decision_records() -> dict[Key, list[str]]:
    records: dict[Key, list[str]] = {}
    if not DECISIONS.is_dir():
        return records
    for path in sorted(DECISIONS.glob("*.md")):
        for key in sorted(rule_keys_in(path.read_text(encoding="utf-8"))):
            add(records, key, path.name)
    return records


def commits() -> dict[Key, list[str]]:
    result: dict[Key, list[str]] = {}
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
        for key in sorted(rule_keys_in(message)):
            add(result, key, sha.strip())
    return result


METHOD_RE = re.compile(
    r"\b(?:void|def|fun|public|private|protected|static|it|test)\b[^\n(]*?"
    r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(")
METHOD_ID_RE = re.compile(r"(?i)settle[_-]?r(\d{2,})(?:[_-]?v(\d+))?(?![0-9])")


def tests() -> dict[Key, list[str]]:
    found: dict[Key, list[str]] = {}
    if not SERVICES.is_dir():
        return found
    for test_dir in sorted(SERVICES.glob("*/src/test")):
        for path in sorted(p for p in test_dir.rglob("*") if p.is_file()):
            try:
                text = path.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                continue
            relative = path.relative_to(ROOT).as_posix()
            for name in METHOD_RE.findall(text):
                for number, suffix in METHOD_ID_RE.findall(name):
                    add(found, (int(number), version_of(suffix)),
                        f"{relative}::{name}")
            for line_number, line in enumerate(text.splitlines(), 1):
                for number, suffix in FULL_ID_RE.findall(line):
                    key = (int(number), version_of(suffix))
                    if not any(label.startswith(f"{relative}::")
                               for label in found.get(key, [])):
                        add(found, key, f"{relative}:{line_number}")
    return found


RESULT_KEYS = ("result", "status", "outcome", "verdict")


def parity_results() -> dict[Key, list[str]]:
    found: dict[Key, list[str]] = {}
    if not PARITY.is_file():
        return found
    try:
        with PARITY.open(encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return found

    def outcome(obj: dict) -> str:
        for name in RESULT_KEYS:
            if name in obj and isinstance(obj[name], (str, int, float)):
                return str(obj[name])
        if isinstance(obj.get("passed"), bool):
            return "pass" if obj["passed"] else "fail"
        return "recorded"

    def walk(node: object) -> None:
        if isinstance(node, dict):
            mentioned: set[Key] = set()
            for name, value in node.items():
                mentioned |= rule_keys_in(str(name))
                if isinstance(value, str):
                    mentioned |= rule_keys_in(value)
            if mentioned:
                verdict = outcome(node)
                for key in mentioned:
                    add(found, key, verdict)
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(data)
    return found


def cell(values: list[str]) -> str:
    return ", ".join(f"`{value}`" for value in values) if values else NONE


def render(rows: list[dict[str, str]], notes: list[str], version: str) -> str:
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
        f"kind was found. A rule id with a version suffix (`v2`) is an approved "
        f"future state recorded beneath the current-state rule of the same id.",
        "",
    ]
    lines.extend(notes)
    lines.extend([
        "",
        "| " + " | ".join(columns) + " |",
        "|" + "|".join("---" for _ in columns) + "|",
    ])
    for row in rows:
        lines.append("| " + " | ".join(row[column] for column in columns) + " |")
    lines.append("")
    return "\n".join(lines)


def build() -> tuple[str, str]:
    text = SPEC.read_text(encoding="utf-8")
    version = spec_version(text)
    rules = spec_rules(text)
    inventory = spec_scenarios(text)
    numbers = sorted({key[0] for key, _, _ in rules})
    span = (numbers[0], numbers[-1]) if numbers else None
    reviewers = change_log_reviewers(text, span)
    decisions = decision_records()
    commit_index = commits()
    test_index = tests()
    parity_index = parity_results()

    rows: list[dict[str, str]] = []
    with_transcript = with_test = with_parity = 0
    execution_only: list[str] = []
    versioned: list[str] = []
    for key, status, evidence in rules:
        transcript = transcripts_for(evidence, inventory)
        test_names = test_index.get(key, [])
        parity = parity_index.get(key, [])
        with_transcript += bool(transcript)
        with_test += bool(test_names)
        with_parity += bool(parity)
        if not transcript and EXECUTION_RE.search(evidence):
            execution_only.append(rule_label(key))
        if key[1] > 1:
            versioned.append(rule_label(key))
        rows.append({
            "rule": rule_label(key),
            "spec version": version,
            "status": status,
            "transcript": cell(transcript),
            "decision record": cell(decisions.get(key, [])),
            "commit": cell(commit_index.get(key, [])),
            "test": cell(test_names),
            "parity result": cell(parity),
            "reviewer": cell(reviewers.get(key, [])),
        })
    rows_note = (f" ({len(rows)} rows: {', '.join(versioned)} "
                 f"{'is' if len(versioned) == 1 else 'are'} listed beside the "
                 f"current-state rule of the same id)") if versioned else ""
    summary = (f"Summary: {len(numbers)} rules{rows_note}; {with_transcript} "
               f"with a transcript, {with_test} with a test, {with_parity} "
               f"with a parity result.")
    notes = [summary]
    if execution_only:
        notes.append("")
        notes.append(
            "Evidenced by execution (`E:` citation in the spec) rather than by a "
            "transcript, so `transcript` reads `none` although the rule is not "
            "unevidenced: " + ", ".join(execution_only) + ".")
    return render(rows, notes, version), summary


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

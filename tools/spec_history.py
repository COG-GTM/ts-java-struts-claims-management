#!/usr/bin/env python3
"""Generate docs/specs/SPEC-SETTLE-001-history.md from the git history of the spec.

Nothing in the output is typed by hand. The script walks every commit on the
current branch that touched ``docs/specs/SPEC-SETTLE-001.md``, reads the spec
as it was at each commit, and records for every rule id and version:

* the spec ``Version:`` and the commit (short sha, date, subject) in which that
  rule version first appeared;
* for a superseded version, the commit that superseded it and the
  ``docs/changes/CHG-nnn`` record its successor cites, if any;
* for every rule with more than one version, the old and new text side by side;
* the tests whose javadoc cites the version, under
  ``services/settlement-service/src/test``, and the transcripts
  ``parity/routes.json`` maps to it.

It also checks that a rule version, once published, kept its text: the spec is
append-only, so a version whose words differ between two commits is listed as
amended in place.

The closing section explains the three ways a rule changes here, with the
examples taken from the history itself. Mention matching (an unqualified id
belongs to every version of the rule, a qualified one to that version only)
and the test and transcript lookups are shared with tools/traceability.py.

The output holds no timestamp, so running it twice on the same commit writes
the same bytes.

Usage: python3 tools/spec_history.py  (see `make spec-history`)
"""

import glob
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import traceability  # noqa: E402

ROOT = traceability.ROOT
SPEC = traceability.SPEC
OPEN_QUESTIONS = os.path.join("docs", "specs", "OPEN-QUESTIONS.md")
OUTPUT = os.path.join("docs", "specs", "SPEC-SETTLE-001-history.md")
TEST_ROOT = os.path.join("services", "settlement-service", "src", "test")

HEADING = re.compile(r"^\*\*(SETTLE-R\d+) \(v(\d+)(?:, superseded by v(\d+))?\)\*\* — ?(.*)$")
VERSION_LINE = re.compile(r"^Version:\s*(\S+)")
EVIDENCE = re.compile(r"^(Observed|Read|Approved):")
CHANGE_RECORD = re.compile(r"docs/changes/(CHG-\d+)[\w.-]*")
OQ_ROW = re.compile(r"^\| (OQ-\d+) \| (.*?) \| (.*?) \| (.*?) \| (.*?) \| (.*?) \|\s*$")


def git(*args):
    return subprocess.check_output(["git"] + list(args), cwd=ROOT).decode("utf-8")


def spec_commits(path):
    """Oldest first: (sha, short sha, date, subject) of each commit touching path."""
    out = git("log", "--first-parent", "--reverse", "--format=%H%x00%h%x00%ad%x00%s",
              "--date=short", "--", path)
    return [tuple(line.split("\0")) for line in out.splitlines() if line]


def file_at(sha, path):
    try:
        return subprocess.check_output(["git", "show", "%s:%s" % (sha, path)], cwd=ROOT,
                                       stderr=subprocess.DEVNULL).decode("utf-8")
    except subprocess.CalledProcessError:
        return ""


def parse_spec(text):
    """(spec version, {(rule, version): rule dict}, {(rule, version): history row})."""
    spec_version = "-"
    rules = {}
    history = {}
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        version = VERSION_LINE.match(line)
        if version:
            spec_version = version.group(1)
        heading = HEADING.match(line)
        if heading:
            body = [heading.group(4)]
            index += 1
            while index < len(lines) and lines[index].strip():
                body.append(lines[index])
                index += 1
            prose, evidence = split_evidence(body)
            rules[(heading.group(1), heading.group(2))] = {
                "superseded_by": heading.group(3),
                "prose": prose,
                "evidence": evidence,
            }
            continue
        row = traceability.HISTORY.match(line)
        if row:
            history[(row.group(1), row.group(2))] = row.group(3)
        index += 1
    return spec_version, rules, history


def split_evidence(body):
    """The rule statement, and its Observed/Read/Approved lines, each joined."""
    prose, evidence = [], []
    for line in body:
        if evidence or EVIDENCE.match(line.strip()):
            evidence.append(line.strip())
        else:
            prose.append(line.strip())
    joined = " ".join(evidence)
    joined = re.sub(r"\s*(?<!^)(Observed|Read|Approved):", r"<br>\1:", joined)
    return " ".join(prose), joined


def rule_sort_key(rule):
    return int(rule[0].split("-R")[1]), int(rule[1])


def label(rule):
    return "%s (v%s)" % rule


def javadoc_citations():
    """Rule version -> tests (Class#method) under the settlement service."""
    citations = {}
    for directory, unused, names in os.walk(os.path.join(ROOT, TEST_ROOT)):
        for name in sorted(names):
            if not name.endswith(".java"):
                continue
            path = os.path.join(directory, name)
            class_name = name[: -len(".java")]
            lines = traceability.read(os.path.relpath(path, ROOT)).splitlines()
            index = 0
            while index < len(lines):
                if lines[index].strip().startswith("/**"):
                    block = []
                    while index < len(lines):
                        block.append(lines[index])
                        if traceability.JAVADOC_END.search(lines[index]):
                            break
                        index += 1
                    member = traceability.member_after(lines, index + 1)
                    for rule in traceability.mentions("\n".join(block)):
                        test = class_name + ("#" + member if member else "")
                        citations.setdefault(rule, set()).add(test)
                index += 1
    return citations


def open_questions():
    """Rows of OPEN-QUESTIONS.md with the commit that first carried each one."""
    rows = []
    seen = set()
    for sha, short, date, subject in spec_commits(OPEN_QUESTIONS):
        for line in file_at(sha, OPEN_QUESTIONS).splitlines():
            row = OQ_ROW.match(line)
            if row and row.group(1) not in seen:
                seen.add(row.group(1))
                rows.append({"id": row.group(1), "question": row.group(2),
                             "blocks": row.group(3), "status": row.group(6),
                             "commit": (short, date, subject)})
    current = {}
    if os.path.exists(os.path.join(ROOT, OPEN_QUESTIONS)):
        for line in traceability.read(OPEN_QUESTIONS).splitlines():
            row = OQ_ROW.match(line)
            if row:
                current[row.group(1)] = row.group(6)
    for row in rows:
        row["status"] = current.get(row["id"], row["status"] + " (row since removed)")
    return rows


def cell(text):
    return text.replace("|", "\\|") if text else "-"


def commit_cell(commit):
    short, date, subject = commit[1], commit[2], commit[3]
    return "`%s` %s — %s" % (short, date, cell(subject))


def collect():
    commits = spec_commits(SPEC)
    if not commits:
        raise SystemExit("no history for %s on this branch" % SPEC)
    versions = {}          # (rule, version) -> record
    spec_versions = []     # (commit, spec version)
    amended = []           # (rule, version, commit) where the text changed
    previous_text = {}
    for commit in commits:
        spec_version, rules, history = parse_spec(file_at(commit[0], SPEC))
        spec_versions.append((commit, spec_version))
        for rule, found in rules.items():
            record = versions.setdefault(rule, {
                "introduced": commit, "spec_version": spec_version,
                "history": history.get(rule, ""), "superseded_in": None,
                "superseded_by": None,
            })
            record["prose"], record["evidence"] = found["prose"], found["evidence"]
            record["history"] = history.get(rule, record["history"])
            text = (found["prose"], found["evidence"])
            if rule in previous_text and previous_text[rule] != text:
                amended.append((rule, commit))
            previous_text[rule] = text
            if found["superseded_by"] and record["superseded_in"] is None:
                record["superseded_in"] = commit
                record["superseded_by"] = found["superseded_by"]
        for rule, record in versions.items():
            if rule not in rules and record.get("removed_in") is None:
                record["removed_in"] = commit
    for record in versions.values():
        cited = set()
        for text in (record["prose"], record["evidence"], record["history"]):
            cited |= set(CHANGE_RECORD.findall(text))
        record["cites"] = sorted(cited)
    for rule, record in versions.items():
        successor = versions.get((rule[0], record["superseded_by"])) if record["superseded_by"] else None
        record["change_records"] = successor["cites"] if successor else record["cites"]
    return commits, spec_versions, versions, amended


def side_by_side(rule_id, chain):
    lines = ["### %s" % rule_id, ""]
    for old, new in zip(chain, chain[1:]):
        old_record, new_record = old[1], new[1]
        lines += [
            "| %s — spec %s, `%s` | %s — spec %s, `%s` |" % (
                label(old[0]), old_record["spec_version"], old_record["introduced"][1],
                label(new[0]), new_record["spec_version"], new_record["introduced"][1]),
            "| --- | --- |",
            "| %s | %s |" % (cell(old_record["prose"]), cell(new_record["prose"])),
            "| %s | %s |" % (cell(old_record["evidence"]), cell(new_record["evidence"])),
            "",
        ]
        records = new_record["cites"]
        lines.append("Change record: %s." % (
            ", ".join("`docs/changes/%s`" % name for name in records) if records
            else "none — the rule text changed without a behaviour change"))
        lines.append("")
    return lines


def how_this_spec_changes(versions, questions):
    corrections, approved = [], []
    for rule, record in sorted(versions.items(), key=lambda item: rule_sort_key(item[0])):
        if not record["superseded_by"]:
            continue
        successor = (rule[0], record["superseded_by"])
        entry = (rule, successor, record["superseded_in"], record["change_records"])
        (approved if record["change_records"] else corrections).append(entry)

    def example_correction():
        if not corrections:
            return "No example in this branch's history yet."
        rule, successor, commit, unused = corrections[0]
        return ("Example: %s was superseded by %s in `%s` (%s, \"%s\"); the Rule history "
                "row for %s reads: %s. No change record is cited, the monolith and the "
                "service behave as before, and the parity verdicts are untouched." % (
                    label(rule), label(successor), commit[1], commit[2], cell(commit[3]),
                    label(successor), cell(versions[successor]["history"]) or "-"))

    def example_approved():
        if not approved:
            return "No example in this branch's history yet."
        rule, successor, commit, records = approved[0]
        return ("Example: %s was superseded by %s in `%s` (%s, \"%s\"), which cites %s. "
                "The same commit added the record, the tests that cite %s, and the "
                "`approved_differences` entry in `parity/routes.json`, so `make parity` "
                "reports the affected transcript as CHANGED rather than PASS or FAIL." % (
                    label(rule), label(successor), commit[1], commit[2], cell(commit[3]),
                    ", ".join("`docs/changes/%s`" % name for name in records),
                    label(successor)))

    def example_question():
        for question in questions:
            blocked = traceability.mentions(question["blocks"])
            if not blocked:
                continue
            rule_id = sorted(blocked)[0][0]
            chain = sorted((rule for rule in versions if rule[0] == rule_id), key=rule_sort_key)
            current = chain[-1] if chain else None
            later = [rule for rule in chain
                     if versions[rule]["introduced"][2] > question["commit"][1]]
            unchanged = ("no new version of %s has been added since" % rule_id if not later
                         else "%s has since moved on to %s" % (rule_id, label(later[-1])))
            return ("Example: %s, added in `%s` (%s, \"%s\"), asks: \"%s\" It blocks %s; "
                    "its status is %s. The rule it names keeps its current text (%s), and %s." % (
                        question["id"], question["commit"][0], question["commit"][1],
                        cell(question["commit"][2]), cell(question["question"]),
                        cell(question["blocks"]), cell(question["status"]),
                        label(current) if current else "-", unchanged))
        return "No example in this branch's history yet."

    return [
        "## How this spec changes",
        "",
        "Rule ids are permanent and rule text is append-only: a rule is never rewritten in",
        "place, a new version of the same id is added beside the old one, the old heading",
        "is marked superseded, a Rule history row is added, and the spec `Version:` is",
        "bumped. The history above shows three distinct reasons for a change, and each",
        "leaves a different trail.",
        "",
        "1. **A correction of a misread** — the legacy code or a transcript was read",
        "   incompletely or wrongly, so the rule text is revised, but nothing about the",
        "   system's behaviour changes. Trail: a new rule version, a Rule history row, no",
        "   `docs/changes/CHG-nnn` record, no entry in `parity/routes.json`.",
        "   " + example_correction(),
        "",
        "2. **An approved behaviour change** — the extracted service is required to behave",
        "   differently from the monolith. Trail: a new rule version whose text names the",
        "   `docs/changes/CHG-nnn` record (`Approved:`), the record itself, tests that cite",
        "   the new version, and an `approved_differences` entry in `parity/routes.json`;",
        "   the legacy version is kept as the monolith's behaviour.",
        "   " + example_approved(),
        "",
        "3. **An open question** — the reading is correct but it is not known whether the",
        "   behaviour is intended. Trail: a row in `docs/specs/OPEN-QUESTIONS.md` naming the",
        "   rule, with an interim decision; the rule itself is unchanged until the owner",
        "   answers, at which point the answer arrives as one of the two kinds above.",
        "   " + example_question(),
        "",
    ]


def main():
    traceability.require_full_history()
    commits, spec_versions, versions, amended = collect()
    citations = javadoc_citations()
    routes = json.loads(traceability.read(os.path.join("parity", "routes.json")))
    questions = open_questions()
    ordered = sorted(versions, key=rule_sort_key)

    lines = [
        "# SPEC-SETTLE-001 — rule history",
        "",
        "Generated by `make spec-history` (tools/spec_history.py). Do not edit: every",
        "cell is read out of the git history of `docs/specs/SPEC-SETTLE-001.md` on the",
        "current branch, the tests under `services/settlement-service/src/test`,",
        "`parity/routes.json` and `docs/specs/OPEN-QUESTIONS.md`, and the file is",
        "rewritten byte for byte on every run.",
        "",
        "## Commits that changed the spec",
        "",
        "| Commit | Date | Spec version | Subject |",
        "| --- | --- | --- | --- |",
    ]
    for commit, spec_version in spec_versions:
        lines.append("| `%s` | %s | %s | %s |" % (commit[1], commit[2], spec_version, cell(commit[3])))
    seen_versions = [version for unused, version in spec_versions]
    lines += [
        "",
        "Spec versions seen on this branch, oldest first: %s." % ", ".join(seen_versions),
        "",
        "## Rule versions",
        "",
        "*Introduced in* is the first commit whose spec carries the rule version, and *Spec",
        "version* the `Version:` line of that spec. *Superseded in* is the first commit whose",
        "spec marks the version superseded, and *Change record* the `docs/changes/CHG-nnn`",
        "record the version cites (for a superseded version, the record its successor",
        "cites). *Tests* are the tests whose javadoc cites the version, *Transcripts* the",
        "scenarios `parity/routes.json` maps to it.",
        "",
        "| Rule | Status | Spec version | Introduced in | Superseded in | Change record | Tests | Transcripts |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for rule in ordered:
        record = versions[rule]
        if record["superseded_by"]:
            status = "superseded by v%s" % record["superseded_by"]
            superseded = commit_cell(record["superseded_in"])
            changes = traceability.cell(["`docs/changes/%s`" % name for name in record["change_records"]])
        else:
            status, superseded = "current", "-"
            changes = traceability.cell(["`docs/changes/%s`" % name for name in record["change_records"]])
        if record.get("removed_in"):
            status += "; removed in `%s`" % record["removed_in"][1]
        tests = traceability.tests_for(rule, citations)
        transcripts = traceability.transcripts_for(rule, routes)
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
            label(rule), status, record["spec_version"], commit_cell(record["introduced"]),
            superseded, changes,
            traceability.cell(["`%s`" % name for name in tests]),
            traceability.cell(["`%s`" % name for name in transcripts])))

    multi = {}
    for rule in ordered:
        multi.setdefault(rule[0], []).append((rule, versions[rule]))
    chains = [(rule_id, chain) for rule_id, chain in multi.items() if len(chain) > 1]
    lines += ["", "## Rules with more than one version", ""]
    if chains:
        lines.append("%d of %d rules: %s. Old text on the left, the version that superseded it on the right." % (
            len(chains), len(multi), ", ".join(rule_id for rule_id, unused in chains)))
        lines.append("")
        for rule_id, chain in chains:
            lines += side_by_side(rule_id, chain)
    else:
        lines += ["None: every rule has a single version.", ""]

    lines += ["## Rule versions amended in place", ""]
    if amended:
        lines += ["The spec is append-only; these versions changed their words after they",
                  "were published, which should not happen:", ""]
        for rule, commit in amended:
            lines.append("* %s in %s" % (label(rule), commit_cell(commit)))
        lines.append("")
    else:
        lines += ["None: every rule version kept its text once published (reflowed line breaks",
                  "and the `superseded by` marker on the heading do not count).", ""]

    lines += how_this_spec_changes(versions, questions)

    with open(os.path.join(ROOT, OUTPUT), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
    print("wrote %s (%d rule versions over %d commits; %d rules with more than one version)" % (
        OUTPUT, len(ordered), len(commits), len(chains)))


if __name__ == "__main__":
    main()

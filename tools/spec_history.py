#!/usr/bin/env python3
"""Write the change history of docs/specs/SPEC-SETTLE-001.md.

Nothing in the output is typed by hand. The script walks every commit on the
current branch that touched the spec, parses the rules each revision declares,
and diffs consecutive revisions, so that for every rule version it can say:

* when it appeared (commit, date, author, spec ``Version:`` at that commit);
* what the spec's own "Rule history" row says about it;
* whether and where it was later marked superseded, or edited in place (rule
  text is meant to be append-only, so an in-place edit is reported as a check
  failure, not hidden);
* the evidence written into the rule (``Observed:`` transcripts, ``Read:``
  source lines, ``Approved:`` change records);
* the tests whose javadoc cites it, the transcripts ``parity/routes.json``
  maps to it with their ``parity/report.json`` verdicts, and the
  ``docs/changes/CHG-nnn`` records that name it.

The rule-mention, test-citation and transcript lookups are the ones
``tools/traceability.py`` uses, imported from it, so both documents agree.

Two files are written:

* ``docs/specs/SPEC-SETTLE-001-history.md`` — the timeline and a table of every
  rule version, readable on GitHub;
* ``docs/specs/SPEC-SETTLE-001-history.html`` — the same data as a
  self-contained page (inline CSS and JavaScript, no server, no external
  assets): click a rule version on the timeline to see the old and new text
  side by side with links to its commit, tests and change record.

The output carries no timestamp; links to files use ``--ref`` (default: the
current branch) so the same tree writes the same bytes.

Usage: python3 tools/spec_history.py [--ref BRANCH]  (see `make spec-history`)
"""

import argparse
import datetime
import glob
import html
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import traceability  # noqa: E402  (tools/traceability.py)

ROOT = traceability.ROOT
SPEC = traceability.SPEC
SPEC_NAME = os.path.basename(SPEC)[: -len(".md")]
OUT_MD = os.path.join("docs", "specs", SPEC_NAME + "-history.md")
OUT_HTML = os.path.join("docs", "specs", SPEC_NAME + "-history.html")

HEADING = re.compile(r"^\*\*(SETTLE-R\d+) \(v(\d+)([^)]*)\)\*\*\s*[—-]\s*(.*)$")
SUPERSEDED = re.compile(r"superseded by v(\d+)")
VERSION_LINE = re.compile(r"^Version:\s*(\S+)")
EVIDENCE_START = re.compile(r"^(Observed|Read|Approved):")
EVIDENCE_SPLIT = re.compile(r"(?=\b(?:Observed|Read|Approved):)")
SOURCE_REF = re.compile(r"^([\w./-]+\.\w+)(?::([\d,-]+))?$")
GITHUB = re.compile(r"github\.com[/:]([^/]+)/([^/\s]+?)(?:\.git)?/?$")

git = traceability.git


def rule_key(rule_id, version):
    return "%s (v%s)" % (rule_id, version)


# --- reading one revision of the spec ---------------------------------------

def parse_spec(text):
    """Spec version, rules in order, and the Rule history rows of one revision.

    Each rule is {id, version, flags, text, evidence, section}; ``text`` is the
    rule's wording without the bold heading, ``evidence`` the list of trailing
    ``Observed:`` / ``Read:`` / ``Approved:`` clauses.
    """
    spec_version = None
    section = None
    rules = []
    history = {}
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        version = VERSION_LINE.match(line)
        if version and spec_version is None:
            spec_version = version.group(1)
        if line.startswith("## "):
            section = line[3:].strip()
        heading = HEADING.match(line)
        if heading:
            block = [heading.group(4)]
            index += 1
            while index < len(lines) and lines[index].strip():
                block.append(lines[index])
                index += 1
            body, evidence = split_evidence(block)
            rules.append({
                "id": heading.group(1),
                "version": int(heading.group(2)),
                "flags": heading.group(3).strip(", "),
                "text": body,
                "evidence": evidence,
                "section": section,
            })
            continue
        row = traceability.HISTORY.match(line)
        if row:
            history[rule_key(row.group(1), int(row.group(2)))] = row.group(3)
        index += 1
    return spec_version, rules, history


def split_evidence(block):
    for position, line in enumerate(block):
        if EVIDENCE_START.match(line):
            body = " ".join(part.strip() for part in block[:position])
            tail = " ".join(part.strip() for part in block[position:])
            clauses = [clause.strip().rstrip(";").strip()
                       for clause in EVIDENCE_SPLIT.split(tail) if clause.strip()]
            return body, [parse_evidence(clause) for clause in clauses]
    return " ".join(part.strip() for part in block), []


def parse_evidence(clause):
    kind, _, rest = clause.partition(":")
    items = []
    for item in split_outside_parentheses(rest):
        entry = {"text": item}
        ref = SOURCE_REF.match(item)
        if kind in ("Read", "Approved") and ref:
            entry["path"] = ref.group(1)
            entry["lines"] = ref.group(2)
        elif kind == "Observed":
            name, _, remark = item.partition(" ")
            name = name.rstrip(",")
            if os.path.exists(os.path.join(ROOT, "transcripts", name + ".json")):
                entry["transcript"] = name
                entry["remark"] = remark.strip()
        items.append(entry)
    return {"kind": kind, "items": items}


def split_outside_parentheses(text):
    """Split on ';' but not inside parentheses: a transcript remark such as
    '(... departs from; recorded as CHANGED ...)' is one item."""
    items, depth, current = [], 0, []
    for char in text:
        if char == ";" and depth == 0:
            items.append("".join(current))
            current = []
            continue
        depth += (char == "(") - (char == ")")
        current.append(char)
    items.append("".join(current))
    return [item.strip() for item in items if item.strip()]


# --- the commits that changed the spec --------------------------------------

def spec_commits():
    """Every commit on the current branch that touched the spec, oldest first."""
    log = git("log", "--no-merges", "--topo-order", "--reverse",
              "--format=%H%x1f%h%x1f%at%x1f%an%x1f%s%x1f%(trailers:key=Co-authored-by,valueonly,separator=%x1e)",
              "--", SPEC)
    commits = []
    for record in log.splitlines():
        if not record.strip():
            continue
        sha, short, stamp, author, subject, trailers = record.split("\x1f")
        # Formatted here, not by git: git's %aI writes "+00:00" or "Z" for UTC
        # depending on its version, which would make the output differ by machine.
        date = datetime.datetime.fromtimestamp(int(stamp), datetime.timezone.utc).isoformat()
        coauthors = [name.split("<")[0].strip() for name in trailers.split("\x1e") if name.strip()]
        commits.append({"sha": sha, "short": short, "date": date[:10],
                        "datetime": date, "author": author,
                        "coauthors": coauthors, "subject": subject})
    return commits


def revisions():
    """Each spec commit with the spec as it stood at that commit, and the
    events (added / superseded / edited / removed rule versions) relative to
    the revision before it."""
    result = []
    previous = {}
    previous_version = None
    for commit in spec_commits():
        spec_version, rules, history = parse_spec(
            git("show", "%s:%s" % (commit["sha"], SPEC)))
        current = {rule_key(rule["id"], rule["version"]): rule for rule in rules}
        events = []
        for key, rule in current.items():
            before = previous.get(key)
            if before is None:
                kind = "added" if rule["version"] == 1 else "new-version"
                events.append({"kind": kind, "rule": key})
            else:
                if rule["text"] != before["text"]:
                    events.append({"kind": "edited", "rule": key})
                elif rule["flags"] != before["flags"]:
                    events.append({"kind": "superseded", "rule": key,
                                   "flags": rule["flags"]})
                if rule["evidence"] != before["evidence"] and rule["text"] == before["text"]:
                    events.append({"kind": "evidence", "rule": key})
        for key in previous:
            if key not in current:
                events.append({"kind": "removed", "rule": key})
        result.append({
            "commit": commit,
            "spec_version": spec_version,
            "previous_spec_version": previous_version,
            "rules": rules,
            "history": history,
            "events": events,
        })
        previous = current
        previous_version = spec_version
    return result


# --- evidence outside the spec ----------------------------------------------

def test_citations():
    """Rule mention -> list of {name, path, line} for the tests citing it."""
    citations = {}
    for path in sorted(traceability.test_files()):
        rel = os.path.relpath(path, ROOT)
        class_name = os.path.basename(path)[: -len(".java")]
        lines = traceability.read(rel).splitlines()
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
                line_number = member_line(lines, index + 1)
                for rule in traceability.mentions("\n".join(block)):
                    name = class_name + ("#" + member if member else "")
                    citations.setdefault(rule, []).append(
                        {"name": name, "path": rel, "line": line_number})
            index += 1
    return citations


def member_line(lines, index):
    for offset, line in enumerate(lines[index:index + 6]):
        stripped = line.strip()
        if stripped and not stripped.startswith("@") and not stripped.startswith("//"):
            return index + offset + 1
    return index + 1


def tests_for(rule_id, version, citations):
    found = {}
    for (cited_id, cited_version), tests in citations.items():
        if cited_id == rule_id and cited_version in (None, str(version)):
            for test in tests:
                found[test["name"]] = test
    return [found[name] for name in sorted(found)]


def change_records_for(rule_id, version):
    records = []
    for path in sorted(glob.glob(os.path.join(ROOT, "docs", "changes", "*.md"))):
        rel = os.path.relpath(path, ROOT)
        text = traceability.read(rel)
        if traceability.refers_to(text, (rule_id, str(version))):
            title = next((line[2:].strip() for line in text.splitlines()
                          if line.startswith("# ")), os.path.basename(path))
            records.append({"path": rel, "title": title})
    return records


# --- assembling the catalogue -----------------------------------------------

def repo_url():
    try:
        remote = git("remote", "get-url", "origin").strip()
    except Exception:  # noqa: BLE001  (no remote: links are omitted)
        return None
    match = GITHUB.search(remote)
    return "https://github.com/%s/%s" % (match.group(1), match.group(2)) if match else None


def current_ref():
    """Branch name for the file links. GitHub Actions checks out a detached
    commit, so the branch is taken from its environment there to keep the
    generated files identical to a local run on the same branch."""
    for var in ("GITHUB_HEAD_REF", "GITHUB_REF_NAME"):
        if os.environ.get(var):
            return os.environ[var]
    ref = git("rev-parse", "--abbrev-ref", "HEAD").strip()
    return ref if ref != "HEAD" else git("rev-parse", "HEAD").strip()


def build(ref):
    traceability.require_full_history()
    revs = revisions()
    if not revs:
        raise SystemExit("no commit on this branch touches " + SPEC)
    head = revs[-1]
    citations = test_citations()
    routes = json.loads(traceability.read(os.path.join("parity", "routes.json")))
    parity = traceability.verdicts()

    first_seen = {}
    later = {}
    for rev in revs:
        for event in rev["events"]:
            if event["kind"] in ("added", "new-version"):
                first_seen[event["rule"]] = rev
            else:
                later.setdefault(event["rule"], []).append(
                    {"kind": event["kind"], "commit": rev["commit"],
                     "spec_version": rev["spec_version"]})

    catalogue = []
    by_id = {}
    for rule in head["rules"]:
        by_id.setdefault(rule["id"], []).append(rule)
    for rule in head["rules"]:
        key = rule_key(rule["id"], rule["version"])
        intro = first_seen.get(key)
        superseded = SUPERSEDED.search(rule["flags"])
        earlier = [other for other in by_id[rule["id"]]
                   if other["version"] == rule["version"] - 1]
        scenarios = traceability.transcripts_for((rule["id"], str(rule["version"])), routes)
        catalogue.append({
            "key": key,
            "id": rule["id"],
            "version": rule["version"],
            "section": rule["section"],
            "status": ("superseded by v%s" % superseded.group(1)) if superseded else "current",
            "text": rule["text"],
            "evidence": rule["evidence"],
            "previous": earlier[0] if earlier else None,
            "introduced": {
                "commit": intro["commit"], "spec_version": intro["spec_version"],
            } if intro else None,
            "history": head["history"].get(key),
            "later": later.get(key, []),
            "tests": tests_for(rule["id"], rule["version"], citations),
            "transcripts": [{"name": name, "verdict": parity.get(name, "not replayed")}
                            for name in scenarios],
            "changes": change_records_for(rule["id"], rule["version"]),
        })

    checks = run_checks(revs, catalogue)
    return {
        "spec": SPEC,
        "spec_name": SPEC_NAME,
        "spec_version": head["spec_version"],
        "repo_url": repo_url(),
        "ref": ref,
        "revisions": [{
            "commit": rev["commit"],
            "spec_version": rev["spec_version"],
            "previous_spec_version": rev["previous_spec_version"],
            "rule_count": len(rev["rules"]),
            "events": rev["events"],
        } for rev in revs],
        "rules": catalogue,
        "checks": checks,
    }


def run_checks(revs, catalogue):
    """Facts a reader should not have to notice by eye."""
    notes = []
    versions = [rev["spec_version"] for rev in revs]
    for before, after in zip(versions, versions[1:]):
        try:
            step = round((float(after) - float(before)) * 10)
        except (TypeError, ValueError):
            continue
        if step > 1:
            notes.append("Spec version jumped from %s to %s in `%s`; no commit on this "
                         "branch declares the version(s) in between." % (
                             before, after, revs[versions.index(after)]["commit"]["short"]))
        elif step < 1:
            notes.append("Spec version did not increase (%s -> %s) in `%s`." % (
                before, after, revs[versions.index(after)]["commit"]["short"]))
    for rev in revs:
        for event in rev["events"]:
            if event["kind"] == "edited":
                notes.append("%s was edited in place in `%s`; rule text is append-only." % (
                    event["rule"], rev["commit"]["short"]))
            if event["kind"] == "removed":
                notes.append("%s was removed in `%s`; rule ids are permanent." % (
                    event["rule"], rev["commit"]["short"]))
    for rule in catalogue:
        if rule["history"] is None:
            notes.append("%s has no Rule history row." % rule["key"])
        elif rule["introduced"]:
            declared = traceability.SPEC_VERSION.search(rule["history"])
            if declared and declared.group(1) != rule["introduced"]["spec_version"]:
                notes.append("%s: Rule history says version %s but the rule first appears "
                             "in the spec at version %s (`%s`)." % (
                                 rule["key"], declared.group(1),
                                 rule["introduced"]["spec_version"],
                                 rule["introduced"]["commit"]["short"]))
        if rule["version"] > 1 and rule["previous"] is None:
            notes.append("%s has no v%d text in the spec." % (rule["key"], rule["version"] - 1))
        if rule["version"] > 1 and rule["previous"] is not None:
            flagged = SUPERSEDED.search(rule["previous"]["flags"])
            if not flagged or int(flagged.group(1)) != rule["version"]:
                notes.append("%s (v%d) is not marked superseded by v%d." % (
                    rule["id"], rule["version"] - 1, rule["version"]))
    return notes


# --- Markdown ---------------------------------------------------------------

def commit_link(data, commit):
    if data["repo_url"]:
        return "[`%s`](%s/commit/%s)" % (commit["short"], data["repo_url"], commit["sha"])
    return "`%s`" % commit["short"]


def file_link(data, path, lines=None, label=None):
    label = label or (path + (":" + lines if lines else ""))
    anchor = ""
    if lines:
        first = re.split(r"[,-]", lines)[0]
        anchor = "#L" + first
        last = lines.split(",")[0].split("-")
        if len(last) == 2:
            anchor += "-L" + last[1]
    rel = os.path.relpath(os.path.join(ROOT, path), os.path.join(ROOT, "docs", "specs"))
    return "[`%s`](%s%s)" % (label, rel.replace(os.sep, "/"), anchor)


def evidence_md(data, evidence):
    parts = []
    for group in evidence:
        rendered = []
        for item in group["items"]:
            if "path" in item:
                rendered.append(file_link(data, item["path"], item["lines"]))
            elif "transcript" in item:
                rendered.append(file_link(data, "transcripts/%s.json" % item["transcript"],
                                          label=item["transcript"])
                                + ((" " + item["remark"]) if item["remark"] else ""))
            else:
                rendered.append(item["text"])
        parts.append("%s: %s" % (group["kind"], "; ".join(rendered)))
    return "<br>".join(parts) if parts else "-"


EVENT_WORDS = {
    "added": "added",
    "new-version": "new version",
    "superseded": "marked superseded",
    "edited": "text edited in place",
    "evidence": "evidence changed",
    "removed": "removed",
}


def write_markdown(data):
    lines = [
        "# %s — change history" % data["spec_name"],
        "",
        "Generated by `make spec-history` (tools/spec_history.py). Do not edit: every",
        "line is read out of the git history of `%s`, the spec's own Rule" % data["spec"],
        "history table, the test javadoc, `parity/routes.json`, `parity/report.json`",
        "and `docs/changes/`. The interactive version of this page is",
        "`%s`." % os.path.basename(OUT_HTML),
        "",
        "Spec version now: **%s**, %d rule versions, %d commits on this branch." % (
            data["spec_version"], len(data["rules"]), len(data["revisions"])),
        "",
        "## Timeline",
        "",
    ]
    for rev in data["revisions"]:
        commit = rev["commit"]
        who = commit["author"]
        if commit["coauthors"]:
            who += " with " + ", ".join(commit["coauthors"])
        version = ("spec %s -> %s" % (rev["previous_spec_version"], rev["spec_version"])
                   if rev["previous_spec_version"] else "spec %s" % rev["spec_version"])
        lines.append("### %s — %s (%s)" % (commit["datetime"], commit_link(data, commit), version))
        lines.append("")
        lines.append("%s — %s. %d rule versions after this commit." % (
            who, commit["subject"], rev["rule_count"]))
        lines.append("")
        grouped = {}
        for event in rev["events"]:
            grouped.setdefault(event["kind"], []).append(event)
        for kind in EVENT_WORDS:
            events = grouped.get(kind)
            if not events:
                continue
            names = ", ".join(e["rule"] for e in events)
            if kind == "superseded":
                names = ", ".join("%s (%s)" % (e["rule"], e["flags"]) for e in events)
            lines.append("* %s: %s" % (EVENT_WORDS[kind].capitalize(), names))
        lines.append("")

    lines += [
        "## Rule versions",
        "",
        "*Introduced* is the first commit on this branch whose spec declares the rule",
        "version, with the spec `Version:` at that commit. *Status* is read from the",
        "rule heading. *Tests* are the tests whose javadoc cites the rule version,",
        "*Transcripts* the scenarios `parity/routes.json` maps to it with their",
        "`parity/report.json` verdicts, *Change record* the `docs/changes` files that",
        "name it.",
        "",
        "| Rule | Section | Introduced | Status | Rule history | Tests | Transcripts | Change record |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for rule in data["rules"]:
        intro = rule["introduced"]
        introduced = ("spec %s, %s, %s" % (intro["spec_version"], intro["commit"]["date"],
                                           commit_link(data, intro["commit"]))
                      if intro else "-")
        status = rule["status"]
        for event in rule["later"]:
            status += "; %s in %s" % (EVENT_WORDS[event["kind"]], commit_link(data, event["commit"]))
        tests = ", ".join(file_link(data, t["path"], str(t["line"]), t["name"])
                          for t in rule["tests"]) or "-"
        transcripts = ", ".join(
            "%s (%s)" % (file_link(data, "transcripts/%s.json" % t["name"], label=t["name"]),
                         t["verdict"]) for t in rule["transcripts"]) or "-"
        changes = ", ".join(file_link(data, c["path"], label=os.path.basename(c["path"]))
                            for c in rule["changes"]) or "-"
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
            rule["key"], rule["section"] or "-", introduced, status,
            (rule["history"] or "-").replace("|", "\\|"), tests, transcripts, changes))
    lines.append("")

    lines += ["## What changed, version by version", ""]
    for rule in data["rules"]:
        if rule["version"] == 1:
            continue
        lines.append("### %s" % rule["key"])
        lines.append("")
        intro = rule["introduced"]
        if intro:
            lines.append("Introduced at spec %s in %s (%s): %s." % (
                intro["spec_version"], commit_link(data, intro["commit"]),
                intro["commit"]["date"], intro["commit"]["subject"]))
            lines.append("")
        if rule["history"]:
            lines.append("Rule history: %s" % rule["history"])
            lines.append("")
        if rule["previous"]:
            lines.append("**Before — %s (v%d)**" % (rule["id"], rule["version"] - 1))
            lines.append("")
            lines.append("> " + rule["previous"]["text"])
            lines.append(">")
            lines.append("> " + evidence_md(data, rule["previous"]["evidence"]))
            lines.append("")
        lines.append("**After — %s**" % rule["key"])
        lines.append("")
        lines.append("> " + rule["text"])
        lines.append(">")
        lines.append("> " + evidence_md(data, rule["evidence"]))
        lines.append("")
        if rule["changes"]:
            lines.append("Change record: " + ", ".join(
                "%s — %s" % (file_link(data, c["path"], label=os.path.basename(c["path"])), c["title"])
                for c in rule["changes"]))
            lines.append("")
        if rule["tests"]:
            lines.append("Tests: " + ", ".join(
                file_link(data, t["path"], str(t["line"]), t["name"]) for t in rule["tests"]))
            lines.append("")

    lines += ["## Checks", ""]
    if data["checks"]:
        lines += ["* " + note for note in data["checks"]]
    else:
        lines.append("No findings: every rule version has a history row, versions increase by one, and no rule text was edited in place.")
    lines.append("")

    with open(os.path.join(ROOT, OUT_MD), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))


# --- HTML -------------------------------------------------------------------

def write_html(data):
    template = HTML_TEMPLATE
    payload = json.dumps(data, ensure_ascii=False, sort_keys=True).replace("</", "<\\/")
    page = (template
            .replace("__TITLE__", html.escape(data["spec_name"] + " — change history"))
            .replace("__DATA__", payload))
    with open(os.path.join(ROOT, OUT_HTML), "w", encoding="utf-8") as handle:
        handle.write(page)


HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>__TITLE__</title>
<style>
:root {
  --bg: #f6f7f9; --panel: #ffffff; --ink: #1c2330; --muted: #5b6473; --line: #d9dee6;
  --accent: #0b5fff; --accent-ink: #ffffff; --added: #d9f5e3; --added-ink: #0d5b2d;
  --removed: #fde2e2; --removed-ink: #8a1c1c; --chip: #eef2f7; --warn: #fff4d6; --warn-ink: #7a5300;
  --current: #0d5b2d; --superseded: #8a1c1c; --mono: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #11151c; --panel: #181d26; --ink: #e8ecf2; --muted: #9aa5b5; --line: #2a323f;
    --accent: #6ea0ff; --accent-ink: #0b1220; --added: #15381f; --added-ink: #9be3b5;
    --removed: #45191c; --removed-ink: #ffb3b3; --chip: #222a36; --warn: #3a2f12; --warn-ink: #ffd982;
    --current: #9be3b5; --superseded: #ffb3b3;
  }
}
* { box-sizing: border-box; }
html, body { margin: 0; background: var(--bg); color: var(--ink); font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
a { color: var(--accent); }
code, .mono { font-family: var(--mono); font-size: 0.92em; }
header { padding: 20px 28px 12px; border-bottom: 1px solid var(--line); background: var(--panel); }
header h1 { margin: 0 0 4px; font-size: 22px; }
header p { margin: 0; color: var(--muted); }
.layout { display: grid; grid-template-columns: minmax(300px, 380px) minmax(0, 1fr); gap: 0; min-height: calc(100vh - 86px); }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
nav { border-right: 1px solid var(--line); background: var(--panel); padding: 18px 20px 40px; overflow: auto; }
nav h2, main h2 { font-size: 13px; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); margin: 0 0 12px; }
.search { width: 100%; padding: 8px 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--bg); color: var(--ink); margin-bottom: 16px; font: inherit; }
.timeline { list-style: none; margin: 0; padding: 0 0 0 18px; border-left: 2px solid var(--line); }
.timeline > li { position: relative; margin: 0 0 22px; }
.timeline > li::before { content: ""; position: absolute; left: -24px; top: 6px; width: 10px; height: 10px; border-radius: 50%; background: var(--accent); border: 2px solid var(--panel); }
.when { font-size: 12px; color: var(--muted); }
.subject { margin: 2px 0 6px; font-weight: 600; }
.subject a { color: inherit; text-decoration: none; }
.subject a:hover { text-decoration: underline; }
.version { display: inline-block; font-size: 12px; padding: 1px 7px; border-radius: 10px; background: var(--chip); margin-left: 6px; font-weight: 500; }
.events { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.chip { font: inherit; font-size: 12.5px; font-family: var(--mono); padding: 3px 9px; border-radius: 14px; border: 1px solid var(--line); background: var(--chip); color: var(--ink); cursor: pointer; }
.chip:hover, .chip:focus-visible { border-color: var(--accent); outline: none; box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 35%, transparent); }
.chip.active { background: var(--accent); color: var(--accent-ink); border-color: var(--accent); }
.chip .k { opacity: .7; font-family: system-ui, sans-serif; margin-left: 5px; font-size: 11px; }
.kind-label { font-size: 11px; text-transform: uppercase; letter-spacing: .05em; color: var(--muted); margin: 8px 0 2px; }
main { padding: 22px 28px 48px; min-width: 0; overflow-wrap: anywhere; }
.empty { color: var(--muted); padding: 40px 0; }
.detail h3 { margin: 0 0 4px; font-size: 20px; }
.detail .meta { color: var(--muted); margin-bottom: 14px; }
.status { display: inline-block; font-size: 12px; font-weight: 600; padding: 1px 8px; border-radius: 10px; margin-left: 8px; vertical-align: middle; }
.status.current { background: var(--added); color: var(--added-ink); }
.status.superseded { background: var(--removed); color: var(--removed-ink); }
.compare { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin: 14px 0 18px; }
@media (max-width: 1100px) { .compare { grid-template-columns: 1fr; } }
.pane { min-width: 0; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 14px 16px; }
.pane h4 { margin: 0 0 8px; font-size: 13px; color: var(--muted); font-weight: 600; }
.pane h4 .note { font-weight: 400; margin-left: 6px; }
.pane p { margin: 0 0 10px; }
.pane .none { color: var(--muted); font-style: italic; }
del { background: var(--removed); color: var(--removed-ink); text-decoration: none; border-radius: 2px; padding: 0 1px; }
ins { background: var(--added); color: var(--added-ink); text-decoration: none; border-radius: 2px; padding: 0 1px; }
.evidence { font-size: 13.5px; border-top: 1px dashed var(--line); padding-top: 8px; margin-top: 8px; }
.evidence b { color: var(--muted); font-weight: 600; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px; }
.card { min-width: 0; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 14px 16px; }
.card h4 { margin: 0 0 8px; font-size: 13px; color: var(--muted); text-transform: uppercase; letter-spacing: .05em; }
.card ul { margin: 0; padding-left: 18px; }
.card li { margin: 2px 0; }
.verdict { font-size: 11.5px; padding: 0 6px; border-radius: 8px; background: var(--chip); margin-left: 4px; }
.verdict.PASS { background: var(--added); color: var(--added-ink); }
.verdict.FAIL { background: var(--removed); color: var(--removed-ink); }
.verdict.CHANGED { background: var(--warn); color: var(--warn-ink); }
.checks { margin-top: 26px; }
.checks li { background: var(--warn); color: var(--warn-ink); border-radius: 6px; padding: 6px 10px; margin: 6px 0; list-style: none; }
.checks ul { padding: 0; margin: 0; }
.all { margin-top: 26px; }
.all table { width: 100%; border-collapse: collapse; font-size: 13.5px; background: var(--panel); }
.all th, .all td { text-align: left; padding: 6px 10px; border-bottom: 1px solid var(--line); vertical-align: top; }
.all th { color: var(--muted); font-weight: 600; font-size: 12px; }
.all tr.hidden { display: none; }
.all button.link { background: none; border: 0; padding: 0; color: var(--accent); font: inherit; font-family: var(--mono); cursor: pointer; text-decoration: underline; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
</head>
<body>
<header>
  <h1 id="title"></h1>
  <p id="subtitle"></p>
</header>
<div class="layout">
  <nav aria-label="Timeline">
    <h2>Timeline</h2>
    <input class="search" id="search" type="search" placeholder="Filter rules (e.g. R06, deductible)" aria-label="Filter rules">
    <ol class="timeline" id="timeline"></ol>
  </nav>
  <main>
    <section id="detail" aria-live="polite"><p class="empty">Click a rule version on the timeline, or in the table below, to compare it with the version before it.</p></section>
    <section class="checks" id="checks"></section>
    <section class="all">
      <h2>All rule versions</h2>
      <table id="rules"><thead><tr><th>Rule</th><th>Section</th><th>Introduced</th><th>Status</th><th>Tests</th><th>Transcripts</th><th>Change record</th></tr></thead><tbody></tbody></table>
    </section>
  </main>
</div>
<script id="data" type="application/json">__DATA__</script>
<script>
(function () {
  var data = JSON.parse(document.getElementById('data').textContent);
  var rules = {};
  data.rules.forEach(function (r) { rules[r.key] = r; });
  var KIND = {added: 'added', 'new-version': 'new version', superseded: 'marked superseded',
              edited: 'edited in place', evidence: 'evidence changed', removed: 'removed'};

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (k === 'text') node.textContent = attrs[k];
      else if (k === 'html') node.innerHTML = attrs[k];
      else node.setAttribute(k, attrs[k]);
    });
    (children || []).forEach(function (c) { if (c) node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
    return node;
  }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]; }); }
  function commitUrl(c) { return data.repo_url ? data.repo_url + '/commit/' + c.sha : null; }
  function fileUrl(path, lines) {
    if (!data.repo_url) return null;
    var url = data.repo_url + '/blob/' + data.ref + '/' + path;
    if (lines) {
      var first = String(lines).split(/[,-]/)[0];
      var range = String(lines).split(',')[0].split('-');
      url += '#L' + first + (range.length === 2 ? '-L' + range[1] : '');
    }
    return url;
  }
  function link(label, url, mono) {
    var a = url ? el('a', {href: url, target: '_blank', rel: 'noopener', text: label}) : el('span', {text: label});
    if (mono) a.classList.add('mono');
    return a;
  }

  // word-level diff (LCS) between two texts
  function wordDiff(a, b) {
    var A = a ? a.split(/(\s+)/) : [], B = b ? b.split(/(\s+)/) : [];
    var n = A.length, m = B.length, i, j;
    var L = [];
    for (i = 0; i <= n; i++) { L.push(new Array(m + 1).fill(0)); }
    for (i = n - 1; i >= 0; i--) for (j = m - 1; j >= 0; j--)
      L[i][j] = A[i] === B[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
    var left = '', right = '';
    i = 0; j = 0;
    while (i < n && j < m) {
      if (A[i] === B[j]) { left += esc(A[i]); right += esc(B[j]); i++; j++; }
      else if (L[i + 1][j] >= L[i][j + 1]) { if (A[i].trim()) left += '<del>' + esc(A[i]) + '</del>'; else left += esc(A[i]); i++; }
      else { if (B[j].trim()) right += '<ins>' + esc(B[j]) + '</ins>'; else right += esc(B[j]); j++; }
    }
    while (i < n) { left += A[i].trim() ? '<del>' + esc(A[i]) + '</del>' : esc(A[i]); i++; }
    while (j < m) { right += B[j].trim() ? '<ins>' + esc(B[j]) + '</ins>' : esc(B[j]); j++; }
    var common = L[0][0], shorter = Math.min(n, m) || 1;
    // A rewrite shares only filler words with its predecessor; marking those
    // word by word hides the change, so the texts are shown plain instead. An
    // extension keeps most of the old words, and is marked.
    if (common / shorter < 0.75) return {left: esc(a), right: esc(b), rewritten: true};
    return {left: left, right: right, rewritten: false};
  }

  function evidenceNode(evidence) {
    var box = el('div', {'class': 'evidence'});
    if (!evidence.length) { box.appendChild(el('span', {'class': 'none', text: 'No evidence line.'})); return box; }
    evidence.forEach(function (group) {
      var line = el('div', {}, [el('b', {text: group.kind + ': '})]);
      group.items.forEach(function (item, idx) {
        if (idx) line.appendChild(document.createTextNode('; '));
        if (item.path) line.appendChild(link(item.path + (item.lines ? ':' + item.lines : ''), fileUrl(item.path, item.lines), true));
        else if (item.transcript) { line.appendChild(link(item.transcript, fileUrl('transcripts/' + item.transcript + '.json'), true)); if (item.remark) line.appendChild(document.createTextNode(' ' + item.remark)); }
        else line.appendChild(document.createTextNode(item.text));
      });
      box.appendChild(line);
    });
    return box;
  }

  function pane(title, rule, htmlText, note) {
    var h = el('h4', {text: title});
    if (note) h.appendChild(el('span', {'class': 'note', text: note}));
    var p = el('div', {'class': 'pane'}, [h]);
    if (!rule) { p.appendChild(el('p', {'class': 'none', text: 'No earlier version of this rule: this is where it first appears.'})); return p; }
    p.appendChild(el('p', {html: htmlText}));
    p.appendChild(evidenceNode(rule.evidence));
    return p;
  }

  function show(key, fromChip) {
    var r = rules[key];
    if (!r) return;
    document.querySelectorAll('.chip').forEach(function (c) { c.classList.toggle('active', c.getAttribute('data-rule') === key); });
    var detail = document.getElementById('detail');
    detail.innerHTML = '';
    var status = el('span', {'class': 'status ' + (r.status === 'current' ? 'current' : 'superseded'), text: r.status});
    detail.appendChild(el('h3', {}, [el('span', {'class': 'mono', text: r.key}), status]));
    var meta = el('div', {'class': 'meta'});
    if (r.introduced) {
      var c = r.introduced.commit;
      meta.appendChild(document.createTextNode('Introduced at spec ' + r.introduced.spec_version + ' on ' + c.date + ' in '));
      meta.appendChild(link(c.short, commitUrl(c), true));
      meta.appendChild(document.createTextNode(' — ' + c.subject + ' (' + c.author + (c.coauthors.length ? ' with ' + c.coauthors.join(', ') : '') + ').'));
    }
    r.later.forEach(function (e) {
      meta.appendChild(document.createTextNode(' ' + KIND[e.kind].charAt(0).toUpperCase() + KIND[e.kind].slice(1) + ' at spec ' + e.spec_version + ' on ' + e.commit.date + ' in '));
      meta.appendChild(link(e.commit.short, commitUrl(e.commit), true));
      meta.appendChild(document.createTextNode('.'));
    });
    if (r.section) meta.appendChild(document.createTextNode(' Section: ' + r.section + '.'));
    detail.appendChild(meta);
    if (r.history) detail.appendChild(el('p', {}, [el('b', {text: 'Rule history: '}), r.history]));

    var diff = r.previous ? wordDiff(r.previous.text, r.text) : {left: '', right: esc(r.text)};
    var compare = el('div', {'class': 'compare'});
    var note = r.previous ? (diff.rewritten ? '(rewritten: changed words are not marked)' : '(removed words marked)') : '';
    compare.appendChild(pane(r.previous ? 'Before — ' + r.id + ' (v' + (r.version - 1) + ')' : 'Before', r.previous, diff.left, note));
    compare.appendChild(pane('After — ' + r.key, r, diff.right, r.previous && !diff.rewritten ? '(added words marked)' : ''));
    detail.appendChild(compare);

    var cards = el('div', {'class': 'cards'});
    var commitCard = el('div', {'class': 'card'}, [el('h4', {text: 'Commit'})]);
    if (r.introduced) {
      var ic = r.introduced.commit;
      commitCard.appendChild(el('div', {}, [link(ic.short, commitUrl(ic), true), ' ' + ic.subject]));
      commitCard.appendChild(el('div', {'class': 'when', text: ic.datetime + ' · ' + ic.author}));
      commitCard.appendChild(el('div', {}, [link('Spec at this commit', data.repo_url ? data.repo_url + '/blob/' + ic.sha + '/' + data.spec : null)]));
    } else commitCard.appendChild(el('span', {'class': 'none', text: 'Not found in the history of this branch.'}));
    cards.appendChild(commitCard);

    var testCard = el('div', {'class': 'card'}, [el('h4', {text: 'Tests (' + r.tests.length + ')'})]);
    if (r.tests.length) testCard.appendChild(el('ul', {}, r.tests.map(function (t) { return el('li', {}, [link(t.name, fileUrl(t.path, t.line), true)]); })));
    else testCard.appendChild(el('span', {'class': 'none', text: 'No test javadoc cites this rule version.'}));
    cards.appendChild(testCard);

    var trCard = el('div', {'class': 'card'}, [el('h4', {text: 'Transcripts (' + r.transcripts.length + ')'})]);
    if (r.transcripts.length) trCard.appendChild(el('ul', {}, r.transcripts.map(function (t) {
      return el('li', {}, [link(t.name, fileUrl('transcripts/' + t.name + '.json'), true), el('span', {'class': 'verdict ' + t.verdict.split(' ')[0], text: t.verdict})]);
    })));
    else trCard.appendChild(el('span', {'class': 'none', text: 'parity/routes.json maps no transcript to this rule version.'}));
    cards.appendChild(trCard);

    var chgCard = el('div', {'class': 'card'}, [el('h4', {text: 'Change record'})]);
    if (r.changes.length) chgCard.appendChild(el('ul', {}, r.changes.map(function (c) { return el('li', {}, [link(c.path.split('/').pop(), fileUrl(c.path), true), ' — ' + c.title]); })));
    else chgCard.appendChild(el('span', {'class': 'none', text: 'No docs/changes record names this rule version.'}));
    cards.appendChild(chgCard);
    detail.appendChild(cards);

    if (!fromChip) detail.scrollIntoView({behavior: 'smooth', block: 'start'});
    if (history.replaceState) history.replaceState(null, '', '#' + encodeURIComponent(key));
  }

  function chip(key, kind) {
    var b = el('button', {'class': 'chip', type: 'button', 'data-rule': key, 'aria-label': key + ', ' + KIND[kind]}, [key]);
    if (kind !== 'added' && kind !== 'new-version') b.appendChild(el('span', {'class': 'k', text: KIND[kind]}));
    b.addEventListener('click', function () { show(key, true); });
    return b;
  }

  function renderTimeline(filter) {
    var ol = document.getElementById('timeline');
    ol.innerHTML = '';
    data.revisions.slice().reverse().forEach(function (rev) {
      var c = rev.commit;
      var li = el('li', {'data-sha': c.sha});
      li.appendChild(el('div', {'class': 'when', text: c.date + ' · ' + c.author}));
      var version = rev.previous_spec_version ? rev.previous_spec_version + ' → ' + rev.spec_version : rev.spec_version;
      li.appendChild(el('div', {'class': 'subject'}, [link(c.subject, commitUrl(c)), el('span', {'class': 'version', text: 'spec ' + version})]));
      li.appendChild(el('div', {'class': 'when'}, [link(c.short, commitUrl(c), true), ' · ' + rev.rule_count + ' rule versions after this commit']));
      var order = ['new-version', 'superseded', 'edited', 'evidence', 'removed', 'added'];
      var any = false;
      order.forEach(function (kind) {
        var evs = rev.events.filter(function (e) { return e.kind === kind && matches(e.rule, filter); });
        if (!evs.length) return;
        any = true;
        li.appendChild(el('div', {'class': 'kind-label', text: KIND[kind] + ' (' + evs.length + ')'}));
        var wrap = el('div', {'class': 'events'});
        evs.forEach(function (e) { wrap.appendChild(chip(e.rule, kind)); });
        li.appendChild(wrap);
      });
      if (any || !filter) ol.appendChild(li);
    });
  }

  function matches(key, filter) {
    if (!filter) return true;
    var r = rules[key];
    var hay = (key + ' ' + (r ? r.text + ' ' + (r.section || '') : '')).toLowerCase();
    return filter.split(/\s+/).every(function (w) { return !w || hay.indexOf(w) >= 0; });
  }

  function renderTable(filter) {
    var tbody = document.querySelector('#rules tbody');
    tbody.innerHTML = '';
    data.rules.forEach(function (r) {
      var tr = el('tr', {'class': matches(r.key, filter) ? '' : 'hidden'});
      var btn = el('button', {'class': 'link', type: 'button', text: r.key});
      btn.addEventListener('click', function () { show(r.key, false); });
      tr.appendChild(el('td', {}, [btn]));
      tr.appendChild(el('td', {text: r.section || '-'}));
      var intro = el('td');
      if (r.introduced) { intro.appendChild(document.createTextNode('spec ' + r.introduced.spec_version + ' · ' + r.introduced.commit.date + ' · ')); intro.appendChild(link(r.introduced.commit.short, commitUrl(r.introduced.commit), true)); }
      else intro.textContent = '-';
      tr.appendChild(intro);
      tr.appendChild(el('td', {}, [el('span', {'class': 'status ' + (r.status === 'current' ? 'current' : 'superseded'), text: r.status, style: 'margin-left:0'})]));
      tr.appendChild(el('td', {text: r.tests.length ? r.tests.length + ' test' + (r.tests.length === 1 ? '' : 's') : '-'}));
      var tds = el('td');
      r.transcripts.forEach(function (t, i) { if (i) tds.appendChild(document.createTextNode(', ')); tds.appendChild(el('span', {'class': 'mono', text: t.name})); tds.appendChild(el('span', {'class': 'verdict ' + t.verdict.split(' ')[0], text: t.verdict})); });
      if (!r.transcripts.length) tds.textContent = '-';
      tr.appendChild(tds);
      var chg = el('td');
      r.changes.forEach(function (c, i) { if (i) chg.appendChild(document.createTextNode(', ')); chg.appendChild(link(c.path.split('/').pop(), fileUrl(c.path), true)); });
      if (!r.changes.length) chg.textContent = '-';
      tr.appendChild(chg);
      tbody.appendChild(tr);
    });
  }

  function renderChecks() {
    var box = document.getElementById('checks');
    box.appendChild(el('h2', {text: 'Checks'}));
    if (!data.checks.length) { box.appendChild(el('p', {'class': 'none', text: 'No findings.'})); return; }
    box.appendChild(el('ul', {}, data.checks.map(function (n) { return el('li', {text: n.replace(/`/g, '')}); })));
  }

  document.getElementById('title').textContent = data.spec_name + ' — change history';
  document.getElementById('subtitle').textContent = 'Spec version ' + data.spec_version + ' · ' + data.rules.length + ' rule versions · ' + data.revisions.length + ' commits on ' + data.ref + ' · generated by tools/spec_history.py from the git history of ' + data.spec;
  renderTimeline('');
  renderTable('');
  renderChecks();
  document.getElementById('search').addEventListener('input', function (e) {
    var f = e.target.value.trim().toLowerCase();
    renderTimeline(f); renderTable(f);
  });
  var initial = decodeURIComponent(location.hash.replace(/^#/, ''));
  if (initial && rules[initial]) show(initial, false);
})();
</script>
</body>
</html>
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--ref", default=None,
                        help="branch or commit used in links to files (default: current branch)")
    args = parser.parse_args()
    data = build(args.ref or current_ref())
    write_markdown(data)
    write_html(data)
    print("wrote %s and %s (%d rule versions, %d commits, %d checks)" % (
        OUT_MD, OUT_HTML, len(data["rules"]), len(data["revisions"]), len(data["checks"])))


if __name__ == "__main__":
    main()

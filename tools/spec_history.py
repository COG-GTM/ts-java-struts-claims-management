#!/usr/bin/env python3
"""Generate the change history of docs/specs/SPEC-SETTLE-001.md.

Writes docs/specs/SPEC-SETTLE-001-history.md and its self-contained HTML twin
docs/specs/SPEC-SETTLE-001-history.html. Nothing in either file is typed by
hand. Every fact is read out of the repository:

* the spec as it stood at every commit that touched it (``git log`` and
  ``git show <commit>:<spec>``): its Version line, each rule version's text and
  its Rule history row;
* for every rule version, the commit that introduced it, every later commit
  that changed its text, and the text before and after, side by side;
* the commits anywhere under the traced sources whose changed lines mention the
  rule version, from the introducing commit onwards (the same per-line reading
  as tools/traceability.py);
* the tests whose javadoc cites it, with file, line and the commit that added
  the test method;
* the docs/changes/CHG-nnn records that name it, with the lines that do;
* the transcripts parity/routes.json maps to it and their verdicts in
  parity/report.json.

The output carries no timestamp of its own, no branch name and no remote URL
read from the machine, so running it twice on the same commit writes the same
bytes, locally and in CI. Dates are formatted here from the commit's Unix time.

Usage: python3 tools/spec_history.py  (see `make spec-history`)
"""

import difflib
import glob
import html
import json
import os
import re
import sys
from datetime import datetime, timezone

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import traceability as tr  # noqa: E402

ROOT = tr.ROOT
SPEC = tr.SPEC
SPEC_NAME = os.path.splitext(os.path.basename(SPEC))[0]
OUT_MD = os.path.join("docs", "specs", SPEC_NAME + "-history.md")
OUT_HTML = os.path.join("docs", "specs", SPEC_NAME + "-history.html")
REPO_URL = "https://github.com/COG-GTM/ts-java-struts-claims-management"
LINK_REF = "migrated-app"

RULE_HEADING = re.compile(r"^\*\*([A-Z]+-R\d+) \(v(\d+)([^)]*)\)\*\*")
VERSION_LINE = re.compile(r"^Version:\s*(\S+)")
SECTION = re.compile(r"^## (.+)")
SUPERSEDED = re.compile(r"superseded by v(\d+)")
ROW_VERSION = re.compile(r"version (\d+)\.(\d+)", re.I)


def git(*args):
    return tr.git(*args)


def when(unix):
    return datetime.fromtimestamp(int(unix), timezone.utc).strftime(
        "%Y-%m-%d %H:%M UTC")


def key(rule):
    return (int(rule[0].split("-R")[1]), int(rule[1]))


def label(rule):
    return "%s (v%s)" % rule


def anchor(rule):
    return "%s-v%s" % (rule[0].split("-", 1)[1], rule[1])


def normalise(text):
    """Rule text without the heading qualifier and line wrapping."""
    text = RULE_HEADING.sub(lambda m: "**%s (v%s)**" % (m.group(1), m.group(2)),
                            text)
    return " ".join(text.split())


def parse_spec(text):
    """Version line, rule blocks (in order) and Rule history rows of a spec."""
    version = None
    rules = {}
    order = []
    history = {}
    section = None
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        found_version = VERSION_LINE.match(line)
        if found_version and version is None:
            version = found_version.group(1)
        found_section = SECTION.match(line)
        if found_section:
            section = found_section.group(1).strip()
        heading = RULE_HEADING.match(line)
        if heading:
            rule = (heading.group(1), heading.group(2))
            start = index
            block = []
            while index < len(lines) and lines[index].strip():
                block.append(lines[index])
                index += 1
            qualifier = heading.group(3).strip(", ")
            superseded = SUPERSEDED.search(qualifier)
            rules[rule] = {
                "text": "\n".join(block),
                "line": start + 1,
                "section": section,
                "superseded_by": superseded.group(1) if superseded else None,
            }
            order.append(rule)
            continue
        row = tr.HISTORY.match(line)
        if row:
            history[(row.group(1), row.group(2))] = row.group(3).strip()
        index += 1
    return {"version": version, "rules": rules, "order": order,
            "history": history}


def word_diff(old, new):
    """Old and new text as HTML, deletions and insertions marked."""
    a, b = old.split(), new.split()
    left, right = [], []
    matcher = difflib.SequenceMatcher(None, a, b, autojunk=False)
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            same = html.escape(" ".join(a[i1:i2]))
            left.append(same)
            right.append(same)
            continue
        if i2 > i1:
            left.append("<del>%s</del>" % html.escape(" ".join(a[i1:i2])))
        if j2 > j1:
            right.append("<ins>%s</ins>" % html.escape(" ".join(b[j1:j2])))
    return " ".join(left), " ".join(right)


def spec_commits():
    out = git("log", "--reverse", "--topo-order",
              "--format=%H%x09%at%x09%s", "--", SPEC)
    commits = []
    for line in out.splitlines():
        sha, unix, subject = line.split("\t", 2)
        commits.append({"sha": sha, "short": sha[:7], "date": when(unix),
                        "subject": subject})
    return commits


def commit_info(sha):
    unix, subject = git("log", "-1", "--format=%at%x09%s", sha).strip().split(
        "\t", 1)
    return {"sha": sha, "short": sha[:7], "date": when(unix),
            "subject": subject}


def build_history(commits):
    """Walk the spec's commits; return per-commit snapshots and per-rule data."""
    snapshots = []
    versions = {}
    previous = None
    for commit in commits:
        spec = parse_spec(git("show", "%s:%s" % (commit["sha"], SPEC)))
        snapshots.append(spec)
        added, edited, superseded, removed, restored = [], [], [], [], []
        for rule in spec["order"]:
            block = spec["rules"][rule]
            before = previous["rules"].get(rule) if previous else None
            entry = versions.setdefault(rule, {"edits": []})
            if before is None and "introduced" in entry:
                entry.setdefault("restored", []).append(commit)
                entry["removed_in"] = None
                restored.append(rule)
                before = {"text": entry["last_text"],
                          "superseded_by": entry["last_superseded_by"]}
            if before is None:
                entry.update({
                    "introduced": commit, "intro_text": block["text"],
                    "intro_version": spec["version"],
                    "removed_in": None})
                added.append(rule)
            elif before["text"] != block["text"]:
                entry["edits"].append({
                    "commit": commit, "version": spec["version"],
                    "before": before["text"], "after": block["text"],
                    "wording_same": normalise(before["text"])
                    == normalise(block["text"])})
                edited.append(rule)
                if block["superseded_by"] and not before["superseded_by"]:
                    superseded.append(rule)
            entry["last_text"] = block["text"]
            entry["last_superseded_by"] = block["superseded_by"]
        if previous:
            for rule in previous["order"]:
                if rule not in spec["rules"]:
                    versions[rule]["removed_in"] = commit
                    removed.append(rule)
        new_rows = [r for r in spec["history"]
                    if not previous or r not in previous["history"]]
        commit.update({
            "version": spec["version"],
            "version_before": previous["version"] if previous else None,
            "added": added, "edited": edited, "superseded": superseded,
            "removed": removed, "restored": restored, "history_rows": new_rows,
            "diff": git("show", "--format=", "--unified=3", commit["sha"],
                        "--", SPEC),
        })
        previous = spec
    return snapshots, versions


def commit_trail(rules, order_index):
    """Commits whose changed lines mention each rule version, from the commit
    that introduced it onwards, with the files those lines are in."""
    trail = {rule: [] for rule in rules}
    shas = git("log", "--reverse", "--topo-order", "--no-merges", "--format=%H",
               "--", *searched_paths()).split()
    for sha in shas:
        diff = git("show", "--format=", "--unified=0", sha, "--",
                   *searched_paths())
        files = {}
        path = None
        for line in diff.splitlines():
            if line.startswith("+++ "):
                path = line[6:] if line.startswith("+++ b/") else path
                continue
            if line.startswith("--- ") or line[:1] not in ("+", "-"):
                if line.startswith("--- a/"):
                    path = line[6:]
                continue
            for rule in rules:
                if tr.refers_to(line[1:], rule):
                    files.setdefault(rule, set()).add(path)
        for rule, paths in files.items():
            start = order_index.get(rules[rule]["introduced"]["sha"], 0)
            if order_index.get(sha, 0) >= start:
                info = commit_info(sha)
                info["files"] = sorted(p for p in paths if p)
                trail[rule].append(info)
    return trail


def searched_paths():
    return tr.SEARCHED + [":(exclude)" + path
                          for path in tr.GENERATED + [OUT_MD, OUT_HTML]]


def javadoc_tests():
    """Every test javadoc that cites a rule: class, member, file, line, text."""
    found = []
    for path in sorted(tr.test_files()):
        rel = os.path.relpath(path, ROOT)
        class_name = os.path.basename(path)[: -len(".java")]
        lines = tr.read(rel).splitlines()
        index = 0
        while index < len(lines):
            if lines[index].strip().startswith("/**"):
                start = index
                block = []
                while index < len(lines):
                    block.append(lines[index])
                    if tr.JAVADOC_END.search(lines[index]):
                        break
                    index += 1
                text = "\n".join(block)
                cited = tr.mentions(text)
                if cited:
                    member = tr.member_after(lines, index + 1)
                    found.append({
                        "name": class_name + ("#" + member if member else ""),
                        "member": member, "file": rel, "line": start + 1,
                        "javadoc": clean_javadoc(block), "cites": cited})
            index += 1
    for test in found:
        test["added"] = added_in(test["file"], test["member"])
    return found


def clean_javadoc(block):
    out = []
    for line in block:
        line = line.strip()
        line = re.sub(r"^/\*\*\s?|\s?\*/$|^\*\s?", "", line)
        out.append(line)
    return "\n".join(l for l in out if l).strip()


def added_in(path, member):
    needle = ("void %s(" % member) if member else os.path.basename(path)
    shas = git("log", "--reverse", "--topo-order", "--format=%H",
               "-S" + needle, "--", path).split()
    if not shas:
        return None
    return commit_info(shas[0])


def tests_for(rule, tests):
    return [t for t in tests
            if any(cid == rule[0] and ver in (None, rule[1])
                   for cid, ver in t["cites"])]


def change_records():
    records = []
    for path in sorted(glob.glob(os.path.join(ROOT, "docs", "changes", "*.md"))):
        rel = os.path.relpath(path, ROOT)
        text = tr.read(rel)
        lines = text.splitlines()
        meta = {}
        for line in lines[:12]:
            match = re.match(r"^(Status|Date):\s*(.+)", line)
            if match:
                meta[match.group(1).lower()] = match.group(2).strip()
        shas = git("log", "--reverse", "--topo-order", "--diff-filter=A",
                   "--format=%H", "--", rel).split()
        records.append({
            "file": rel, "name": os.path.basename(rel)[:-3],
            "title": lines[0].lstrip("# ").strip() if lines else rel,
            "status": meta.get("status", "-"), "date": meta.get("date", "-"),
            "added": commit_info(shas[0]) if shas else None,
            "lines": lines})
    return records


def records_for(rule, records):
    out = []
    for record in records:
        hits = [{"line": n + 1, "text": text}
                for n, text in enumerate(record["lines"])
                if tr.refers_to(text, rule)]
        if hits:
            out.append({k: record[k] for k in
                        ("file", "name", "title", "status", "date", "added")})
            out[-1]["excerpt"] = hits
    return out


def checks(commits, versions, current, rows):
    """Consistency of the history with the spec's own conventions."""
    found = []
    seen = [c["version"] for c in commits]
    gaps = []
    for before, commit in zip(commits, commits[1:]):
        try:
            a = [int(x) for x in before["version"].split(".")]
            b = [int(x) for x in commit["version"].split(".")]
        except (AttributeError, ValueError):
            continue
        if a[0] == b[0] and b[1] - a[1] > 1:
            skipped = ", ".join("%d.%d" % (a[0], m)
                                for m in range(a[1] + 1, b[1]))
            gaps.append("Version moves %s to %s in `%s`; no commit of the "
                        "spec carries %s." % (before["version"],
                                              commit["version"],
                                              commit["short"], skipped))
    found.append({
        "check": "Version line sequence",
        "result": "Note" if gaps else "OK",
        "detail": " ".join(gaps) if gaps else
        "Versions in commit order: %s." % ", ".join(seen)})

    unbumped = [c["short"] for c in commits[1:]
                if (c["added"] or c["edited"] or c["removed"] or c["restored"])
                and c["version"] == c["version_before"]]
    found.append({
        "check": "Version line bumped when rules change",
        "result": "Note" if unbumped else "OK",
        "detail": ("Rules changed without a Version bump in %s."
                   % ", ".join("`%s`" % s for s in unbumped)) if unbumped else
        "Every commit that added, changed, removed or restored a rule version also changed the Version line."})

    missing, unstated, mismatched = [], [], []
    for row in rows:
        rule = row["rule"]
        text = current["history"].get(rule)
        if text is None:
            missing.append(label(rule))
            continue
        stated = ROW_VERSION.search(text)
        intro = versions[rule]["intro_version"]
        if stated is None:
            unstated.append(label(rule))
        elif intro and "%s.%s" % stated.groups() != intro:
            mismatched.append("%s says %s.%s, introduced at %s"
                              % (label(rule), stated.group(1), stated.group(2),
                                 intro))
    problems = []
    if missing:
        problems.append("No Rule history row: %s." % ", ".join(missing))
    if unstated:
        problems.append("Row names no spec version: %s." % ", ".join(unstated))
    if mismatched:
        problems.append("Row version differs from the spec version that "
                        "introduced the rule: %s." % "; ".join(mismatched))
    found.append({
        "check": "Rule history row for every rule version",
        "result": "Note" if problems else "OK",
        "detail": " ".join(problems) if problems else
        "All %d rule versions have a row, and each row names the spec version "
        "that introduced it." % len(rows)})

    reworded = []
    rewrapped = []
    for row in rows:
        for edit in versions[row["rule"]]["edits"]:
            (rewrapped if edit["wording_same"] else reworded).append(
                "%s in `%s`" % (label(row["rule"]), edit["commit"]["short"]))
    found.append({
        "check": "Rule text is append-only",
        "result": "Note" if reworded else "OK",
        "detail": ("Wording changed after introduction: %s." % ", ".join(reworded))
        if reworded else
        ("No rule version was reworded after it was introduced. Re-wrapped "
         "only, with the superseded marker added: %s." % ", ".join(rewrapped))
        if rewrapped else "No rule version was edited after it was introduced."})

    broken = []
    for row in rows:
        rule = row["rule"]
        block = current["rules"].get(rule)
        if block and block["superseded_by"] and \
                (rule[0], block["superseded_by"]) not in current["rules"]:
            broken.append("%s points to a missing v%s" % (label(rule),
                                                           block["superseded_by"]))
        if int(rule[1]) > 1:
            prior = current["rules"].get((rule[0], str(int(rule[1]) - 1)))
            if not prior:
                broken.append("%s has no v%d" % (label(rule), int(rule[1]) - 1))
            elif prior["superseded_by"] != rule[1]:
                broken.append("v%d of %s is not marked superseded by v%s"
                              % (int(rule[1]) - 1, rule[0], rule[1]))
    found.append({
        "check": "Superseded versions kept and linked",
        "result": "Note" if broken else "OK",
        "detail": "; ".join(broken) + "." if broken else
        "Every revised rule keeps its earlier text, marked superseded by the "
        "version that replaced it."})

    removed = [label(r["rule"]) for r in rows if r["status"] == "Removed"]
    found.append({
        "check": "No rule version removed",
        "result": "Note" if removed else "OK",
        "detail": ("Removed from the spec: %s." % ", ".join(removed)) if removed
        else "Every rule version ever published is still in the spec."})

    untested = [label(r["rule"]) for r in rows
                if not r["tests"] and r["status"] == "Current"]
    found.append({
        "check": "Current rule versions with no test citing them",
        "result": "Note" if untested else "OK",
        "detail": ", ".join(untested) + "." if untested else
        "Every current rule version is cited by at least one test javadoc."})
    return found


def collect():
    tr.require_full_history()
    commits = spec_commits()
    if not commits:
        raise SystemExit("%s has no history" % SPEC)
    snapshots, versions = build_history(commits)
    current = snapshots[-1]
    order_index = {sha: n for n, sha in enumerate(
        git("rev-list", "--reverse", "--topo-order", "HEAD").split())}
    trail = commit_trail(versions, order_index)
    tests = javadoc_tests()
    records = change_records()
    routes = json.loads(tr.read(os.path.join("parity", "routes.json")))
    parity = tr.verdicts()

    rows = []
    for rule in sorted(versions, key=key):
        data = versions[rule]
        block = current["rules"].get(rule)
        if block is None:
            status = "Removed"
        elif block["superseded_by"]:
            status = "Superseded"
        else:
            status = "Current"
        predecessor = None
        if int(rule[1]) > 1:
            prior = (rule[0], str(int(rule[1]) - 1))
            intro_index = commits.index(data["introduced"])
            before = snapshots[intro_index]["rules"].get(prior)
            if before is not None:
                old, new = word_diff(before["text"], data["intro_text"])
                predecessor = {"rule": label(prior), "anchor": anchor(prior),
                               "text": before["text"], "old_html": old,
                               "new_html": new}
        edits = []
        for edit in data["edits"]:
            old, new = word_diff(edit["before"], edit["after"])
            edits.append({"commit": short_commit(edit["commit"]),
                          "version": edit["version"],
                          "before": edit["before"], "after": edit["after"],
                          "old_html": old, "new_html": new,
                          "wording_same": edit["wording_same"]})
        scenarios = tr.transcripts_for(rule, routes)
        successor = (rule[0], str(int(rule[1]) + 1))
        rows.append({
            "rule": rule, "id": rule[0], "v": int(rule[1]),
            "label": label(rule), "anchor": anchor(rule), "status": status,
            "section": (block or {}).get("section") or "-",
            "spec_line": (block or {}).get("line"),
            "text": (block or {}).get("text") or data["intro_text"],
            "introduced": short_commit(data["introduced"]),
            "intro_version": data["intro_version"],
            "removed_in": short_commit(data["removed_in"])
            if data["removed_in"] else None,
            "restored_in": [short_commit(c) for c in data.get("restored", [])],
            "history_row": current["history"].get(rule, "-"),
            "predecessor": predecessor,
            "successor": anchor(successor) if successor in versions else None,
            "edits": edits,
            "tests": [{k: t[k] for k in ("name", "file", "line", "javadoc",
                                          "added")}
                      for t in tests_for(rule, tests)],
            "changes": records_for(rule, records),
            "transcripts": [{"name": s, "verdict": parity.get(s, "not replayed")}
                            for s in scenarios],
            "trail": trail[rule],
        })
    revised_ids = sorted({r["id"] for r in rows if r["v"] > 1},
                         key=lambda i: int(i.split("-R")[1]))
    for row in rows:
        row["revised"] = row["id"] in revised_ids
    return {
        "spec": SPEC, "spec_name": SPEC_NAME, "repo": REPO_URL,
        "link_ref": LINK_REF,
        "current_version": current["version"],
        "commits": [dict(short_commit(c), version=c["version"],
                         version_before=c["version_before"],
                         added=[anchor(r) for r in c["added"]],
                         edited=[anchor(r) for r in c["edited"]],
                         superseded=[anchor(r) for r in c["superseded"]],
                         removed=[anchor(r) for r in c["removed"]],
                         restored=[anchor(r) for r in c["restored"]],
                         history_rows=[label(r) for r in c["history_rows"]],
                         diff=c["diff"]) for c in commits],
        "rows": rows,
        "revised_ids": revised_ids,
        "checks": checks(commits, versions, current, rows),
    }


def short_commit(commit):
    return {k: commit[k] for k in ("sha", "short", "date", "subject")}


# ---------------------------------------------------------------- Markdown

def md_escape(text):
    return text.replace("|", "\\|").replace("\n", " ")


def md_commit(commit):
    return "[`%s`](%s/commit/%s)" % (commit["short"], REPO_URL, commit["sha"])


def md_rules(anchors, rows_by_anchor):
    return ", ".join(rows_by_anchor[a]["label"] for a in anchors)


def write_markdown(data):
    rows = data["rows"]
    by_anchor = {r["anchor"]: r for r in rows}
    ids = {r["id"] for r in rows}
    lines = [
        "# %s change history" % data["spec_name"],
        "",
        "Generated by `make spec-history` (tools/spec_history.py) from the git "
        "history of",
        "`%s`, the tests, `docs/changes/`, `parity/routes.json` and" % data["spec"],
        "`parity/report.json`. Do not edit; the interactive view is",
        "[%s-history.html](%s-history.html)." % (data["spec_name"], data["spec_name"]),
        "",
        "Current spec version **%s**: %d rule ids, %d rule versions, %d revised "
        "rule ids, %d commits to the spec." % (
            data["current_version"], len(ids), len(rows),
            len(data["revised_ids"]), len(data["commits"])),
        "",
        "## Timeline",
        "",
        "| Spec version | Commit | Date | Rule versions added | Rule text edited | Rule history rows added |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for c in data["commits"]:
        version = c["version"] if not c["version_before"] else \
            "%s → %s" % (c["version_before"], c["version"])
        lines.append("| %s | %s %s | %s | %s | %s | %s |" % (
            version, md_commit(c), md_escape(c["subject"]), c["date"],
            ", ".join(x for x in [md_rules(c["added"], by_anchor),
                                  md_rules(c["restored"], by_anchor)
                                  and md_rules(c["restored"], by_anchor)
                                  + " (restored)"] if x) or "-",
            md_rules(c["edited"], by_anchor) or "-",
            ", ".join(c["history_rows"]) or "-"))

    lines += ["", "## Rule versions", "",
              "*Introduced* is the commit that first published the rule "
              "version in the spec. *Commits* are the commits whose changed "
              "lines mention it from then on. *Tests* are the tests whose "
              "javadoc cites it.",
              "",
              "| Rule | Status | Introduced | Text edits | Tests | Change records | Parity | Commits |",
              "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for r in rows:
        status = r["status"]
        if r["status"] == "Superseded" and r["successor"]:
            status = "Superseded by v%d" % (r["v"] + 1)
        lines.append("| %s | %s | %s %s (v%s) | %s | %s | %s | %s | %s |" % (
            r["label"], status, md_commit(r["introduced"]),
            r["introduced"]["date"][:10], r["intro_version"],
            ", ".join("%s (v%s)" % (md_commit(e["commit"]), e["version"])
                      for e in r["edits"]) or "-",
            ", ".join("`%s`" % t["name"] for t in r["tests"]) or "-",
            ", ".join("[%s](../changes/%s)" % (c["name"], os.path.basename(c["file"]))
                      for c in r["changes"]) or "-",
            ", ".join("%s: %s" % (t["name"], t["verdict"])
                      for t in r["transcripts"]) or "-",
            ", ".join(md_commit(t) for t in r["trail"]) or "-"))

    lines += ["", "## Revisions, old and new text", ""]
    for r in rows:
        if r["predecessor"]:
            p = r["predecessor"]
            lines += [
                "### %s replaces %s" % (r["label"], p["rule"]),
                "",
                "Introduced in %s %s, spec version %s. Rule history: %s" % (
                    md_commit(r["introduced"]), md_escape(r["introduced"]["subject"]),
                    r["intro_version"], md_escape(r["history_row"])),
                "",
                "| %s (old) | %s (new) |" % (p["rule"], r["label"]),
                "| --- | --- |",
                "| %s | %s |" % (md_escape(p["text"]), md_escape(r["text"])),
                ""]
            if r["changes"]:
                lines.append("Change records: %s." % ", ".join(
                    "[%s](../changes/%s) (%s, %s)" % (
                        c["name"], os.path.basename(c["file"]), c["status"],
                        c["date"]) for c in r["changes"]))
            lines.append("Tests: %s." % (", ".join(
                "`%s` (%s:%d)" % (t["name"], t["file"], t["line"])
                for t in r["tests"]) or "none"))
            lines.append("")

    edits = [(r, e) for r in rows for e in r["edits"]]
    lines += ["## Edits to published rule text", ""]
    if not edits:
        lines += ["None.", ""]
    for r, e in edits:
        lines += [
            "### %s in %s" % (r["label"], e["commit"]["short"]),
            "",
            "%s %s, spec version %s. %s" % (
                md_commit(e["commit"]), md_escape(e["commit"]["subject"]),
                e["version"],
                "Wording unchanged (re-wrapped, heading qualifier changed)."
                if e["wording_same"] else "Wording changed."),
            "",
            "| Before | After |",
            "| --- | --- |",
            "| %s | %s |" % (md_escape(e["before"]), md_escape(e["after"])),
            ""]

    lines += ["## Consistency checks", "", "| Check | Result | Detail |",
              "| --- | --- | --- |"]
    for c in data["checks"]:
        lines.append("| %s | %s | %s |" % (c["check"], c["result"],
                                           md_escape(c["detail"])))
    lines.append("")
    with open(os.path.join(ROOT, OUT_MD), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))


# ---------------------------------------------------------------- HTML

def write_html(data):
    payload = json.dumps(data, sort_keys=True, ensure_ascii=False,
                         default=list).replace("</", "<\\/")
    page = TEMPLATE.replace("__TITLE__", html.escape(data["spec_name"])) \
                   .replace("__DATA__", payload)
    with open(os.path.join(ROOT, OUT_HTML), "w", encoding="utf-8") as handle:
        handle.write(page)


def main():
    data = collect()
    for row in data["rows"]:
        row["rule"] = list(row["rule"])
    write_markdown(data)
    write_html(data)
    print("wrote %s and %s (%d rule versions, %d spec commits)" % (
        OUT_MD, OUT_HTML, len(data["rows"]), len(data["commits"])))


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>__TITLE__ change history</title>
<!-- Generated by make spec-history (tools/spec_history.py). Do not edit. -->
<style>
:root{--bg:#fbfbf8;--card:#fff;--ink:#16181d;--muted:#555b66;--line:#d9dbe0;--accent:#1d4ed8;--accent-soft:#e6edff;--ok:#146c2e;--ok-soft:#e3f4e8;--warn:#8a5300;--warn-soft:#fff1d6;--bad:#a0221b;--bad-soft:#fde8e6}
*{box-sizing:border-box}
html{font-size:18px}
body{margin:0;background:var(--bg);color:var(--ink);font-family:system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif;line-height:1.5}
header{padding:24px 32px 12px;border-bottom:1px solid var(--line);background:var(--card)}
h1{margin:0 0 4px;font-size:1.7rem}
h2{font-size:1.3rem;margin:0 0 12px}
h3{font-size:1.05rem;margin:18px 0 8px}
.sub{color:var(--muted);margin:0}
.stats{display:flex;flex-wrap:wrap;gap:10px;margin-top:14px}
.stat{border:1px solid var(--line);border-radius:10px;padding:6px 14px;background:var(--bg)}
.stat b{font-size:1.25rem;margin-right:6px}
.layout{display:grid;grid-template-columns:minmax(0,1.15fr) minmax(420px,1fr);gap:24px;padding:20px 32px 40px;align-items:start}
@media (max-width:1100px){.layout{grid-template-columns:1fr}#panel{position:static;max-height:none}}
section{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:18px 20px;margin-bottom:20px}
#panel{position:sticky;top:12px;max-height:calc(100vh - 24px);overflow:auto;background:var(--card);border:2px solid var(--accent);border-radius:12px;padding:18px 20px;overflow-wrap:anywhere}
.timeline{display:flex;gap:0;align-items:stretch;overflow-x:auto;padding:8px 2px 4px}
.tnode{flex:1 1 0;min-width:180px;position:relative;padding-top:30px}
.tnode:before{content:"";position:absolute;top:13px;left:0;right:0;height:4px;background:var(--line)}
.tnode:first-child:before{left:50%}.tnode:last-child:before{right:50%}
.tnode .dot{position:absolute;top:4px;left:50%;margin-left:-11px;width:22px;height:22px;border-radius:50%;background:var(--accent);border:4px solid var(--card);box-shadow:0 0 0 2px var(--accent)}
.tnode button{display:block;width:calc(100% - 12px);margin:0 6px;text-align:left;font:inherit;background:var(--bg);border:1px solid var(--line);border-radius:10px;padding:10px 12px;cursor:pointer;color:inherit}
.tnode button:hover,.tnode button:focus-visible,.tnode button.sel{border-color:var(--accent);background:var(--accent-soft);outline:none}
.tnode .ver{font-size:1.3rem;font-weight:700}
.tnode .meta{color:var(--muted);font-size:.85rem}
.tnode .chg{font-size:.9rem;margin-top:4px}
table{border-collapse:collapse;width:100%}
th,td{border-bottom:1px solid var(--line);padding:7px 8px;text-align:left;vertical-align:top}
th{font-size:.85rem;color:var(--muted);font-weight:600}
tbody tr.rule{cursor:pointer}
tbody tr.rule:hover,tbody tr.rule:focus-visible{background:var(--accent-soft);outline:none}
tbody tr.rule.sel{background:var(--accent-soft);box-shadow:inset 4px 0 0 var(--accent)}
code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:.88em}
a{color:var(--accent)}
.badge{display:inline-block;border-radius:999px;padding:1px 10px;font-size:.8rem;font-weight:600;border:1px solid transparent;white-space:nowrap}
.b-current{background:var(--ok-soft);color:var(--ok);border-color:#b7dfc2}
.b-superseded{background:#eceef2;color:#3b4250;border-color:#cfd3da}
.b-removed{background:var(--bad-soft);color:var(--bad)}
.b-pass{background:var(--ok-soft);color:var(--ok)}
.b-changed{background:var(--warn-soft);color:var(--warn)}
.b-other{background:var(--bad-soft);color:var(--bad)}
.b-ok{background:var(--ok-soft);color:var(--ok)}
.b-note{background:var(--warn-soft);color:var(--warn)}
.b-rev{background:var(--accent-soft);color:var(--accent)}
.controls{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px}
.controls button{font:inherit;font-size:.9rem;border:1px solid var(--line);background:var(--bg);border-radius:999px;padding:4px 14px;cursor:pointer;color:inherit}
.controls button[aria-pressed="true"]{background:var(--accent);border-color:var(--accent);color:#fff}
.controls input{font:inherit;font-size:.95rem;padding:5px 10px;border:1px solid var(--line);border-radius:8px;min-width:220px}
.count{color:var(--muted);font-size:.9rem}
.side{display:grid;grid-template-columns:1fr 1fr;gap:12px}
@media (max-width:700px){.side{grid-template-columns:1fr}}
.side>div{border:1px solid var(--line);border-radius:10px;padding:10px 12px;background:var(--bg)}
.side h4{margin:0 0 6px;font-size:.9rem;color:var(--muted)}
.rtext{font-size:.95rem;overflow-wrap:anywhere}
del{background:#fbd5d1;color:#7a1610;text-decoration:line-through}
ins{background:#cdeed6;color:#0d4f20;text-decoration:none}
ul.ev{padding-left:18px;margin:6px 0}
ul.ev li{margin:4px 0}
details{border:1px solid var(--line);border-radius:8px;padding:6px 10px;margin:6px 0;background:var(--bg)}
summary{cursor:pointer}
pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:.8rem;margin:6px 0;background:#f4f5f7;border-radius:6px;padding:8px}
pre.diff{max-height:420px;overflow:auto}
.dl-add{background:#dff3e4;display:block}.dl-del{background:#fbe3e0;display:block}.dl-hunk{color:var(--accent);display:block}
.linkbtn{font:inherit;background:none;border:none;color:var(--accent);text-decoration:underline;cursor:pointer;padding:0}
.muted{color:var(--muted)}
.empty{color:var(--muted);font-style:italic}
</style>
</head>
<body>
<header>
<h1 id="title"></h1>
<p class="sub" id="subtitle"></p>
<div class="stats" id="stats"></div>
</header>
<div class="layout">
<div>
<section aria-labelledby="h-tl">
<h2 id="h-tl">Timeline of the spec</h2>
<p class="muted">Each node is a commit that changed the spec. Click one to see what it added and its diff.</p>
<div class="timeline" id="timeline" role="list"></div>
<h3>Timeline as text</h3>
<table><thead><tr><th>Spec version</th><th>Commit</th><th>Date</th><th>Added</th><th>Text edited</th></tr></thead><tbody id="tl-table"></tbody></table>
</section>
<section aria-labelledby="h-rules">
<h2 id="h-rules">Rule versions</h2>
<div class="controls" id="filters" role="group" aria-label="Filter rule versions"></div>
<div class="controls"><input id="search" type="search" placeholder="Search rule id, text, test, commit" aria-label="Search"><span class="count" id="count"></span></div>
<table><thead><tr><th>Rule</th><th>Status</th><th>Introduced</th><th>Edits</th><th>Tests</th><th>Change record</th><th>Parity</th></tr></thead><tbody id="rules"></tbody></table>
</section>
<section aria-labelledby="h-checks">
<h2 id="h-checks">Consistency checks</h2>
<table><thead><tr><th>Check</th><th>Result</th><th>Detail</th></tr></thead><tbody id="checks"></tbody></table>
</section>
</div>
<aside id="panel" aria-live="polite" aria-label="Evidence"></aside>
</div>
<script type="application/json" id="data">__DATA__</script>
<script>
(function(){
"use strict";
var D=JSON.parse(document.getElementById("data").textContent);
var byAnchor={};D.rows.forEach(function(r){byAnchor[r.anchor]=r;});
var byCommit={};D.commits.forEach(function(c){byCommit[c.short]=c;});
var $=function(id){return document.getElementById(id);};
function esc(s){return String(s==null?"":s).replace(/[&<>"']/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[ch];});}
function cLink(c){return c?'<a href="'+esc(D.repo)+'/commit/'+esc(c.sha)+'" target="_blank" rel="noopener"><code>'+esc(c.short)+'</code></a>':"-";}
function bLink(path,line,text){return '<a href="'+esc(D.repo)+'/blob/'+esc(D.link_ref)+'/'+esc(path)+(line?'#L'+line:'')+'" target="_blank" rel="noopener"><code>'+esc(text||(path+(line?':'+line:'')))+'</code></a>';}
function ruleBtn(a){var r=byAnchor[a];return '<button class="linkbtn" data-rule="'+esc(a)+'">'+esc(r?r.label:a)+'</button>';}
function statusBadge(r){var cls={Current:"b-current",Superseded:"b-superseded",Removed:"b-removed"}[r.status];var t=r.status+(r.status==="Superseded"&&r.successor?" by v"+(r.v+1):"");return '<span class="badge '+cls+'">'+esc(t)+'</span>';}
function verdictBadge(v){var cls=/^PASS/.test(v)?"b-pass":/^CHANGED/.test(v)?"b-changed":"b-other";return '<span class="badge '+cls+'">'+esc(v)+'</span>';}
function changeNames(r){return r.changes.map(function(c){return c.name.split("-").slice(0,2).join("-");});}

var ids={};D.rows.forEach(function(r){ids[r.id]=1;});
$("title").textContent=D.spec_name+" change history";
$("subtitle").innerHTML="Generated by <code>make spec-history</code> from the git history of <code>"+esc(D.spec)+"</code>, the test javadoc, <code>docs/changes/</code> and the parity files. Every claim below opens its evidence in the panel.";
var stats=[[D.current_version,"current spec version"],[D.commits.length,"commits to the spec"],[Object.keys(ids).length,"rule ids"],[D.rows.length,"rule versions"],[D.revised_ids.length,"revised rule ids"],[D.rows.filter(function(r){return r.changes.length;}).length,"versions with a change record"]];
$("stats").innerHTML=stats.map(function(s){return '<div class="stat"><b>'+esc(s[0])+'</b>'+esc(s[1])+'</div>';}).join("");

function commitSummary(c){var p=[];if(c.added.length)p.push("+"+c.added.length+" rule version"+(c.added.length>1?"s":""));if(c.edited.length)p.push(c.edited.length+" edited");if(c.superseded.length)p.push(c.superseded.length+" superseded");if(c.removed.length)p.push(c.removed.length+" removed");if(c.restored.length)p.push(c.restored.length+" restored");return p.join(", ")||"no rule change";}
$("timeline").innerHTML=D.commits.map(function(c){return '<div class="tnode" role="listitem"><span class="dot"></span><button data-commit="'+esc(c.short)+'"><div class="ver">v'+esc(c.version)+'</div><div class="meta"><code>'+esc(c.short)+'</code> · '+esc(c.date.slice(0,10))+'</div><div class="chg">'+esc(commitSummary(c))+'</div></button></div>';}).join("");
$("tl-table").innerHTML=D.commits.map(function(c){return '<tr><td>'+(c.version_before?esc(c.version_before)+" → ":"")+esc(c.version)+'</td><td><button class="linkbtn" data-commit="'+esc(c.short)+'"><code>'+esc(c.short)+'</code></button> '+esc(c.subject)+'</td><td>'+esc(c.date)+'</td><td>'+(c.added.length>3?c.added.length+" rule versions":c.added.map(ruleBtn).join(", ")||"-")+'</td><td>'+(c.edited.map(ruleBtn).join(", ")||"-")+'</td></tr>';}).join("");

var FILTERS=[["all","All"],["current","Current"],["superseded","Superseded"],["revised","Revised rules"],["chg","With change record"],["untested","No tests"]];
var state={f:"all",q:""};
$("filters").innerHTML=FILTERS.map(function(f){return '<button data-filter="'+f[0]+'" aria-pressed="'+(f[0]==="all")+'">'+f[1]+'</button>';}).join("");
function matches(r){
  var f=state.f;
  if(f==="current"&&r.status!=="Current")return false;
  if(f==="superseded"&&r.status!=="Superseded")return false;
  if(f==="revised"&&!r.revised)return false;
  if(f==="chg"&&!r.changes.length)return false;
  if(f==="untested"&&r.tests.length)return false;
  if(state.q){var hay=[r.label,r.text,r.status,r.introduced.short,r.introduced.subject].concat(r.tests.map(function(t){return t.name;}),changeNames(r),r.trail.map(function(t){return t.short;})).join(" ").toLowerCase();if(hay.indexOf(state.q)<0)return false;}
  return true;
}
var selected=null;
function renderRules(){
  var shown=D.rows.filter(matches);
  $("rules").innerHTML=shown.map(function(r){
    var par=r.transcripts.map(function(t){return verdictBadge(t.verdict.split(" ")[0]);}).join(" ")||'<span class="muted">-</span>';
    return '<tr class="rule'+(r.anchor===selected?' sel':'')+'" tabindex="0" data-rule="'+esc(r.anchor)+'"><td><b>'+esc(r.label)+'</b>'+(r.revised?' <span class="badge b-rev">revised</span>':'')+'</td><td>'+statusBadge(r)+'</td><td>v'+esc(r.intro_version)+' <code>'+esc(r.introduced.short)+'</code></td><td>'+(r.edits.length||'-')+'</td><td>'+(r.tests.length||'<span class="badge b-note">0</span>')+'</td><td>'+(changeNames(r).join(", ")||'-')+'</td><td>'+par+'</td></tr>';
  }).join("")||'<tr><td colspan="7" class="empty">No rule version matches.</td></tr>';
  $("count").textContent=shown.length+" of "+D.rows.length+" rule versions";
}
$("checks").innerHTML=D.checks.map(function(c){return '<tr><td>'+esc(c.check)+'</td><td><span class="badge '+(c.result==="OK"?"b-ok":"b-note")+'">'+esc(c.result)+'</span></td><td>'+esc(c.detail).replace(/`([^`]+)`/g,function(m,s){return byCommit[s]?'<button class="linkbtn" data-commit="'+s+'"><code>'+s+'</code></button>':'<code>'+s+'</code>';})+'</td></tr>';}).join("");

function sideBySide(oldTitle,oldHtml,newTitle,newHtml){return '<div class="side"><div><h4>'+oldTitle+'</h4><div class="rtext">'+oldHtml+'</div></div><div><h4>'+newTitle+'</h4><div class="rtext">'+newHtml+'</div></div></div>';}
function openRule(a,push){
  var r=byAnchor[a];if(!r)return;selected=a;
  var h='<h2>'+esc(r.label)+' '+statusBadge(r)+'</h2>';
  h+='<p class="muted">Section: '+esc(r.section)+(r.spec_line?' · '+bLink(D.spec,r.spec_line,"spec line "+r.spec_line):'')+'</p>';
  if(r.successor)h+='<p>Replaced by '+ruleBtn(r.successor)+'.</p>';
  h+='<h3>Old and new text</h3>';
  if(r.predecessor){h+=sideBySide(esc(r.predecessor.rule)+' (old) · '+ruleBtn(r.predecessor.anchor),r.predecessor.old_html,esc(r.label)+' (new)',r.predecessor.new_html);}
  r.edits.forEach(function(e){h+='<p>Edited in '+cLink(e.commit)+' (spec v'+esc(e.version)+'): '+(e.wording_same?'wording unchanged, re-wrapped and heading qualifier changed.':'<b>wording changed.</b>')+'</p>'+sideBySide("Before",e.old_html,"After",e.new_html);});
  if(!r.predecessor&&!r.edits.length)h+='<p class="muted">Unchanged since it was introduced.</p><div class="side" style="grid-template-columns:1fr"><div><div class="rtext">'+esc(r.text)+'</div></div></div>';
  h+='<h3>Introduced</h3><p>'+cLink(r.introduced)+' '+esc(r.introduced.subject)+'<br><span class="muted">'+esc(r.introduced.date)+' · spec version '+esc(r.intro_version)+' · <button class="linkbtn" data-commit="'+esc(r.introduced.short)+'">open this commit</button></span></p><p><span class="muted">Rule history row:</span> '+esc(r.history_row)+'</p>';
  h+='<h3>Tests ('+r.tests.length+')</h3>';
  h+=r.tests.length?r.tests.map(function(t){return '<details><summary><code>'+esc(t.name)+'</code></summary><p>'+bLink(t.file,t.line,t.file.split("/").pop()+":"+t.line)+(t.added?' · added in '+cLink(t.added)+' '+esc(t.added.subject):'')+'</p><pre>'+esc(t.javadoc)+'</pre></details>';}).join(""):'<p class="empty">No test javadoc cites this rule version.</p>';
  h+='<h3>Change records ('+r.changes.length+')</h3>';
  h+=r.changes.length?r.changes.map(function(c){return '<details open><summary><b>'+esc(c.title)+'</b></summary><p>'+bLink(c.file)+' · '+esc(c.status)+' · '+esc(c.date)+(c.added?' · added in '+cLink(c.added):'')+'</p><ul class="ev">'+c.excerpt.map(function(x){return '<li>'+bLink(c.file,x.line,"L"+x.line)+' '+esc(x.text)+'</li>';}).join("")+'</ul></details>';}).join(""):'<p class="empty">No change record names this rule version.</p>';
  h+='<h3>Transcripts and parity</h3>';
  h+=r.transcripts.length?'<ul class="ev">'+r.transcripts.map(function(t){return '<li>'+bLink("transcripts/"+t.name+".json",null,t.name)+' '+verdictBadge(t.verdict)+'</li>';}).join("")+'</ul>':'<p class="empty">No transcript is mapped to it in parity/routes.json.</p>';
  h+='<h3>Commits that mention it ('+r.trail.length+')</h3>';
  h+=r.trail.length?'<ul class="ev">'+r.trail.map(function(t){return '<li>'+cLink(t)+' '+esc(t.subject)+' <span class="muted">('+esc(t.date.slice(0,10))+')</span><br><span class="muted">'+t.files.map(esc).join(", ")+'</span></li>';}).join("")+'</ul>':'<p class="empty">None.</p>';
  $("panel").innerHTML=h;$("panel").scrollTop=0;
  renderRules();markTimeline(null);
  if(push!==false&&history.replaceState)history.replaceState(null,"","#"+a);
}
function diffHtml(d){return d.split("\n").map(function(l){var c=/^\+(?!\+\+)/.test(l)?"dl-add":/^-(?!--)/.test(l)?"dl-del":/^@@/.test(l)?"dl-hunk":"";return c?'<span class="'+c+'">'+esc(l)+'</span>':esc(l)+"\n";}).join("");}
function markTimeline(s){Array.prototype.forEach.call(document.querySelectorAll(".tnode button"),function(b){b.classList.toggle("sel",b.getAttribute("data-commit")===s);});}
function openCommit(s,push){
  var c=byCommit[s];if(!c)return;selected=null;
  var h='<h2>Spec version '+(c.version_before?esc(c.version_before)+' → ':'')+esc(c.version)+'</h2>';
  h+='<p>'+cLink(c)+' '+esc(c.subject)+'<br><span class="muted">'+esc(c.date)+' · <a href="'+esc(D.repo)+'/blob/'+esc(c.sha)+'/'+esc(D.spec)+'" target="_blank" rel="noopener">spec as of this commit</a></span></p>';
  function list(t,a){return a.length?'<h3>'+t+' ('+a.length+')</h3><p>'+a.map(ruleBtn).join(", ")+'</p>':"";}
  h+=list("Rule versions added",c.added)+list("Rule text edited",c.edited)+list("Marked superseded",c.superseded)+list("Removed",c.removed)+list("Restored",c.restored);
  if(c.history_rows.length)h+='<h3>Rule history rows added</h3><p>'+esc(c.history_rows.join(", "))+'</p>';
  h+='<h3>Diff of the spec</h3><pre class="diff">'+diffHtml(c.diff)+'</pre>';
  $("panel").innerHTML=h;$("panel").scrollTop=0;
  renderRules();markTimeline(s);
  if(push!==false&&history.replaceState)history.replaceState(null,"","#commit-"+s);
}
document.addEventListener("click",function(e){
  var t=e.target.closest("[data-rule],[data-commit],[data-filter]");if(!t)return;
  if(t.hasAttribute("data-filter")){state.f=t.getAttribute("data-filter");Array.prototype.forEach.call(document.querySelectorAll("[data-filter]"),function(b){b.setAttribute("aria-pressed",String(b===t));});renderRules();return;}
  if(t.hasAttribute("data-rule"))openRule(t.getAttribute("data-rule"));else openCommit(t.getAttribute("data-commit"));
});
document.addEventListener("keydown",function(e){if((e.key==="Enter"||e.key===" ")&&e.target.matches&&e.target.matches("tr[data-rule]")){e.preventDefault();openRule(e.target.getAttribute("data-rule"));}});
$("search").addEventListener("input",function(){state.q=this.value.trim().toLowerCase();renderRules();});
renderRules();
var h=decodeURIComponent(location.hash.slice(1));
if(byAnchor[h])openRule(h,false);else if(/^commit-/.test(h)&&byCommit[h.slice(7)])openCommit(h.slice(7),false);
else{var rev=D.rows.filter(function(r){return r.predecessor;});if(rev.length)openRule(rev[rev.length-1].anchor,false);else openCommit(D.commits[D.commits.length-1].short,false);}
})();
</script>
</body>
</html>
"""


if __name__ == "__main__":
    main()

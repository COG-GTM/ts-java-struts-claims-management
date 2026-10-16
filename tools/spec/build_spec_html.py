#!/usr/bin/env python3
"""Writes docs/specs/SPEC-PAY-001.html, the interactive twin of SPEC-PAY-001.md.

Nothing in the HTML is typed by hand. The rules, their status and their
citations come from the Markdown spec; each `Read:` citation is resolved to the
cited lines of the legacy file; each `Observed:` transcript name to the file in
transcripts/; each `live Lnn` to that request and response in
docs/specs/SPEC-PAY-001-live.json; open questions come from the SPEC-PAY-001
rows of docs/specs/OPEN-QUESTIONS.md. The page is one file with no network
access: it opens from file:// and from the repository browser.

Usage: python3 tools/spec/build_spec_html.py  (see `make payment-spec-html`)
"""

import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = "docs/specs/SPEC-PAY-001.md"
LIVE = "docs/specs/SPEC-PAY-001-live.json"
QUESTIONS = "docs/specs/OPEN-QUESTIONS.md"
OUTPUT = "docs/specs/SPEC-PAY-001.html"

HEADING = re.compile(r"^\*\*(PAY-R\d+) \(v(\d+)(?:, superseded by v\d+)?\)\*\* — (.*)$")
SECTION = re.compile(r"^## (.*)$")
LIVE_REF = re.compile(r"\bL(\d{2})\b")
DB_REF = re.compile(r"db_after\.(\w+)")
CITATION = re.compile(r"^\s*([\w./-]+\.\w+):([\d,\-]+)\s*$")


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


def line_numbers(spec):
    numbers = []
    for part in spec.split(","):
        if "-" in part:
            first, last = part.split("-")
            numbers += list(range(int(first), int(last) + 1))
        elif part:
            numbers.append(int(part))
    return numbers


def source_excerpt(citation):
    match = CITATION.match(citation)
    if not match:
        raise SystemExit("cannot resolve Read citation: %r" % citation)
    path, spec = match.groups()
    lines = read(path).splitlines()
    wanted = line_numbers(spec)
    if max(wanted) > len(lines):
        raise SystemExit("%s has no line %d" % (path, max(wanted)))
    return {"path": path, "lines": spec,
            "code": [[n, lines[n - 1]] for n in wanted]}


def parse_rules():
    rules, section, current, field = [], None, None, None
    for line in read(SPEC).splitlines():
        heading = SECTION.match(line)
        if heading:
            section = heading.group(1)
            current = None
            continue
        rule = HEADING.match(line)
        if rule:
            current = {"id": rule.group(1), "version": rule.group(2),
                       "section": section, "text": rule.group(3),
                       "status": "", "observed": "", "read": "", "approved": ""}
            rules.append(current)
            field = "text"
            continue
        if current is None:
            continue
        if not line.strip():
            current = None
            continue
        for key, prefix in (("status", "Status:"), ("observed", "Observed:"),
                            ("read", "Read:"), ("approved", "Approved:")):
            if line.startswith(prefix):
                field = key
                line = line[len(prefix):]
                break
        current[field] = (current[field] + " " + line.strip()).strip()
    return rules


def evidence(rule, live, transcripts):
    observed = rule["observed"]
    names = [name for name in sorted(transcripts)
             if re.search(r"\b%s\b" % name, observed)]
    steps = sorted({"L" + n for n in LIVE_REF.findall(observed)})
    db = sorted(set(DB_REF.findall(observed)))
    sources = [source_excerpt(c) for c in rule["read"].split(";") if c.strip()]
    for step in steps:
        if step not in live:
            raise SystemExit("%s cites unknown live step %s" % (rule["id"], step))
    return {"transcripts": names, "steps": steps, "db": db, "sources": sources}


def parse_questions():
    questions = []
    for line in read(QUESTIONS).splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) == 6 and cells[0].startswith("OQ-") and "SPEC-PAY-001" in line:
            questions.append(dict(zip(
                ["id", "question", "blocks", "owner", "interim", "state"], cells)))
    return questions


def main():
    live_file = json.loads(read(LIVE))
    live = {step["id"]: step for step in live_file["steps"]}
    transcripts = {}
    for name in sorted(os.listdir(os.path.join(ROOT, "transcripts"))):
        if name.startswith("payment_") and name.endswith(".json"):
            transcripts[name[:-5]] = json.loads(read("transcripts/" + name))
    rules = parse_rules()
    for rule in rules:
        rule["evidence"] = evidence(rule, live, transcripts)
        if rule["status"].rstrip(".") not in ("Observed", "Read-only", "Changed"):
            raise SystemExit("%s has no Observed/Read-only/Changed status" % rule["id"])
        if rule["status"].startswith("Changed") and not rule["approved"]:
            raise SystemExit("%s is Changed without an Approved: record" % rule["id"])
        rule["status"] = rule["status"].rstrip(".")
    version = re.search(r"^Version: (.*)$", read(SPEC), re.M).group(1)
    data = {
        "version": version, "spec": SPEC, "live_file": LIVE,
        "rules": rules, "questions": parse_questions(), "live": live,
        "db_after": live_file["db_after"], "transcripts": transcripts,
    }
    payload = json.dumps(data, sort_keys=True, ensure_ascii=False)
    payload = payload.replace("</", "<\\/").replace("<!--", "<\\!--")
    template = read("tools/spec/spec_view_template.html")
    html = template.replace("/*__DATA__*/null", payload)
    with open(os.path.join(ROOT, OUTPUT), "w", encoding="utf-8") as handle:
        handle.write(html)
    print("wrote %s (%d rules, %d open questions)"
          % (OUTPUT, len(rules), len(data["questions"])))


if __name__ == "__main__":
    main()

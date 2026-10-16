#!/usr/bin/env python3
"""Writes docs/changes/CHG-nnn-*.html next to every change record.

Run by ``make change-html``. Nothing in the HTML is typed by hand: the record
is read from its Markdown, every ``path:lines`` citation in it is resolved to
the cited legacy lines, every rule version it names is linked to its spec, and
the parity scenarios that ``parity/routes.json`` registers against the record
are shown with their verdict from ``parity/report.json``. The file is
self-contained: no scripts, styles, fonts or images are loaded from anywhere.
"""
import glob
import html
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CITATION = re.compile(r"`?((?:src|services|tools|docs|parity)/[\w./-]+\.(?:java|xml|jsp|sql|py|json|md)):(\d+(?:-\d+)?)`?")
RULE = re.compile(r"\b([A-Z]+)-R(\d+)\b(?: \((v\d+)[^)]*\)| (v\d+)\b)?")  # "<SLICE>-Rnn (v2, ...)" or "<SLICE>-Rnn v1"
SCENARIO = re.compile(r"`(payment_[A-Za-z0-9_]+|settlement_[A-Za-z0-9_]+)`")
TEMPLATE = os.path.join(ROOT, "tools", "changes", "change_view_template.html")


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


def excerpt(path, spec):
    lines = read(path).splitlines()
    first, last = (spec.split("-") + [spec])[:2]
    first, last = int(first), int(last)
    if last > len(lines):
        raise SystemExit("%s has no line %d (cited by a change record)" % (path, last))
    return [[n, lines[n - 1]] for n in range(first, last + 1)]


def sections(markdown):
    """[(heading, [lines])] with the title separate; the header block is the
    first section."""
    title, current, out = "", "Record", []
    body = []
    for line in markdown.splitlines():
        if line.startswith("# ") and not title:
            title = line[2:]
        elif line.startswith("## "):
            out.append((current, body))
            current, body = line[3:], []
        else:
            body.append(line)
    out.append((current, body))
    return title, out


def inline(text, spec_links, scenarios=frozenset()):
    """Escape, then turn citations, rule versions, scenarios and code into links
    and <code>; the order matters so a link is never re-escaped."""
    pieces = []
    pos = 0
    for match in CITATION.finditer(text):
        pieces.append(("text", text[pos:match.start()]))
        pieces.append(("cite", match.group(1), match.group(2)))
        pos = match.end()
    pieces.append(("text", text[pos:]))
    out = []
    for piece in pieces:
        if piece[0] == "cite":
            path, spec = piece[1], piece[2]
            out.append('<a class="cite" href="#src-%s">%s:%s</a>' % (anchor(path, spec), html.escape(path), spec))
            continue
        chunk = html.escape(piece[1])
        # Scenario links first: the backtick conversion below would otherwise eat the delimiters SCENARIO matches.
        def scenario_link(match):
            name = match.group(1)
            if name not in scenarios:
                return match.group(0)  # a backticked identifier that only looks like a scenario name
            return '<a class="scn" href="#scn-%s"><code>%s</code></a>' % (name, name)
        chunk = SCENARIO.sub(scenario_link, chunk)
        chunk = re.sub(r"`([^`]+)`", r"<code>\1</code>", chunk)
        def rule_link(match):
            slice_name, version = match.group(1), match.group(3) or match.group(4)
            spec = spec_links.get(slice_name)
            label = match.group(0)
            if not spec:
                return label
            # A versioned mention opens that version's card (#PAY-R05-v2); an unversioned one opens the latest.
            fragment = "%s-R%s" % (slice_name, match.group(2)) + ("-" + version if version else "")
            return '<a class="rule" href="%s#%s">%s</a>' % (spec, fragment, label)
        chunk = RULE.sub(rule_link, chunk)
        out.append(chunk)
    return "".join(out)


def anchor(path, spec):
    return re.sub(r"[^A-Za-z0-9]", "-", path + "-" + spec)


def render_body(lines, spec_links, scenarios=frozenset()):
    out, para, table = [], [], []

    def flush():
        if para:
            out.append("<p>%s</p>" % inline(" ".join(para), spec_links, scenarios))
            para.clear()
        if table:
            rows = [r for r in table if not re.match(r"^\|[\s|-]+\|$", r)]
            cells = [[c.strip() for c in r.strip("|").split("|")] for r in rows]
            out.append("<table><thead><tr>%s</tr></thead><tbody>%s</tbody></table>" % (
                "".join("<th>%s</th>" % inline(c, spec_links, scenarios) for c in cells[0]),
                "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % inline(c, spec_links, scenarios) for c in r) for r in cells[1:])))
            table.clear()

    items = []
    for line in lines:
        if line.startswith("|"):
            table.append(line)
            continue
        if line.startswith("* "):
            if para:
                flush()
            items.append(line[2:])
            continue
        if line.startswith("  ") and items:
            items[-1] += " " + line.strip()
            continue
        if items and (not line.strip()):
            out.append("<ul>%s</ul>" % "".join("<li>%s</li>" % inline(i, spec_links, scenarios) for i in items))
            items = []
        if not line.strip():
            flush()
        else:
            para.append(line.strip())
    if items:
        out.append("<ul>%s</ul>" % "".join("<li>%s</li>" % inline(i, spec_links, scenarios) for i in items))
    flush()
    return "".join(out)


def parity_rows(record_rel):
    routes = json.loads(read("parity/routes.json"))
    approvals = [e for e in routes.get("approved_differences", []) if e["record"] == record_rel]
    verdicts = {}
    path = os.path.join(ROOT, "parity", "report.json")
    if os.path.exists(path):
        verdicts = {r["scenario"]: r for r in json.loads(read("parity/report.json"))["scenarios"]}
    rows = []
    for entry in approvals:
        row = verdicts.get(entry["scenario"], {})
        rows.append({"scenario": entry["scenario"], "summary": entry["summary"],
                     "rules": entry["rules"], "expected": entry["expected"],
                     "legacy": entry.get("legacy"), "precondition": entry.get("precondition") or row.get("precondition"),
                     "request": entry.get("request") or row.get("request"),
                     "verdict": row.get("verdict", "not run"), "actual": row.get("actual")})
    return rows


def build(record_path):
    rel = os.path.relpath(record_path, ROOT)
    markdown = read(rel)
    title, parts = sections(markdown)
    spec_links = {}
    for spec in glob.glob(os.path.join(ROOT, "docs", "specs", "SPEC-*-001.html")):
        slice_name = os.path.basename(spec).split("-")[1]
        spec_links[slice_name] = os.path.relpath(spec, os.path.dirname(record_path))
    cites = []
    seen = set()
    for match in CITATION.finditer(markdown):
        key = (match.group(1), match.group(2))
        if key not in seen:
            seen.add(key)
            cites.append({"path": key[0], "lines": key[1], "anchor": anchor(*key), "code": excerpt(*key)})
    parity = parity_rows(rel)
    scenarios = frozenset(row["scenario"] for row in parity)
    data = {
        "title": title, "record": rel,
        "sections": [{"heading": h, "html": render_body(b, spec_links, scenarios)} for h, b in parts],
        "sources": cites, "parity": parity,
        "report": os.path.relpath(os.path.join(ROOT, "parity", "report.html"), os.path.dirname(record_path)),
    }
    payload = json.dumps(data, sort_keys=True).replace("</", "<\\/")
    out = read(os.path.relpath(TEMPLATE, ROOT)).replace("/*__DATA__*/null", payload)
    target = record_path[:-3] + ".html"
    with open(target, "w", encoding="utf-8") as handle:
        handle.write(out)
    print("wrote %s (%d sections, %d cited ranges, %d parity scenarios)" % (
        os.path.relpath(target, ROOT), len(data["sections"]), len(cites), len(data["parity"])))


def main():
    records = sorted(glob.glob(os.path.join(ROOT, "docs", "changes", "CHG-*.md")))
    if not records:
        raise SystemExit("no change records under docs/changes")
    for record in records:
        build(record)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Builds docs/analysis/payments-slice.html from docs/analysis/payments-slice.md.

The Markdown is the source of truth. This script parses its numbered findings, the
flow-map node and edge tables (section 7) and the documentation-vs-code table
(section 8), then embeds the cited legacy source lines, transcripts and live steps
(docs/analysis/payments-live.json) so that every claim opens its evidence in place.
The output is one self-contained file: no network, no external scripts or fonts.
"""
import html
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MD = os.path.join(ROOT, "docs/analysis/payments-slice.md")
LIVE = os.path.join(ROOT, "docs/analysis/payments-live.json")
OUT = os.path.join(ROOT, "docs/analysis/payments-slice.html")

CITE_RE = re.compile(r"`((?:src|docs|tools|transcripts)/[^`\s:]+|README\.md|Makefile)(?::([\d,\-]+))?`")
LIVE_RE = re.compile(r"\bL\d{2}\b")
FINDING_RE = re.compile(r"^(\d+)\. \*\*(Read|Observed)\*\* — (.*?)(?=^\d+\. \*\*|^#|^\|| \Z|\Z)", re.M | re.S)
NO_EMBED = {"docs/analysis/payments-live.json", "docs/analysis/payments-slice.html",
            "docs/analysis/payments-slice.md"}
MAX_LINES = 60
COLUMNS = ["screen", "action", "sql", "table"]
COLUMN_TITLES = {"screen": "Screens", "action": "Actions", "sql": "SQL", "table": "Tables"}
VERDICT_KEY = {"Confirmed": "confirmed", "Contradicted": "contradicted", "Not verifiable": "nv"}

esc = html.escape


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def inline(text):
    """Minimal Markdown inline rendering: code spans, bold, live-step and finding refs."""
    parts = re.split(r"(`[^`]*`)", text)
    out = []
    for part in parts:
        if part.startswith("`") and part.endswith("`") and len(part) > 1:
            out.append("<code>%s</code>" % esc(part[1:-1]))
            continue
        chunk = esc(part)
        chunk = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", chunk)
        chunk = LIVE_RE.sub(lambda m: '<a class="live-ref" href="#live-%s">%s</a>' % (m.group(0), m.group(0)), chunk)
        out.append(chunk)
    return "".join(out)


def link_findings(text_html):
    def repl(m):
        nums = re.findall(r"\d+", m.group(2))
        links = ", ".join('<a class="f-ref" href="#finding-%s">%s</a>' % (n, n) for n in nums)
        return m.group(1) + links
    return re.sub(r"(findings? )((?:\d+(?:, | and )?)+)", repl, text_html, flags=re.I)


def parse_lines(spec):
    lines = []
    for piece in spec.split(","):
        if "-" in piece:
            a, b = piece.split("-")
            lines.extend(range(int(a), int(b) + 1))
        elif piece:
            lines.append(int(piece))
    return lines


class Snippets:
    def __init__(self):
        self.cache = {}

    def key(self, path, spec):
        return path + (":" + spec if spec else "")

    def render(self, path, spec):
        k = self.key(path, spec)
        if k in self.cache:
            return self.cache[k]
        full = os.path.join(ROOT, path)
        if path in NO_EMBED or not os.path.isfile(full):
            self.cache[k] = None
            return None
        with open(full, encoding="utf-8") as fh:
            src = fh.read().splitlines()
        wanted = parse_lines(spec) if spec else list(range(1, len(src) + 1))
        wanted = [n for n in wanted if 1 <= n <= len(src)][:MAX_LINES]
        rows = []
        prev = None
        for n in wanted:
            if prev is not None and n != prev + 1:
                rows.append('<span class="ln gap">…</span>\n')
            rows.append('<span class="ln">%d</span>%s\n' % (n, esc(src[n - 1])))
            prev = n
        body = "<pre class=\"code\"><code>%s</code></pre>" % "".join(rows)
        self.cache[k] = body
        return body


def cites_in(text):
    seen = []
    for m in CITE_RE.finditer(text):
        item = (m.group(1), m.group(2) or "")
        if item not in seen:
            seen.append(item)
    return seen


def lives_in(text):
    seen = []
    for m in LIVE_RE.finditer(text):
        if m.group(0) not in seen:
            seen.append(m.group(0))
    return seen


def render_live(step):
    req = step["request"]
    resp = step["response"]
    form = req.get("form") or {}
    lines = ["%s %s" % (req["method"], req["path"])]
    if form:
        lines.append("  " + "&".join("%s=%s" % (k, v) for k, v in form.items()))
    if req.get("note"):
        lines.append("  (" + req["note"] + ")")
    out = ['<div class="live" id="live-%s"><div class="live-head"><span class="tag tag-observed">Observed</span> '
           '<strong>%s</strong> %s</div>' % (step["id"], step["id"], esc(step["purpose"]))]
    out.append('<div class="live-grid"><div><div class="mini">Request</div><pre class="code"><code>%s</code></pre></div>'
               % esc("\n".join(lines)))
    if "summary" in resp:
        s = resp["summary"]
        body = "issued: %s\nerror pages: %d\nper request: %s" % (
            ", ".join(s["issued"]), s["error_pages"],
            ", ".join("error" if r["error_page"] else r["fields"].get("checkNumber", "?") for r in resp["responses"]))
    else:
        rl = ["HTTP %s" % resp["status"]]
        if resp.get("location"):
            rl.append("Location: " + resp["location"])
        if resp.get("forward"):
            rl.append("ns:view " + resp["forward"])
        if resp.get("error_page"):
            rl.append("Claims System Error page, Reference status: %s" % resp.get("error_reference_status"))
        if resp.get("link_count_detail61"):
            rl.append('links to detail.do?paymentId=61: %d' % resp["link_count_detail61"])
        fields = resp.get("fields") or {}
        if fields:
            rl.append("f_ spans:")
            rl.extend("  %s = %s" % (k, v) for k, v in fields.items())
        body = "\n".join(rl)
    out.append('<div><div class="mini">Response</div><pre class="code"><code>%s</code></pre></div></div></div>' % esc(body))
    return "".join(out)


def evidence_html(text, snippets, live_steps, open_=False):
    parts = []
    for path, spec in cites_in(text):
        snip = snippets.render(path, spec)
        label = esc(path + (":" + spec if spec else ""))
        if snip is None:
            parts.append('<div class="cite-plain"><code>%s</code></div>' % label)
        else:
            parts.append('<details class="cite"%s><summary><span class="tag tag-read">Read</span> <code>%s</code></summary>%s</details>'
                         % (" open" if open_ else "", label, snip))
    for lid in lives_in(text):
        if lid in live_steps:
            parts.append('<details class="cite"%s><summary><span class="tag tag-observed">Live</span> <code>%s</code> %s</summary>%s</details>'
                         % (" open" if open_ else "", lid, esc(live_steps[lid]["purpose"]), render_live(live_steps[lid])))
    return "".join(parts)


def parse_md(text):
    findings = {}
    for m in FINDING_RE.finditer(text):
        body = re.sub(r"\s*\n\s*", " ", m.group(3)).strip()
        findings[int(m.group(1))] = {"tag": m.group(2), "text": body}
    sections = {}
    current = None
    for line in text.splitlines():
        if line.startswith("## "):
            current = line[3:].strip()
            sections[current] = []
        elif current:
            sections[current].append(line)
    nodes, edges, docs = [], [], []
    for line in sections.get("7. Flow map", []):
        if not line.startswith("| ") or line.startswith("| ---") or line.startswith("| node") or line.startswith("| from"):
            continue
        cells = [c.strip() for c in line.strip("|").split(" | ")]
        if len(cells) == 6:
            nodes.append({"id": cells[0], "col": cells[1], "label": cells[2], "sub": cells[3],
                          "findings": [int(x) for x in re.findall(r"\d+", cells[4])],
                          "live": re.findall(r"L\d{2}", cells[5])})
        elif len(cells) == 4:
            edges.append({"from": cells[0], "to": cells[1], "label": cells[2],
                          "findings": [int(x) for x in re.findall(r"\d+", cells[3])]})
    for line in sections.get("8. Documentation vs code", []):
        if not line.startswith("| D"):
            continue
        cells = [c.strip() for c in line.strip("|").split(" | ")]
        docs.append({"id": cells[0], "claim": cells[1], "source": cells[2], "verdict": cells[3], "evidence": cells[4]})
    summary = [l[2:] for l in sections.get("Summary", []) if l.startswith("* ")]
    # join wrapped bullet lines
    bullets, buf = [], None
    for line in sections.get("Summary", []):
        if line.startswith("* "):
            if buf:
                bullets.append(buf)
            buf = line[2:].strip()
        elif line.strip() and buf is not None:
            buf += " " + line.strip()
    if buf:
        bullets.append(buf)
    titles = [(k, v) for k, v in sections.items()]
    return findings, nodes, edges, docs, bullets, titles


def section_of(findings_text, md_text):
    """Map finding number -> section title by scanning the Markdown in order."""
    mapping = {}
    current = None
    for line in md_text.splitlines():
        if line.startswith("## "):
            current = line[3:].strip()
        m = re.match(r"^(\d+)\. \*\*", line)
        if m:
            mapping[int(m.group(1))] = current
    return mapping


def build():
    md_text = open(MD, encoding="utf-8").read()
    live = json.load(open(LIVE, encoding="utf-8"))
    live_steps = {s["id"]: s for s in live["steps"]}
    findings, nodes, edges, docs, summary, _ = parse_md(md_text)
    sec = section_of(findings, md_text)
    snippets = Snippets()
    node_by_id = {n["id"]: n for n in nodes}
    for e in edges:
        assert e["from"] in node_by_id and e["to"] in node_by_id, e
    for n in nodes:
        for f in n["findings"]:
            assert f in findings, (n["id"], f)
    for lid in lives_in(md_text):
        assert lid in live_steps, lid

    counts = {"Read": sum(1 for f in findings.values() if f["tag"] == "Read"),
              "Observed": sum(1 for f in findings.values() if f["tag"] == "Observed")}
    vcounts = {v: sum(1 for d in docs if d["verdict"] == v) for v in VERDICT_KEY}

    # ---- findings articles (static, work without script)
    f_html = []
    last_sec = None
    for n in sorted(findings):
        f = findings[n]
        if sec.get(n) != last_sec:
            last_sec = sec.get(n)
            f_html.append('<h3 class="fsec" data-fsec>%s</h3>' % esc(last_sec))
        tag = f["tag"].lower()
        f_html.append(
            '<article class="finding" id="finding-%d" data-tag="%s"><div class="f-head"><span class="num">%d</span>'
            '<span class="tag tag-%s">%s</span></div><p>%s</p>%s</article>'
            % (n, tag, n, tag, f["tag"], link_findings(inline(f["text"])),
               '<div class="evidence">%s</div>' % evidence_html(f["text"], snippets, live_steps)))

    # ---- docs vs code rows
    d_html = []
    for d in docs:
        vk = VERDICT_KEY[d["verdict"]]
        ev = evidence_html(d["source"], snippets, live_steps, open_=True) + evidence_html(d["evidence"], snippets, live_steps)
        refs = re.findall(r"[Ff]indings? ((?:\d+(?:, )?)+)", d["evidence"])
        d_html.append(
            '<tr id="%s" data-verdict="%s"><th scope="row">%s</th><td class="claim">%s<div class="src">%s</div></td>'
            '<td><span class="badge badge-%s">%s</span></td><td class="ev"><details><summary>%s</summary><div class="evidence">%s</div></details></td></tr>'
            % (d["id"], vk, d["id"], inline(d["claim"]), inline(d["source"]), vk,
               {"confirmed": "&#10003; Confirmed", "contradicted": "&#10007; Contradicted", "nv": "? Not verifiable"}[vk],
               link_findings(inline(d["evidence"])), ev))

    # ---- diagram
    colx = {"screen": 20, "action": 360, "sql": 700, "table": 1040}
    w, h, gap = 270, 62, 22
    per_col = {c: [n for n in nodes if n["col"] == c] for c in COLUMNS}
    tallest = max(len(v) for v in per_col.values())
    height = 70 + tallest * (h + gap)
    pos = {}
    for c, items in per_col.items():
        total = len(items) * (h + gap) - gap
        top = 60 + (tallest * (h + gap) - gap - total) / 2
        for i, n in enumerate(items):
            pos[n["id"]] = (colx[c], top + i * (h + gap))
    svg = ['<svg viewBox="0 0 1330 %d" role="group" aria-labelledby="flow-title flow-desc" class="flow">' % height,
           '<title id="flow-title">Payment slice flow: screens, actions, SQL, tables</title>',
           '<desc id="flow-desc">Select a node to open its evidence. The same flow is listed as text below the diagram.</desc>',
           '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
           '<path d="M0,0 L10,5 L0,10 z" class="arrowhead"/></marker></defs>']
    for c in COLUMNS:
        svg.append('<text class="coltitle" x="%d" y="34">%s</text>' % (colx[c], COLUMN_TITLES[c]))
    for i, e in enumerate(edges):
        x1, y1 = pos[e["from"]]
        x2, y2 = pos[e["to"]]
        back = COLUMNS.index(node_by_id[e["to"]]["col"]) < COLUMNS.index(node_by_id[e["from"]]["col"])
        if back:
            sx, sy, tx, ty = x1, y1 + h * 0.72, x2 + w, y2 + h * 0.72
        else:
            sx, sy, tx, ty = x1 + w, y1 + h * 0.4, x2, y2 + h * 0.4
        mx = (sx + tx) / 2
        cls = "edge back" if back else "edge"
        if e["label"] == "exception":
            cls += " exc"
        svg.append('<path class="%s" data-from="%s" data-to="%s" d="M%.0f,%.0f C%.0f,%.0f %.0f,%.0f %.0f,%.0f" marker-end="url(#arrow)"><title>%s → %s: %s</title></path>'
                   % (cls, e["from"], e["to"], sx, sy, mx, sy, mx, ty, tx, ty,
                      esc(node_by_id[e["from"]]["label"]), esc(node_by_id[e["to"]]["label"]), esc(e["label"])))
    for n in nodes:
        x, y = pos[n["id"]]
        svg.append('<g class="node node-%s" id="node-%s" data-node="%s" tabindex="0" role="button" aria-label="%s, %s. Open evidence.">'
                   '<rect x="%d" y="%.0f" width="%d" height="%d" rx="6"/>'
                   '<text class="n-title" x="%d" y="%.0f">%s</text><text class="n-sub" x="%d" y="%.0f">%s</text></g>'
                   % (n["col"], n["id"], n["id"], esc(n["label"]), esc(n["sub"]), x, y, w, h,
                      x + 12, y + 26, esc(n["label"]), x + 12, y + 48, esc(n["sub"])))
    svg.append("</svg>")

    # ---- flow as text
    t_html = []
    for c in COLUMNS:
        t_html.append("<h3>%s</h3><ul class=\"flowtext\">" % COLUMN_TITLES[c])
        for n in per_col[c]:
            outs = ["%s <b>%s</b> (%s)" % ("&rarr;", esc(node_by_id[e["to"]]["label"]), esc(e["label"]))
                    for e in edges if e["from"] == n["id"]]
            t_html.append('<li><button class="linklike" data-node="%s">%s</button> — %s%s</li>'
                          % (n["id"], esc(n["label"]), esc(n["sub"]), ("; " + "; ".join(outs)) if outs else ""))
        t_html.append("</ul>")

    node_data = {n["id"]: {"label": n["label"], "sub": n["sub"], "col": COLUMN_TITLES[n["col"]],
                           "findings": n["findings"], "live": n["live"],
                           "edges": [{"other": node_by_id[e["to"] if e["from"] == n["id"] else e["from"]]["label"],
                                      "dir": "out" if e["from"] == n["id"] else "in", "label": e["label"]}
                                     for e in edges if n["id"] in (e["from"], e["to"])]}
                 for n in nodes}
    live_html = "".join('<template id="tpl-%s">%s</template>' % (k, render_live(v)) for k, v in live_steps.items())

    summary_html = "".join("<li>%s</li>" % link_findings(inline(b)) for b in summary)

    page = TEMPLATE
    for k, v in {
        "@@SUMMARY@@": summary_html,
        "@@NREAD@@": str(counts["Read"]), "@@NOBS@@": str(counts["Observed"]),
        "@@NFIND@@": str(len(findings)), "@@NDOCS@@": str(len(docs)),
        "@@NCONF@@": str(vcounts["Confirmed"]), "@@NCONTRA@@": str(vcounts["Contradicted"]),
        "@@NNV@@": str(vcounts["Not verifiable"]), "@@NLIVE@@": str(len(live_steps)),
        "@@SVG@@": "".join(svg), "@@FLOWTEXT@@": "".join(t_html),
        "@@DOCROWS@@": "".join(d_html), "@@FINDINGS@@": "".join(f_html),
        "@@LIVE@@": "".join(render_live(s) for s in live["steps"]),
        "@@NODEDATA@@": json.dumps(node_data, sort_keys=True).replace("</", "<\\/"),
        "@@TEMPLATES@@": live_html,
    }.items():
        page = page.replace(k, v)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(page)
    print("wrote %s (%d findings, %d nodes, %d edges, %d doc claims, %d live steps)"
          % (os.path.relpath(OUT, ROOT), len(findings), len(nodes), len(edges), len(docs), len(live_steps)))


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Payment slice: how it actually behaves</title>
<style>
:root{--bg:#fbfaf7;--fg:#1d1d1b;--muted:#5d5b55;--line:#d9d6ce;--panel:#ffffff;--accent:#1f5fae;--code:#f3f1ec;
--ok:#1e6b3a;--ok-bg:#e3f1e7;--bad:#9b1c1c;--bad-bg:#fbe5e3;--nv:#6b5410;--nv-bg:#f6edcf;--obs:#1f5fae;--obs-bg:#e2ecf8;--read:#4b4a46;--read-bg:#ecebe6}
*{box-sizing:border-box}
html{font-size:18px}
body{margin:0;background:var(--bg);color:var(--fg);font:1rem/1.55 system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif}
main{max-width:1400px;margin:0 auto;padding:28px 32px 80px}
h1{font-size:2rem;margin:0 0 6px}h2{font-size:1.5rem;margin:48px 0 10px;padding-top:18px;border-top:1px solid var(--line)}
h3{font-size:1.15rem;margin:26px 0 8px}
p.sub{color:var(--muted);margin:0 0 18px;font-size:1.05rem}
a{color:var(--accent)}code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:.86em;background:var(--code);padding:1px 4px;border-radius:3px;overflow-wrap:anywhere}
nav.toc{display:flex;flex-wrap:wrap;gap:6px 18px;margin:8px 0 20px;font-size:.95rem}
.metrics{display:flex;flex-wrap:wrap;gap:12px;margin:18px 0}
.metric{border:1px solid var(--line);background:var(--panel);border-radius:6px;padding:10px 16px;min-width:150px}
.metric .v{font-size:1.6rem;font-weight:650}.metric .l{color:var(--muted);font-size:.85rem}
ul.summary li{margin:6px 0}
.tag,.badge{display:inline-block;font-size:.78rem;font-weight:650;border-radius:4px;padding:1px 7px;white-space:nowrap}
.tag-read{background:var(--read-bg);color:var(--read)}.tag-observed{background:var(--obs-bg);color:var(--obs)}
.badge{font-size:.85rem;padding:3px 9px}
.badge-confirmed{background:var(--ok-bg);color:var(--ok)}.badge-contradicted{background:var(--bad-bg);color:var(--bad)}.badge-nv{background:var(--nv-bg);color:var(--nv)}
.flowwrap{border:1px solid var(--line);background:var(--panel);border-radius:8px;padding:8px;overflow-x:auto}
svg.flow{width:100%;min-width:900px;height:auto;display:block}
.coltitle{font-size:20px;font-weight:700;fill:var(--muted)}
.node rect{fill:#fff;stroke:#8c8a83;stroke-width:1.5}
.node-screen rect{fill:#f4f8fd}.node-action rect{fill:#f7f4fb}.node-sql rect{fill:#f6f6f2}.node-table rect{fill:#f2f8f3}
.node{cursor:pointer}.node:hover rect{stroke:var(--accent);stroke-width:2.5}
.node:focus{outline:none}.node:focus-visible rect,.node.sel rect{stroke:var(--accent);stroke-width:3.5}
.n-title{font-size:17px;font-weight:650;fill:var(--fg)}.n-sub{font-size:14px;fill:var(--muted)}
.edge{fill:none;stroke:#7d7b74;stroke-width:1.6}.edge.back{stroke-dasharray:6 4}.edge.exc{stroke:var(--bad)}
.arrowhead{fill:#7d7b74}
svg.flow.has-sel .edge{opacity:.15}svg.flow.has-sel .edge.on{opacity:1;stroke:var(--accent);stroke-width:2.6}
svg.flow.has-sel .node{opacity:.45}svg.flow.has-sel .node.near,svg.flow.has-sel .node.sel{opacity:1}
.legend{color:var(--muted);font-size:.9rem;margin:8px 4px}
#panel{border:2px solid var(--accent);background:var(--panel);border-radius:8px;padding:16px 20px;margin-top:16px}
#panel[hidden]{display:none}
#panel h3{margin-top:0}
.filters{display:flex;flex-wrap:wrap;gap:8px;margin:10px 0 14px}
.filters button,.linklike,.closebtn{font:inherit;font-size:.92rem;border:1px solid var(--line);background:var(--panel);border-radius:5px;padding:4px 12px;cursor:pointer;color:var(--fg)}
.filters button[aria-pressed=true]{background:var(--fg);color:#fff;border-color:var(--fg)}
.linklike{border:none;background:none;padding:0;color:var(--accent);text-decoration:underline;font-size:1rem}
button:focus-visible,a:focus-visible,summary:focus-visible{outline:3px solid var(--accent);outline-offset:2px}
table.docs{border-collapse:collapse;width:100%;background:var(--panel)}
table.docs caption{text-align:left;color:var(--muted);padding:6px 0}
table.docs th,table.docs td{border-top:1px solid var(--line);padding:10px 10px;vertical-align:top;text-align:left}
table.docs thead th{border-top:none;font-size:.9rem;color:var(--muted)}
td.claim{width:38%}.src{color:var(--muted);font-size:.85rem;margin-top:4px}
td.ev summary{cursor:pointer}
tr[hidden]{display:none}
.finding{border-top:1px solid var(--line);padding:10px 0}
.finding[hidden]{display:none}
.f-head{display:flex;gap:10px;align-items:center}.num{font-weight:700;min-width:2em}
.finding p{margin:4px 0 6px}
details.cite{margin:6px 0;border-left:3px solid var(--line);padding-left:10px}
details.cite summary{cursor:pointer;font-size:.92rem}
.cite-plain{font-size:.9rem;margin:4px 0}
pre.code{background:var(--code);padding:8px 10px;border-radius:5px;overflow-x:auto;font-size:.82rem;line-height:1.45;margin:6px 0}
pre.code code{background:none;padding:0;font-size:1em}
.ln{display:inline-block;min-width:3.2em;color:#8a877f;user-select:none}.ln.gap{color:#b0ada5}
.live{border:1px solid var(--line);border-radius:6px;padding:8px 12px;margin:8px 0;background:#fff}
.live-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.mini{font-size:.78rem;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}
:target{scroll-margin-top:20px}
.finding:target,tr:target,.live:target{background:#fff8d6}
@media (max-width:800px){main{padding:16px}.live-grid{grid-template-columns:1fr}td.claim{width:auto}}
@media print{#panel,.filters,nav.toc{display:none}details{display:block}details>*{display:block}}
</style>
</head>
<body>
<main>
<header>
<h1>Payment slice: how it actually behaves</h1>
<p class="sub">NorthStar claims monolith, <code>/payment/*.do</code>. Built from the legacy source, the two payment transcripts and @@NLIVE@@ live requests against the running app. Source of truth: <code>docs/analysis/payments-slice.md</code>.</p>
<nav class="toc" aria-label="Sections"><a href="#summary">Summary</a><a href="#flow">Flow</a><a href="#docs">Docs vs code</a><a href="#findings">Findings</a><a href="#live">Live requests</a></nav>
</header>

<section id="summary" aria-labelledby="h-summary">
<h2 id="h-summary" style="border-top:none;margin-top:10px">Summary</h2>
<ul class="summary">@@SUMMARY@@</ul>
<div class="metrics">
<div class="metric"><div class="v">@@NFIND@@</div><div class="l">findings (@@NOBS@@ observed, @@NREAD@@ read)</div></div>
<div class="metric"><div class="v">@@NCONTRA@@ / @@NDOCS@@</div><div class="l">doc statements contradicted</div></div>
<div class="metric"><div class="v">@@NCONF@@</div><div class="l">confirmed</div></div>
<div class="metric"><div class="v">@@NNV@@</div><div class="l">not verifiable</div></div>
<div class="metric"><div class="v">1</div><div class="l">table written (PAYMENT)</div></div>
</div>
</section>

<section id="flow" aria-labelledby="h-flow">
<h2 id="h-flow">Flow: screens → actions → SQL → tables</h2>
<p>Select any box to open its source lines and the live requests that exercised it. Solid arrows are requests and queries; dashed arrows are forwards back to a JSP; red arrows end on the error page.</p>
<div class="flowwrap">@@SVG@@</div>
<p class="legend">AuthFilter guards every <code>*.do</code> request and has no arrows of its own. No screen links to <code>issue.do</code>.</p>
<aside id="panel" hidden aria-live="polite" tabindex="-1"></aside>
<h3>Flow as text</h3>
@@FLOWTEXT@@
</section>

<section id="docs" aria-labelledby="h-docs">
<h2 id="h-docs">Documentation vs code</h2>
<p>Every statement the docs, javadoc, screen text or transcripts make about the payment slice. Open a row's evidence to see the documented text next to the code and the live request.</p>
<div class="filters" role="group" aria-label="Filter by verdict" data-filter="docs">
<button aria-pressed="true" data-v="all">All (@@NDOCS@@)</button>
<button aria-pressed="false" data-v="contradicted">&#10007; Contradicted (@@NCONTRA@@)</button>
<button aria-pressed="false" data-v="confirmed">&#10003; Confirmed (@@NCONF@@)</button>
<button aria-pressed="false" data-v="nv">? Not verifiable (@@NNV@@)</button>
</div>
<table class="docs"><caption>Documented statements about the payment slice and their verdicts</caption>
<thead><tr><th scope="col">ID</th><th scope="col">Statement and source</th><th scope="col">Verdict</th><th scope="col">Evidence</th></tr></thead>
<tbody>@@DOCROWS@@</tbody></table>
</section>

<section id="findings" aria-labelledby="h-findings">
<h2 id="h-findings">Findings</h2>
<p><span class="tag tag-observed">Observed</span> seen in the running app (live step or database row). <span class="tag tag-read">Read</span> known from the source only.</p>
<div class="filters" role="group" aria-label="Filter findings" data-filter="findings">
<button aria-pressed="true" data-v="all">All (@@NFIND@@)</button>
<button aria-pressed="false" data-v="observed">Observed (@@NOBS@@)</button>
<button aria-pressed="false" data-v="read">Read (@@NREAD@@)</button>
</div>
@@FINDINGS@@
</section>

<section id="live" aria-labelledby="h-live">
<h2 id="h-live">Live requests</h2>
<p>Recorded by <code>tools/analysis/payments_live.py</code> against <code>make run</code> after a fresh <code>make seed</code>; rows read back by <code>tools/analysis/PayDbDump.java</code>.</p>
@@LIVE@@
</section>
</main>
@@TEMPLATES@@
<script type="application/json" id="node-data">@@NODEDATA@@</script>
<script>
(function(){
  var data = JSON.parse(document.getElementById('node-data').textContent);
  var svg = document.querySelector('svg.flow');
  var panel = document.getElementById('panel');
  function esc(s){var d=document.createElement('div');d.textContent=s;return d.innerHTML;}
  function select(id, focusPanel){
    var n = data[id]; if(!n) return;
    svg.classList.add('has-sel');
    svg.querySelectorAll('.node').forEach(function(g){g.classList.remove('sel','near');});
    svg.querySelectorAll('.edge').forEach(function(e){
      var on = e.getAttribute('data-from')===id || e.getAttribute('data-to')===id;
      e.classList.toggle('on', on);
      if(on){
        svg.querySelector('[data-node="'+e.getAttribute('data-from')+'"]').classList.add('near');
        svg.querySelector('[data-node="'+e.getAttribute('data-to')+'"]').classList.add('near');
      }
    });
    svg.querySelector('[data-node="'+id+'"]').classList.add('sel');
    var h = '<h3>'+esc(n.label)+' <span class="tag tag-read">'+esc(n.col)+'</span></h3><p>'+esc(n.sub)+'</p>';
    if(n.edges.length){h+='<p><strong>Connections:</strong> '+n.edges.map(function(e){return (e.dir==='out'?'&rarr; ':'&larr; ')+esc(e.other)+' ('+esc(e.label)+')';}).join('; ')+'</p>';}
    h += '<h4>Findings</h4><div class="pf"></div><h4>Live requests</h4><div class="pl"></div>';
    h += '<p><button class="closebtn" type="button">Close</button></p>';
    panel.innerHTML = h;
    var pf = panel.querySelector('.pf');
    n.findings.forEach(function(num){
      var src = document.getElementById('finding-'+num); if(!src) return;
      var c = src.cloneNode(true); c.removeAttribute('id'); c.hidden=false;
      c.querySelectorAll('details').forEach(function(d){d.open=true;});
      pf.appendChild(c);
    });
    var pl = panel.querySelector('.pl');
    n.live.forEach(function(lid){
      var t = document.getElementById('tpl-'+lid); if(t){ var c=t.content.cloneNode(true); c.firstChild.removeAttribute('id'); pl.appendChild(c);} });
    panel.querySelector('.closebtn').addEventListener('click', function(){
      panel.hidden=true; svg.classList.remove('has-sel');
      svg.querySelectorAll('.node').forEach(function(g){g.classList.remove('sel','near');});
      var g=svg.querySelector('[data-node="'+id+'"]'); if(g) g.focus();
    });
    panel.hidden = false;
    if(history.replaceState) history.replaceState(null,'','#node-'+id);
    if(focusPanel){ panel.focus({preventScroll:true}); panel.scrollIntoView({behavior:'smooth',block:'start'}); }
  }
  svg.querySelectorAll('.node').forEach(function(g){
    g.addEventListener('click', function(){select(g.getAttribute('data-node'), true);});
    g.addEventListener('keydown', function(ev){ if(ev.key==='Enter'||ev.key===' '){ev.preventDefault();select(g.getAttribute('data-node'), true);} });
  });
  document.querySelectorAll('button[data-node]').forEach(function(b){
    b.addEventListener('click', function(){select(b.getAttribute('data-node'), true);});
  });
  document.querySelectorAll('.filters').forEach(function(group){
    var kind = group.getAttribute('data-filter');
    group.querySelectorAll('button').forEach(function(b){
      b.addEventListener('click', function(){
        group.querySelectorAll('button').forEach(function(o){o.setAttribute('aria-pressed', o===b?'true':'false');});
        var v = b.getAttribute('data-v');
        if(kind==='docs'){
          document.querySelectorAll('table.docs tbody tr').forEach(function(tr){tr.hidden = !(v==='all'||tr.getAttribute('data-verdict')===v);});
        } else {
          document.querySelectorAll('#findings .finding').forEach(function(a){a.hidden = !(v==='all'||a.getAttribute('data-tag')===v);});
        }
      });
    });
  });
  document.querySelectorAll('table.docs tbody tr').forEach(function(tr){
    tr.addEventListener('click', function(ev){
      if(ev.target.closest('a,summary,details')) return;
      var d = tr.querySelector('details'); if(d) d.open = !d.open;
    });
  });
  function openTarget(){
    var h = location.hash.slice(1); if(!h) return;
    if(h.indexOf('node-')===0){ select(h.slice(5), false); return; }
    var el = document.getElementById(h); if(!el) return;
    el.hidden = false;
    var d = el.querySelector('details'); if(d) d.open = true;
    if(el.tagName==='DETAILS') el.open = true;
  }
  window.addEventListener('hashchange', openTarget); openTarget();
})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    sys.exit(build())

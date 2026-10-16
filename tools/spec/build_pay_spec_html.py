"""Build docs/specs/SPEC-PAY-001.html (self-contained, no server, no external
assets) from docs/specs/SPEC-PAY-001.md, the cited legacy source lines, the
payment transcripts and docs/specs/SPEC-PAY-001-live.json, and refresh the
live-evidence table inside the Markdown spec.

    python3 tools/spec/build_pay_spec_html.py
"""
import html, json, os, re

SPEC = os.path.join("docs", "specs", "SPEC-PAY-001.md")
OUT = os.path.join("docs", "specs", "SPEC-PAY-001.html")
LIVE = os.path.join("docs", "specs", "SPEC-PAY-001-live.json")
OQ = os.path.join("docs", "specs", "OPEN-QUESTIONS.md")
TRANSCRIPTS = os.path.join("transcripts")

RULE = re.compile(r"^\*\*(PAY-R\d+) \((v\d+)\)\*\* — (.*)$")
CITE = re.compile(r"((?:src|docs|tools|transcripts)/[\w./-]+):([\d,-]+)")
STEP = re.compile(r"\bL\d\d\b")
DBROW = re.compile(r"db rows? ([\d,\- ]+)")
TRANSCRIPT = re.compile(r"\b(payment_[a-z_]+)\b")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def parse_spec(text):
    sections, rules, current, section = [], [], None, None
    lines = text.splitlines()
    for line in lines:
        if line.startswith("## "):
            section = line[3:].strip()
            sections.append(section)
            continue
        match = RULE.match(line)
        if match:
            current = {"id": match.group(1), "version": match.group(2), "section": section,
                       "text": match.group(3), "observed": [], "read": []}
            rules.append(current)
            continue
        if current is None:
            continue
        if line.strip() == "":
            current = None
            continue
        if line.startswith("Observed:") or line.startswith("Read:"):
            current["_mode"] = "observed" if line.startswith("Observed:") else "read"
            line = line.split(":", 1)[1]
        mode = current.get("_mode")
        if mode:
            current[mode].append(line.strip())
        else:
            current["text"] += " " + line.strip()
    for rule in rules:
        rule.pop("_mode", None)
        rule["observed"] = " ".join(rule["observed"]).rstrip(";")
        rule["read"] = " ".join(rule["read"]).rstrip(";")
        rule["text"] = rule["text"].replace("\n", " ")
    return sections, rules


def parse_open_questions(text):
    rows = []
    for line in text.splitlines():
        if line.startswith("| OQ-") and "SPEC-PAY-001" in line:
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            rows.append({"id": cells[0], "question": cells[1], "blocks": cells[2], "owner": cells[3],
                         "interim": cells[4], "status": cells[5]})
    return rows


def expand(ranges):
    out = []
    for part in ranges.split(","):
        if "-" in part:
            a, b = part.split("-")
            out.extend(range(int(a), int(b) + 1))
        elif part.strip():
            out.append(int(part))
    return out


def source_excerpts(citation_text):
    excerpts = []
    for path, ranges in CITE.findall(citation_text):
        if not os.path.exists(path):
            continue
        lines = read(path).splitlines()
        wanted = expand(ranges)
        rows = [(n, lines[n - 1] if n - 1 < len(lines) else "") for n in wanted]
        excerpts.append({"path": path, "ranges": ranges, "rows": rows})
    return excerpts


def md_inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(r"\b(PAY-R\d+)\b", r'<a class="xref" href="#\1">\1</a>', text)
    return text


def main():
    spec = read(SPEC)
    sections, rules = parse_spec(spec)
    version = re.search(r"^Version: (.*)$", spec, re.M).group(1)
    live = json.load(open(LIVE, encoding="utf-8"))
    steps = {s["id"]: s for s in live["steps"]}
    db_rows = live.get("db", {}).get("rows", [])
    transcripts = {}
    for name in sorted(os.listdir(TRANSCRIPTS)):
        if name.startswith("payment_") and name.endswith(".json"):
            transcripts[name[:-5]] = json.load(open(os.path.join(TRANSCRIPTS, name), encoding="utf-8"))
    questions = parse_open_questions(read(OQ))

    for rule in rules:
        rule["steps"] = sorted(set(STEP.findall(rule["observed"])))
        rule["transcripts"] = sorted(set(t for t in TRANSCRIPT.findall(rule["observed"]) if t in transcripts))
        rule["db"] = []
        for group in DBROW.findall(rule["observed"]):
            rule["db"].extend(expand(group.replace(" ", "")))
        rule["sources"] = source_excerpts(rule["read"])
        if rule["steps"] or rule["transcripts"] or rule["db"]:
            rule["status"] = "observed"
        elif rule["sources"]:
            rule["status"] = "read"
        else:
            rule["status"] = "open"

    counts = {"observed": sum(r["status"] == "observed" for r in rules),
              "read": sum(r["status"] == "read" for r in rules), "open": len(questions)}

    # ---- Markdown live-evidence block ----
    md = ["| Step | Request | Result | Rendered fields |", "| --- | --- | --- | --- |"]
    for s in live["steps"]:
        req = s["request"]
        form = " ".join("%s=%s" % (k, v if len(v) <= 24 else v[:21] + "...") for k, v in req["form"].items())
        form = form.replace("|", "\\|")
        fields = s["response"]["fields"]
        shown = ", ".join("%s=%s" % kv for kv in list(fields.items())[:4])
        if len(fields) > 4:
            shown += ", ... (%d fields)" % len(fields)
        md.append("| %s | `%s %s` %s | %s %s | %s |" % (
            s["id"], req["method"], req["path"], ("`" + form + "`") if form else "",
            s["response"]["status"], s["response"]["result"].replace("|", "\\|"), shown.replace("|", "\\|") or "-"))
    md.append("")
    md.append("Read-only JDBC probe after the run (`%s`):" % live["db"]["columns"])
    md.append("")
    md.append("```")
    md.extend(db_rows)
    md.append("```")
    block = "<!-- live-evidence:begin -->\n" + "\n".join(md) + "\n<!-- live-evidence:end -->"
    spec_out = re.sub(r"<!-- live-evidence:begin -->.*?<!-- live-evidence:end -->", lambda m: block, spec, flags=re.S)
    if spec_out != spec:
        with open(SPEC, "w", encoding="utf-8") as handle:
            handle.write(spec_out)

    # ---- HTML ----
    e = html.escape

    def step_html(sid):
        s = steps[sid]
        req, res = s["request"], s["response"]
        form_rows = "".join("<tr><th>%s</th><td>%s</td></tr>" % (e(k), e(v) if v else "<i>(empty)</i>")
                            for k, v in req["form"].items()) or "<tr><td colspan=2><i>no parameters</i></td></tr>"
        field_rows = "".join("<tr><th><code>f_%s</code></th><td>%s</td></tr>" % (e(k), e(v))
                             for k, v in res["fields"].items()) or "<tr><td colspan=2><i>no ns:field values rendered</i></td></tr>"
        snippets = "".join("<figcaption>%s</figcaption><pre>%s</pre>" % (e(k), e(v))
                           for k, v in res["snippets"].items() if v)
        return ('<article class="step" id="step-%s"><h4><span class="sid">%s</span> %s</h4>'
                '<div class="rr"><div><h5>Request</h5><p class="req"><b>%s</b> %s</p><table>%s</table></div>'
                '<div><h5>Response</h5><p class="req">HTTP <b>%s</b> &middot; %s</p><table>%s</table>%s</div></div></article>') % (
            sid, sid, e(s["title"]), e(req["method"]), e(req["path"]), form_rows, res["status"], e(res["result"]),
            field_rows, ('<figure class="snips">%s</figure>' % snippets) if snippets else "")

    def transcript_html(name):
        t = transcripts[name]
        return ('<article class="step transcript"><h4><span class="sid">transcript</span> %s <small>%s</small></h4>'
                '<pre>%s</pre></article>') % (e(name), e(t.get("description", "")), e(json.dumps(t, indent=1)))

    def db_html(ids):
        rows = [r for r in db_rows if r.split(" | ")[0].strip().isdigit() and int(r.split(" | ")[0]) in ids]
        rows += [r for r in db_rows if r.startswith("count=") or r.startswith("claim118")]
        return ('<article class="step db"><h4><span class="sid">db</span> Read-only JDBC probe after the run</h4>'
                '<p class="muted">%s</p><pre>%s</pre></article>') % (e(live["db"]["columns"]), e("\n".join(rows)))

    def source_html(src):
        body = "".join('<span class="ln">%d</span>%s\n' % (n, e(code)) for n, code in src["rows"])
        return '<figure class="src"><figcaption>%s:%s</figcaption><pre>%s</pre></figure>' % (e(src["path"]), e(src["ranges"]), body)

    label = {"observed": "Observed", "read": "Read-only", "open": "Open question"}
    parts = []
    for section in sections:
        section_rules = [r for r in rules if r["section"] == section]
        if not section_rules:
            continue
        parts.append('<section class="group"><h2>%s</h2>' % e(section))
        for r in section_rules:
            evidence = [step_html(s) for s in r["steps"]]
            evidence += [transcript_html(t) for t in r["transcripts"]]
            if r["db"]:
                evidence.append(db_html(set(r["db"])))
            parts.append(
                '<details class="rule" id="%s" data-status="%s" data-text="%s">'
                '<summary><span class="rid">%s <small>(%s)</small></span><span class="badge %s">%s</span>'
                '<span class="short">%s</span></summary>'
                '<div class="body"><p class="full">%s</p>'
                '<div class="cols"><div class="col"><h3>Cited source lines</h3><p class="cite">Read: %s</p>%s</div>'
                '<div class="col"><h3>Proof on the running legacy app</h3><p class="cite">Observed: %s</p>%s</div></div>'
                '</div></details>' % (
                    r["id"], r["status"], e(r["text"].lower()), r["id"], r["version"], r["status"], label[r["status"]],
                    md_inline(r["text"][:140] + ("…" if len(r["text"]) > 140 else "")), md_inline(r["text"]),
                    md_inline(r["read"]) or "<i>none</i>", "".join(source_html(s) for s in r["sources"]) or "<p class=muted>No source citation.</p>",
                    md_inline(r["observed"]) or "<i>none</i>", "".join(evidence) or "<p class=muted>No transcript or live request.</p>"))
        parts.append("</section>")

    parts.append('<section class="group" id="open-questions"><h2>Open questions (not rules)</h2>'
                 '<p class="muted">Code facts the running app could not show, or behaviour whose business intent is unknown. '
                 'Rows live in <code>docs/specs/OPEN-QUESTIONS.md</code>.</p>')
    for q in questions:
        parts.append('<details class="rule" id="%s" data-status="open" data-text="%s"><summary><span class="rid">%s</span>'
                     '<span class="badge open">Open question</span><span class="short">%s</span></summary>'
                     '<div class="body"><p class="full">%s</p><table class="oq"><tr><th>Blocks</th><td>%s</td></tr>'
                     '<tr><th>Owner</th><td>%s</td></tr><tr><th>Interim decision</th><td>%s</td></tr><tr><th>Status</th><td>%s</td></tr></table>'
                     '%s</div></details>' % (
                         q["id"], e(q["question"].lower()), q["id"], md_inline(q["question"][:140] + "…"), md_inline(q["question"]),
                         md_inline(q["blocks"]), e(q["owner"]), md_inline(q["interim"]), e(q["status"]),
                         "".join(source_html(s) for s in source_excerpts(q["question"].replace("ClaimsActionSupport.java", "src/main/java/com/northstar/claims/web/ClaimsActionSupport.java").replace("PaymentDetailAction.java", "src/main/java/com/northstar/claims/web/PaymentDetailAction.java")))))
    parts.append("</section>")

    page = TEMPLATE.replace("{{VERSION}}", e(version)).replace("{{BODY}}", "\n".join(parts))
    page = page.replace("{{N_RULES}}", str(len(rules))).replace("{{N_OBS}}", str(counts["observed"]))
    page = page.replace("{{N_READ}}", str(counts["read"])).replace("{{N_OPEN}}", str(counts["open"]))
    page = page.replace("{{N_STEPS}}", str(len(live["steps"]))).replace("{{N_TRANSCRIPTS}}", str(len(transcripts)))
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(page)
    print("rules=%d observed=%d read-only=%d open=%d -> %s" % (len(rules), counts["observed"], counts["read"], counts["open"], OUT))


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>SPEC-PAY-001 — Payment slice, evidence viewer</title>
<style>
:root{--bg:#fff;--fg:#1b1f23;--muted:#5b6670;--line:#d8dde3;--panel:#f5f7f9;--code:#eef1f4;--accent:#0b5fff;
 --obs:#0a7f3f;--obs-bg:#e3f6ea;--read:#9a5b00;--read-bg:#fff1dc;--open:#b00020;--open-bg:#fde7ea;--focus:#ff9d00}
@media (prefers-color-scheme:dark){:root{--bg:#0f1317;--fg:#e6e9ec;--muted:#9aa5b1;--line:#2b333c;--panel:#161c22;--code:#1c242c;--accent:#6ea8ff;
 --obs:#5bd58f;--obs-bg:#0f3a24;--read:#ffc46b;--read-bg:#3d2a07;--open:#ff7b8f;--open-bg:#43101a}}
html[data-theme=light]{--bg:#fff;--fg:#1b1f23;--muted:#5b6670;--line:#d8dde3;--panel:#f5f7f9;--code:#eef1f4;--accent:#0b5fff;--obs:#0a7f3f;--obs-bg:#e3f6ea;--read:#9a5b00;--read-bg:#fff1dc;--open:#b00020;--open-bg:#fde7ea}
html[data-theme=dark]{--bg:#0f1317;--fg:#e6e9ec;--muted:#9aa5b1;--line:#2b333c;--panel:#161c22;--code:#1c242c;--accent:#6ea8ff;--obs:#5bd58f;--obs-bg:#0f3a24;--read:#ffc46b;--read-bg:#3d2a07;--open:#ff7b8f;--open-bg:#43101a}
*{box-sizing:border-box}
body{margin:0;font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;color:var(--fg);background:var(--bg)}
header{position:sticky;top:0;z-index:2;background:var(--bg);border-bottom:1px solid var(--line);padding:12px 20px}
header h1{font-size:19px;margin:0 0 4px}
header .sub{color:var(--muted);font-size:13px;margin:0 0 8px}
.toolbar{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
.toolbar button,.toolbar input{font:inherit;font-size:13px;border:1px solid var(--line);background:var(--panel);color:var(--fg);border-radius:999px;padding:4px 12px;cursor:pointer}
.toolbar input{cursor:text;flex:1 1 180px;min-width:140px;border-radius:8px}
.toolbar button[aria-pressed=true]{border-color:var(--accent);box-shadow:inset 0 0 0 1px var(--accent)}
.toolbar .count{font-weight:600}
main{max-width:1200px;margin:0 auto;padding:16px 20px 60px}
.group h2{font-size:16px;margin:28px 0 8px;padding-bottom:4px;border-bottom:1px solid var(--line)}
details.rule{border:1px solid var(--line);border-radius:8px;margin:8px 0;background:var(--bg)}
details.rule[hidden]{display:none}
summary{list-style:none;display:grid;grid-template-columns:auto auto 1fr;gap:10px;align-items:center;padding:10px 14px;cursor:pointer;border-radius:8px}
summary::-webkit-details-marker{display:none}
summary:hover{background:var(--panel)}
summary:focus-visible,.toolbar :focus-visible,a:focus-visible{outline:3px solid var(--focus);outline-offset:2px}
.rid{font-weight:700;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;white-space:nowrap}
.rid small{font-weight:400;color:var(--muted)}
.badge{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.03em;padding:2px 8px;border-radius:999px;white-space:nowrap}
.badge.observed{color:var(--obs);background:var(--obs-bg)}
.badge.read{color:var(--read);background:var(--read-bg)}
.badge.open{color:var(--open);background:var(--open-bg)}
.short{color:var(--muted);font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
details[open] .short{display:none}
.body{padding:0 14px 14px;border-top:1px solid var(--line)}
.full{margin:12px 0}
.cols{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.col h3{font-size:13px;text-transform:uppercase;letter-spacing:.04em;color:var(--muted);margin:8px 0 4px}
.cite{font-size:13px;color:var(--muted);margin:0 0 8px;word-break:break-word}
code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.92em;background:var(--code);padding:0 4px;border-radius:4px}
pre{margin:0;padding:10px 12px;background:var(--code);border-radius:6px;overflow:auto;font:12.5px/1.45 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre}
figure{margin:0 0 10px}
figcaption{font-size:12px;color:var(--muted);font-family:ui-monospace,SFMono-Regular,Menlo,monospace;margin:0 0 3px}
.ln{display:inline-block;width:3.2em;color:var(--muted);text-align:right;margin-right:1em;user-select:none}
.step{border:1px solid var(--line);border-radius:6px;padding:8px 10px;margin:0 0 10px;background:var(--panel)}
.step h4{margin:0 0 6px;font-size:14px}
.step h5{margin:6px 0 2px;font-size:12px;color:var(--muted);text-transform:uppercase}
.sid{display:inline-block;background:var(--obs-bg);color:var(--obs);font-family:ui-monospace,monospace;font-size:12px;padding:1px 7px;border-radius:4px;margin-right:4px}
.rr{display:grid;grid-template-columns:1fr;gap:12px}
.rr table th{width:34%}
.req{margin:0 0 4px;font-size:13px;word-break:break-all}
table{border-collapse:collapse;font-size:13px;width:100%}
td,th{border:1px solid var(--line);padding:3px 6px;text-align:left;vertical-align:top;word-break:break-word}
th{background:var(--bg);font-weight:600;white-space:nowrap}
.muted{color:var(--muted);font-size:13px}
a.xref{color:var(--accent);text-decoration:none}
a.xref:hover{text-decoration:underline}
.legend{display:flex;gap:10px;flex-wrap:wrap;font-size:13px;margin:10px 0 0}
.legend span{display:inline-flex;gap:6px;align-items:center}
.empty{display:none;padding:20px;color:var(--muted);text-align:center}
@media (max-width:760px){.cols,.rr{grid-template-columns:1fr}summary{grid-template-columns:auto auto;}.short{grid-column:1/-1;white-space:normal}main{padding:12px}}
@media (prefers-reduced-motion:no-preference){summary{transition:background .15s}}
@media print{header{position:static}.toolbar{display:none}details.rule{break-inside:avoid}details:not([open]) .body{display:block}.short{display:none}}
</style>
</head>
<body>
<header>
<h1>SPEC-PAY-001 — Payment issue, history, detail and remittance <small style="font-weight:400;color:var(--muted)">version {{VERSION}}</small></h1>
<p class="sub">{{N_RULES}} rules, each proved against the running legacy app ({{N_STEPS}} live requests, {{N_TRANSCRIPTS}} recorded transcripts, one read-only database probe) and cited to legacy source lines. Click a rule to see the cited source lines and the transcript or live request/response that proves it.</p>
<div class="toolbar" role="group" aria-label="Filter rules">
<button type="button" data-filter="all" aria-pressed="true">All <span class="count">{{N_RULES}} + {{N_OPEN}}</span></button>
<button type="button" data-filter="observed" aria-pressed="false"><span class="badge observed">Observed</span> <span class="count">{{N_OBS}}</span></button>
<button type="button" data-filter="read" aria-pressed="false"><span class="badge read">Read-only</span> <span class="count">{{N_READ}}</span></button>
<button type="button" data-filter="open" aria-pressed="false"><span class="badge open">Open question</span> <span class="count">{{N_OPEN}}</span></button>
<input type="search" id="q" placeholder="Search rule text (e.g. fallback, NaN, CHK-)" aria-label="Search rules">
<button type="button" id="expand">Expand all</button>
<button type="button" id="collapse">Collapse all</button>
<button type="button" id="theme" aria-label="Toggle light or dark theme">Theme</button>
</div>
<p class="legend"><span><span class="badge observed">Observed</span> proved by a transcript or a recorded live request/response (plus source citation)</span>
<span><span class="badge read">Read-only</span> source citation only, not yet shown on the running app</span>
<span><span class="badge open">Open question</span> not a rule; recorded in OPEN-QUESTIONS.md</span></p>
</header>
<main>
{{BODY}}
<p class="empty" id="empty">No rule matches the current filter.</p>
</main>
<script>
(function(){
 var rules=Array.prototype.slice.call(document.querySelectorAll('details.rule'));
 var filter='all', query='';
 function apply(){
  var shown=0;
  rules.forEach(function(d){
   var ok=(filter==='all'||d.dataset.status===filter)&&(!query||d.dataset.text.indexOf(query)>=0||d.id.toLowerCase().indexOf(query)>=0);
   d.hidden=!ok; if(ok) shown++;
  });
  document.querySelectorAll('.group').forEach(function(g){g.hidden=!g.querySelector('details.rule:not([hidden])');});
  document.getElementById('empty').style.display=shown?'none':'block';
 }
 document.querySelectorAll('[data-filter]').forEach(function(b){
  b.addEventListener('click',function(){
   filter=b.dataset.filter;
   document.querySelectorAll('[data-filter]').forEach(function(x){x.setAttribute('aria-pressed',String(x===b));});
   apply();
  });
 });
 document.getElementById('q').addEventListener('input',function(e){query=e.target.value.trim().toLowerCase();apply();});
 document.getElementById('expand').addEventListener('click',function(){rules.forEach(function(d){if(!d.hidden)d.open=true;});});
 document.getElementById('collapse').addEventListener('click',function(){rules.forEach(function(d){d.open=false;});});
 document.getElementById('theme').addEventListener('click',function(){
  var h=document.documentElement, dark=h.dataset.theme?h.dataset.theme==='dark':matchMedia('(prefers-color-scheme: dark)').matches;
  h.dataset.theme=dark?'light':'dark';
 });
 function openHash(){
  var id=location.hash.replace('#',''); if(!id) return;
  var d=document.getElementById(id); if(d&&d.tagName==='DETAILS'){d.open=true;d.scrollIntoView();d.querySelector('summary').focus();}
 }
 document.querySelectorAll('a.xref').forEach(function(a){a.addEventListener('click',function(){setTimeout(openHash,0);});});
 window.addEventListener('hashchange',openHash); openHash();
})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()

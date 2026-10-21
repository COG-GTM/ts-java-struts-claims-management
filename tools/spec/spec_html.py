#!/usr/bin/env python3
"""Check every citation in docs/specs/SPEC-PAY-001.md and write its HTML twin.

The Markdown spec stays the source of truth. This tool reads it, the live
proof evidence (docs/specs/SPEC-PAY-001-proof.json, written by
tools/spec/payment_proof.py), the transcripts it cites and the legacy source
lines it cites, and writes docs/specs/SPEC-PAY-001.html: one self-contained
file (no network, opens from file://) where every rule opens to its source
lines and to the requests and responses that prove it.

It fails, and writes nothing, when a rule has no Read: or no Observed:
citation, a cited file or line does not exist, or a cited live case, step or
transcript is missing. The output has no timestamp: the same inputs always
give the same bytes.

Usage: python3 tools/spec/spec_html.py  (see `make payment-spec-html`)
"""

import html
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SPEC = "docs/specs/SPEC-PAY-001.md"
PROOF = "docs/specs/SPEC-PAY-001-proof.json"
QUESTIONS = "docs/specs/OPEN-QUESTIONS.md"
OUTPUT = "docs/specs/SPEC-PAY-001.html"
SPEC_ID = "SPEC-PAY-001"
RULE_PREFIX = "PAY"

HEADING = re.compile(r"^\*\*(%s-R\d+) \(v(\d+)(, superseded by v\d+)?\)\*\* — (.*)$" % RULE_PREFIX)
SECTION = re.compile(r"^## (.*)$")
CITE = re.compile(r"^(Read|Observed): (.*)$")
SOURCE_REF = re.compile(r"^(\S+?):(\d+)(?:-(\d+))?$")
LIVE_REF = re.compile(r"^live:([a-z0-9-]+)#(\d+)$")
CONTEXT_LINES = 3


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as stream:
        return stream.read()


def parse_spec(text):
    """Rules in spec order, the version line, and the screen-text claim table."""
    rules, claims, errors = [], [], []
    version, section, current, last_kind = "-", "", None, None
    for line in text.splitlines():
        match = re.match(r"^Version: (.*)$", line)
        if match:
            version = match.group(1)
        match = SECTION.match(line)
        if match:
            section, current = match.group(1), None
            continue
        match = HEADING.match(line)
        if match:
            current = {"id": match.group(1), "version": match.group(2),
                       "superseded": (match.group(3) or "").lstrip(", "),
                       "section": section, "text": match.group(4),
                       "read": [], "observed": []}
            rules.append(current)
            last_kind = None
            continue
        if section == "Screen text checked against behaviour" and line.startswith("| ") \
                and not line.startswith(("| Claim", "| ---")):
            cells = [c.strip() for c in line.strip("|").split(" | ")]
            claims.append(dict(zip(["claim", "where", "result", "evidence"], cells)))
            continue
        if current is None:
            continue
        if not line.strip():
            current, last_kind = None, None
            continue
        match = CITE.match(line)
        if match:
            last_kind = match.group(1).lower()
            body = match.group(2)
        elif last_kind:
            body = line
        else:
            current["text"] += " " + line.strip()
            continue
        key = "read" if last_kind == "read" else "observed"
        current[key] += [p.strip() for p in body.split(";") if p.strip()]
    for rule in rules:
        if not rule["read"]:
            errors.append("%s has no Read: citation" % rule["id"])
        if not rule["observed"]:
            errors.append("%s has no Observed: citation" % rule["id"])
    return version, rules, claims, errors


def source_excerpt(ref, cache, errors, owner):
    match = SOURCE_REF.match(ref)
    if not match:
        errors.append("%s: Read citation %r is not path:line" % (owner, ref))
        return None
    path, start = match.group(1), int(match.group(2))
    end = int(match.group(3) or start)
    if path not in cache:
        if not os.path.isfile(os.path.join(ROOT, path)):
            errors.append("%s: %s does not exist" % (owner, path))
            return None
        cache[path] = read(path).splitlines()
    lines = cache[path]
    if not 1 <= start <= end <= len(lines):
        errors.append("%s: %s has no lines %d-%d" % (owner, path, start, end))
        return None
    first = max(1, start - CONTEXT_LINES)
    last = min(len(lines), end + CONTEXT_LINES)
    return {"ref": ref, "path": path, "start": start, "end": end,
            "lines": [[n, lines[n - 1], start <= n <= end]
                      for n in range(first, last + 1)]}


def evidence(ref, cases, errors, owner):
    match = LIVE_REF.match(ref)
    if match:
        case = cases.get(match.group(1))
        step = int(match.group(2))
        if case is None:
            errors.append("%s: no live case %s in %s" % (owner, match.group(1), PROOF))
            return None
        if not 1 <= step <= len(case["exchanges"]):
            errors.append("%s: case %s has no step %d" % (owner, case["id"], step))
            return None
        return {"ref": ref, "kind": "live", "case": case["id"], "step": step}
    path = os.path.join("transcripts", ref + ".json")
    if not os.path.isfile(os.path.join(ROOT, path)):
        errors.append("%s: Observed citation %r is neither a live case nor a transcript"
                      % (owner, ref))
        return None
    return {"ref": ref, "kind": "transcript", "path": path,
            "transcript": json.loads(read(path))}


def open_questions():
    rows = []
    for line in read(QUESTIONS).splitlines():
        if line.startswith("| OQ-") and SPEC_ID in line:
            cells = [c.strip() for c in line.strip().strip("|").split(" | ")]
            rows.append(dict(zip(["id", "question", "blocks", "owner",
                                  "interim", "status"], cells)))
    return rows


def main():
    version, rules, claims, errors = parse_spec(read(SPEC))
    proof = json.loads(read(PROOF))
    cases = {case["id"]: case for case in proof["cases"]}
    cache, used_cases = {}, set()
    for rule in rules:
        rule["sources"] = [s for s in (source_excerpt(r, cache, errors, rule["id"])
                                       for r in rule["read"]) if s]
        rule["evidence"] = [e for e in (evidence(r, cases, errors, rule["id"])
                                        for r in rule["observed"]) if e]
        rule["status"] = "Observed" if rule["observed"] else "Read-only"
        used_cases |= {e["case"] for e in rule["evidence"] if e["kind"] == "live"}
    for claim in claims:
        for ref in claim["evidence"].split(";"):
            found = evidence(ref.strip(), cases, errors, "screen text claim")
            if found and found["kind"] == "live":
                used_cases.add(found["case"])
        source_excerpt(claim["where"], cache, errors, "screen text claim")
    unused = sorted(set(cases) - used_cases)
    if unused:
        errors.append("live cases cited by no rule: " + ", ".join(unused))
    if errors:
        sys.exit("SPEC-PAY-001 citation check failed:\n  " + "\n  ".join(errors))
    data = {"spec": SPEC_ID, "version": version, "source": SPEC,
            "proof": PROOF, "method": proof["method"], "rules": rules,
            "cases": proof["cases"], "claims": claims,
            "questions": open_questions()}
    payload = json.dumps(data, sort_keys=True, ensure_ascii=False,
                         separators=(",", ":")).replace("</", "<\\/")
    page = TEMPLATE.replace("@@TITLE@@", html.escape(SPEC_ID)).replace("@@DATA@@", payload)
    with open(os.path.join(ROOT, OUTPUT), "w", encoding="utf-8") as stream:
        stream.write(page)
    print("checked %d rules, %d live cases; wrote %s" % (len(rules), len(cases), OUTPUT))


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>@@TITLE@@ — interactive view</title>
<!-- Generated by tools/spec/spec_html.py from docs/specs/SPEC-PAY-001.md. Do not edit. -->
<style>
:root{--ink:#1d2433;--muted:#5b6475;--line:#d9dde5;--bg:#fbfaf7;--card:#fff;--accent:#1f5fbf;
--obs:#1b7f4b;--obsbg:#e3f4ea;--ro:#8a5a00;--robg:#fbefd5;--oq:#a12b2b;--oqbg:#fbe4e4;--hl:#fff3b0}
*{box-sizing:border-box}
body{margin:0;font:19px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif;color:var(--ink);background:var(--bg)}
header{padding:28px 40px 16px;border-bottom:1px solid var(--line);background:#fff;position:sticky;top:0;z-index:2}
h1{margin:0 0 4px;font-size:30px}
.sub{color:var(--muted);font-size:17px}
.bar{display:flex;flex-wrap:wrap;gap:10px;margin-top:14px;align-items:center}
.bar button{font:inherit;font-size:17px;padding:6px 14px;border:1px solid var(--line);border-radius:20px;background:#fff;cursor:pointer}
.bar button.on{background:var(--ink);color:#fff;border-color:var(--ink)}
.bar input{font:inherit;font-size:17px;padding:6px 12px;border:1px solid var(--line);border-radius:8px;min-width:260px}
main{padding:20px 40px 60px;max-width:1300px}
h2{font-size:24px;margin:30px 0 10px}
.rule,.oq{background:var(--card);border:1px solid var(--line);border-radius:10px;margin:10px 0}
.rule>summary,.oq>summary{list-style:none;cursor:pointer;padding:14px 18px;display:flex;gap:14px;align-items:baseline}
.rule>summary::-webkit-details-marker{display:none}
.rule[open]>summary{border-bottom:1px solid var(--line)}
.rid{font-weight:700;white-space:nowrap;min-width:150px;flex:0 0 auto}
details[id]{scroll-margin-top:190px}
.badge{font-size:14px;font-weight:600;padding:2px 10px;border-radius:12px;white-space:nowrap}
.b-Observed{background:var(--obsbg);color:var(--obs)} .b-Read-only{background:var(--robg);color:var(--ro)}
.b-Open{background:var(--oqbg);color:var(--oq)}
.b-Confirmed{background:var(--obsbg);color:var(--obs)} .b-Contradicted{background:var(--oqbg);color:var(--oq)}
.b-Not{background:var(--robg);color:var(--ro)}
.count{color:var(--muted);font-size:15px;margin-left:auto;white-space:nowrap}
.body{padding:6px 18px 18px}
.body h3{font-size:18px;margin:16px 0 6px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}
code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:.88em;background:#f0f1f4;padding:1px 5px;border-radius:4px}
.chips{display:flex;flex-wrap:wrap;gap:8px}
.chip{font:inherit;font-size:16px;padding:5px 12px;border-radius:8px;border:1px solid var(--line);background:#f6f7f9;cursor:pointer}
.chip.on{border-color:var(--accent);background:#e8f0fc;color:var(--accent)}
.panel{margin-top:10px;border:1px solid var(--line);border-radius:8px;overflow:hidden}
.panel .ph{background:#f3f4f7;padding:8px 12px;font-size:16px;font-weight:600}
pre.src{margin:0;padding:8px 0;font:15px/1.45 ui-monospace,Menlo,Consolas,monospace;overflow-x:auto;background:#fff}
pre.src span{display:block;padding:0 12px;white-space:pre}
pre.src span.hit{background:var(--hl)}
pre.src i{display:inline-block;width:3.2em;color:#99a;font-style:normal;user-select:none}
table{border-collapse:collapse;width:100%;font-size:16px}
th,td{border:1px solid var(--line);padding:5px 9px;text-align:left;vertical-align:top}
th{background:#f6f7f9}
.kv{display:grid;grid-template-columns:170px 1fr;gap:4px 12px;padding:10px 12px;font-size:16px}
.kv b{color:var(--muted);font-weight:600}
details.raw summary{cursor:pointer;color:var(--accent);padding:6px 12px;font-size:16px}
details.raw pre{margin:0;padding:10px 12px;max-height:340px;overflow:auto;font:13px/1.4 ui-monospace,Menlo,Consolas,monospace;background:#fafafa;white-space:pre-wrap}
.ex{border-top:1px dashed var(--line)}
.hidden{display:none}
.note{color:var(--muted);font-size:16px}
</style></head>
<body>
<header>
<h1 id="title"></h1>
<div class="sub" id="sub"></div>
<div class="bar" id="bar"></div>
</header>
<main>
<div id="rules"></div>
<h2>Open questions</h2>
<p class="note">Behaviour that could not be established by a request against the legacy app, from <code>docs/specs/OPEN-QUESTIONS.md</code>.</p>
<div id="questions"></div>
<h2>Screen text checked against behaviour</h2>
<div id="claims"></div>
<h2>How the proof is re-run</h2>
<p class="note" id="method"></p>
</main>
<script type="application/json" id="data">@@DATA@@</script>
<script>
(function(){
"use strict";
var D=JSON.parse(document.getElementById("data").textContent);
var cases={};D.cases.forEach(function(c){cases[c.id]=c;});
function el(tag,attrs,kids){var e=document.createElement(tag);
 for(var k in attrs||{}){if(k==="text")e.textContent=attrs[k];else if(k==="cls")e.className=attrs[k];else e.setAttribute(k,attrs[k]);}
 (kids||[]).forEach(function(c){if(c)e.appendChild(typeof c==="string"?document.createTextNode(c):c);});return e;}
function md(text){var out=el("span");text.split(/(`[^`]*`)/).forEach(function(p){
 if(p.length>1&&p[0]==="`"&&p[p.length-1]==="`")out.appendChild(el("code",{text:p.slice(1,-1)}));else out.appendChild(document.createTextNode(p));});return out;}
function badge(s){return el("span",{cls:"badge b-"+s.split(" ")[0],text:s});}
document.getElementById("title").textContent=D.spec+" — Payment slice";
document.getElementById("sub").appendChild(md("Version "+D.version+" · "+D.rules.length+" rules · "+D.cases.length+" live cases · source of truth `"+D.source+"` · evidence `"+D.proof+"`"));
document.getElementById("method").appendChild(md(D.method+" Re-run with `make payment-spec-proof`; `make payment-spec-check` fails if legacy behaviour moved."));

function kvTable(rows){var t=el("table");t.appendChild(el("tr",{},[el("th",{text:"Field"}),el("th",{text:"Value"})]));
 rows.forEach(function(r){t.appendChild(el("tr",{},[el("td",{},[el("code",{text:r[0]})]),el("td",{text:r[1]})]));});return t;}
function sqlTable(q){var wrap=el("div",{},[el("div",{cls:"kv"},[el("b",{text:"SQL after run"}),el("code",{text:q.sql})])]);
 var t=el("table");t.appendChild(el("tr",{},q.columns.map(function(c){return el("th",{text:c});})));
 if(!q.rows.length)t.appendChild(el("tr",{},[el("td",{colspan:String(q.columns.length),text:"(no rows)"})]));
 q.rows.forEach(function(r){t.appendChild(el("tr",{},r.map(function(v){return el("td",{text:v});})));});wrap.appendChild(t);return wrap;}
function params(r){var p=r.form||r.query||{};var ks=Object.keys(p);return ks.length?ks.map(function(k){return k+"="+p[k];}).join("&"):"(none)";}

function sourcePanel(s){var pre=el("pre",{cls:"src"});
 s.lines.forEach(function(l){pre.appendChild(el("span",{cls:l[2]?"hit":""},[el("i",{text:String(l[0])}),l[1]]));});
 return el("div",{cls:"panel"},[el("div",{cls:"ph"},[el("code",{text:s.ref})]),pre]);}
function livePanel(ev){var c=cases[ev.case];var p=el("div",{cls:"panel"});
 p.appendChild(el("div",{cls:"ph"},["Live case ",el("code",{text:c.id})," — "+c.title]));
 p.appendChild(el("div",{cls:"kv"},[el("b",{text:"Precondition"}),el("span",{},[c.precondition.name].concat(c.precondition.sql.map(function(s){return el("div",{},[el("code",{text:s})]);}))),
  el("b",{text:"Logged in as"}),el("span",{text:c.actor||"(nobody)"})]));
 c.exchanges.forEach(function(x,i){var cited=(i+1)===ev.step;var r=x.request,o=x.response;
  var box=el("div",{cls:"ex"+(cited?"":" hidden")});
  box.appendChild(el("div",{cls:"kv"},[el("b",{text:"Step "+(i+1)+" request"}),el("span",{},[el("code",{text:r.method+" "+r.path}),"  "+params(r)+(r.note?"  ("+r.note+")":"")]),
   el("b",{text:"Response"}),el("span",{},[el("code",{text:String(o.status)}),"  "+(o.location?"redirect "+o.location:"forward "+o.view)])]));
  if(o.fields.length)box.appendChild(kvTable(o.fields));
  var cells=o.text_cells.filter(function(t){return ["Operational record view","2019-04-01"].indexOf(t)<0;});
  if(cells.length)box.appendChild(el("div",{cls:"kv"},[el("b",{text:"Other text on page"}),el("span",{text:cells.join(" · ")})]));
  if(o.links.length)box.appendChild(el("div",{cls:"kv"},[el("b",{text:"Payment links"}),el("span",{},o.links.map(function(h){return el("code",{text:h});}))]));
  box.appendChild(el("details",{cls:"raw"},[el("summary",{text:"Raw response HTML"}),el("pre",{text:o.body})]));
  p.appendChild(box);});
 if(c.exchanges.length>1){var b=el("button",{cls:"chip",text:"Show all "+c.exchanges.length+" steps of this case"});
  b.addEventListener("click",function(){p.querySelectorAll(".ex").forEach(function(e){e.classList.remove("hidden");});b.remove();});
  p.appendChild(el("div",{cls:"kv"},[el("b",{text:"Cited step"}),el("span",{},["step "+ev.step+" ",b])]));}
 c.db_after.forEach(function(q){p.appendChild(sqlTable(q));});return p;}
function transcriptPanel(ev){var t=ev.transcript,e=t.expected;var p=el("div",{cls:"panel"});
 p.appendChild(el("div",{cls:"ph"},["Transcript ",el("code",{text:ev.path})," — "+t.description]));
 p.appendChild(el("div",{cls:"kv"},[el("b",{text:"Request"}),el("span",{},[el("code",{text:t.request.method+" "+t.request.path}),"  "+params(t.request)]),
  el("b",{text:"Response"}),el("span",{},[el("code",{text:String(e.status)}),"  "+e.result]),
  el("b",{text:"Precondition"}),el("span",{text:"make capture: fresh seed, then every earlier scenario in tools/capture/capture.py, including the settlement scenarios"})]));
 p.appendChild(kvTable(Object.keys(e.business_fields).map(function(k){return [k,e.business_fields[k]];})));
 var ds=Object.keys(e.db_state);if(ds.length)p.appendChild(kvTable(ds.map(function(k){return ["db: "+k,e.db_state[k]];})));return p;}

function evidenceBlock(title,items,render,label){var wrap=el("div");wrap.appendChild(el("h3",{text:title}));
 var chips=el("div",{cls:"chips"}),area=el("div");wrap.appendChild(chips);wrap.appendChild(area);
 items.forEach(function(it,i){var b=el("button",{cls:"chip",text:label(it)});var panel=render(it);panel.classList.add("hidden");area.appendChild(panel);
  b.addEventListener("click",function(){var show=panel.classList.contains("hidden");panel.classList.toggle("hidden",!show);b.classList.toggle("on",show);});
  chips.appendChild(b);if(i===0){panel.classList.remove("hidden");b.classList.add("on");}});return wrap;}

var host=document.getElementById("rules"),section=null,ruleEls=[];
D.rules.forEach(function(r){if(r.section!==section){section=r.section;host.appendChild(el("h2",{cls:"sec",text:section}));}
 var rid=r.superseded?r.id+"-v"+r.version:r.id;var d=el("details",{cls:"rule",id:rid});d.dataset.status=r.status;d.dataset.text=(r.id+" "+r.text).toLowerCase();
 d.appendChild(el("summary",{},[el("span",{cls:"rid",text:r.id+" (v"+r.version+(r.superseded?", "+r.superseded:"")+")"}),badge(r.status),md(r.text),el("span",{cls:"count",text:r.sources.length+" src · "+r.evidence.length+" req"})]));
 var body=el("div",{cls:"body"});var built=false;
 d.addEventListener("toggle",function(){if(d.open){if(!built){built=true;
   body.appendChild(evidenceBlock("Source lines (Read)",r.sources,sourcePanel,function(s){return s.ref.replace("src/main/java/com/northstar/claims/","…/").replace("src/main/webapp/WEB-INF/","WEB-INF/");}));
   body.appendChild(evidenceBlock("Request and response (Observed)",r.evidence,function(e){return e.kind==="live"?livePanel(e):transcriptPanel(e);},function(e){return e.kind==="live"?e.case+" step "+e.step:"transcript "+e.ref;}));}
  if(location.hash!=="#"+rid)history.replaceState(null,"","#"+rid);}});
 d.appendChild(body);host.appendChild(d);ruleEls.push(d);});

var qhost=document.getElementById("questions");
D.questions.forEach(function(q){var d=el("details",{cls:"oq rule",id:q.id});d.dataset.status="Open question";d.dataset.text=(q.id+" "+q.question).toLowerCase();
 d.appendChild(el("summary",{},[el("span",{cls:"rid",text:q.id}),badge("Open question"),md(q.question)]));
 d.appendChild(el("div",{cls:"body"},[el("div",{cls:"kv"},[el("b",{text:"Blocks"}),md(q.blocks),el("b",{text:"Owner"}),el("span",{text:q.owner}),el("b",{text:"Interim decision"}),md(q.interim),el("b",{text:"Status"}),el("span",{text:q.status})])]));
 qhost.appendChild(d);ruleEls.push(d);});

var ct=el("table");ct.appendChild(el("tr",{},["Claim","Where","Verdict","Evidence"].map(function(h){return el("th",{text:h});})));
D.claims.forEach(function(c){var verdict=c.result.split(":")[0].replace(/ only.*/,"");
 var ev=c.evidence.split(";").map(function(s){s=s.trim();var m=/^live:([a-z0-9-]+)#(\d+)$/.exec(s);if(!m)return el("code",{text:s});
  var a=el("a",{href:"#"},[el("code",{text:s})]);a.addEventListener("click",function(e){e.preventDefault();var p=livePanel({case:m[1],step:+m[2]});
   var row=a.closest("tr").nextSibling;if(row&&row.classList.contains("evrow")){row.remove();return;}
   var tr=el("tr",{cls:"evrow"},[el("td",{colspan:"4"},[p])]);a.closest("tr").after(tr);});return a;});
 ct.appendChild(el("tr",{},[el("td",{},[md(c.claim)]),el("td",{},[el("code",{text:c.where})]),el("td",{},[badge(verdict),el("div",{},[md(c.result)])]),el("td",{},ev)]));});
document.getElementById("claims").appendChild(ct);

var bar=document.getElementById("bar"),filter="All",query="";
function counts(s){return ruleEls.filter(function(e){return s==="All"||e.dataset.status===s;}).length;}
function apply(){ruleEls.forEach(function(e){var ok=(filter==="All"||e.dataset.status===filter)&&(!query||e.dataset.text.indexOf(query)>=0);e.classList.toggle("hidden",!ok);});
 document.querySelectorAll("h2.sec").forEach(function(h){var n=h.nextElementSibling,any=false;while(n&&n.tagName!=="H2"){if(!n.classList.contains("hidden"))any=true;n=n.nextElementSibling;}h.classList.toggle("hidden",!any);});}
["All","Observed","Read-only","Open question"].forEach(function(s){var b=el("button",{cls:s==="All"?"on":"",text:s+" ("+counts(s)+")"});
 b.addEventListener("click",function(){filter=s;bar.querySelectorAll("button").forEach(function(x){x.classList.remove("on");});b.classList.add("on");apply();});bar.appendChild(b);});
var search=el("input",{type:"search",placeholder:"Search rules, e.g. NaN, 119, CHK-"});
search.addEventListener("input",function(){query=search.value.toLowerCase();apply();});bar.appendChild(search);
var expand=el("button",{text:"Open all"});expand.addEventListener("click",function(){var open=expand.textContent==="Open all";
 ruleEls.forEach(function(e){if(!e.classList.contains("hidden"))e.open=open;});expand.textContent=open?"Close all":"Open all";});bar.appendChild(expand);
if(location.hash){var h=location.hash.slice(1),t=document.getElementById(h)||document.getElementById(h.replace(/-v\d+$/,""));if(t&&t.tagName==="DETAILS"){t.open=true;t.scrollIntoView();}}
})();
</script>
</body></html>
"""

if __name__ == "__main__":
    main()

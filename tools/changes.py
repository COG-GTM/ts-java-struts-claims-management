#!/usr/bin/env python3
"""Generate the impact block and the HTML twin of every change record.

For each ``docs/changes/CHG-nnn-*.md`` this rewrites the text between
``<!-- impact:begin -->`` and ``<!-- impact:end -->`` (when the record has
those markers) and writes ``docs/changes/CHG-nnn-*.html`` next to it.

The impact of CHG-nnn is computed, not written by hand:

* rule versions touched: every rule version in ``docs/specs/SPEC-*-001.md``
  whose text or citations name the record, and the versions it supersedes;
* scenarios: every ``approved_differences`` entry of ``parity/routes.json``
  for the record, with its verdict from ``parity/report.json``; a scenario
  declared with its own request block in ``payment_scenarios`` is "added",
  any other is "reclassified";
* tests: every ``@Test`` method under ``services/*/src/test`` whose javadoc
  names the record;
* files changed: the record, its HTML view, and the files of every commit
  whose message names the record (still present in the tree). Uncommitted
  changes are not counted, so run it again after the commit that names the
  record and commit the refreshed block.

Usage: python3 tools/changes.py   (make changes). The output has no timestamp
and no commit ids, so a clean checkout regenerates the same bytes.
"""

import glob
import html
import json
import os
import re
import subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CHANGES = os.path.join("docs", "changes")
BEGIN, END = "<!-- impact:begin -->", "<!-- impact:end -->"
HEADING = re.compile(r"^\*\*([A-Z]+-R\d+) \(v(\d+)(?:, superseded by v(\d+))?\)\*\* — (.*)$")
JAVADOC = re.compile(r"/\*\*((?:(?!\*/).)*)\*/\s*@Test\s+void\s+(\w+)\s*\(", re.S)
RULE_REF = re.compile(r"[A-Z]+-R\d+ \(v\d+\)")


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as stream:
        return stream.read()


def git(*args):
    return subprocess.run(["git"] + list(args), cwd=ROOT, capture_output=True, text=True,
                          check=True).stdout


def spec_rules():
    """(spec, rule id, version, superseded-by, full text) for every rule version."""
    rules = []
    for spec in sorted(glob.glob(os.path.join(ROOT, "docs", "specs", "SPEC-*-001.md"))):
        current = None
        for line in read(spec).splitlines():
            match = HEADING.match(line)
            if match:
                current = {"spec": os.path.relpath(spec, ROOT), "id": match.group(1),
                           "version": int(match.group(2)), "superseded_by": match.group(3),
                           "text": [line]}
                rules.append(current)
            elif current is not None:
                if not line.strip():
                    current = None
                else:
                    current["text"].append(line)
    for rule in rules:
        rule["text"] = "\n".join(rule["text"])
    return rules


def rule_versions(change, record, rules):
    touched = []
    for rule in rules:
        if record not in rule["text"]:
            continue
        older = [r for r in rules if r["id"] == rule["id"] and r["version"] < rule["version"]]
        for old in older:
            touched.append(dict(old, effect="superseded by v%d" % rule["version"]))
        touched.append(dict(rule, effect="added (%s)" % change))
    return touched


def scenarios(change):
    routes = json.loads(read(os.path.join("parity", "routes.json")))
    declared = routes.get("payment_scenarios", {})
    report = {(r.get("slice", "settlement"), r["scenario"]): r for r in
              json.loads(read(os.path.join("parity", "report.json")))["scenarios"]}
    out = []
    for entry in routes.get("approved_differences", []):
        if entry["change"] != change:
            continue
        name = entry["scenario"]
        slice_name = "payment" if name in declared else "settlement"
        row = report.get((slice_name, name), {})
        out.append({"scenario": name, "slice": slice_name,
                    "kind": "added" if "request" in declared.get(name, {}) else "reclassified",
                    "verdict": row.get("verdict", "not run"), "summary": entry["summary"],
                    "rules": row.get("rules", [])})
    return out


def tests(change):
    out = []
    for path in sorted(glob.glob(os.path.join(ROOT, "services", "*", "src", "test", "**", "*.java"),
                                 recursive=True)):
        for doc, method in JAVADOC.findall(read(path)):
            if change in doc:
                cited = sorted(set(RULE_REF.findall(doc)), key=lambda r: (r.split(" ")[0], r))
                out.append({"file": os.path.relpath(path, ROOT),
                            "test": "%s.%s" % (os.path.basename(path)[:-5], method),
                            "rules": cited})
    return out


def files_changed(change, own):
    files = set(own)
    log = git("log", "--all", "--grep=" + change, "--name-only", "--format=")
    files.update(line for line in log.splitlines() if line.strip() and "__pycache__" not in line)
    return sorted(f for f in files if os.path.exists(os.path.join(ROOT, f)) or f in own)


def impact_markdown(change, impact):
    out = [BEGIN, "",
           "Generated by `make changes` (tools/changes.py) from the specs, "
           "`parity/routes.json`, `parity/report.json`, the test javadocs and the git "
           "history of commits naming %s. Do not edit by hand." % change, "",
           "### Rule versions touched", "", "| Rule version | Spec | Effect |", "| --- | --- | --- |"]
    for rule in impact["rules"]:
        out.append("| %s (v%d) | `%s` | %s |" % (rule["id"], rule["version"], rule["spec"],
                                                 rule["effect"]))
    for kind, title in (("added", "Scenarios added"), ("reclassified", "Scenarios reclassified")):
        rows = [s for s in impact["scenarios"] if s["kind"] == kind]
        out += ["", "### %s (%d)" % (title, len(rows)), "",
                "| Scenario | Verdict | What the approval says |", "| --- | --- | --- |"]
        for s in rows:
            out.append("| `%s` | %s | %s |" % (s["scenario"], s["verdict"], s["summary"]))
    out += ["", "### Tests added (%d)" % len(impact["tests"]), "",
            "| Test | Rules cited | File |", "| --- | --- | --- |"]
    for t in impact["tests"]:
        out.append("| `%s` | %s | `%s` |" % (t["test"], ", ".join(t["rules"]) or "-", t["file"]))
    out += ["", "### Files changed (%d)" % len(impact["files"]), ""]
    out += ["- `%s`" % f for f in impact["files"]]
    out += ["", END]
    return "\n".join(out)


def inline(text):
    parts = re.split(r"(`[^`]*`)", text)
    out = []
    for part in parts:
        if len(part) > 1 and part.startswith("`") and part.endswith("`"):
            out.append("<code>%s</code>" % html.escape(part[1:-1]))
        else:
            out.append(html.escape(part))
    return "".join(out)


def md_to_html(text):
    """The subset of Markdown the change records use."""
    out, para, table, items = [], [], [], []

    def flush():
        if para:
            out.append("<p>%s</p>" % inline(" ".join(para)))
            del para[:]
        if items:
            out.append("<ul>%s</ul>" % "".join("<li>%s</li>" % inline(i) for i in items))
            del items[:]
        if table:
            head = "".join("<th>%s</th>" % inline(c) for c in table[0])
            body = "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % inline(c) for c in row)
                           for row in table[2:])
            out.append("<table><tr>%s</tr>%s</table>" % (head, body))
            del table[:]

    for line in text.splitlines():
        if line.startswith("<!--"):
            continue
        heading = re.match(r"^(#+) (.*)$", line)
        if heading:
            flush()
            level = min(len(heading.group(1)), 3)
            out.append("<h%d>%s</h%d>" % (level, inline(heading.group(2)), level))
        elif line.startswith("| "):
            if para or items:
                flush()
            table.append([c.strip() for c in line.strip().strip("|").split(" | ")])
        elif re.match(r"^[*-] ", line):
            if para or table:
                flush()
            items.append(line[2:])
        elif line.startswith("  ") and items:
            items[-1] += " " + line.strip()
        elif not line.strip():
            flush()
        else:
            if table:
                flush()
            para.append(line.strip())
    flush()
    return "\n".join(out)


def page(change, record, text, impact):
    data = json.dumps({"change": change, "record": record, "impact": impact},
                      sort_keys=True).replace("</", "<\\/")
    return TEMPLATE.replace("@@TITLE@@", html.escape(text.splitlines()[0].lstrip("# "))) \
        .replace("@@BODY@@", md_to_html(text.split(BEGIN)[0])) \
        .replace("@@RECORD@@", html.escape(os.path.basename(record))) \
        .replace("@@DATA@@", data)


def main():
    rules = spec_rules()
    for path in sorted(glob.glob(os.path.join(ROOT, CHANGES, "CHG-*.md"))):
        record = os.path.relpath(path, ROOT)
        change = "-".join(os.path.basename(path).split("-")[:2])
        twin = record[:-3] + ".html"
        text = read(record)
        impact = {"rules": rule_versions(change, record, rules), "scenarios": scenarios(change),
                  "tests": tests(change), "files": files_changed(change, [record, twin])}
        if BEGIN in text and END in text:
            head, rest = text.split(BEGIN, 1)
            text = head + impact_markdown(change, impact) + rest.split(END, 1)[1]
            with open(path, "w", encoding="utf-8") as stream:
                stream.write(text)
        with open(os.path.join(ROOT, twin), "w", encoding="utf-8") as stream:
            stream.write(page(change, record, text, impact))
        print("Wrote %s%s" % (twin, " and the impact block of " + record
                              if BEGIN in text else ""))


TEMPLATE = """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>@@TITLE@@</title>
<style>
body{font:20px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;margin:0;background:#fff;color:#1d2330}
main{max-width:1180px;margin:0 auto;padding:24px 32px 64px}
h1{font-size:34px;margin:8px 0} h2{font-size:27px;margin-top:36px;border-bottom:2px solid #e3e7ef;padding-bottom:4px} h3{font-size:22px}
code{background:#f2f4f8;padding:1px 5px;border-radius:4px;font-size:.9em}
table{border-collapse:collapse;width:100%;margin:10px 0} th,td{border:1px solid #d6dbe5;padding:6px 10px;text-align:left;vertical-align:top}
th{background:#f2f4f8} .sub{color:#4b5568} a{color:#1f56c2}
.badge{display:inline-block;padding:2px 10px;border-radius:12px;font-size:16px;font-weight:600}
.v-PASS{background:#e3f5e8;color:#1c6b33} .v-CHANGED{background:#fff1d6;color:#8a5300} .v-FAIL{background:#fde4e4;color:#a01c1c}
.e-added{background:#e5eefc;color:#1f4f9c} .e-superseded{background:#eceff3;color:#4b5568}
details{border:1px solid #d6dbe5;border-radius:8px;margin:8px 0;padding:6px 12px} summary{cursor:pointer;font-weight:600}
pre{background:#f7f8fb;border:1px solid #e3e7ef;padding:10px;white-space:pre-wrap;font-size:16px}
.filters button{font:inherit;font-size:17px;margin:0 6px 6px 0;padding:3px 12px;border:1px solid #c5ccd9;border-radius:16px;background:#fff;cursor:pointer}
.filters button.on{background:#1d2330;color:#fff} .hidden{display:none}
</style></head><body><main>
<div class="sub">Change record · source of truth <a href="@@RECORD@@">@@RECORD@@</a> · generated by <code>make changes</code> (tools/changes.py); do not edit</div>
@@BODY@@
<h2 id="impact">Impact (generated)</h2>
<div id="impact-view"></div>
</main>
<script>
var D=@@DATA@@;
function el(t,a,k){var e=document.createElement(t);for(var x in a||{}){if(x==="text")e.textContent=a[x];else if(x==="cls")e.className=a[x];else e.setAttribute(x,a[x]);}
(k||[]).forEach(function(c){if(c!=null)e.appendChild(typeof c==="string"?document.createTextNode(c):c);});return e;}
var up="../../";var V=document.getElementById("impact-view");var I=D.impact;
function specView(r){return up+r.spec.replace(/\\.md$/,".html")+"#"+r.id;}
V.appendChild(el("h3",{text:"Rule versions touched ("+I.rules.length+")"}));
var byId={};I.rules.forEach(function(r){(byId[r.id]=byId[r.id]||[]).push(r);});
Object.keys(byId).forEach(function(id){var vs=byId[id];var d=el("details",{open:"open"},[el("summary",{},[id+" — "+vs.map(function(r){return "v"+r.version+" "+r.effect;}).join(" · ")])]);
d.appendChild(el("div",{},["Open in the spec view: ",el("a",{href:specView(vs[0]),target:"_blank",text:vs[0].spec+"#"+id})," · tests: ",
 el("span",{text:I.tests.filter(function(t){return t.rules.some(function(x){return x.indexOf(id+" ")===0;});}).map(function(t){return t.test;}).join(", ")||"none"})," · record: ",el("a",{href:"#",text:D.record})]));
var tb=el("table",{},[el("tr",{},vs.map(function(r){return el("th",{},[el("span",{cls:"badge e-"+r.effect.split(" ")[0],text:"v"+r.version+" "+r.effect})]);}))]);
tb.appendChild(el("tr",{},vs.map(function(r){return el("td",{},[el("pre",{text:r.text})]);})));d.appendChild(tb);V.appendChild(d);});
var state="all";var rows=[];
V.appendChild(el("h3",{text:"Scenarios ("+I.scenarios.length+")"}));
var f=el("div",{cls:"filters"});[["all","All"],["added","Added"],["reclassified","Reclassified"]].forEach(function(o){var b=el("button",{cls:o[0]==="all"?"on":"",text:o[1]+" ("+(o[0]==="all"?I.scenarios.length:I.scenarios.filter(function(s){return s.kind===o[0];}).length)+")"});
b.onclick=function(){state=o[0];Array.prototype.forEach.call(f.children,function(x){x.className="";});b.className="on";rows.forEach(function(r){r.tr.classList.toggle("hidden",state!=="all"&&r.s.kind!==state);});};f.appendChild(b);});V.appendChild(f);
var st=el("table",{},[el("tr",{},["Scenario","Kind","Verdict","What the approval says"].map(function(h){return el("th",{text:h});}))]);
I.scenarios.forEach(function(s){var tr=el("tr",{},[el("td",{},[el("a",{href:up+"parity/report.html#"+s.slice+"-"+s.scenario,target:"_blank",text:s.scenario})]),el("td",{text:s.kind}),
el("td",{},[el("span",{cls:"badge v-"+s.verdict.split(" ")[0],text:s.verdict})]),el("td",{text:s.summary})]);st.appendChild(tr);rows.push({s:s,tr:tr});});V.appendChild(st);
V.appendChild(el("div",{cls:"sub",text:"Each scenario opens its expected-versus-actual comparison in parity/report.html."}));
V.appendChild(el("h3",{text:"Tests added ("+I.tests.length+")"}));
var tt=el("table",{},[el("tr",{},["Test","Rules cited"].map(function(h){return el("th",{text:h});}))]);
I.tests.forEach(function(t){tt.appendChild(el("tr",{},[el("td",{},[el("a",{href:up+t.file,target:"_blank",text:t.test})]),el("td",{text:t.rules.join(", ")||"-"})]));});V.appendChild(tt);
V.appendChild(el("h3",{text:"Files changed ("+I.files.length+")"}));
V.appendChild(el("ul",{},I.files.map(function(p){return el("li",{},[el("a",{href:up+p,target:"_blank"},[el("code",{text:p})])]);})));
</script></body></html>
"""

if __name__ == "__main__":
    main()

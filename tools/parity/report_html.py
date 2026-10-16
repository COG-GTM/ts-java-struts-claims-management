"""parity/report.html: the parity report and its approved changes as one
self-contained, interactive page (no network, opens from file://).

Built from the merged report rows plus what the repository already holds:
parity/routes.json (approved differences, preconditions, rule mapping), the
transcripts, parity/legacy/<scenario>.json (legacy answers for approved
differences without a transcript), parity/preconditions/payment/*.sql, the
docs/changes/CHG-nnn records and the rule text of docs/specs/SPEC-*.md. The
output holds no timestamp or revision, so the same inputs write the same bytes.
"""

import glob
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RULE_HEADING = re.compile(r"^\*\*((?:SETTLE|PAY)-R\d+) \(v(\d+)([^)]*)\)\*\* — (.*)$")


def _read(*parts):
    path = os.path.join(ROOT, *parts)
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def _json(*parts):
    text = _read(*parts)
    return json.loads(text) if text is not None else None


def _rules():
    """Every rule version's text, keyed "PAY-R05 (v2)"."""
    rules = {}
    for path in sorted(glob.glob(os.path.join(ROOT, "docs", "specs", "SPEC-*.md"))):
        spec = os.path.basename(path)[:-3]
        current = None
        with open(path, encoding="utf-8") as handle:
            for line in handle.read().splitlines():
                heading = RULE_HEADING.match(line)
                if heading:
                    current = "%s (v%s)" % (heading.group(1), heading.group(2))
                    rules[current] = {"spec": spec, "note": heading.group(3).strip(", "),
                                      "text": heading.group(4)}
                elif current and line.strip() and not line.startswith(("#", "|")):
                    rules[current]["text"] += "\n" + line
                else:
                    current = None
    return rules


def _rule_keys(cited, rules):
    """Expand "PAY-R05" to every version, keep "PAY-R05 (v2)" as is."""
    keys = []
    for entry in cited:
        if entry in rules:
            keys.append(entry)
        else:
            keys += sorted(key for key in rules if key.startswith(entry + " ("))
    return keys


def data(rows):
    routes = _json("parity", "routes.json") or {}
    rules = _rules()
    approvals = {entry["scenario"]: entry for entry in routes.get("approved_differences", [])}
    preconditions = dict(routes.get("payment_preconditions", {}))
    scenarios = []
    for row in rows:
        name = row["scenario"]
        approval = approvals.get(name)
        transcript = _json("transcripts", name + ".json")
        legacy = transcript or _json("parity", "legacy", name + ".json")
        precondition = row.get("precondition") or preconditions.get(name) or (
            approval or {}).get("precondition")
        cited = list(routes.get("transcript_rules", {}).get(name, []))
        cited += [rule for rule in (approval or {}).get("rules", []) if rule not in cited]
        scenarios.append({
            "scenario": name,
            "slice": name.split("_")[0],
            "description": row["description"],
            "verdict": row["verdict"],
            "difference": row["difference"],
            "change": row.get("change"),
            "record": row.get("record"),
            "legacy_source": ("transcripts/%s.json" % name) if transcript else (
                ("parity/legacy/%s.json" % name) if legacy else None),
            "legacy_setup": (legacy or {}).get("legacy_setup", []),
            "request": (legacy or {}).get("request") or (approval or {}).get("request"),
            "legacy": (legacy or {}).get("expected"),
            "approved": (approval or {}).get("expected"),
            "actual": row.get("actual"),
            "precondition": precondition,
            "precondition_sql": _read("parity", "preconditions", "payment", precondition)
            if precondition else None,
            "rules": _rule_keys(cited, rules),
        })
    changes = []
    for entry in routes.get("approved_differences", []):
        changes.append({
            "change": entry["change"],
            "record": entry["record"],
            "summary": entry["summary"],
            "scenario": entry["scenario"],
            "rules": entry.get("rules", []),
            "text": _read(entry["record"]) or "",
        })
    return {"scenarios": scenarios, "changes": changes, "rules": rules}


def write(rows, path):
    payload = json.dumps(data(rows), indent=1, sort_keys=True, ensure_ascii=False)
    payload = payload.replace("</", "<\\/")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(TEMPLATE.replace("__DATA__", payload))


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Parity and approved changes</title>
<style>
:root{--bg:#f6f7f9;--panel:#fff;--ink:#1d2330;--mute:#5d6678;--line:#dfe3ea;--pass:#1f7a4d;--passbg:#e5f4ec;--chg:#8a5a00;--chgbg:#fdf1d8;--fail:#b42318;--failbg:#fde7e5;--acc:#2f5bd3}
*{box-sizing:border-box}body{margin:0;font:14px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:var(--ink);background:var(--bg)}
header{padding:18px 24px 10px;background:var(--panel);border-bottom:1px solid var(--line)}
h1{font-size:20px;margin:0 0 4px}header p{margin:0;color:var(--mute)}
.chips{display:flex;gap:8px;margin-top:10px;flex-wrap:wrap}
.chip{padding:3px 10px;border-radius:999px;font-weight:600;font-size:12px;border:1px solid transparent}
.PASS{background:var(--passbg);color:var(--pass)}.CHANGED{background:var(--chgbg);color:var(--chg)}.FAIL{background:var(--failbg);color:var(--fail)}
nav{display:flex;gap:4px;padding:0 24px;background:var(--panel);border-bottom:1px solid var(--line)}
nav button{border:0;background:none;padding:10px 14px;font:inherit;cursor:pointer;color:var(--mute);border-bottom:2px solid transparent}
nav button.on{color:var(--ink);border-color:var(--acc);font-weight:600}
main{display:grid;grid-template-columns:minmax(380px,1fr) 1.4fr;gap:16px;padding:16px 24px}
.panel{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:14px;min-width:0}
.filters{display:flex;gap:6px;flex-wrap:wrap;align-items:center;margin-bottom:10px}
.filters button{border:1px solid var(--line);background:#fff;border-radius:6px;padding:4px 9px;cursor:pointer;font:inherit;font-size:12px}
.filters button.on{background:var(--ink);color:#fff;border-color:var(--ink)}
.filters input{flex:1;min-width:120px;padding:5px 8px;border:1px solid var(--line);border-radius:6px;font:inherit}
table{border-collapse:collapse;width:100%}th,td{text-align:left;padding:6px 8px;border-bottom:1px solid var(--line);vertical-align:top}
th{font-size:12px;color:var(--mute);font-weight:600}
tbody tr{cursor:pointer}tbody tr:hover{background:#f1f4fa}tbody tr.sel{background:#e8eefc}
code,pre{font:12px/1.4 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
pre{background:#f3f5f8;border:1px solid var(--line);border-radius:6px;padding:8px;overflow:auto;margin:6px 0;white-space:pre-wrap}
h2{font-size:16px;margin:0 0 6px}h3{font-size:13px;margin:14px 0 4px;color:var(--mute);text-transform:uppercase;letter-spacing:.04em}
.cols{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:10px}
.col{border:1px solid var(--line);border-radius:6px;padding:8px}.col h4{margin:0 0 6px;font-size:13px}
.col.legacy{border-top:3px solid #7a869a}.col.approved{border-top:3px solid var(--chg)}.col.service{border-top:3px solid var(--acc)}
.kv{display:grid;grid-template-columns:auto 1fr;gap:2px 10px;font-size:12px}.kv span:nth-child(odd){color:var(--mute)}
.diff{background:#fff6e0}.rule{border-left:3px solid var(--line);padding:4px 10px;margin:6px 0}.rule.sup{opacity:.75}
.rule b{font-family:ui-monospace,monospace}.muted{color:var(--mute)}a{color:var(--acc);cursor:pointer}
.md table{font-size:12px;margin:6px 0}.md h1{font-size:17px}.md h2{font-size:15px;margin-top:14px}
.empty{color:var(--mute);font-style:italic}
main[hidden]{display:none}.col code{word-break:break-word}
@media(max-width:900px){main{grid-template-columns:1fr}}
</style>
</head>
<body>
<header>
<h1>Parity and approved changes</h1>
<p>Generated by <code>make parity</code> (tools/parity/report_html.py) from parity/report.json, parity/routes.json, the transcripts and the change records. Click a scenario for its evidence.</p>
<div class="chips" id="chips"></div>
</header>
<nav><button id="tab-scenarios" class="on">Scenarios</button><button id="tab-changes">Changes</button></nav>
<main id="view-scenarios">
<section class="panel">
<div class="filters" id="slice-filters"></div>
<div class="filters" id="verdict-filters"></div>
<div class="filters"><input id="q" placeholder="Search scenario, rule, change"></div>
<table><thead><tr><th>Scenario</th><th>Legacy behaviour</th><th>Verdict</th></tr></thead><tbody id="rows"></tbody></table>
</section>
<section class="panel" id="detail"><p class="empty">Select a scenario.</p></section>
</main>
<main id="view-changes" hidden>
<section class="panel"><table><thead><tr><th>Change</th><th>Summary</th><th>Scenario</th></tr></thead><tbody id="change-rows"></tbody></table></section>
<section class="panel" id="change-detail"><p class="empty">Select a change.</p></section>
</main>
<script type="application/json" id="data">__DATA__</script>
<script>
const D = JSON.parse(document.getElementById('data').textContent);
const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const vclass = v => v.split(' ')[0];
const inline = t => esc(t).replace(/`([^`]+)`/g,'<code>$1</code>').replace(/\*\*([^*]+)\*\*/g,'<b>$1</b>');
const state = {slice:'all', verdict:'all', q:'', sel:null, change:null};

function chips(){
  const n = {}; D.scenarios.forEach(s => n[vclass(s.verdict)] = (n[vclass(s.verdict)]||0)+1);
  $('chips').innerHTML = `<span class="chip" style="background:#eef1f6">${D.scenarios.length} scenarios</span>` +
    ['PASS','CHANGED','FAIL'].map(v => `<span class="chip ${v}">${v} ${n[v]||0}</span>`).join('') +
    `<span class="chip" style="background:#eef1f6">${D.changes.length} approved changes</span>`;
}
function filters(){
  const slices = ['all', ...new Set(D.scenarios.map(s => s.slice))];
  $('slice-filters').innerHTML = '<span class="muted">Slice</span>' + slices.map(s => `<button data-slice="${s}" class="${state.slice===s?'on':''}">${s}</button>`).join('');
  $('verdict-filters').innerHTML = '<span class="muted">Verdict</span>' + ['all','PASS','CHANGED','FAIL'].map(v => `<button data-verdict="${v}" class="${state.verdict===v?'on':''}">${v}</button>`).join('');
}
function visible(){
  const q = state.q.toLowerCase();
  return D.scenarios.filter(s => (state.slice==='all'||s.slice===state.slice) &&
    (state.verdict==='all'||vclass(s.verdict)===state.verdict) &&
    (!q || [s.scenario,s.description,s.verdict,s.change||'',s.rules.join(' ')].join(' ').toLowerCase().includes(q)));
}
function rows(){
  const list = visible();
  $('rows').innerHTML = list.map(s => `<tr data-s="${esc(s.scenario)}" class="${state.sel===s.scenario?'sel':''}"><td><code>${esc(s.scenario)}</code></td><td>${esc(s.description)}</td><td><span class="chip ${vclass(s.verdict)}">${esc(s.verdict)}</span></td></tr>`).join('') ||
    '<tr><td colspan="3" class="empty">No scenario matches.</td></tr>';
}
function kv(obj){
  const keys = Object.keys(obj||{});
  if (!keys.length) return '<span class="empty">none</span>';
  return '<div class="kv">' + keys.sort().map(k => `<span>${esc(k)}</span><span><code>${esc(JSON.stringify(obj[k]))}</code></span>`).join('') + '</div>';
}
function answer(title, cls, a, extra){
  if (!a) return `<div class="col ${cls}"><h4>${title}</h4><p class="empty">${extra||'not recorded'}</p></div>`;
  return `<div class="col ${cls}"><h4>${title}</h4>
   <div class="kv"><span>status</span><span><code>${esc(a.status)}</code></span>
   <span>screen</span><span><code>${esc(a.screen||a.result||'')}</code></span></div>
   <h3>Business fields</h3>${kv(a.business_fields)}
   <h3>Validation errors</h3>${(a.validation_errors||[]).length?a.validation_errors.map(e=>'<div><code>'+esc(e)+'</code></div>').join(''):'<span class="empty">none</span>'}
   <h3>Database</h3>${kv(a.db_state)}${extra?'<p class="muted">'+extra+'</p>':''}</div>`;
}
function ruleBlock(key){
  const r = D.rules[key]; if (!r) return `<div class="rule"><b>${esc(key)}</b> <span class="muted">not in a spec</span></div>`;
  return `<div class="rule ${r.note?'sup':''}"><b>${esc(key)}</b> <span class="muted">${esc(r.spec)}${r.note?' · '+esc(r.note):''}</span><div>${inline(r.text).replace(/\n/g,' ')}</div></div>`;
}
function detail(){
  const s = D.scenarios.find(x => x.scenario === state.sel);
  if (!s){ $('detail').innerHTML = '<p class="empty">Select a scenario.</p>'; return; }
  const req = s.request || {};
  const act = s.actual ? {status:s.actual.status, screen:(s.actual.body||{}).screen, business_fields:(s.actual.body||{}).fields, validation_errors:(s.actual.body||{}).errors, db_state:s.actual.db_state} : null;
  $('detail').innerHTML = `<h2><code>${esc(s.scenario)}</code> <span class="chip ${vclass(s.verdict)}">${esc(s.verdict)}</span></h2>
   <p>${esc(s.description)}</p>
   ${s.difference && s.difference!=='-' ? `<p class="${vclass(s.verdict)}" style="padding:6px 8px;border-radius:6px">${esc(s.difference)}</p>`:''}
   ${s.change ? `<p>Approved by <a data-change="${esc(s.change)}">${esc(s.change)}</a> — <code>${esc(s.record)}</code></p>`:''}
   <h3>Request</h3><pre>${esc(req.method||'')} ${esc(req.path||'')}\n${esc(Object.keys(req.form||{}).sort().map(k=>k+'='+req.form[k]).join('\n'))}</pre>
   <h3>Database precondition</h3>${s.precondition ? `<p><code>parity/preconditions/payment/${esc(s.precondition)}</code> after the legacy schema.sql and seed.sql</p><pre>${esc(s.precondition_sql)}</pre>` : `<p class="muted">${s.slice==='payment' ? 'None: continues from the database the previous payment scenario left.' : 'The service fixture (legacy schema.sql and seed.sql).'}</p>`}
   ${s.legacy_setup.length ? `<p class="muted">Legacy side reached by replaying ${s.legacy_setup.map(x=>'<code>'+esc(x)+'</code>').join(', ')} from a fresh <code>make seed</code>.</p>`:''}
   <h3>Answers</h3><div class="cols">
   ${answer('Legacy', 'legacy', s.legacy, s.legacy_source ? '<code>'+esc(s.legacy_source)+'</code>' : '')}
   ${s.approved ? answer('Approved ('+esc(s.change)+')', 'approved', s.approved, '<code>parity/routes.json</code>') : ''}
   ${answer('Service', 'service', act, act ? '' : 'Service response not kept by this slice script; verdict only.')}
   </div>
   <h3>Rules</h3>${s.rules.length ? s.rules.map(ruleBlock).join('') : '<p class="empty">No rule mapped.</p>'}`;
}
function md(text){
  const lines = text.split('\n'); let out = '', list = false, table = [];
  const flushTable = () => { if (!table.length) return; const cells = r => r.replace(/^\||\|$/g,'').split('|').map(c=>c.trim());
    out += '<table><thead><tr>' + cells(table[0]).map(c=>'<th>'+inline(c)+'</th>').join('') + '</tr></thead><tbody>' +
      table.slice(2).map(r => '<tr>' + cells(r).map(c=>'<td>'+inline(c)+'</td>').join('') + '</tr>').join('') + '</tbody></table>'; table = []; };
  let para = [];
  const flushPara = () => { if (para.length){ out += '<p>' + inline(para.join(' ')) + '</p>'; para = []; } };
  for (const line of lines){
    if (line.startsWith('|')){ flushPara(); if (list){out+='</ul>';list=false;} table.push(line); continue; } else flushTable();
    let m;
    if ((m = line.match(/^(#{1,3}) (.*)/))){ flushPara(); if (list){out+='</ul>';list=false;} out += `<h${m[1].length}>${inline(m[2])}</h${m[1].length}>`; }
    else if ((m = line.match(/^\* (.*)/))){ flushPara(); if (!list){out+='<ul>';list=true;} out += '<li>' + inline(m[1]); }
    else if (/^\s+\S/.test(line) && list){ out += ' ' + inline(line.trim()); }
    else if (!line.trim()){ flushPara(); if (list){out+='</ul>';list=false;} }
    else { if (list){out+='</ul>';list=false;} para.push(line); }
  }
  flushPara(); flushTable(); if (list) out += '</ul>'; return out;
}
function changes(){
  $('change-rows').innerHTML = D.changes.map(c => `<tr data-c="${esc(c.change)}" class="${state.change===c.change?'sel':''}"><td><b>${esc(c.change)}</b></td><td>${esc(c.summary)}</td><td><code>${esc(c.scenario)}</code></td></tr>`).join('');
  const c = D.changes.find(x => x.change === state.change);
  if (!c){ $('change-detail').innerHTML = '<p class="empty">Select a change.</p>'; return; }
  const ids = [...new Set(c.rules.map(r => r.replace(/ \(v\d+\)$/,'')))];
  const versions = Object.keys(D.rules).filter(k => ids.some(id => k.startsWith(id+' ('))).sort();
  const s = D.scenarios.find(x => x.scenario === c.scenario);
  $('change-detail').innerHTML = `<h2>${esc(c.change)} <span class="chip CHANGED">${s?esc(s.verdict):'not replayed'}</span></h2><p>${esc(c.summary)}</p>
   <p>Scenario <a data-goto="${esc(c.scenario)}"><code>${esc(c.scenario)}</code></a> · record <code>${esc(c.record)}</code></p>
   <h3>Rule versions</h3>${versions.map(ruleBlock).join('')}
   <h3>Change record</h3><div class="md">${md(c.text)}</div>`;
}
function show(tab){
  $('view-scenarios').hidden = tab!=='scenarios'; $('view-changes').hidden = tab!=='changes';
  $('tab-scenarios').classList.toggle('on', tab==='scenarios'); $('tab-changes').classList.toggle('on', tab==='changes');
}
function route(){
  const h = decodeURIComponent(location.hash.slice(1)); let m;
  if ((m = h.match(/^change=(.+)/))){ state.change = m[1]; show('changes'); changes(); }
  else { if ((m = h.match(/^scenario=(.+)/))) state.sel = m[1]; show('scenarios'); rows(); detail(); }
}
document.addEventListener('click', e => {
  const t = e.target.closest('[data-slice],[data-verdict],[data-s],[data-c],[data-change],[data-goto]'); if (!t) return;
  if (t.dataset.slice){ state.slice = t.dataset.slice; filters(); rows(); }
  else if (t.dataset.verdict){ state.verdict = t.dataset.verdict; filters(); rows(); }
  else if (t.dataset.s){ go('scenario=' + t.dataset.s); }
  else if (t.dataset.c){ go('change=' + t.dataset.c); }
  else if (t.dataset.change){ go('change=' + t.dataset.change); }
  else if (t.dataset.goto){ go('scenario=' + t.dataset.goto); }
});
function go(hash){ history.replaceState(null, '', '#' + hash); route(); }
$('q').addEventListener('input', e => { state.q = e.target.value; rows(); });
$('tab-scenarios').onclick = () => go(state.sel ? 'scenario=' + state.sel : '');
$('tab-changes').onclick = () => go('change=' + (state.change || (D.changes[D.changes.length-1]||{}).change || ''));
window.addEventListener('hashchange', route);
chips(); filters(); rows(); changes(); route();
</script>
</body>
</html>
"""

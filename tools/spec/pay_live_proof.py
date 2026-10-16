"""Drive the running legacy app (Jetty, port 8080) through the payment slice and
record every request/response as evidence for SPEC-PAY-001.

Usage: make seed && make run, then  python3 tools/spec/pay_live_proof.py
Writes docs/specs/SPEC-PAY-001-live.json. Read-only on the repo; it only talks HTTP.
"""
import http.cookiejar, json, re, sys, urllib.parse, urllib.request

BASE = "http://localhost:8080/claims"
OUT = "docs/specs/SPEC-PAY-001-live.json"

jar = http.cookiejar.CookieJar()

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect())
steps = []

def call(step_id, title, method, path, form=None, snippets=()):
    form = form or {}
    data = urllib.parse.urlencode(form)
    url = BASE + path
    if method == "GET" and data:
        url += "?" + data
    req = urllib.request.Request(url, data=data.encode() if method == "POST" else None, method=method)
    if method == "POST":
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        resp = opener.open(req, timeout=30)
        status, body, location = resp.status, resp.read().decode("utf-8", "replace"), None
    except urllib.error.HTTPError as e:
        status, body, location = e.code, e.read().decode("utf-8", "replace"), e.headers.get("Location")
    view = re.search(r"<!-- ns:view ([^ ]+) ", body)
    if location:
        result = "redirect " + location
    elif "Claims System Error" in body:
        ref = re.search(r"Reference status:\s*([^<]*)", body)
        result = "error.jsp (Reference status: %s)" % (ref.group(1) if ref else "?")
    elif view:
        result = "forward " + view.group(1)
    else:
        result = "other"
    fields = dict(re.findall(r'<span id="f_([A-Za-z0-9_]+)">([^<]*)</span>', body))
    found = {}
    for label, pattern in snippets:
        m = re.search(pattern, body, re.S)
        found[label] = m.group(0).strip() if m else None
    rec = {"id": step_id, "title": title, "request": {"method": method, "path": "/claims" + path, "form": form},
           "response": {"status": status, "result": result, "fields": fields, "snippets": found}}
    steps.append(rec)
    print("%-6s %s %-45s -> %s %s %s" % (step_id, method, path + (("?" + data) if method == "GET" and data else ""),
                                          status, result, json.dumps(fields)[:160]))
    return rec

AUDIT = [("screenAudit", r'<table class="screen-audit".*?</table>')]
LINKS = [("linkParagraphs", r'(<p><a href="detail\.do[^"]*">[^<]*</a> \|\s*<a href="remittance\.do[^"]*">[^<]*</a></p>\s*){2}')]
LINKS1 = [("detailLink", r'<a href="detail\.do[^"]*">'), ("remittanceLink", r'<a href="remittance\.do[^"]*">')]
PAYEE = [("payeeCell", r'<th>Payee</th><td>[^<]*</td>'), ("methodCell", r'<th>Method</th><td>[^<]*</td>')]
ROWS = [("tableRows", r'<table class="(?:detail|history|remittance)"[^>]*>.*?</table>')]

# --- unauthenticated ---
call("L01", "History without a session", "GET", "/payment/history.do")
call("L02", "Issue without a session", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Anon", "amount": "1", "paymentMethod": "CHECK"})
call("L03", "Login as supervisor", "POST", "/login.do", {"username": "supervisor", "password": "supervisor"})

# --- fresh-seed reads ---
call("L04", "History, no parameters, fresh seed", "GET", "/payment/history.do", snippets=LINKS + LINKS1 + AUDIT)
call("L05", "History for claim 1 (seeded payment 1)", "GET", "/payment/history.do", {"claimId": "1"}, LINKS1)
call("L06", "History, claimId=abc falls back to 119", "GET", "/payment/history.do", {"claimId": "abc"}, LINKS1)
call("L07", "Detail, no parameters, fresh seed (default 61 absent)", "GET", "/payment/detail.do")
call("L08", "Detail for missing payment 9999", "GET", "/payment/detail.do", {"paymentId": "9999"})
call("L09", "Detail for seeded payment 1", "GET", "/payment/detail.do", {"paymentId": "1"},
     PAYEE + AUDIT)
call("L10", "Detail, paymentId=abc falls back to 61 (absent)", "GET", "/payment/detail.do", {"paymentId": "abc"})
call("L11", "Remittance, no parameters, fresh seed", "GET", "/payment/remittance.do", snippets=AUDIT)
call("L12", "Remittance for claim 1", "GET", "/payment/remittance.do", {"claimId": "1"},
     [("payeeCells", r'(?s)<th>Payee</th>.*?</table>')])
call("L13", "Remittance, claimId=abc falls back to 119", "GET", "/payment/remittance.do", {"claimId": "abc"})

# --- issue: happy path and fallbacks (settlement 118 amount 1118, 119 amount 0, 120 amount 0) ---
call("L14", "Issue, no amount: falls back to settlement amount", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Live Payee", "paymentMethod": "wire"}, AUDIT)
call("L15", "Issue, amount=abc: falls back to settlement amount", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Second Payee", "amount": "abc", "paymentMethod": "CHECK"})
call("L16", "Issue, claimId=abc falls back to 119; amount 12.345", "POST", "/payment/issue.do",
     {"claimId": "abc", "payeeName": "Third Payee", "amount": "12.345", "paymentMethod": "CHECK"})
call("L17", "Issue, claim 120 (settlement amount 0), no amount", "POST", "/payment/issue.do",
     {"claimId": "120", "payeeName": "Zero Payee", "paymentMethod": "CHECK"})
# --- issue: failures (no id consumed) ---
call("L18", "Issue for claim without a settlement", "POST", "/payment/issue.do",
     {"claimId": "9999", "payeeName": "Nobody", "amount": "1", "paymentMethod": "CHECK"})
call("L19", "Issue without payeeName", "POST", "/payment/issue.do",
     {"claimId": "118", "amount": "1", "paymentMethod": "CHECK"})
call("L20", "Issue without paymentMethod", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "No Method", "amount": "1"})
call("L21", "Issue with 101-char payeeName", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "P" * 101, "amount": "1", "paymentMethod": "CHECK"})
call("L22", "Issue with 21-char paymentMethod", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Long Method", "amount": "1", "paymentMethod": "M" * 21})
call("L23", "Issue with empty payeeName", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "", "amount": "1", "paymentMethod": "CHECK"})
call("L24", "Remittance 118 after failures (count unchanged)", "GET", "/payment/remittance.do", {"claimId": "118"})
# --- issue: parsing edge cases ---
call("L25", "Issue, negative amount", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Negative Payee", "amount": "-5", "paymentMethod": "CHECK"})
call("L26", "Issue by GET", "GET", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Get Payee", "amount": "7", "paymentMethod": "CHECK"})
call("L27", "Issue, amount=1e3", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Sci Payee", "amount": "1e3", "paymentMethod": "CHECK"})
call("L28", "Issue, amount=1,000 (unparseable, falls back)", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Comma Payee", "amount": "1,000", "paymentMethod": "CHECK"})
call("L29", "Issue, amount with surrounding spaces", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Space Payee", "amount": " 5 ", "paymentMethod": "CHECK"})
call("L30", "Issue, client checkNumber ignored, 100-char payee and 20-char method accepted", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Q" * 100, "amount": "1", "paymentMethod": "N" * 20, "checkNumber": "MINE-1"})
call("L31", "Issue, payee with HTML characters", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "O'Brien <b>&</b> \"Co\"", "amount": "2", "paymentMethod": "ach"})
call("L32", "Issue, amount=NaN", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "NaN Payee", "amount": "NaN", "paymentMethod": "CHECK"})
# --- reads after issuing ---
call("L33", "Detail of payment 61 (issued in L14, method wire)", "GET", "/payment/detail.do", {"paymentId": "61"}, PAYEE)
call("L34", "Detail of payment 72 (HTML payee, method ach)", "GET", "/payment/detail.do", {"paymentId": "72"}, PAYEE)
call("L35", "Detail, no parameters, now that 61 exists", "GET", "/payment/detail.do")
call("L36", "History for claim 118 (ordered by payment id)", "GET", "/payment/history.do", {"claimId": "118"},
     LINKS1 + [("rowOrder", r'(?s)<th>Check number</th>.*?</table>')])
call("L37", "History, no parameters, after L16 (claim 119)", "GET", "/payment/history.do", snippets=LINKS1)
call("L38", "History, claimId=abc after L16", "GET", "/payment/history.do", {"claimId": "abc"})
call("L39", "Remittance for claim 118", "GET", "/payment/remittance.do", {"claimId": "118"},
     [("rows", r'(?s)<th>Check number</th>.*?</table>')])
call("L40", "Remittance for claim 119", "GET", "/payment/remittance.do", {"claimId": "119"})
call("L41", "Remittance for claim 120 (zero amount payment)", "GET", "/payment/remittance.do", {"claimId": "120"})
# --- latest settlement wins: add a second settlement for claim 118 then issue without amount ---
call("L42", "Settlement save for claim 118 (creates a newer settlement)", "POST", "/settlement/save.do",
     {"claimId": "118", "coveredAmount": "3000", "deductible": "250", "depreciation": "0"})
call("L43", "Issue for claim 118, no amount, after newer settlement", "POST", "/payment/issue.do",
     {"claimId": "118", "payeeName": "Latest Payee", "paymentMethod": "CHECK"})
call("L44", "Detail of payment 74 (amount from newer settlement)", "GET", "/payment/detail.do", {"paymentId": "74"}, PAYEE)
call("L45", "Detail of payment 73 (amount NaN)", "GET", "/payment/detail.do", {"paymentId": "73"}, PAYEE)
call("L46", "Detail of payment 65 (empty payee)", "GET", "/payment/detail.do", {"paymentId": "65"}, PAYEE)
call("L47", "Remittance for claim 118 at end of run", "GET", "/payment/remittance.do", {"claimId": "118"})

json.dump({"base": BASE, "note": "Captured by tools/spec/pay_live_proof.py against make seed && make run (fresh seed, Java 11).",
           "steps": steps}, open(OUT, "w"), indent=1)
print("wrote", OUT, len(steps), "steps")

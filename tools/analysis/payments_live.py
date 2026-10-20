#!/usr/bin/env python3
"""Live probes of the legacy payment slice (/payment/*.do) against a running
Jetty on localhost:8080 started from a fresh `make seed`.

Writes docs/analysis/payments-live.json: every step's exact request and the
observed response (HTTP status, Location, ns:view forward, <ns:field> values,
whether error.jsp rendered, and the link hrefs on the page). Read-only for the
legacy code; it only drives the HTTP endpoints. Ids are max+1, so rerun only
from a fresh `make seed`.
"""
import concurrent.futures
import http.cookiejar
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"
OUT = "docs/analysis/payments-live.json"
FIELD_RE = re.compile(r'<span id="f_([^"]+)">(.*?)</span>', re.S)
VIEW_RE = re.compile(r"<!--\s*ns:view\s+([^ ]+)\s*-->")
HREF_RE = re.compile(r'href="([^"]*(?:payment|settlement)/[^"]*)"')
ERR_RE = re.compile(r"Reference status: ([^<]*)<")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def opener():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect)


def call(op, method, path, form=None):
    data = None
    url = BASE + path
    if form is not None and method == "POST":
        data = urllib.parse.urlencode(form).encode()
    elif form:
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode(form)
    req = urllib.request.Request(url, data=data, method=method)
    try:
        r = op.open(req, timeout=30)
        code, headers, body = r.getcode(), r.headers, r.read().decode()
    except urllib.error.HTTPError as e:
        code, headers, body = e.code, e.headers, e.read().decode()
    view = VIEW_RE.search(body)
    err = ERR_RE.search(body)
    loc = headers.get("Location")
    return {
        "status": code,
        "location": re.sub(r";jsessionid=[^?]+", "", loc) if loc else None,
        "forward": view.group(1) if view else None,
        "error_page": "Claims System Error" in body,
        "error_reference_status": err.group(1).strip() if err else None,
        "fields": {k: re.sub(r"\s+", " ", v).strip() for k, v in FIELD_RE.findall(body)},
        "links": sorted(set(HREF_RE.findall(body))),
        "link_count_detail61": body.count('href="detail.do?paymentId=61"'),
    }


STEPS = []


def step(op, sid, purpose, method, path, form=None):
    resp = call(op, method, path, form)
    STEPS.append({"id": sid, "purpose": purpose,
                  "request": {"method": method, "path": path, "form": form or {}},
                  "response": resp})
    print(sid, method, path, form or "", "->", resp["status"], resp["forward"],
          "ERROR" if resp["error_page"] else "", json.dumps(resp["fields"])[:200])
    return resp


def main():
    anon = opener()
    step(anon, "L01", "Unauthenticated request is redirected", "GET", "/claims/payment/history.do")
    op = opener()
    step(op, "L02", "Sign in", "POST", "/claims/login.do", {"username": "supervisor", "password": "supervisor"})
    step(op, "L03", "Nav link: history with no claimId (fresh seed)", "GET", "/claims/payment/history.do")
    step(op, "L04", "History for a seeded claim", "GET", "/claims/payment/history.do?claimId=2")
    step(op, "L05", "History detail link target with no row 61 yet", "GET", "/claims/payment/detail.do?paymentId=61")
    step(op, "L06", "Detail with no paymentId (fresh seed)", "GET", "/claims/payment/detail.do")
    step(op, "L07", "Detail for a seeded payment", "GET", "/claims/payment/detail.do?paymentId=2")
    step(op, "L08", "Remittance for a seeded claim", "GET", "/claims/payment/remittance.do?claimId=2")
    step(op, "L09", "Remittance with no claimId (fresh seed)", "GET", "/claims/payment/remittance.do")
    step(op, "L10", "Issue by GET, no amount: falls back to seeded settlement 119 amount", "GET",
         "/claims/payment/issue.do", {"claimId": "119", "payeeName": "Get Payee", "paymentMethod": "CHECK"})
    step(op, "L11", "Issue by POST, transcript inputs plus a checkNumber", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "payeeName": "Reserved Claimant", "amount": "1000.00",
          "paymentMethod": "CHECK", "checkNumber": "MY-CHECK-1"})
    step(op, "L12", "Negative amount", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "payeeName": "Neg", "amount": "-50", "paymentMethod": "CHECK"})
    step(op, "L13", "Sub-cent amount", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "payeeName": "Half", "amount": "1.005", "paymentMethod": "CHECK"})
    step(op, "L14", "Amount above the 1000 policy limit of claim 119", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "payeeName": "Big", "amount": "250000", "paymentMethod": "CHECK"})
    step(op, "L15", "Unparsable amount", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "payeeName": "Bad", "amount": "abc", "paymentMethod": "CHECK"})
    step(op, "L16", "Free-text lower-case payment method", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "payeeName": "Wire", "amount": "10", "paymentMethod": "wire"})
    step(op, "L17", "Missing payeeName", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "amount": "10", "paymentMethod": "CHECK"})
    step(op, "L18", "Missing paymentMethod", "POST", "/claims/payment/issue.do",
         {"claimId": "119", "payeeName": "NoMethod", "amount": "10"})
    step(op, "L19", "Claim with no settlement", "POST", "/claims/payment/issue.do",
         {"claimId": "999", "payeeName": "Ghost", "amount": "10", "paymentMethod": "CHECK"})
    step(op, "L20", "Unparsable claimId", "POST", "/claims/payment/issue.do",
         {"claimId": "abc", "payeeName": "Default", "amount": "5", "paymentMethod": "CHECK"})
    step(op, "L21", "Issue against a CLOSED claim (120) with a seeded zero settlement", "POST",
         "/claims/payment/issue.do",
         {"claimId": "120", "payeeName": "Closed claim", "amount": "75", "paymentMethod": "CHECK"})
    step(op, "L22", "Save a new settlement for claim 119 (1000.00)", "POST", "/claims/settlement/save.do",
         {"claimId": "119", "coveredAmount": "5000.00", "deductible": "500.00", "depreciation": "0.00"})
    step(op, "L23", "Issue with no amount after the save: newest settlement amount", "POST",
         "/claims/payment/issue.do", {"claimId": "119", "payeeName": "After save", "paymentMethod": "CHECK"})
    step(op, "L24", "History for claim 119 after the issues", "GET", "/claims/payment/history.do?claimId=119")
    step(op, "L25", "Detail with no paymentId now resolves 61", "GET", "/claims/payment/detail.do")
    step(op, "L26", "Detail of the sub-cent payment", "GET", "/claims/payment/detail.do?paymentId=64")
    step(op, "L27", "Remittance for claim 119 after the issues", "GET", "/claims/payment/remittance.do?claimId=119")
    # Concurrency: eight simultaneous issues; ids come from max+1 in a separate connection.
    forms = [{"claimId": "2", "payeeName": "Race %d" % i, "amount": "1", "paymentMethod": "CHECK"}
             for i in range(8)]
    with concurrent.futures.ThreadPoolExecutor(8) as pool:
        results = list(pool.map(lambda f: call(op, "POST", "/claims/payment/issue.do", f), forms))
    STEPS.append({"id": "L28", "purpose": "Eight concurrent issues for claim 2",
                  "request": {"method": "POST", "path": "/claims/payment/issue.do", "form": forms[0],
                              "note": "8 parallel requests, payeeName Race 0..7"},
                  "response": {"summary": {
                      "issued": sorted(r["fields"].get("checkNumber") for r in results if not r["error_page"]),
                      "error_pages": sum(1 for r in results if r["error_page"])},
                      "responses": results}})
    print("L28", STEPS[-1]["response"]["summary"])
    step(op, "L29", "History for claim 2 after the race", "GET", "/claims/payment/history.do?claimId=2")
    with open(OUT, "w") as fh:
        json.dump({"base": BASE, "precondition": "fresh make seed, then make run", "steps": STEPS},
                  fh, indent=1, sort_keys=True)
        fh.write("\n")


if __name__ == "__main__":
    sys.exit(main())

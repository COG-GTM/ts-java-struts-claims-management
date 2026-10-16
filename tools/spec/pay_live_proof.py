#!/usr/bin/env python3
"""Proves SPEC-PAY-001 against the running legacy application.

Resets the database to the seed (as `make capture` does), starts Jetty on 8080,
sends a fixed list of requests, stops Jetty, reads the PAYMENT and SETTLEMENT
rows read-only with tools/spec/PayDbRead.java, and writes everything to
docs/specs/SPEC-PAY-001-live.json. Each step is quoted by id (L01, L02, ...)
from the spec. Re-running from the seed writes the same bytes.

Passwords sent to /login.do are recorded as "***".

Usage: python3 tools/spec/pay_live_proof.py           (make payment-spec-proof)
       python3 tools/spec/pay_live_proof.py --check   (make payment-spec-check)
--check replays the same requests but compares the result with the committed
file instead of overwriting it, and exits 1 when legacy behaviour moved.
"""

import http.cookiejar
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(ROOT, "tools", "capture"))
sys.dont_write_bytecode = True
import capture  # noqa: E402  (reuses reset_database, start_server, fields, result)

OUTPUT = os.path.join(ROOT, "docs", "specs", "SPEC-PAY-001-live.json")
HSQLDB = os.path.expanduser(
    "~/.m2/repository/org/hsqldb/hsqldb/2.7.3/hsqldb-2.7.3.jar")
LINK_RE = re.compile(r'<a href="((?:detail|remittance)\.do[^"]*)"')
TAG_RE = re.compile(r"<[^>]+>")

PAYEE = "Reserved Claimant"


def issue(**form):
    base = {"claimId": "119", "payeeName": PAYEE, "paymentMethod": "CHECK"}
    base.update(form)
    return {k: v for k, v in base.items() if v is not None}


# (id, actor, method, path, form, purpose)
STEPS = [
    ("L01", None, "GET", "/payment/history.do", {},
     "No session: the filter redirects to login"),
    ("L02", "supervisor", "POST", "/login.do",
     {"username": "supervisor", "password": "supervisor"}, "Sign in"),
    ("L03", "supervisor", "GET", "/payment/remittance.do?claimId=119", {},
     "Seed state: claim 119 has no payments"),
    ("L04", "supervisor", "POST", "/payment/issue.do", issue(),
     "Amount omitted on the seed: falls back to settlement 119 (0)"),
    ("L05", "supervisor", "POST", "/settlement/save.do",
     {"claimId": "119", "coveredAmount": "5000.00", "deductible": "500.00",
      "depreciation": "0.00"},
     "Setup: save a newer settlement for claim 119 (1000.00), as capture does"),
    ("L06", "supervisor", "POST", "/payment/issue.do", issue(),
     "Amount omitted: falls back to the newest settlement (1000.00)"),
    ("L07", "supervisor", "POST", "/payment/issue.do", issue(amount="abc"),
     "Unparseable amount: falls back to the newest settlement"),
    ("L08", "supervisor", "POST", "/payment/issue.do", issue(amount="-250.00"),
     "Negative amount is accepted"),
    ("L09", "supervisor", "POST", "/payment/issue.do", issue(amount="1.005"),
     "Half-cent amount is stored unrounded and displayed with %.2f"),
    ("L10", "supervisor", "POST", "/payment/issue.do", issue(amount="5000.00"),
     "Amount above the settlement is accepted"),
    ("L11", "supervisor", "POST", "/payment/issue.do", issue(amount="NaN"),
     "NaN parses as a double and is accepted"),
    ("L12", "supervisor", "POST", "/payment/issue.do", issue(amount="1e3"),
     "Exponent notation parses"),
    ("L13", "supervisor", "POST", "/payment/issue.do",
     issue(amount="10.00", checkNumber="MY-CHECK-1"),
     "checkNumber request parameter is ignored"),
    ("L14", "supervisor", "POST", "/payment/issue.do",
     issue(amount="10.00", paymentMethod="check"),
     "paymentMethod is stored as sent (not upper-cased)"),
    ("L15", "supervisor", "POST", "/payment/issue.do",
     issue(amount="10.00", paymentMethod=None),
     "paymentMethod omitted: NOT NULL insert failure"),
    ("L16", "supervisor", "POST", "/payment/issue.do",
     issue(amount="10.00", payeeName=None),
     "payeeName omitted: NOT NULL insert failure"),
    ("L17", "supervisor", "POST", "/payment/issue.do",
     issue(amount="10.00", paymentMethod="ELECTRONIC-FUNDS-XFER"),
     "paymentMethod longer than VARCHAR(20): insert failure"),
    ("L18", "supervisor", "POST", "/payment/issue.do",
     issue(claimId="9999", amount="10.00"),
     "Claim with no settlement row: null settlement"),
    ("L19", "supervisor", "POST", "/payment/issue.do",
     issue(claimId=None, amount="11.00"), "claimId omitted: 119"),
    ("L20", "supervisor", "POST", "/payment/issue.do",
     issue(claimId="abc", amount="12.00"), "claimId unparseable: 119"),
    ("L21", "supervisor", "GET",
     "/payment/issue.do?claimId=1&payeeName=Get+Payee&amount=5&paymentMethod=CHECK",
     {}, "A GET also issues a payment"),
    ("L22", "supervisor", "POST", "/payment/issue.do",
     issue(claimId="120", amount="20.00"),
     "Claim 120 (status CLOSED) is paid; settlement 120 is linked"),
    ("L23", "supervisor", "POST", "/payment/issue.do",
     issue(claimId="120", amount="1.00", payeeName="<b>A&B</b>"),
     "Payee text with markup is stored as sent"),
    ("L24", "supervisor", "GET", "/payment/history.do", {},
     "History with no claimId: claim 119, issue order, fixed detail link"),
    ("L25", "supervisor", "GET", "/payment/history.do?claimId=1", {},
     "History for claim 1: seeded and new payments"),
    ("L26", "supervisor", "GET", "/payment/history.do?claimId=9999", {},
     "History for a claim with no payments"),
    ("L27", "supervisor", "GET", "/payment/detail.do?paymentId=66", {},
     "Detail of one payment"),
    ("L28", "supervisor", "GET", "/payment/detail.do", {},
     "Detail with no paymentId: payment 61"),
    ("L29", "supervisor", "GET", "/payment/detail.do?paymentId=9999", {},
     "Detail of a payment that does not exist"),
    ("L30", "supervisor", "GET", "/payment/detail.do?paymentId=75", {},
     "Detail shows payee markup escaped"),
    ("L31", "supervisor", "GET", "/payment/detail.do?paymentId=70", {},
     "Detail shows the lower-case method"),
    ("L32", "supervisor", "GET", "/payment/remittance.do", {},
     "Remittance with no claimId: claim 119 (total includes NaN)"),
    ("L33", "supervisor", "GET", "/payment/remittance.do?claimId=1", {},
     "Remittance total is the sum of stored amounts"),
    ("L34", "supervisor", "GET", "/payment/remittance.do?claimId=9999", {},
     "Remittance for a claim with no payments"),
    ("L35", "adjuster7", "POST", "/login.do",
     {"username": "adjuster7", "password": "legacy7"}, "Sign in as an adjuster"),
    ("L36", "adjuster7", "POST", "/payment/issue.do",
     issue(claimId="120", amount="2.00"), "An adjuster can issue a payment"),
    ("L37", "adjuster7", "GET", "/payment/remittance.do?claimId=120", {},
     "Remittance for claim 120 after the adjuster's payment"),
    ("L38", None, "POST", "/payment/issue.do",
     issue(claimId="120", amount="3.00"),
     "No session: issue redirects to login and inserts nothing"),
    ("L39", None, "GET", "/payment/detail.do?paymentId=61", {},
     "No session: detail redirects to login"),
    ("L40", None, "GET", "/payment/remittance.do?claimId=119", {},
     "No session: remittance redirects to login"),
    ("L41", "supervisor", "GET", "/payment/detail.do?paymentId=abc", {},
     "Detail with an unparseable paymentId: payment 61"),
]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def send(opener, method, path, form):
    data = urllib.parse.urlencode(form).encode("utf-8") if form else None
    req = urllib.request.Request(capture.BASE + path, data=data, method=method)
    try:
        response = opener.open(req, timeout=20)
        return response.getcode(), response.headers.get("Location"), \
            response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get("Location"), \
            error.read().decode("utf-8")


def excerpt(body):
    """The business part of the page as plain text lines (no nav, no audit)."""
    start = body.find("<h2>")
    if start < 0:
        start = body.find('<table class="error"')
    end = body.find('<table class="screen-audit"')
    chunk = body[max(start, 0): end if end > start else len(body)]
    chunk = re.sub(r"</(tr|h2|p|h1)>", "\n", chunk)
    lines = [re.sub(r"\s+", " ", TAG_RE.sub(" ", line)).strip()
             for line in chunk.splitlines()]
    import html
    return [html.unescape(line) for line in lines if line]


def raw_markup(body, needle):
    index = body.find(needle)
    return body[index: index + 120] if index >= 0 else ""


def recorded(form):
    return {k: ("***" if k == "password" else v) for k, v in form.items()}


def main():
    check = "--check" in sys.argv[1:]
    capture.reset_database()
    process = capture.start_server()
    steps = []
    try:
        openers = {}
        for step_id, actor, method, path, form, purpose in STEPS:
            key = actor or "anonymous"
            if key not in openers:
                handlers = [urllib.request.HTTPCookieProcessor(
                    http.cookiejar.CookieJar()), NoRedirect()]
                openers[key] = urllib.request.build_opener(*handlers)
            code, location, body = send(openers[key], method, path, form)
            if location:
                location = re.sub(r";jsessionid=[^?]+", "", location)
                location = re.sub(r"^https?://[^/]+", "", location)
            row = {
                "id": step_id, "purpose": purpose, "actor": key,
                "request": {"method": method, "path": "/claims" + path,
                            "form": recorded(form)},
                "response": {
                    "status": code, "location": location,
                    "result": capture.result(body, code, location),
                    "business_fields": capture.fields(body),
                    "validation_errors": capture.ERROR_RE.findall(body),
                    "links": LINK_RE.findall(body),
                    "text": excerpt(body) if code == 200 else [],
                },
            }
            if step_id == "L30":
                row["response"]["payee_markup"] = raw_markup(
                    body, "<td>&lt;b&gt;")
            steps.append(row)
            print("%s %s %s -> %s %s" % (step_id, method, path, code,
                                         row["response"]["result"]))
    finally:
        process.terminate()
        process.wait(timeout=30)
    db = subprocess.check_output(
        ["java", "-cp", HSQLDB, os.path.join(HERE, "PayDbRead.java"),
         os.path.join(ROOT, "target", "db", "northstar")], cwd=ROOT)
    out = {
        "description": "Live proof of SPEC-PAY-001 against the legacy app "
                       "(make payment-spec-proof), from a fresh make seed.",
        "base_url": capture.BASE,
        "steps": steps,
        "db_after": json.loads(db.decode("utf-8")),
    }
    text = json.dumps(out, indent=2, sort_keys=True) + "\n"
    if check:
        with open(OUTPUT, encoding="utf-8") as handle:
            committed = json.load(handle)
        moved = [s["id"] for s, c in zip(out["steps"], committed["steps"]) if s != c]
        if len(out["steps"]) != len(committed["steps"]):
            moved.append("step count")
        if out["db_after"] != committed["db_after"]:
            moved.append("db_after")
        if moved:
            raise SystemExit("legacy payment behaviour differs from %s: %s"
                             % (os.path.relpath(OUTPUT, ROOT), ", ".join(moved)))
        print("%s matches the legacy app (%d steps)"
              % (os.path.relpath(OUTPUT, ROOT), len(out["steps"])))
        return
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        handle.write(text)
    print("wrote %s (%d steps)" % (os.path.relpath(OUTPUT, ROOT), len(steps)))


if __name__ == "__main__":
    main()

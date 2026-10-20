#!/usr/bin/env python3
"""Re-runnable live proof for docs/specs/SPEC-PAY-001.md.

Every case runs against the real legacy app (mvn jetty:run, port 8080) on
its own database: the freshly seeded database (make seed), plus the case's
precondition SQL, applied while Jetty is stopped. The case then logs in as
its actor, sends its requests, stops Jetty and reads the PAYMENT rows it
names straight from the HSQLDB file. Nothing depends on the order the cases
run in.

  python3 tools/spec/payment_proof.py            # rewrite the evidence file
  python3 tools/spec/payment_proof.py --check    # re-run, fail on any change
  python3 tools/spec/payment_proof.py --case ID  # run one case, print it

Writes docs/specs/SPEC-PAY-001-proof.json. Never touches transcripts/.
"""

import argparse
import glob
import html
import http.cookiejar
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BASE = "http://localhost:8080"
CONTEXT = "/claims"
DB_DIR = os.path.join(ROOT, "target", "db")
DB_PATH = os.path.join(DB_DIR, "northstar")
PRISTINE = os.path.join(ROOT, "target", "spec-proof", "pristine")
EVIDENCE = os.path.join(ROOT, "docs", "specs", "SPEC-PAY-001-proof.json")
SQL_RUNNER = os.path.join(ROOT, "tools", "spec", "SqlRunner.java")
HSQLDB_JAR = os.environ.get("HSQLDB_JAR", os.path.expanduser(
    "~/.m2/repository/org/hsqldb/hsqldb/2.7.3/hsqldb-2.7.3.jar"))

FIELD_RE = re.compile(r'<span id="f_([^"]+)">(.*?)</span>', re.S)
VIEW_RE = re.compile(r"<!-- ns:view ([^ ]+) -->")
HREF_RE = re.compile(r'<a href="([^"]+)"')
AUDIT_RE = re.compile(
    r"<th>(Screen context|Operator|As of date)</th>\s*<td>(.*?)</td>", re.S)
CELL_RE = re.compile(r"<td>([^<]*)</td>")
NAV_CELLS = ("Quick Links", "Operator:", "Application version",
             "Claims operations environment", "Service desk", "Session:")

PAYMENT_ROWS = ("select payment_id, claim_id, settlement_id, payee_name, "
                "amount, payment_method, check_number, issued_date, status "
                "from PAYMENT where payment_id > 60 order by payment_id")
PAYMENT_COUNT = "select count(*) as payment_rows, max(payment_id) as max_id from PAYMENT"

ISSUE = CONTEXT + "/payment/issue.do"
HISTORY = CONTEXT + "/payment/history.do"
DETAIL = CONTEXT + "/payment/detail.do"
REMIT = CONTEXT + "/payment/remittance.do"

FRESH = {"name": "fresh seed", "sql": []}


def post(path, form, note=""):
    return {"method": "POST", "path": path, "form": form, "note": note}


def get(path, query=None, note=""):
    return {"method": "GET", "path": path, "query": query or {}, "note": note}


PAYEE = {"claimId": "119", "payeeName": "Reserved Claimant",
         "amount": "1000.00", "paymentMethod": "CHECK"}

CASES = [
    {"id": "issue-post",
     "title": "POST issue.do for seeded claim 119 inserts payment 61 and shows the confirmation page",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, PAYEE), get(DETAIL, {"paymentId": "61"})],
     "db": [PAYMENT_ROWS, PAYMENT_COUNT]},
    {"id": "issue-get",
     "title": "GET issue.do with query parameters also inserts a payment",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [get(ISSUE, {"claimId": "1", "payeeName": "Get Payee",
                           "amount": "10", "paymentMethod": "CHECK"})],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-claim-fallback",
     "title": "Missing or non-numeric claimId falls back to claim 119",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, {"claimId": "abc", "payeeName": "Fallback A",
                            "amount": "5", "paymentMethod": "CHECK"}),
               post(ISSUE, {"payeeName": "Fallback B", "amount": "6",
                            "paymentMethod": "CHECK"}),
               post(ISSUE, {"claimId": "1.5", "payeeName": "Fallback C",
                            "amount": "7", "paymentMethod": "CHECK"})],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-amount-fallback-seed",
     "title": "Missing or non-numeric amount falls back to the settlement amount (0 for seeded claim 119)",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, {"claimId": "119", "payeeName": "No Amount",
                            "paymentMethod": "CHECK"}),
               post(ISSUE, {"claimId": "119", "payeeName": "Bad Amount",
                            "amount": "1,000.00", "paymentMethod": "CHECK"}),
               post(ISSUE, {"claimId": "1", "payeeName": "Claim 1 No Amount",
                            "paymentMethod": "CHECK"})],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-newest-settlement",
     "title": "The newest settlement (highest settlement_id) supplies settlement_id and the amount fallback",
     "precondition": {"name": "claim 119 has a second, newer settlement 121 of 1000",
                      "sql": ["INSERT INTO SETTLEMENT VALUES (121, 119, 1500, 500, 0, FALSE, 1000, 'adjuster1', '2019-03-02')"]},
     "actor": "supervisor",
     "steps": [post(ISSUE, {"claimId": "119", "payeeName": "Newest Settlement",
                            "paymentMethod": "CHECK"})],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-amount-raw",
     "title": "Any value Double.parseDouble accepts is stored as sent; screens print it with %.2f",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, dict(PAYEE, amount="-50")),
               post(ISSUE, dict(PAYEE, amount="1.005")),
               post(ISSUE, dict(PAYEE, amount="1e3")),
               get(REMIT, {"claimId": "119"}, "total before NaN"),
               post(ISSUE, dict(PAYEE, amount="NaN")),
               get(REMIT, {"claimId": "119"}, "total after NaN")],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-check-number",
     "title": "A submitted checkNumber is ignored; the check number is CHK- plus the payment id",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, dict(PAYEE, checkNumber="MANUAL-0001"))],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-next-id",
     "title": "The payment id is the highest existing payment_id plus 1, gaps are not filled",
     "precondition": {"name": "payment 500 exists for claim 1 (gap after 60)",
                      "sql": ["INSERT INTO PAYMENT VALUES (500, 1, 1, 'Gap Payee', 1, 'CHECK', 'CHK-GAP', '2019-03-15', 'ISSUED')"]},
     "actor": "supervisor",
     "steps": [post(ISSUE, PAYEE)],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-required-columns",
     "title": "Missing payeeName or paymentMethod fails the insert: error page, no row, id not consumed",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, {"claimId": "119", "amount": "10",
                            "paymentMethod": "CHECK"}),
               post(ISSUE, {"claimId": "119", "payeeName": "No Method",
                            "amount": "10"}),
               post(ISSUE, PAYEE, "first successful insert after two failures")],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-column-limits",
     "title": "paymentMethod over 20 characters or payeeName over 100 characters fails the insert",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, dict(PAYEE, paymentMethod="M" * 21)),
               post(ISSUE, dict(PAYEE, payeeName="P" * 101)),
               post(ISSUE, dict(PAYEE, paymentMethod="M" * 20,
                                payeeName="P" * 100))],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-no-settlement",
     "title": "A claim with no settlement row gets the error page and no payment",
     "precondition": {"name": "settlement 120 deleted, so claim 120 exists without a settlement",
                      "sql": ["DELETE FROM SETTLEMENT WHERE settlement_id = 120"]},
     "actor": "supervisor",
     "steps": [post(ISSUE, dict(PAYEE, claimId="120")),
               post(ISSUE, dict(PAYEE, claimId="9999"))],
     "db": [PAYMENT_ROWS, PAYMENT_COUNT]},
    {"id": "issue-raw-text",
     "title": "payeeName and paymentMethod are stored exactly as sent and HTML-escaped on screen",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, dict(PAYEE, payeeName="<b>A&B</b> Payee",
                                paymentMethod="wire")),
               get(DETAIL, {"paymentId": "61"}),
               get(REMIT, {"claimId": "119"})],
     "db": [PAYMENT_ROWS]},
    {"id": "issue-no-business-checks",
     "title": "Closed claims, claims already paid and amounts above the settlement are all accepted",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [post(ISSUE, dict(PAYEE, claimId="120"), "claim 120 is CLOSED"),
               post(ISSUE, dict(PAYEE, claimId="1", amount="999999"),
                    "claim 1 already has payment 1; settlement 1 is far lower"),
               post(ISSUE, dict(PAYEE, claimId="1", amount="999999"),
                    "same request again"),
               get(HISTORY, {"claimId": "1"})],
     "db": [PAYMENT_ROWS, "select claim_id, status from CLAIM where claim_id in (1, 119, 120) order by claim_id",
            "select settlement_id, claim_id, settlement_amount from SETTLEMENT where claim_id in (1, 119, 120) order by settlement_id"]},
    {"id": "history",
     "title": "history.do lists the claim's payments; claimId falls back to 119; links are fixed",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [get(HISTORY, {"claimId": "1"}), get(HISTORY),
               get(HISTORY, {"claimId": "abc"})],
     "db": [PAYMENT_COUNT]},
    {"id": "history-order",
     "title": "history.do and remittance.do order rows by payment_id, not by insert order",
     "precondition": {"name": "payments 300 then 200 inserted for claim 2",
                      "sql": ["INSERT INTO PAYMENT VALUES (300, 2, 2, 'Third', 3, 'CHECK', 'CHK-300', '2019-03-20', 'ISSUED')",
                              "INSERT INTO PAYMENT VALUES (200, 2, 2, 'Second', 2, 'CHECK', 'CHK-200', '2019-03-19', 'ISSUED')"]},
     "actor": "supervisor",
     "steps": [get(HISTORY, {"claimId": "2"}), get(REMIT, {"claimId": "2"})],
     "db": []},
    {"id": "detail",
     "title": "detail.do shows one payment; paymentId falls back to 61; a missing payment shows the error page",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [get(DETAIL, {"paymentId": "1"}), get(DETAIL),
               get(DETAIL, {"paymentId": "abc"}),
               get(DETAIL, {"paymentId": "99999"})],
     "db": [PAYMENT_COUNT]},
    {"id": "detail-default-61",
     "title": "With payment 61 present, detail.do with no paymentId and the history link both open payment 61",
     "precondition": {"name": "payment 61 exists for claim 5",
                      "sql": ["INSERT INTO PAYMENT VALUES (61, 5, 5, 'Payee 61', 61, 'EFT', 'CHK-61X', '2019-03-21', 'ISSUED')"]},
     "actor": "supervisor",
     "steps": [get(DETAIL), get(HISTORY, {"claimId": "1"})],
     "db": []},
    {"id": "remittance",
     "title": "remittance.do lists the claim's payments with count and total; claimId falls back to 119",
     "precondition": FRESH, "actor": "supervisor",
     "steps": [get(REMIT, {"claimId": "1"}), get(REMIT),
               get(REMIT, {"claimId": "abc"})],
     "db": [PAYMENT_COUNT]},
    {"id": "remittance-total",
     "title": "Total issued sums every payment row of the claim whatever its status",
     "precondition": {"name": "claim 3 also has a VOID payment of 100",
                      "sql": ["INSERT INTO PAYMENT VALUES (400, 3, 3, 'Void Payee', 100, 'CHECK', 'CHK-400', '2019-03-22', 'VOID')"]},
     "actor": "supervisor",
     "steps": [get(REMIT, {"claimId": "3"}), get(HISTORY, {"claimId": "3"})],
     "db": []},
    {"id": "unauthenticated",
     "title": "Without a logged-in session every payment URL redirects to login and nothing is written",
     "precondition": FRESH, "actor": None,
     "steps": [post(ISSUE, PAYEE),
               get(ISSUE, {"claimId": "1", "payeeName": "X", "paymentMethod": "CHECK"}),
               get(HISTORY), get(DETAIL, {"paymentId": "1"}), get(REMIT)],
     "db": [PAYMENT_COUNT]},
    {"id": "adjuster-issue",
     "title": "Any logged-in operator, including an adjuster, can issue a payment",
     "precondition": FRESH, "actor": "adjuster7",
     "steps": [post(ISSUE, PAYEE)],
     "db": [PAYMENT_ROWS]},
]

PASSWORDS = {"supervisor": "supervisor", "adjuster7": "legacy7"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def send(opener, step):
    url = BASE + step["path"]
    data = None
    if step["method"] == "GET" and step.get("query"):
        url += "?" + urllib.parse.urlencode(step["query"])
    if step["method"] == "POST":
        data = urllib.parse.urlencode(step.get("form", {})).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=step["method"])
    try:
        response = opener.open(req, timeout=30)
        return (response.getcode(), response.headers.get("Location"),
                response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return (error.code, error.headers.get("Location"),
                error.read().decode("utf-8"))


def clean(text):
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def observe(code, location, body):
    location = re.sub(r";jsessionid=[^?]*", "", location or "") or None
    view = VIEW_RE.search(body)
    body = re.sub(r";jsessionid=[^?\"]*", "", body)
    trimmed = "\n".join(line.rstrip() for line in body.splitlines()
                        if line.strip()
                        and "NorthStar claims operator layout" not in line)
    out = {
        "status": code,
        "location": location,
        "view": view.group(1) if view else None,
        "fields": [[name, re.sub(r"\s+", " ", value).strip()]
                   for name, value in FIELD_RE.findall(body)],
        "links": [href for href in HREF_RE.findall(body)
                  if "payment/" in href or href.startswith(("detail.do", "remittance.do"))],
        "audit": {key: clean(value) for key, value in AUDIT_RE.findall(body)},
        "body": trimmed,
    }
    cells = [clean(c) for c in CELL_RE.findall(body)]
    out["text_cells"] = [c for c in cells if c and not c.startswith(NAV_CELLS)]
    return out


def java(*args):
    command = ["java", "-cp", HSQLDB_JAR, SQL_RUNNER, DB_PATH] + list(args)
    done = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if done.returncode != 0:
        raise RuntimeError("SqlRunner failed: " + done.stderr[-2000:])
    return done.stdout


def query(sql):
    lines = java("query", sql).splitlines()
    columns = lines[0][1:].split("\t")
    return {"sql": sql, "columns": columns,
            "rows": [line.split("\t") for line in lines[1:]]}


def remove_db():
    for path in glob.glob(DB_PATH + "*"):
        if os.path.isdir(path):
            shutil.rmtree(path)
        else:
            os.remove(path)


def build_pristine():
    remove_db()
    subprocess.run(["mvn", "-q", "compile"], cwd=ROOT, check=True)
    subprocess.run(["mvn", "-q", "org.codehaus.mojo:exec-maven-plugin:3.3.0:java"],
                   cwd=ROOT, check=True)
    shutil.rmtree(PRISTINE, ignore_errors=True)
    os.makedirs(PRISTINE)
    for path in glob.glob(DB_PATH + "*"):
        shutil.copy2(path, PRISTINE)


def restore_pristine():
    remove_db()
    os.makedirs(DB_DIR, exist_ok=True)
    for path in glob.glob(os.path.join(PRISTINE, "*")):
        shutil.copy2(path, DB_DIR)


def start_jetty():
    process = subprocess.Popen(["mvn", "-q", "jetty:run"], cwd=ROOT,
                               stdout=subprocess.DEVNULL,
                               stderr=subprocess.STDOUT)
    deadline = time.time() + 180
    while time.time() < deadline:
        try:
            code, unused, body = send(urllib.request.build_opener(NoRedirect),
                                      get(CONTEXT + "/login.do"))
            if code == 200 and "Claims Login" in body:
                return process
        except Exception:
            pass
        if process.poll() is not None:
            break
        time.sleep(0.5)
    process.terminate()
    raise RuntimeError("Jetty did not become ready on port 8080")


def stop_jetty(process):
    process.terminate()
    try:
        process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
    deadline = time.time() + 30
    while glob.glob(DB_PATH + ".lck") and time.time() < deadline:
        time.sleep(0.5)


def run_case(case):
    restore_pristine()
    if case["precondition"]["sql"]:
        java("exec", *case["precondition"]["sql"])
    process = start_jetty()
    try:
        jar = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(jar), NoRedirect)
        send(opener, get(CONTEXT + "/login.do"))
        if case["actor"]:
            code, location, body = send(opener, post(
                CONTEXT + "/login.do",
                {"username": case["actor"], "password": PASSWORDS[case["actor"]]}))
            if code not in (200, 302) or "Claims Login" in body:
                raise RuntimeError("login failed for " + case["actor"])
        exchanges = []
        for step in case["steps"]:
            request = {key: value for key, value in step.items()
                       if value not in ("", {}, None)}
            exchanges.append({"request": request,
                              "response": observe(*send(opener, step))})
    finally:
        stop_jetty(process)
    return {
        "id": case["id"],
        "title": case["title"],
        "precondition": case["precondition"],
        "actor": case["actor"],
        "exchanges": exchanges,
        "db_after": [query(sql) for sql in case["db"]],
    }


def dump(data):
    return json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true",
                        help="re-run every case and fail if the evidence changed")
    parser.add_argument("--case", help="run only this case and print it")
    args = parser.parse_args()
    if subprocess.run(["curl", "-s", "-o", "/dev/null", BASE + CONTEXT + "/login.do"]).returncode == 0:
        sys.exit("Port 8080 is already serving: stop the legacy app first.")
    cases = [c for c in CASES if not args.case or c["id"] == args.case]
    if not cases:
        sys.exit("unknown case " + args.case)
    build_pristine()
    results = []
    try:
        for case in cases:
            started = time.time()
            results.append(run_case(case))
            print("%-28s %5.1fs" % (case["id"], time.time() - started), flush=True)
    finally:
        remove_db()
        for path in glob.glob(os.path.join(PRISTINE, "*")):
            shutil.copy2(path, DB_DIR)
    if args.case:
        print(dump(results[0]))
        return
    evidence = {
        "spec": "SPEC-PAY-001",
        "generated_by": "tools/spec/payment_proof.py",
        "legacy": BASE + CONTEXT,
        "method": ("Each case: fresh `make seed` database plus its precondition SQL "
                   "(applied with Jetty stopped), mvn jetty:run, login as the actor, "
                   "the listed HTTP requests with curl-equivalent urllib calls "
                   "(issue.jsp is a confirmation page, not a form), then Jetty "
                   "stopped and the listed SQL read from target/db/northstar."),
        "cases": results,
    }
    text = dump(evidence)
    if args.check:
        with open(EVIDENCE, encoding="utf-8") as stream:
            committed = stream.read()
        if committed != text:
            old = {c["id"]: c for c in json.loads(committed)["cases"]}
            for case in results:
                if old.get(case["id"]) != case:
                    print("CHANGED: " + case["id"])
            sys.exit("Live legacy behaviour differs from %s" % os.path.relpath(EVIDENCE, ROOT))
        print("All %d cases match %s" % (len(results), os.path.relpath(EVIDENCE, ROOT)))
        return
    with open(EVIDENCE, "w", encoding="utf-8") as stream:
        stream.write(text)
    print("Wrote %s (%d cases)" % (os.path.relpath(EVIDENCE, ROOT), len(results)))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""HTTP parity harness for the Java 21 migration.

capture <dir>: reseed the HSQLDB file, start Jetty with the current JAVA_HOME,
replay every Struts action plus the transcript workflow scenarios, and write
one normalized JSON record per request.
compare <baseline> <candidate>: diff two capture directories.
"""

import datetime
import difflib
import glob
import http.cookiejar
import importlib.util
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.dont_write_bytecode = True
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
_spec = importlib.util.spec_from_file_location(
    "capture", os.path.join(ROOT, "tools", "capture", "capture.py"))
capture = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(capture)

# Every action in struts-config*.xml, with a seeded id where the action reads one.
READ_ENDPOINTS = [
    "/home.do",
    "/policy/search.do?lineOfBusiness=HOMEOWNERS",
    "/policy/view.do?policyId=2",
    "/policy/list.do",
    "/policy/coverages.do?policyId=2",
    "/policy/coverageDetail.do?policyId=9001",
    "/policy/insuredParty.do?policyId=2",
    "/policy/renewal.do?policyId=2",
    "/intake/new.do",
    "/intake/confirm.do?claimId=121",
    "/workbench/list.do",
    "/workbench/view.do?claimId=5",
    "/workbench/note.do?claimId=5",
    "/workbench/notes.do?claimId=5",
    "/workbench/reserveHistory.do?claimId=5",
    "/workbench/statusHistory.do?claimId=5",
    "/settlement/detail.do?claimId=119",
    "/payment/detail.do?paymentId=61",
    "/payment/remittance.do?claimId=119",
    "/report/index.do",
    "/report/adjusterWorkload.do",
    "/report/claimAgingDetail.do",
    "/report/reconciliation.do",
    "/report/premiumDetail.do",
    "/admin/adjusters.do",
    "/admin/reference.do",
]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def opener(jar=None):
    handlers = [NoRedirect()]
    if jar is not None:
        handlers.append(urllib.request.HTTPCookieProcessor(jar))
    return urllib.request.build_opener(*handlers)


def masks():
    today = datetime.date.today()
    values = set()
    for fmt in ("%Y-%m-%d", "%m/%d/%Y", "%d/%m/%Y", "%Y%m%d", "%d-%b-%Y",
                "%b %d, %Y", "%d.%m.%Y"):
        values.add(today.strftime(fmt))
    return sorted(values, key=len, reverse=True)


TODAY = masks()


def normalize(text):
    text = re.sub(r";jsessionid=[A-Za-z0-9._-]+", "", text)
    text = re.sub(r"\b\d{2}:\d{2}:\d{2}(\.\d+)?\b", "<TIME>", text)
    for value in TODAY:
        text = text.replace(value, "<TODAY>")
    text = re.sub(r"Powered by Jetty:// [^<]*", "Powered by Jetty://", text)
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").split("\n")]
    return "\n".join(line for line in lines if line.strip())


def call(op, method, path, form=None):
    data = urllib.parse.urlencode(form).encode("utf-8") if form else None
    req = urllib.request.Request(capture.BASE + path, data=data, method=method)
    try:
        response = op.open(req, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    body = response.read().decode("utf-8", "replace")
    location = response.headers.get("Location")
    if location:
        location = normalize(re.sub(r"^https?://[^/]+", "", location))
    return {
        "request": {"method": method, "path": capture.CONTEXT_PATH + path,
                    "form": form or {}},
        "status": response.getcode() if hasattr(response, "getcode") else response.code,
        "content_type": (response.headers.get("Content-Type") or "").lower().replace(" ", ""),
        "location": location,
        "body": normalize(body),
    }


def name_for(index, method, path):
    slug = re.sub(r"[^A-Za-z0-9]+", "_", path.split("?")[0]).strip("_")
    return "%02d_%s_%s" % (index, method.lower(), slug)


def do_capture(out):
    os.makedirs(out, exist_ok=True)
    for old in glob.glob(os.path.join(out, "*.json")):
        os.remove(old)
    capture.reset_database()
    process = capture.start_server()
    records = []
    try:
        anonymous = opener()
        records.append(("anon", call(anonymous, "GET", "/login.do")))
        records.append(("anon", call(anonymous, "GET", "/workbench/list.do")))
        jar = http.cookiejar.CookieJar()
        op = opener(jar)
        call(op, "GET", "/login.do")
        for scenario in capture.SCENARIOS:
            name, module, description, method, path, form, probes = scenario
            records.append(("scenario:" + name, call(op, method, path, form)))
        for path in READ_ENDPOINTS:
            records.append(("read", call(op, "GET", path)))
        records.append(("logout", call(op, "GET", "/logout.do")))
        records.append(("after-logout", call(op, "GET", "/workbench/list.do")))
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except Exception:
            process.kill()
    index = []
    for i, (kind, record) in enumerate(records, 1):
        record["kind"] = kind
        name = name_for(i, record["request"]["method"], record["request"]["path"])
        with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as stream:
            json.dump(record, stream, indent=2, sort_keys=True)
            stream.write("\n")
        index.append(name)
    print("captured %d requests into %s" % (len(index), out))


def expected_count():
    return 2 + len(capture.SCENARIOS) + len(READ_ENDPOINTS) + 2


def do_compare(baseline, candidate):
    names = sorted(os.path.basename(p) for p in glob.glob(os.path.join(baseline, "*.json")))
    other = sorted(os.path.basename(p) for p in glob.glob(os.path.join(candidate, "*.json")))
    expected = expected_count()
    errors = []
    for label, directory, found in (("baseline", baseline, names), ("candidate", candidate, other)):
        if not found:
            errors.append("%s %s has no captured requests" % (label, directory))
        elif len(found) != expected:
            errors.append("%s %s has %d requests, expected %d" % (label, directory, len(found), expected))
    for name in sorted(set(names) - set(other)):
        errors.append("only in baseline: %s" % name)
    for name in sorted(set(other) - set(names)):
        errors.append("only in candidate: %s" % name)
    if errors:
        for error in errors:
            print("ERROR " + error)
        print("compare FAILED: request sets differ, nothing compared")
        return 1
    mismatches = 0
    for name in names:
        a = json.load(open(os.path.join(baseline, name), encoding="utf-8"))
        b = json.load(open(os.path.join(candidate, name), encoding="utf-8"))
        diffs = [key for key in ("status", "content_type", "location", "body") if a.get(key) != b.get(key)]
        if diffs:
            mismatches += 1
            print("MISMATCH %s: %s" % (name, ", ".join(diffs)))
            if "body" in diffs:
                for line in list(difflib.unified_diff(a["body"].split("\n"), b["body"].split("\n"),
                                                      "baseline", "candidate", lineterm="", n=1))[:40]:
                    print("    " + line)
    print("compared %d requests, %d mismatches" % (len(names), mismatches))
    return 1 if mismatches else 0


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "capture":
        do_capture(sys.argv[2])
        return 0
    if len(sys.argv) == 4 and sys.argv[1] == "compare":
        return do_compare(sys.argv[2], sys.argv[3])
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Record what the legacy app does for each approved difference that has no
transcript of its own.

A CHG-nnn scenario such as ``payment_issue_above_settlement`` is not one of
the recorded transcripts (transcripts/ is written only by ``make capture``),
so the legacy side of its comparison is observed here instead, the same way:
from a fresh ``make seed`` database, against the monolith on 8080, logged in
as supervisor. The entry's ``legacy_setup`` transcripts are replayed first to
reach its precondition on the legacy side (the service gets the same state
from its ``precondition`` script), then its ``request`` is sent and the
response and the ``legacy_probes`` are written, in transcript form, to
``parity/legacy/<scenario>.json``.

Needs nothing listening on 8080: it seeds the database and starts its own
Jetty, as ``make capture`` does. Usage: ``make payment-legacy-observe``.
"""

import http.cookiejar
import json
import os
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "capture"))
import capture  # noqa: E402

LEGACY_DIR = os.path.join(ROOT, "parity", "legacy")


def strip_context(path):
    return path[len(capture.CONTEXT_PATH):] if path.startswith(capture.CONTEXT_PATH) else path


def send(opener, request):
    return capture.request(opener, request["method"], strip_context(request["path"]),
                           request.get("form") or None)


def observe(entry):
    capture.reset_database()
    process = capture.start_server()
    try:
        opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        capture.request(opener, "POST", "/login.do",
                        {"username": "supervisor", "password": "supervisor"})
        for name in entry.get("legacy_setup", []):
            with open(os.path.join(ROOT, "transcripts", name + ".json")) as handle:
                send(opener, json.load(handle)["request"])
        code, location, body = send(opener, entry["request"])
        state = {key: capture.probe(opener, key, path)
                 for key, path in sorted(entry.get("legacy_probes", {}).items())}
        return {
            "scenario": entry["scenario"],
            "description": entry.get("description", entry["summary"]),
            "actor": "supervisor",
            "legacy_setup": entry.get("legacy_setup", []),
            "request": entry["request"],
            "expected": {
                "status": code,
                "result": capture.result(body, code, location),
                "business_fields": capture.fields(body),
                "validation_errors": capture.ERROR_RE.findall(body),
                "db_state": state,
            },
        }
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def main():
    with open(os.path.join(ROOT, "parity", "routes.json")) as handle:
        routes = json.load(handle)
    os.makedirs(LEGACY_DIR, exist_ok=True)
    for entry in routes.get("approved_differences", []):
        if "request" not in entry:
            continue
        observed = observe(entry)
        path = os.path.join(LEGACY_DIR, entry["scenario"] + ".json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(observed, handle, indent=2, sort_keys=True)
            handle.write("\n")
        print("wrote %s" % os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()

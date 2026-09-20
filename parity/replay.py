#!/usr/bin/env python3
"""Replay the settlement transcripts against services/settlement-service.

The transcripts under transcripts/ are the parity oracle of SPEC-SETTLE-001 and are read
strictly read-only: this harness never captures, rewrites or reorders them.

For every scenario listed in parity/routes.yaml the harness sends the transcript's request to
the mapped service route and compares four things with the transcript:

  * the HTTP status,
  * the forward the response declares through its `ns:view` marker,
  * the business fields (`<span id="f_NAME">`),
  * the validation error keys (`ns:error` markers).

Any scenario in transcripts/index.json that routes.yaml does not map is reported as SKIP.
One line is printed per scenario, parity/report.md is written, and the exit status is 1 when
any scenario fails.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
TRANSCRIPTS = REPO_ROOT / "transcripts"
ROUTES_FILE = Path(__file__).resolve().parent / "routes.yaml"
REPORT_FILE = Path(__file__).resolve().parent / "report.md"

# The markers tools/capture/capture.py reads out of the legacy responses.
FIELD_RE = re.compile(r'<span id="f_([^"]+)">(.*?)</span>', re.S)
VIEW_RE = re.compile(r"<!--\s*ns:view\s+([^ ]+)\s*-->")
ERROR_RE = re.compile(r"<!--\s*ns:error\s+([^ ]+)\s*-->")

PASS = "PASS"
FAIL = "FAIL"
SKIP = "SKIP"


@dataclass
class Outcome:
    scenario: str
    verdict: str
    detail: str
    differences: list[str] = field(default_factory=list)


def load_yaml(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def parse_response(status: int, body: str) -> dict:
    """Extract the same observable surface the capture harness records."""
    view = VIEW_RE.search(body)
    return {
        "status": status,
        "result": "forward:" + view.group(1) if view else "",
        "business_fields": {name: value for name, value in FIELD_RE.findall(body)},
        "validation_errors": ERROR_RE.findall(body),
    }


def send(base_url: str, method: str, path: str, form: dict, timeout: float) -> tuple[int, str]:
    url = base_url.rstrip("/") + path
    data = urllib.parse.urlencode(form or {}).encode("utf-8")
    if method.upper() == "GET":
        if form:
            url = url + "?" + urllib.parse.urlencode(form)
        request = urllib.request.Request(url, method="GET")
    else:
        request = urllib.request.Request(
            url, data=data, method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:  # noqa: S310 - fixed localhost URL
            return response.getcode(), response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode("utf-8", "replace")


def compare(expected: dict, actual: dict) -> list[str]:
    differences: list[str] = []

    if int(expected.get("status", 200)) != actual["status"]:
        differences.append(f"status {actual['status']} != {expected.get('status')}")

    expected_result = expected.get("result", "")
    if expected_result != actual["result"]:
        differences.append(f"forward {actual['result'] or '(none)'} != {expected_result}")

    expected_fields = expected.get("business_fields", {})
    actual_fields = actual["business_fields"]
    for name in sorted(expected_fields):
        if name not in actual_fields:
            differences.append(f"field {name} missing")
        elif str(expected_fields[name]) != actual_fields[name]:
            differences.append(f"field {name} {actual_fields[name]} != {expected_fields[name]}")
    for name in sorted(set(actual_fields) - set(expected_fields)):
        differences.append(f"field {name} unexpected ({actual_fields[name]})")

    expected_errors = list(expected.get("validation_errors", []))
    if expected_errors != actual["validation_errors"]:
        differences.append(
            f"validation errors {actual['validation_errors']} != {expected_errors}")

    return differences


def replay(base_url: str, timeout: float) -> list[Outcome]:
    routes = load_yaml(ROUTES_FILE)
    mapped = {entry["scenario"]: entry for entry in routes["scenarios"]}
    skip_reason = routes.get("skip", {}).get("reason", "no route mapping")
    index = load_json(TRANSCRIPTS / "index.json")

    outcomes: list[Outcome] = []
    for entry in index:
        scenario = entry["scenario"]
        route = mapped.get(scenario)
        if route is None:
            outcomes.append(Outcome(scenario, SKIP, skip_reason))
            continue

        transcript = load_json(TRANSCRIPTS / f"{scenario}.json")
        request = transcript["request"]
        service = route["service"]
        try:
            status, body = send(base_url, service.get("method", request["method"]),
                                service["path"], request.get("form", {}), timeout)
        except OSError as failure:
            outcomes.append(Outcome(scenario, FAIL, f"service unreachable at {base_url}: {failure}"))
            continue

        differences = compare(transcript["expected"], parse_response(status, body))
        if differences:
            outcomes.append(Outcome(scenario, FAIL, "; ".join(differences), differences))
        else:
            fields = len(transcript["expected"].get("business_fields", {}))
            outcomes.append(Outcome(
                scenario, PASS,
                f"status {status}, forward and {fields} business fields match"))
    return outcomes


def write_report(outcomes: list[Outcome], base_url: str) -> None:
    counts = {verdict: sum(1 for o in outcomes if o.verdict == verdict) for verdict in (PASS, FAIL, SKIP)}
    lines = [
        "# Settlement parity report",
        "",
        f"- Generated: {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%SZ')}",
        f"- Service: {base_url}",
        "- Oracle: `transcripts/` (read-only), mapped by `parity/routes.yaml`",
        f"- Result: {counts[PASS]} PASS, {counts[FAIL]} FAIL, {counts[SKIP]} SKIP",
        "",
        "| Scenario | Verdict | Detail |",
        "| --- | --- | --- |",
    ]
    for outcome in outcomes:
        detail = outcome.detail.replace("|", "\\|")
        lines.append(f"| `{outcome.scenario}` | {outcome.verdict} | {detail} |")
    lines.append("")
    REPORT_FILE.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=None,
                        help="settlement-service base URL (default: parity/routes.yaml)")
    parser.add_argument("--timeout", type=float, default=10.0)
    arguments = parser.parse_args()

    base_url = arguments.base_url or load_yaml(ROUTES_FILE)["service"]["base_url"]
    outcomes = replay(base_url, arguments.timeout)

    for outcome in outcomes:
        print(f"{outcome.verdict:4} {outcome.scenario}: {outcome.detail}")

    write_report(outcomes, base_url)
    failures = sum(1 for outcome in outcomes if outcome.verdict == FAIL)
    skipped = sum(1 for outcome in outcomes if outcome.verdict == SKIP)
    print(f"\n{len(outcomes) - failures - skipped} passed, {failures} failed, {skipped} skipped; "
          f"report written to {REPORT_FILE.relative_to(REPO_ROOT)}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

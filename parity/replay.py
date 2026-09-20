#!/usr/bin/env python3
"""Replay the golden legacy transcripts against an extracted Spring service.

Reads transcripts/index.json, looks each scenario's module up in
parity/routes.yaml, and for every module marked ``extracted`` sends the recorded
request to the mapped service endpoint. It then compares, per scenario:

  * HTTP status
  * result   (forward:<jsp> / error:<key>) against the service's legacyForward
  * business_fields (the f_* spans the JSP rendered)
  * validation_errors (ordered ApplicationResources keys)
  * db_state probes, resolved through the probe definitions in routes.yaml

Modules not marked extracted are reported ``SKIP (not yet extracted)``; scenarios
in an extracted module whose legacy path has no route are reported
``SKIP (route not extracted)`` and never counted as PASS, but only if the module
lists them under ``unrouted`` in routes.yaml. An unrouted scenario that is not
listed is a FAIL, so coverage cannot shrink silently. Transcripts are read only:
this script never writes under transcripts/.

``--report`` writes a Markdown report and, alongside it, a JSON report (same
name, ``.json``) with one entry per scenario: scenario, module, result, detail
and ``rules``, the SETTLE-R ids whose Evidence cell in the module's spec
(``modules.<m>.spec`` in routes.yaml) cites that transcript. The ids are read
from the spec's rule tables, not typed here, using the same citation forms as
tools/traceability.py (backticked scenario names, "All N scenarios",
"All N `calculate` scenarios"). ``rules`` is a comma-separated string so that
tools/traceability.py, which scans string values, picks the ids up.

Exit status is 0 when every replayed scenario passed (and, with --strict, when
no routed-module scenario was skipped at all), 1 otherwise, 2 for usage errors.

Only non-standard dependency: PyYAML.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_ROUTES = os.path.join(ROOT, "parity", "routes.yaml")
DEFAULT_INDEX = os.path.join(ROOT, "transcripts", "index.json")

PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"


class Http:
    """Tiny urllib wrapper; returns (status, json-or-None, raw body)."""

    def __init__(self, timeout):
        self.timeout = timeout
        self.opener = urllib.request.build_opener()

    def send(self, method, url, form=None, encoding="form"):
        data = None
        if form and encoding == "query":
            separator = "&" if "?" in url else "?"
            url = url + separator + urllib.parse.urlencode(form)
        elif form is not None and encoding == "form":
            data = urllib.parse.urlencode(form).encode("utf-8")
        request = urllib.request.Request(url, data=data, method=method)
        if data is not None:
            request.add_header("Content-Type", "application/x-www-form-urlencoded")
        request.add_header("Accept", "application/json")
        try:
            response = self.opener.open(request, timeout=self.timeout)
            status, body = response.getcode(), response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read().decode("utf-8")
        try:
            parsed = json.loads(body) if body else None
        except ValueError:
            parsed = None
        return status, parsed, body


def load_yaml(path):
    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


RULE_ID_RE = re.compile(r"SETTLE-R(\d{2,})(?:[\s_-]?[vV](\d+)\b)?")
ALL_SCENARIOS_RE = re.compile(r"\bAll\s+(\w+)\s+(?:`(\w+)`\s+)?scenarios\b", re.IGNORECASE)
NUMBER_WORDS = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
                "seven": 7, "eight": 8, "nine": 9, "ten": 10}


def markdown_tables(text):
    """Every pipe table in ``text`` as a list of rows (header first), cells stripped."""
    tables, current = [], []
    for line in text.splitlines():
        if line.lstrip().startswith("|"):
            if re.fullmatch(r"\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?", line.strip()):
                continue
            current.append([cell.strip() for cell in line.strip().strip("|").split("|")])
        elif current:
            tables.append(current)
            current = []
    if current:
        tables.append(current)
    return tables


def spec_rules_by_scenario(spec_path, transcripts_dir):
    """scenario -> [rule id] from the Evidence column of the spec's rule tables.

    A rule cites a scenario by its backticked name, or by "All N scenarios" /
    "All N `calculate` scenarios", which expand over the spec's transcript
    inventory (section 2.1) exactly as tools/traceability.py expands them.
    """
    with open(spec_path, encoding="utf-8") as handle:
        tables = markdown_tables(handle.read())
    inventory = []
    for table in tables:
        if [cell.lower() for cell in table[0][:2]] != ["scenario", "file"]:
            continue
        for row in table[1:]:
            name = row[0].strip("`")
            if os.path.isfile(os.path.join(transcripts_dir, name + ".json")):
                inventory.append(name)

    def request_path(name):
        try:
            return load_json(os.path.join(transcripts_dir, name + ".json"))["request"]["path"]
        except (OSError, KeyError, ValueError):
            return ""

    by_scenario = {}
    for table in tables:
        header = [cell.lower() for cell in table[0]]
        if "id" not in header or "evidence" not in header:
            continue
        id_col, evidence_col = header.index("id"), header.index("evidence")
        for row in table[1:]:
            if len(row) <= max(id_col, evidence_col):
                continue
            match = RULE_ID_RE.fullmatch(row[id_col])
            if not match:
                continue
            rule = "SETTLE-R%s" % match.group(1) + (" v" + match.group(2) if match.group(2) else "")
            evidence = row[evidence_col]
            cited = [name for name in re.findall(r"`([a-z0-9_]+)`", evidence) if name in inventory]
            for count, qualifier in ALL_SCENARIOS_RE.findall(evidence):
                number = NUMBER_WORDS.get(count.lower(), int(count) if count.isdigit() else None)
                pool = [name for name in inventory
                        if not qualifier or request_path(name).endswith("/%s.do" % qualifier)]
                if number == len(pool):
                    cited.extend(pool)
            for name in cited:
                rules = by_scenario.setdefault(name, [])
                if rule not in rules:
                    rules.append(rule)
    return by_scenario


def find_route(routes, method, path):
    for route in routes:
        legacy = route["legacy"]
        if legacy["path"] == path and legacy.get("method", method).upper() == method.upper():
            return route
    return None


def service_result(response_cfg, body):
    """Map the service envelope onto the transcript's ``result`` line."""
    if not isinstance(body, dict):
        return "<non-json response>"
    forward = body.get(response_cfg["forward_key"])
    if forward is None:
        return "<no %s>" % response_cfg["forward_key"]
    error_forwards = response_cfg.get("error_forwards") or {}
    if forward in error_forwards:
        return error_forwards[forward]
    return "forward:" + forward


def service_fields(response_cfg, body):
    """capture.py drops blank spans, so blank service fields are dropped too."""
    if not isinstance(body, dict):
        return {}
    fields = body.get(response_cfg["fields_key"]) or {}
    return {name: str(value) for name, value in fields.items() if str(value).strip() != ""}


def service_validation(response_cfg, body):
    if not isinstance(body, dict):
        return []
    return list(body.get(response_cfg["validation_key"]) or [])


def compare_fields(expected, actual):
    problems = []
    for name in sorted(set(expected) | set(actual)):
        if name not in actual:
            problems.append("%s missing (legacy %s)" % (name, expected[name]))
        elif name not in expected:
            problems.append("%s unexpected (%s)" % (name, actual[name]))
        elif expected[name] != actual[name]:
            problems.append("%s %s != legacy %s" % (name, actual[name], expected[name]))
    return problems


def resolve_probe(http, base_url, probes, module, response_cfg, key):
    for probe in probes:
        if probe.get("module") not in (None, module):
            continue
        match = re.match(probe["pattern"], key)
        if not match:
            continue
        path = probe["request"]["path"].format(**match.groupdict())
        status, body, unused = http.send(probe["request"].get("method", "GET"), base_url + path)
        if status != 200:
            return None, "probe %s -> HTTP %d" % (key, status)
        return service_fields(response_cfg, body).get(probe["field"], ""), None
    return None, "probe %s has no definition in routes.yaml" % key


def replay_scenario(http, base_url, config, module, transcript):
    request = transcript["request"]
    expected = transcript["expected"]
    route = find_route(config.get("routes") or [], request["method"], request["path"])
    if route is None:
        detail = "route not extracted: %s %s" % (request["method"], request["path"])
        allowed = (config.get("modules") or {}).get(module, {}).get("unrouted") or []
        if transcript["scenario"] in allowed:
            return SKIP, detail
        return FAIL, detail + " (not listed under modules.%s.unrouted)" % module

    service = route["service"]
    url = base_url + service["path"]
    try:
        status, body, raw = http.send(service.get("method", request["method"]), url,
                                      request.get("form") or {}, service.get("encoding", "form"))
    except (urllib.error.URLError, OSError) as error:
        return FAIL, "service unreachable at %s: %s" % (url, error)

    response_cfg = config["response"]
    problems = []
    if status != expected["status"]:
        problems.append("status %s != legacy %s" % (status, expected["status"]))
    actual_result = service_result(response_cfg, body)
    if actual_result != expected["result"]:
        problems.append("result %s != legacy %s" % (actual_result, expected["result"]))
    problems.extend(compare_fields(expected.get("business_fields") or {},
                                   service_fields(response_cfg, body)))
    actual_validation = service_validation(response_cfg, body)
    if actual_validation != list(expected.get("validation_errors") or []):
        problems.append("validation_errors %s != legacy %s"
                        % (actual_validation, expected.get("validation_errors")))
    for key, legacy_value in sorted((expected.get("db_state") or {}).items()):
        try:
            value, error = resolve_probe(http, base_url, config.get("probes") or [], module,
                                         response_cfg, key)
        except (urllib.error.URLError, OSError) as failure:
            value, error = None, "probe %s failed: %s" % (key, failure)
        if error:
            problems.append(error)
        elif value != legacy_value:
            problems.append("%s %s != legacy %s" % (key, value, legacy_value))
    if body is None and not problems:
        problems.append("service returned a non-JSON body: %s" % raw[:120])
    if problems:
        return FAIL, "; ".join(problems)
    return PASS, "status %d, %s, %d fields, %d probes" % (
        status, expected["result"], len(expected.get("business_fields") or {}),
        len(expected.get("db_state") or {}))


def wait_for_service(http, base_url, seconds):
    deadline = time.time() + seconds
    while True:
        try:
            status, unused, raw = http.send("GET", base_url + "/actuator/health")
            if status == 200 and "UP" in raw:
                return True
        except (urllib.error.URLError, OSError):
            pass
        if time.time() >= deadline:
            return False
        time.sleep(1)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--routes", default=DEFAULT_ROUTES)
    parser.add_argument("--transcripts", default=DEFAULT_INDEX,
                        help="transcripts/index.json (read only)")
    parser.add_argument("--base-url", default=None,
                        help="service base URL; defaults to the module's default_base_url")
    parser.add_argument("--module", default=None,
                        help="replay only this extracted module (others are listed as SKIP)")
    parser.add_argument("--report", default=None, help="write a Markdown parity report here")
    parser.add_argument("--wait", type=int, default=60,
                        help="seconds to wait for /actuator/health before replaying")
    parser.add_argument("--timeout", type=int, default=20)
    parser.add_argument("--strict", action="store_true",
                        help="also fail when a scenario in a replayed module has no route")
    args = parser.parse_args(argv)

    config = load_yaml(args.routes)
    index = load_json(args.transcripts)
    modules = config.get("modules") or {}
    transcripts_dir = os.path.dirname(os.path.abspath(args.transcripts))
    http = Http(args.timeout)

    lines = []

    def emit(text=""):
        lines.append(text)
        print(text)

    emit("NorthStar parity replay")
    emit("routes:      %s" % os.path.relpath(args.routes, ROOT))
    emit("transcripts: %s (%d scenarios, read only)" % (os.path.relpath(args.transcripts, ROOT),
                                                         len(index)))
    emit("module:      %s" % (args.module or "all extracted"))

    replayed_modules = [name for name, cfg in modules.items()
                        if (cfg or {}).get("status") == "extracted"
                        and (args.module is None or name == args.module)]
    if args.module and args.module not in modules:
        emit("error: module %r is not listed in %s" % (args.module, args.routes))
        return 2
    if args.module and args.module not in replayed_modules:
        emit("error: module %r is not marked extracted in %s" % (args.module, args.routes))
        return 2

    base_urls = {}
    for name in replayed_modules:
        base_urls[name] = (args.base_url or modules[name].get("default_base_url")).rstrip("/")
        emit("base_url:    %s -> %s" % (name, base_urls[name]))
        if not wait_for_service(http, base_urls[name], args.wait):
            emit("warning: %s did not report UP within %ds" % (base_urls[name], args.wait))
    emit()

    width = max(len(entry["scenario"]) for entry in index)
    header = "%-*s | %-10s | %-4s | %s" % (width, "scenario", "module", "res", "detail")
    emit(header)
    emit("-" * len(header))

    rules_by_scenario = {}
    for name in replayed_modules:
        spec = modules[name].get("spec")
        if spec:
            rules_by_scenario.update(
                spec_rules_by_scenario(os.path.join(ROOT, spec), transcripts_dir))

    counts = {}
    unrouted = []
    results = []
    for entry in index:
        scenario, module = entry["scenario"], entry["module"]
        module_cfg = modules.get(module) or {}
        if module_cfg.get("status") != "extracted":
            verdict, detail = SKIP, "not yet extracted (module %s)" % module
        elif module not in replayed_modules:
            verdict, detail = SKIP, "module %s not selected" % module
        else:
            transcript = load_json(os.path.join(transcripts_dir, scenario + ".json"))
            verdict, detail = replay_scenario(http, base_urls[module], config, module, transcript)
            if verdict == SKIP:
                unrouted.append(scenario)
        counts.setdefault(module, {PASS: 0, FAIL: 0, SKIP: 0})
        counts[module][verdict] += 1
        results.append({"scenario": scenario, "module": module, "result": verdict,
                        "detail": detail,
                        "rules": ", ".join(rules_by_scenario.get(scenario, []))})
        emit("%-*s | %-10s | %-4s | %s" % (width, scenario, module, verdict, detail))
    emit()

    replayed_pass = sum(counts[m][PASS] for m in replayed_modules)
    replayed_fail = sum(counts[m][FAIL] for m in replayed_modules)
    replayed_skip = sum(counts[m][SKIP] for m in replayed_modules)
    other_skip = sum(counts[m][SKIP] for m in counts if m not in replayed_modules)
    summary = "PARITY %s: %s: %d PASS, %d FAIL, %d SKIP (route not extracted%s); %d SKIP (not yet extracted)" % (
        "PASS" if replayed_fail == 0 and (not args.strict or not unrouted) else "FAIL",
        ", ".join(replayed_modules) or "no module",
        replayed_pass, replayed_fail, replayed_skip,
        ": " + ", ".join(unrouted) if unrouted else "",
        other_skip)
    emit(summary)

    exit_code = 0 if (replayed_fail == 0 and (not args.strict or not unrouted)) else 1
    if args.report:
        write_report(args.report, lines, summary, exit_code, args)
        print("report written to %s" % os.path.relpath(args.report, ROOT))
        json_path = os.path.splitext(args.report)[0] + ".json"
        write_json_report(json_path, results, summary, exit_code, args)
        print("report written to %s" % os.path.relpath(json_path, ROOT))
    return exit_code


def write_json_report(path, results, summary, exit_code, args):
    with open(path, "w", encoding="utf-8") as handle:
        json.dump({
            "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "module": args.module or "all extracted modules",
            "base_url": args.base_url,
            "summary": summary,
            "exit_code": exit_code,
            "scenarios": results,
        }, handle, indent=2)
        handle.write("\n")


def write_report(path, lines, summary, exit_code, args):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("# Parity report: %s\n\n" % (args.module or "all extracted modules"))
        handle.write("Golden transcripts recorded from the running Struts monolith "
                     "(`transcripts/`, unmodified) replayed against the extracted service.\n\n")
        handle.write("Command: `python3 parity/replay.py --base-url %s --module %s --report %s`  \n"
                     % (args.base_url, args.module, os.path.relpath(path, ROOT)))
        handle.write("Generated: %s  \n" % time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()))
        handle.write("Exit code: %d\n\n" % exit_code)
        handle.write("## Summary\n\n`%s`\n\n" % summary)
        handle.write("## Console output\n\n```\n%s\n```\n\n" % "\n".join(lines))
        handle.write("## Reading the verdicts\n\n"
                     "* `PASS`: HTTP status, `result` forward, every `business_fields` entry, the "
                     "ordered `validation_errors` list and every `db_state` probe equal the legacy "
                     "recording.\n"
                     "* `SKIP (not yet extracted)`: the transcript's module is marked `legacy` in "
                     "`parity/routes.yaml`; nothing was sent.\n"
                     "* `SKIP (route not extracted)`: the module is extracted but this legacy path has "
                     "no route, i.e. the scenario is filed under the module in `transcripts/index.json` "
                     "but belongs to code outside the extracted seam. It is never counted as a PASS.\n"
                     "* `FAIL`: at least one comparison differed; the detail column lists each "
                     "`<field> <service> != legacy <recorded>` difference.\n")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

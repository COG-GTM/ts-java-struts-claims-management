"""Derive the payment parity scenarios and their database preconditions.

Reads docs/specs/SPEC-PAY-001-live.json (the 41 recorded legacy requests and
the PAYMENT rows they left behind), docs/specs/SPEC-PAY-001.md (whose
``Observed:`` lines say which rule each live request is evidence for) and the
two payment transcripts, and writes:

* ``parity/preconditions/payment/<name>.sql`` - one file per database state a
  scenario starts from, applied on top of the legacy seed by
  ``POST /parity/reset`` of the payment service (ADR-002, "How parity is
  judged"), so no scenario depends on the order another one ran in;
* the ``payment_scenarios`` block of ``parity/routes.json``, spliced into the
  file as text so that the settlement entries keep their bytes.

Run by ``make payment-scenarios``; ``--check`` fails when the committed files
differ from what the inputs derive.
"""
import argparse
import html
import json
import os
import urllib.parse
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIVE = os.path.join(ROOT, "docs", "specs", "SPEC-PAY-001-live.json")
SPEC = os.path.join(ROOT, "docs", "specs", "SPEC-PAY-001.md")
ROUTES = os.path.join(ROOT, "parity", "routes.json")
PRECONDITIONS = os.path.join(ROOT, "parity", "preconditions", "payment")

# Live L05 saved settlement 121 for claim 119 through the legacy settlement
# screen: coveredAmount 5000.00, deductible 500.00, depreciation 0.00, as the
# supervisor, calculated_date 2019-04-01 (SettlementSaveAction.java:34-38),
# giving settlement_amount 1000.00 (db_after.settlements_used).
SETTLEMENT_121 = ("INSERT INTO SETTLEMENT VALUES (121, 119, 5000, 500, 0, FALSE, 1000,"
                  " 'supervisor', '2019-04-01');")

# Steps that are not payment requests (sign-in and the settlement set-up) and
# the unauthenticated requests of PAY-R13, which the service has no equivalent
# of (ADR-002, "Out of scope").
NOT_SCENARIOS = {"L01", "L02", "L05", "L35", "L38", "L39", "L40"}

PATHS = {
    "/claims/payment/issue.do": "/payment/issue",
    "/claims/payment/history.do": "/payment/history",
    "/claims/payment/detail.do": "/payment/detail",
    "/claims/payment/remittance.do": "/payment/remittance",
}


def load(path):
    with open(path) as handle:
        return json.load(handle)


def rules_by_step(spec_text):
    """Map each live step id to the rules whose Observed: lines cite it."""
    found = {}
    rule = None
    observed = False
    for line in spec_text.splitlines():
        head = re.match(r"\*\*(PAY-R\d+) \(v\d+(?:, superseded by v\d+)?\)\*\*", line)
        if head:
            rule = head.group(1)
            observed = False
            continue
        if line.startswith("Observed:"):
            observed = True
        elif line.startswith("Read:") or not line.strip():
            observed = False
        if observed and rule:
            for step in re.findall(r"\bL\d\d\b", line):
                found.setdefault(step, [])
                if rule not in found[step]:
                    found[step].append(rule)
    return found


def sql_amount(amount):
    """The live proof dumps NaN as HSQLDB's script form 0E0/0E0, which HSQLDB
    rejects as a literal (division by zero); CAST('NaN' AS DOUBLE) stores the
    same NaN that PaymentDAO.insert's setDouble stored (PAY-R04)."""
    return "CAST('NaN' AS DOUBLE)" if amount == "0E0/0E0" else amount


def sql_insert(row):
    return ("INSERT INTO PAYMENT VALUES (%s, %s, %s, '%s', %s, '%s', '%s', '%s', '%s');" % (
        row["payment_id"], row["claim_id"], row["settlement_id"],
        row["payee_name"].replace("'", "''"), sql_amount(row["amount"]), row["payment_method"],
        row["check_number"], row["issued_date"], row["status"]))


def precondition_files(live_rows):
    """The SQL of every precondition, keyed by name."""
    rows = {int(row["payment_id"]): row for row in live_rows if int(row["payment_id"]) > 60}
    files = {
        "seed": "-- The legacy seed as loaded from src/main/resources/db/seed.sql: 60 PAYMENT\n"
                "-- rows, settlement 119 (0.00) for claim 119. Nothing to add.\n",
        "settlement-121": "-- Live L05 of docs/specs/SPEC-PAY-001-live.json: settlement 121 for\n"
                          "-- claim 119 at 1000.00, saved before the issue requests.\n"
                          + SETTLEMENT_121 + "\n",
        "transcript-payment-61": "-- The state transcripts/payment_history.json was captured in:\n"
                                 "-- settlement 121 (1000.00) and the payment transcripts/payment_issue.json\n"
                                 "-- issued against it (db_state payment.61.amount 1000.00).\n"
                                 + SETTLEMENT_121 + "\n"
                                 + "INSERT INTO PAYMENT VALUES (61, 119, 121, 'Reserved Claimant',"
                                 " 1000.0E0, 'CHECK', 'CHK-61', '2019-04-03', 'ISSUED');\n",
    }
    for last in sorted(rows):
        lines = ["-- docs/specs/SPEC-PAY-001-live.json db_after.payment_rows_after_seed:",
                 "-- settlement 121 and payments 61 to %d as the live proof left them." % last,
                 SETTLEMENT_121]
        lines += [sql_insert(rows[i]) for i in sorted(rows) if i <= last]
        files["payments-to-%d" % last] = "\n".join(lines) + "\n"
    return files


def expected_display(step, rows_by_id, fields):
    """The <bean:write> texts the service answers in ``display``."""
    path = step["request"]["path"]
    if "detail.do" in path and fields:
        payee = method = None
        for text in step["response"]["text"]:
            if text.startswith("Payee "):
                payee = text[len("Payee "):]
            if text.startswith("Method "):
                method = text[len("Method "):]
        return {"payee": html.escape(payee, quote=True).replace("&#x27;", "&#39;"),
                "method": html.escape(method, quote=True).replace("&#x27;", "&#39;")}
    if "remittance.do" in path:
        display = {}
        for key in fields:
            if key.startswith("remitCheck_"):
                payment_id = key[len("remitCheck_"):]
                row = rows_by_id[int(payment_id)]
                display["payee_" + payment_id] = html.escape(
                    row["payee_name"], quote=True).replace("&#x27;", "&#39;")
        return display
    return {}


def live_scenarios(live, rules):
    all_rows = {int(r["payment_id"]): r for r in live["db_after"]["payment_rows_after_seed"]["rows"]}
    scenarios = []
    issued = 60
    settlement_121 = False
    for step in live["steps"]:
        step_id = step["id"]
        if step_id == "L05":
            settlement_121 = True
        if step_id in NOT_SCENARIOS:
            continue
        before = issued
        request = step["request"]
        path, _, query = request["path"].partition("?")
        form = dict(request.get("form", {}))
        for pair in filter(None, query.split("&")):
            key, _, value = pair.partition("=")
            form[key] = urllib.parse.unquote_plus(value)
        result = step["response"]["result"]
        success_issue = result.endswith("issue.jsp")
        if success_issue:
            issued += 1
        if not settlement_121:
            precondition = "seed"
        else:
            precondition = "payments-to-%d" % before if before > 60 else "settlement-121"
        fields = step["response"]["business_fields"]
        expected = {
            "status": step["response"]["status"],
            "result": result,
            "business_fields": fields,
            "validation_errors": step["response"]["validation_errors"],
            "links": step["response"].get("links", []),
            "display": expected_display(step, all_rows, fields),
            "payment_rows_through": issued,
        }
        scenarios.append({
            "scenario": "payment_live_%s" % step_id,
            "source": "docs/specs/SPEC-PAY-001-live.json#%s" % step_id,
            "description": step["purpose"],
            "actor": step["actor"],
            "precondition": precondition,
            "rules": rules.get(step_id, []),
            "request": {"method": request["method"], "path": PATHS[path], "form": form},
            "expected": expected,
        })
    return scenarios


def transcript_scenarios(rules_from_routes):
    scenarios = []
    for name, precondition in (("payment_issue", "settlement-121"),
                               ("payment_history", "transcript-payment-61")):
        transcript = load(os.path.join(ROOT, "transcripts", name + ".json"))
        request = transcript["request"]
        scenarios.append({
            "scenario": name,
            "source": "transcripts/%s.json" % name,
            "description": transcript["description"],
            "actor": transcript["actor"],
            "precondition": precondition,
            "rules": rules_from_routes.get(name, []),
            "request": {"method": request["method"], "path": PATHS[request["path"]],
                        "form": dict(request.get("form", {}))},
            "expected": {
                "status": transcript["expected"]["status"],
                "result": transcript["expected"]["result"],
                "business_fields": transcript["expected"]["business_fields"],
                "validation_errors": transcript["expected"]["validation_errors"],
                "db_state": transcript["expected"]["db_state"],
            },
        })
    return scenarios


def splice(routes_text, scenarios):
    """Replace or insert the payment_scenarios block without touching other lines."""
    block = json.dumps(scenarios, indent=2, sort_keys=True)
    block = "\n".join("  " + line if line else line for line in block.splitlines())
    comment = ("  \"payment_scenarios_comment\": \"Generated by tools/parity/payment_scenarios.py "
               "(make payment-scenarios) from docs/specs/SPEC-PAY-001-live.json, the Observed: "
               "lines of docs/specs/SPEC-PAY-001.md and the payment transcripts; do not edit by "
               "hand. Each scenario names the parity/preconditions/payment/<precondition>.sql "
               "applied before it is replayed and the PAY rules it is evidence for (ADR-002).\",\n")
    start = routes_text.find("  \"payment_scenarios_comment\":")
    new = comment + "  \"payment_scenarios\": " + block.lstrip() + ",\n"
    if start < 0:
        head, sep, rest = routes_text.partition("{\n")
        return head + sep + new + rest
    end = routes_text.index("\n  ],\n", start) + len("\n  ],\n")
    return routes_text[:start] + new + routes_text[end:]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    live = load(LIVE)
    with open(SPEC) as handle:
        rules = rules_by_step(handle.read())
    with open(ROUTES) as handle:
        routes_text = handle.read()
    routes = json.loads(routes_text)
    scenarios = transcript_scenarios(routes.get("transcript_rules", {})) + live_scenarios(live, rules)
    outputs = {os.path.join(PRECONDITIONS, name + ".sql"): text
               for name, text in precondition_files(live["db_after"]["payment_rows_after_seed"]["rows"]).items()}
    outputs[ROUTES] = splice(routes_text, scenarios)
    stale = []
    for path, text in sorted(outputs.items()):
        current = open(path).read() if os.path.exists(path) else None
        if current != text:
            stale.append(os.path.relpath(path, ROOT))
            if not args.check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "w") as handle:
                    handle.write(text)
    if args.check and stale:
        sys.exit("stale: " + ", ".join(stale))
    print("%d payment scenarios, %d preconditions%s" % (
        len(scenarios), len(outputs) - 1, " (updated %d files)" % len(stale) if stale else ""))


if __name__ == "__main__":
    main()

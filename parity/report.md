# Parity report: settlement

Golden transcripts recorded from the running Struts monolith (`transcripts/`, unmodified) replayed against the extracted service.

Command: `python3 parity/replay.py --base-url http://localhost:8183 --module settlement --report parity/report.md`  
Generated: 2026-09-20 09:44:24 UTC  
Exit code: 0

## Summary

`PARITY PASS: settlement: 6 PASS, 0 FAIL, 2 SKIP (route not extracted: payment_issue, payment_history); 14 SKIP (not yet extracted)`

## Console output

```
NorthStar parity replay
routes:      parity/routes.yaml
transcripts: transcripts/index.json (22 scenarios, read only)
module:      settlement
base_url:    settlement -> http://localhost:8183

scenario                    | module     | res  | detail
--------------------------------------------------------
login                       | policy     | SKIP | not yet extracted (module policy)
policy_search               | policy     | SKIP | not yet extracted (module policy)
policy_view                 | policy     | SKIP | not yet extracted (module policy)
fnol_submit                 | intake     | SKIP | not yet extracted (module intake)
workbench_assign            | workbench  | SKIP | not yet extracted (module workbench)
workbench_status            | workbench  | SKIP | not yet extracted (module workbench)
workbench_reserve           | workbench  | SKIP | not yet extracted (module workbench)
settlement_calculate        | settlement | PASS | status 200, forward:/WEB-INF/jsp/settlement/calculate.jsp, 5 fields, 0 probes
settlement_save             | settlement | PASS | status 200, forward:/WEB-INF/jsp/settlement/save.jsp, 2 fields, 1 probes
payment_issue               | settlement | SKIP | route not extracted: POST /claims/payment/issue.do
payment_history             | settlement | SKIP | route not extracted: GET /claims/payment/history.do
report_open_by_adjuster     | reporting  | SKIP | not yet extracted (module reporting)
report_loss_ratio           | reporting  | SKIP | not yet extracted (module reporting)
report_aged_claims          | reporting  | SKIP | not yet extracted (module reporting)
settlement_blank_deductible | settlement | PASS | status 200, forward:/WEB-INF/jsp/settlement/calculate.jsp, 5 fields, 1 probes
intake_lenient_date         | intake     | SKIP | not yet extracted (module intake)
settlement_half_cent        | settlement | PASS | status 200, forward:/WEB-INF/jsp/settlement/calculate.jsp, 5 fields, 0 probes
settlement_policy_cap       | settlement | PASS | status 200, forward:/WEB-INF/jsp/settlement/calculate.jsp, 5 fields, 0 probes
settlement_deductible_floor | settlement | PASS | status 200, forward:/WEB-INF/jsp/settlement/calculate.jsp, 5 fields, 0 probes
intake_missing_claimant     | intake     | SKIP | not yet extracted (module intake)
intake_missing_description  | intake     | SKIP | not yet extracted (module intake)
intake_bad_date             | intake     | SKIP | not yet extracted (module intake)

PARITY PASS: settlement: 6 PASS, 0 FAIL, 2 SKIP (route not extracted: payment_issue, payment_history); 14 SKIP (not yet extracted)
```

## Reading the verdicts

* `PASS`: HTTP status, `result` forward, every `business_fields` entry, the ordered `validation_errors` list and every `db_state` probe equal the legacy recording.
* `SKIP (not yet extracted)`: the transcript's module is marked `legacy` in `parity/routes.yaml`; nothing was sent.
* `SKIP (route not extracted)`: the module is extracted but this legacy path has no route, i.e. the scenario is filed under the module in `transcripts/index.json` but belongs to code outside the extracted seam. It is never counted as a PASS.
* `FAIL`: at least one comparison differed; the detail column lists each `<field> <service> != legacy <recorded>` difference.

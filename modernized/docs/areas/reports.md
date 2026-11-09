# MIG-14 — reports

## Scope and implementation decisions

* Ports all eight `/report/*.do` mappings into `web.reports.ReportsController` and eight `templates/reports/` views. The view markers retain `/WEB-INF/jsp/report/*.jsp` exactly.
* Every report mapping has `validate="false"`, no named ActionForm, and no report rules in `validation.xml`. No form record or artificial binding/validation was introduced. GET and POST remain read-only HTTP 200 forwards and ignore request parameters.
* Reuses Wave 0's `ReportDAO`, `ClaimDAO`, `PolicyDAO`, `ClaimsSupport` and shared layout/field fragments unchanged. No new repository is necessary: these actions have no inline SQL. No shared foundation, legacy, transcript, snapshot, or parity-tool changes.
* Page headings, labels, notes, links and field types retain the JSP resource keys. Raw DAO/domain values go through `ns :: field`, without pre-formatting.
* Authentication remains the shared login/session rule. The index's `reportAccess="supervisor"` is display metadata, not an additional supervisor-only restriction.

## Preserved quirks and findings

* Report as-of date is **2019-04-01**, independent of request parameters.
* Open-by-adjuster and workload include only `OPEN` and `INVESTIGATING`, grouped and ordered by assigned adjuster.
* Aging includes every status except `CLOSED`/`DENIED`, with inclusive 30/60/90-day boundaries and lexical bucket order `0_30`, `31_60`, `61_90`, `91_PLUS`. Future reported dates still enter `0_30`. Missing buckets are not synthesized.
* The aged report formats **reserve totals as integers**, truncating fractional amounts; this is not money formatting.
* Claim-aging detail includes only `OPEN` claims, in claim-ID order, rather than all statuses included by the aggregate report.
* Loss ratio preserves the exact policy→claim→payment joins, grouping and business-line ordering. Multiple claims/payments can multiply premium and reserve sums; no status filters or deduplication were added. The ratio is a fraction displayed using two-decimal money formatting, not a percentage; zero premium produces zero ratio.
* Workload's `oldestReportedDate` is absent from the legacy row maps. Its date fields intentionally remain blank.
* Reconciliation reuses the loss-ratio maps, which lack `reserveTotal`, `paidTotal` and `variance`. These fields intentionally remain blank rather than inventing new totals. Map `get` preserves JSP EL's missing-key behavior.
* Premium detail includes every policy (not only active policies), in policy-ID order.
* The index retains **eleven links**: seven unique destinations and four repeated detail links. Its model still reports `reportCount=7`.

## Verification

`ReportsIntegrationTest` uses Spring Boot + MockMvc + real in-memory HSQLDB seeded from the unchanged legacy scripts. It covers all eight views for GET/POST, login requirements, detail fields, blank values, duplicate links, empty results, ordering, escaping, aging boundaries, join multiplication, all payment statuses and zero premium.

Verified locally:

| Command | Result |
| --- | --- |
| `make -C modernized build` | PASS |
| `make -C modernized replay AREA=reports` | 3/3 PASS, byte-identical |
| `make -C modernized test` | 17 tests, zero failures/errors/skips |
| `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 make test` | 9 tests, zero failures/errors/skips |

| Golden transcript | Result |
| --- | --- |
| `report_open_by_adjuster` | PASS |
| `report_loss_ratio` | PASS |
| `report_aged_claims` | PASS |

Maven Central returned a rate-limit response instead of the parent POM on this VM. A VM-local Maven settings mirror using `https://maven-central.storage-download.googleapis.com/maven2` resolved dependency downloads; no project dependency or build configuration changed.

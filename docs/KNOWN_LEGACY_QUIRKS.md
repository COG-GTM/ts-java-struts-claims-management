# Known legacy quirks: settlement slice

Behaviour the Struts application has, that a fresh rewrite would not, and
that the settlement service reproduces on purpose. Each entry says where the
behaviour lives, how it was found, and what would have to happen before it is
allowed to change. Nothing here is a defect report; it is a list of things we
promised to keep the same until told otherwise.

| Id | Quirk | Legacy location | How found | Kept in service by | Rule |
| --- | --- | --- | --- | --- | --- |
| QUIRK-01 | Rounding uses `Math.round(amount * 100.0) / 100.0` on a `double`. `1.005` settles at `1.00`, because the binary value is just below `1.005`. `BigDecimal` with `HALF_UP` gives `1.01`. | `SettlementCalculator.calculate` | `make parity` on the first service build: `settlement_half_cent` failed, `1.00` -> `1.01` | `domain/SettlementCalculator` uses the same `double` statements; `SettlementCalculatorTest.halfCentUsesLegacyDoubleMath` | SETTLE-R09 |
| QUIRK-02 | Money display uses `String.format("%.2f", double)`, which rounds the shortest decimal form half up. The same screen therefore shows `coveredAmount 1.01` next to `settlementAmount 1.00` for input `1.005`. | `web/tag/FieldTag` | reading `FieldTag` after QUIRK-01 | `domain/LegacyDisplay.money` uses the same format call | SETTLE-R10 |
| QUIRK-03 | When the claim or its policy does not exist, calculate uses a policy limit of `10000` and still answers. | `SettlementCalculateAction`, `double limit = 10000;` | reading the action; confirmed with `claimId=9999` | `SettlementService.FALLBACK_LIMIT`; `SettlementControllerTest.unknownClaimUsesFallbackLimit` | SETTLE-R05, OQ-01 |
| QUIRK-04 | Save has no such fallback: a missing claim dereferences `null` and lands on the system error screen. | `SettlementSaveAction` | reading the action while porting save | `SettlementService.save` throws `ClaimNotFoundException` | SETTLE-R12 |
| QUIRK-05 | Any exception ends on `error.jsp` with HTTP 200 and no `ns:error` marker, so a captured transcript shows `result: forward:/WEB-INF/jsp/error.jsp`, `status 200` and an empty `validation_errors` list. | `struts-config.xml` `<global-exceptions>`, `error.jsp` | `make parity` first run: `settlement_bad_deductible` failed on `validation_errors` | `api/LegacyErrorHandler` returns screen `error`, status 200, no errors | SETTLE-R06 |
| QUIRK-06 | Missing or non-numeric `claimId`, `coveredAmount` and `depreciation` silently become `119`, `5000` and `0`. | `ClaimsActionSupport.integer` and `.decimal` | reading the action | `domain/LegacyCoercions` | SETTLE-R02, SETTLE-R03 |
| QUIRK-07 | `calculated_date` on save is the constant `2019-04-01`. | `SettlementSaveAction` | reading the action | `SettlementService.SAVE_DATE` | SETTLE-R12 |

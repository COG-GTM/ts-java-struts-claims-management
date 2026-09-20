# Settlement slice: business rules vs. framework plumbing vs. persistence

Scope: `SettlementCalculateAction`, `SettlementSaveAction`, `SettlementDetailAction`, `SettlementForm`,
`ClaimsActionSupport`, `SettlementCalculator`, `SettlementService`, `SettlementDAO`, and the JSPs under
`src/main/webapp/WEB-INF/jsp/settlement`. Commit `225c8d3`.

Legend: **(a)** domain business rule, **(b)** Struts/JSP framework plumbing, **(c)** persistence.
Where a single method mixes categories it is marked **mixed** and the split is spelled out.

---

## 1. Method-by-method classification

### 1.1 SettlementCalculateAction — `src/main/java/com/northstar/claims/web/SettlementCalculateAction.java`

| Method | Lines | Class | Notes |
|---|---|---|---|
| `execute(ActionMapping, ActionForm, HttpServletRequest, HttpServletResponse)` | 20–43 | **mixed (a)+(b)+(c)** | See split below. |

Split inside `execute`:

- (b) plumbing: read raw request parameters and coerce them — lines 23, 32, 33, 34; publish request attributes
  `settlement`, `policyLimit`, `screenName` — lines 39–41; forward selection `mapping.findForward("settlement")` — line 42.
- (a) business rule: the limit-selection rule "use the policy's limit, else 10000" — lines 25–31 (the literal `10000` at line 25
  is a business default expressed as plumbing; see §2.5).
- (a) business rule: blank/absent deductible is treated as `"0"` — lines 36–37.
- (c) persistence: `findClaim(claimId)` at line 24 (delegates to `ClaimDAO`), `new PolicyDAO().findById(...)` at line 27.
- (a) business rule: the calculated settlement is bound to `claimId` — line 38.

### 1.2 SettlementSaveAction — `src/main/java/com/northstar/claims/web/SettlementSaveAction.java`

| Method | Lines | Class | Notes |
|---|---|---|---|
| `execute(...)` | 21–45 | **mixed (a)+(b)+(c)** | See split below. |

Split inside `execute`:

- (b) plumbing: parameter reads/coercions — lines 24, 27, 28, 29; request attributes `settlement`, `settlementAmount`,
  `screenName` — lines 40–43; forward — line 44.
- (a) business rules: blank deductible → `"0"` — lines 31–32; the policy limit is the cap — line 33; the settlement's
  claim association — line 35; the "calculated by" attribution taken from the session user — lines 36–37; the
  calculation date — line 38 (hardcoded `"2019-04-01"`, see §2.6).
- (c) persistence: `findClaim` line 25, `new PolicyDAO().findById` line 26, `nextId("SETTLEMENT")` line 34,
  `new SettlementDAO().save(value)` line 39.

Note: unlike the calculate path, there is **no** null guard on `claim` or `policy` here (lines 26, 33). See §2.5.

### 1.3 SettlementDetailAction — `src/main/java/com/northstar/claims/web/SettlementDetailAction.java`

| Method | Lines | Class | Notes |
|---|---|---|---|
| `execute(...)` | 14–24 | **mixed (b)+(c)** | No business rule at all. |

- (b) plumbing: `claimId` coercion line 17; attributes `settlement`, `screenName`, `screenMode`, `operatorScope`
  lines 19–22; forward line 23.
- (c) persistence: `new SettlementDAO().findByClaim(id)` line 18.
- No handling for `findByClaim` returning `null` (see §2.7).

### 1.4 SettlementForm — `src/main/java/com/northstar/claims/web/form/SettlementForm.java`

All methods are **(b)** framework plumbing. The bean is declared in `struts-config.xml` lines 26–27 and attached to
`/settlement/calculate` (line 208) and `/settlement/save` (line 217) with `validate="false"`
(`struts-config.xml` lines 210, 219), so `validate()` never runs in these flows.

| Method | Lines | Class |
|---|---|---|
| `SettlementForm()` | 19–21 | (b) |
| `getCoveredAmount` / `setCoveredAmount` | 24–31 | (b) |
| `getDeductible` / `setDeductible` | 34–41 | (b) |
| `getDepreciation` / `setDepreciation` | 44–51 | (b) |
| `getPolicyLimit` / `setPolicyLimit` | 54–61 | (b) |
| `reset(ActionMapping, HttpServletRequest)` | 64–70 | (b) |
| `validate(ActionMapping, HttpServletRequest)` | 73–75 | (b) — delegates to `BaseForm.validate`, `src/main/java/com/northstar/claims/web/form/BaseForm.java` lines 31–33, which always returns an empty `ActionErrors` |

Structural finding: **the form bean is populated by Struts but never read.** Both actions call
`request.getParameter(...)` directly (`SettlementCalculateAction` lines 23, 32–34; `SettlementSaveAction` lines 24, 27–29)
and ignore the `form` argument. `policyLimit` (`SettlementForm` lines 16, 54–61) is never read anywhere in the slice —
the limit always comes from `PolicyDAO` or the hardcoded fallback. So there is zero form-level validation on the
settlement inputs and the form is dead weight in the request path.

### 1.5 ClaimsActionSupport — `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java`

| Method | Lines | Class | Notes |
|---|---|---|---|
| `openConnection()` | 24–29 | (c) | Direct `DriverManager` to `jdbc:hsqldb:file:` + `claims.db.path` (default `target/db/northstar`), bypassing `ConnectionPool` used by the DAOs |
| `integer(String, int)` | 31–37 | **mixed (b)+(a)** | Framework coercion, but the caller-supplied fallback makes it decide business outcomes (§2.1) |
| `decimal(String, double)` | 39–45 | **mixed (b)+(a)** | Same (§2.2, §2.4) |
| `normalizedDate(String)` | 47–59 | **mixed (b)+(a)** | Format conversion (b) with a hardcoded `"2019-04-01"` business default (a); not used by the settlement slice |
| `nextId(String)` | 61–75 | (c) | `select coalesce(max(<table>_id),0)+1` — key allocation |
| `update(String)` | 77–87 | (c) | |
| `emptyList()` | 89–91 | (b) | |
| `findClaim(int)` | 93–100 | **mixed (c)+(a)** | (c) lookup; (a)/(b) failure policy: swallows the exception and returns `null` (line 98), turning a DB error into "claim missing" |
| `selectString(String, int)` | 102–120 | **mixed (c)+(b)** | Returns `""` on miss or failure (lines 112, 115) |
| `selectStrings(String)` | 122–142 | (c) | Returns partial/empty list on failure |
| `countRows(String)` | 144–161 | (c) | Returns `0` on failure (line 155) |
| `selectAmount(String, int)` | 163–181 | **mixed (c)+(a)** | Returns `0` on miss *and* on failure (lines 172, 175) — a money value |
| `putClaimSummary(HttpServletRequest, Claim)` | 183–198 | **mixed (b)+(a)** | (b) attribute publishing; (a) the "`UNKNOWN` status / `0` reserve when claim is null" defaults, lines 186–187 |
| `currentOperator(HttpServletRequest)` | 200–204 | (b) | `"unknown"` when no session user |
| `hasText(String)` | 206–208 | (b) | |
| `defaultText(String, String)` | 210–212 | (b) | |
| `quote(String)` | 214–219 | (c) | SQL literal quoting for string-concatenated SQL |
| `money(double)` | 221–224 | (b) | Presentation formatting, `%.2f`, US locale |
| `operatorDate()` | 226–228 | **(a)** | Hardcoded `"2019-04-01"` business date |
| `logScreen(String)` | 230–233 | (b) | |
| `safeList(List)` | 235–237 | (b) | |
| `firstValue(List, String)` | 239–244 | (b) | |
| `normalizeStatus(String)` | 246–251 | **(a)** | Default `"OPEN"` |
| `normalizeMethod(String)` | 253–258 | **(a)** | Default `"CHECK"` |
| `normalizeLossType(String)` | 260–265 | **(a)** | Default `"WATER"` |
| `validDateShape(String)` | 267–269 | (b) | |
| `reportDate()` | 271–273 | **(a)** | Hardcoded `"2019-04-01"` |
| `closeQuietly(ResultSet)` | 275–277 | (c) | |
| `closeQuietly(PreparedStatement)` | 279–281 | (c) | |
| `closeQuietly(Statement)` | 283–285 | (c) | |
| `closeQuietly(Connection)` | 287–289 | (c) | |
| `claimLabel(Claim)` | 291–296 | (b) | `"Unknown claim"` display default |
| `policyLabel(Policy)` | 298–304 | (b) | `"Unknown policy"` display default |
| `approvedStatus(String)` | 306–309 | **(a)** | `APPROVED` or `CLOSED` counts as approved |
| `openStatus(String)` | 311–314 | **(a)** | `OPEN` or `INVESTIGATING` counts as open |
| `safeParameter(HttpServletRequest, String)` | 316–320 | (b) | |
| `rememberScreen(HttpServletRequest, String)` | 322–326 | (b) | |
| `financialAmount(double)` | 328–330 | **(a)** | Amount band `[0, 100000000)`; **not called anywhere in the settlement slice** — the settlement amount is never range-checked |

Of these, the settlement slice only uses `integer`, `decimal`, `findClaim`, `nextId` (and the inherited `log`).

### 1.6 SettlementCalculator — `src/main/java/com/northstar/claims/service/SettlementCalculator.java`

| Method | Lines | Class | Notes |
|---|---|---|---|
| `SettlementCalculator()` (private ctor) | 13–14 | (b) | Singleton scaffolding |
| `getInstance()` | 16–21 | (b) | Lazy singleton, synchronized |
| `calculate(double, String, double, double)` | 24–46 | **(a)** | The only real domain rule in the slice; the `String deductible` parameter and its parse at lines 27–29 are a framework-shaped intrusion into a domain method (§2.3) |
| `round(double)` | 48–50 | **(a)** | Cent rounding rule; duplicated inline at line 37 and unused by the actions |

The domain rule in `calculate`, in order (lines 30–37):
1. `gross = coveredAmount - depreciation` (line 30) — depreciation is applied **before** the deductible.
2. `afterDeductible = gross - deductibleValue` (line 31).
3. Floor at zero: negative results become `0` (lines 32–34). Only applied after the deductible; a negative `gross`
   (depreciation > coveredAmount) is not floored before the deductible is subtracted, but the final floor makes the
   two equivalent for the returned amount.
4. Policy cap: `capped = afterDeductible > policyLimit`, `amount = capped ? policyLimit : afterDeductible` (lines 35–36).
   The cap is applied **after** the deductible, not to the gross loss.
5. Rounding to cents (line 37, §2.8).
6. Result fields (lines 39–43): `coveredAmount`, `deductibleApplied`, `depreciation`, `cappedAtLimit`, `settlementAmount`
   are set; `claimId`, `settlementId`, `calculatedBy`, `calculatedDate` are left to the caller.

### 1.7 SettlementService — `src/main/java/com/northstar/claims/service/SettlementService.java`

**Dead code in this slice**: `grep` across `src` finds no reference to `SettlementService` outside its own file —
no action, JSP or config uses it. It is a second, divergent settlement write path.

| Method | Lines | Class | Notes |
|---|---|---|---|
| `SettlementService()` | 15–17 | (c) | Constructs its own `SettlementDAO` |
| `calculateAndSave(int, double, String, double, double)` | 19–31 | **mixed (a)+(c)** | (a) id/attribution/date rules at lines 25–27: `settlementId = claimId + 10000`, `calculatedBy = "supervisor"`, `calculatedDate = "2019-03-01"`; (c) `dao.save` line 28. These contradict `SettlementSaveAction` (`nextId("SETTLEMENT")`, session user, `"2019-04-01"`) and the synthetic id can collide with `nextId`-allocated keys |
| `findByClaim(int)` | 33–35 | (c) | Pass-through |
| `save(Settlement)` | 37–39 | (c) | Pass-through |

### 1.8 SettlementDAO — `src/main/java/com/northstar/claims/dao/SettlementDAO.java`

All methods are **(c)** persistence; none contain a business rule.

| Method | Lines | Class | Notes |
|---|---|---|---|
| `findById(int)` | 26–44 | (c) | `select * from SETTLEMENT where settlement_id = ?`; `null` when absent (line 38) |
| `findAll()` | 47–65 | (c) | Ordered by `settlement_id` |
| `count()` | 68–82 | (c) | |
| `delete(int)` | 85–97 | (c) | |
| `findByClaim(int)` | 100–115 | **mixed (c)+(a)** | "Latest settlement for a claim" is a selection rule encoded as `order by settlement_id desc` + first row (lines 106, 109): latest = highest surrogate id, not latest `calculated_date`. Returns `null` when the claim has no settlement |
| `save(Settlement)` | 118–142 | (c) | Prepared insert of all nine columns (lines 123–137); no update path, so every save appends a new row |
| `read(ResultSet)` | 145–157 | (c) | Row → bean mapping |
| `trace(String)` | 160–163 | (c) | Unused private logging helper; also writes to `System.out` |

### 1.9 JSPs under `src/main/webapp/WEB-INF/jsp/settlement`

Every construct in these three pages is **(b)** framework/presentation plumbing. There is no calculation in the views.

`calculate.jsp`:
- lines 1–7 page/taglib directives and `<ns:view>` marker; lines 8–15 layout, banner, nav includes.
- line 17 `detail.do?claimId=${claimId}` — note `claimId` is **not** set as a request attribute by
  `SettlementCalculateAction`, so this link renders as `detail.do?claimId=` (see §2.7).
- lines 20, 22, 24, 26, 28 `<ns:field type="money">` renders (b); line 26 formats the **boolean** `cappedAtLimit`
  with `type="money"`, which falls through `FieldTag.formatValue` to the raw text `true`/`false`
  (`src/main/java/com/northstar/claims/web/tag/FieldTag.java` lines 52–58).
- line 30 `save.do` link carries **no parameters**, so following it re-runs the save with all defaults (§2.1).
- lines 38, 42 audit rows: session `user`, and `report.asof.date` from `northstar.properties` line 2 (`2019-04-01`).

`save.jsp`:
- lines 1–15 directives/layout; line 19 renders `${settlementAmount}` (set by `SettlementSaveAction` line 41);
  lines 21–22 renders `${sessionScope.user}`; lines 25–37 audit block; line 36 `report.asof.date`.

`detail.jsp`:
- lines 1–6 directives/includes; lines 10–26 field renders off `${settlement.*}`;
- line 24 `<bean:write name="settlement" property="calculatedBy"/>` requires the `settlement` attribute to exist —
  it throws when the claim has no settlement row (§2.7);
- lines 28–41 audit block; line 39 `report.asof.date`.

Formatting behaviour that belongs to the view layer (`FieldTag`, lines 48–77): `null` renders as empty string;
`money` is `String.format("%.2f", ...)` with the **JVM default locale** (line 54 — unlike
`ClaimsActionSupport.money`, line 222, which pins `Locale.US`), and unparseable input is echoed verbatim;
`integer` truncates via `(long) Double.parseDouble(...)`; `date` re-formats `yyyy-MM-dd` to itself.

---

## 2. Framework coercions and hardcoded defaults that decide a business outcome

### 2.1 Blank or unparseable `claimId` → **119**

- `SettlementCalculateAction` line 23: `int claimId = integer(request.getParameter("claimId"), 119);`
- `SettlementSaveAction` line 24: same call, same literal `119`.
- `SettlementDetailAction` line 17: same call, same literal `119`.
- Mechanism: `ClaimsActionSupport.integer` lines 31–37 catches **every** exception from `Integer.parseInt`, which
  includes `NumberFormatException` for `""`, `"abc"`, `"12.5"` and `NullPointerException` for a missing parameter.
- Outcome: any missing/garbage `claimId` silently targets **claim 119**. On the save path this writes a real
  `SETTLEMENT` row attached to claim 119 (`SettlementSaveAction` line 35, `SettlementDAO.save` lines 118–142) and uses
  claim 119's policy limit. Following `calculate.jsp` line 30 (`save.do` with no parameters) does exactly this.
- Source of the default: the literal `119` in each action; it is not in `northstar.properties` or `struts-config.xml`.

### 2.2 Blank or unparseable `coveredAmount` → **5000.0**

- `SettlementCalculateAction` line 32 and `SettlementSaveAction` line 27:
  `double covered = decimal(request.getParameter("coveredAmount"), 5000);`
- Mechanism: `ClaimsActionSupport.decimal` lines 39–45, same catch-all as above (`null`, `""`, `"abc"`, `"1,000"`,
  `"$1000"` all fall back). Note `Double.parseDouble` *does* accept `" 500 "`, `"1e3"`, `"Infinity"` and `"NaN"`.
- Outcome: a blank covered amount produces a settlement built on a phantom 5000.00 loss rather than an error.
  Hardcoded literal in both actions.

### 2.3 Blank `deductible` → **"0"**; unparseable `deductible` → **HTTP 500**

- `SettlementCalculateAction` lines 34, 36–37 and `SettlementSaveAction` lines 29, 31–32: the deductible is the one
  input **not** passed through `decimal(...)`. It is kept as a `String` and only `null`/empty is mapped to `"0"`.
- `SettlementCalculator.calculate` lines 27–29 then calls `Double.parseDouble(deductible)` **outside any try/catch**;
  the blank check there (line 27, `trim().length() > 0`) is redundant with the action-level check but *does* additionally
  absorb an all-whitespace value (`"   "`) to `0`.
- Outcome asymmetry: `deductible=abc` throws `NumberFormatException` out of `execute`, so Struts renders the error page
  and, on the save path, **nothing is persisted** — whereas `coveredAmount=abc` is silently coerced to 5000 and saved.
  Same field family, two different failure semantics.

### 2.4 Blank or unparseable `depreciation` → **0.0**

- `SettlementCalculateAction` line 33, `SettlementSaveAction` line 28: `decimal(request.getParameter("depreciation"), 0)`.
- Outcome: garbage input means "no depreciation", which raises the settlement rather than rejecting the request.
  Hardcoded literal in both actions.

### 2.5 Missing claim or policy → limit **10000** on calculate, **crash** on save

Calculate path (`SettlementCalculateAction` lines 25–31):
- `double limit = 10000;` (line 25) is the hardcoded seed.
- `findClaim(claimId)` (line 24 → `ClaimsActionSupport` lines 93–100) returns `null` both when the claim does not exist
  **and** when the lookup throws (the exception is logged and swallowed at lines 97–99). In either case the policy is
  never loaded and the cap stays at 10000.
- If the claim exists but `PolicyDAO.findById` returns `null` (`src/main/java/com/northstar/claims/dao/PolicyDAO.java`
  lines 26–44, line 38) the cap also stays at 10000.
- So "claim missing", "database down" and "policy missing" are indistinguishable and all yield a 10000 cap that is
  published to the page as `policyLimit` (line 40).

Save path (`SettlementSaveAction` lines 25–26, 33):
- There is **no null check**. `claim.getPolicyId()` (line 26) throws `NullPointerException` when the claim is absent,
  and `policy.getPolicyLimit()` (line 33) throws when the policy is absent.
- Outcome: the save path 500s instead of falling back to 10000. The same request that renders fine on
  `/settlement/calculate.do` fails on `/settlement/save.do`. The 10000 default therefore exists on the preview screen
  only, which means the previewed amount can differ from what any successful save would have produced.

### 2.6 Other hardcoded values written to the settlement row

- `SettlementSaveAction` line 38: `value.setCalculatedDate("2019-04-01")` — the persisted calculation date is a
  constant, not the current date. The same constant appears in `ClaimsActionSupport.normalizedDate` (lines 49, 58),
  `operatorDate()` (line 227), `reportDate()` (line 272) and `northstar.properties` line 2 (`report.asof.date`),
  but each is its own literal — changing one does not change the others.
- `SettlementSaveAction` lines 36–37: `String.valueOf(request.getSession().getAttribute("user"))` — with no session
  user this stores the four-character string `"null"` in `CALCULATED_BY`, not SQL `NULL`.
- `SettlementSaveAction` line 34: `nextId("SETTLEMENT")` (`ClaimsActionSupport` lines 61–75) allocates the key with
  `select coalesce(max(settlement_id),0)+1` in a separate connection from the insert — two concurrent saves can pick
  the same id.
- `SettlementService` lines 25–27 (unused path): `settlementId = claimId + 10000`, `calculatedBy = "supervisor"`,
  `calculatedDate = "2019-03-01"` — a different id scheme, actor and date than the live action.

### 2.7 Missing settlement on the detail screen

- `SettlementDetailAction` line 18 stores whatever `SettlementDAO.findByClaim` returns, including `null`
  (`SettlementDAO` line 109), with no guard.
- `detail.jsp` line 24 `<bean:write name="settlement" .../>` fails when the attribute is absent, so a claim with no
  settlement yields an error page rather than an empty screen (the `<ns:field value="${settlement.x}">` renders on
  lines 10–26 would themselves have degraded to blanks).
- Related view-level gap: `calculate.jsp` line 17 builds `detail.do?claimId=${claimId}`, but neither
  `SettlementCalculateAction` nor `SettlementSaveAction` sets a `claimId` request attribute (the calculate action sets
  `settlement`, `policyLimit`, `screenName` at lines 39–41). The link therefore sends an empty `claimId`, which
  `SettlementDetailAction` line 17 coerces back to **119** per §2.1.

### 2.8 Exactly how the settlement amount is rounded

- `SettlementCalculator.calculate` line 37: `double rounded = Math.round(amount * 100.0) / 100.0;`
  (identically duplicated in `round(double)`, line 49, which nothing in the slice calls).
- Semantics: binary `double` throughout, no `BigDecimal`. `Math.round(double)` is *half-up toward positive infinity*
  on the already-rounded binary product `amount * 100.0`, then integer-valued division by `100.0` reintroduces binary
  representation error.
- Consequence for "half cent" values: `1.005 * 100.0` is `100.49999999999999` in binary, so `Math.round` gives `100`
  and the result is `1.00`, not `1.01`. This is asserted as intended behaviour in
  `src/test/java/com/northstar/claims/SettlementCalculatorTest.java` lines 41–45 (`halfCentUsesLegacyDoubleMath`).
- Order: rounding is the **last** step — after depreciation (line 30), after the deductible (line 31), after the
  zero floor (lines 32–34) and after the cap (lines 35–36). The cap comparison at line 35 uses the **unrounded**
  `afterDeductible`, so `cappedAtLimit` can be `true` while `settlementAmount` equals the limit exactly, and a
  policy limit carrying more than two decimals is itself rounded on the way out.
- No rounding happens anywhere else: `SettlementDAO.save` line 134 stores the already-rounded `double` into
  `SETTLEMENT_AMOUNT`, and `FieldTag` (lines 52–58) only formats for display.

---

## 3. Items I cannot classify cleanly, and why

1. **`SettlementCalculateAction.execute` / `SettlementSaveAction.execute`** — irreducibly mixed. Parameter coercion,
   default selection, the calculation call, id/actor/date assignment and persistence all live in one method, so no
   single label applies. Broken out in §1.1 and §1.2.
2. **`ClaimsActionSupport.integer` / `decimal`** (lines 31–45) — pure framework coercion by shape, but because the
   fallback is a silently-applied value rather than an error, each call site turns them into a business default
   (119 / 5000 / 0). Their classification depends entirely on the caller.
3. **The literal `10000`** (`SettlementCalculateAction` line 25) — written as a variable initialiser (plumbing style),
   but it is the policy cap applied to a real settlement preview, i.e. a business rule. I cannot tell whether it is a
   deliberate "unknown policy" limit or just a seed value that was never meant to survive a failed lookup; there is no
   comment, config key or test covering it.
4. **`SettlementCalculator.calculate`'s `String deductible` parameter** (line 24) — a domain method taking a
   presentation-layer type and doing its own parse (lines 27–29). Domain by intent, plumbing by signature; it is also
   the reason the unparseable-deductible case escapes as an exception (§2.3).
5. **`SettlementDAO.findByClaim`** (lines 100–115) — labelled persistence, but `order by settlement_id desc` encodes
   the business definition of "current settlement" as "highest surrogate key". Whether that is meant to be the latest
   by time is not recorded anywhere; with `SettlementService.calculateAndSave`'s `claimId + 10000` ids (line 25) it
   would not even be monotonic in time.
6. **`SettlementService` as a whole** — unreferenced (no hits outside its own file). I cannot tell whether it is
   abandoned or a not-yet-wired replacement for `SettlementSaveAction`, and its constants (lines 25–27) disagree with
   the live path, so its rules are neither dead nor authoritative with confidence.
7. **`ClaimsActionSupport.financialAmount`** (lines 328–330) — a business range check `[0, 100000000)` that nothing in
   the settlement slice calls. Cannot tell whether settlement amounts were meant to be validated against it.
8. **`ClaimsActionSupport.openConnection`** (lines 24–29) — persistence, but it opens a raw `DriverManager` connection
   while `SettlementDAO`/`PolicyDAO` use `ConnectionPool`. `nextId` (used by the save path, line 34 of the save action)
   therefore runs on a different connection than the insert, which is why I treat the id allocation as non-transactional
   in §2.6; I have not verified the pool's isolation configuration.
9. **`SettlementForm`** — classified entirely as plumbing, but strictly speaking it is inert: Struts populates it and
   `validate="false"` (`struts-config.xml` lines 210, 219) plus the actions' direct `getParameter` calls mean it has no
   effect on any outcome. `policyLimit` in particular looks like a business input that was never wired.
10. **`calculate.jsp` line 26** — `cappedAtLimit` rendered with `type="money"`. Presentation plumbing, but the output is
    the raw `true`/`false` text rather than a money value, so I cannot tell whether the intended display was a flag or
    the capped amount.

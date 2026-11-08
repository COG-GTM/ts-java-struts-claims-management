# Policy area

## Decisions

* Seven legacy `.do` URLs accept the same request methods through `PolicyController`.
* `PolicySearchForm` and `PolicyViewForm` retain request strings; parsing uses `ClaimsSupport.integer` rather than Spring numeric binding. The remaining actions had no Struts form. Unused inherited BaseForm strings have no observable effect.
* All seven Struts forwards are local, not redirects. All mappings disable validation; `validation.xml` has no rules for these forms (`searchDyn` is unrelated). No new error keys are introduced.
* Inject the existing Wave 0 `PolicyDAO`: its SQL, mapping and ordering already match the legacy DAO. There is no inline policy SQL to move and no new repository is needed.
* Templates reuse layout and `ns` fragments, original message keys, raw field values and original JSP marker paths. The duplicate detail-link paragraph remains.
* Missing policy beans forward to the existing HTTP 200 error page, matching legacy `bean:write` failure, rather than rendering a null-safe policy screen.
* No shared files, legacy files, parity scripts or snapshots were edited.

## Preserved quirks / findings (not fixed)

* Search defaults only null/empty to `AUTO`; whitespace is not trimmed.
* Search SQL concatenates user input; malformed SQL becomes an empty successful result. This injection risk is retained solely for migration parity and needs separate hardening.
* Search orders by policy number; list orders by numeric policy ID.
* Search/list swallow database failures and render empty lists; insured-party swallows lookup failures and fabricates a bean; other actions use the global HTTP 200 error forward.
* View/coverages default ID to `1`; coverage-detail/insured-party/renewal default to `9001`, including malformed numeric strings.
* Both coverage screens use empty coverage lists, even when a policy exists.
* Insured-party overwrites number/name/address/dates/status with synthetic fixed values, even for real policies, and never saves them.
* Renewal falls back to policy `9001` only for a missing row, not a database exception; there is no renewal update.
* Duplicate links and fragment formatting (including locale-sensitive money and date behavior) remain unchanged.

## Verification

`make -C modernized build`, `make -C modernized replay AREA=policy`, `make -C modernized test`.
`PolicyIntegrationTest` uses real JDBC with legacy schema/seed and MockMvc to cover all seven screens, search defaults/empty/error results, missing IDs, fabricated insured data, fallback, database errors and read-only state.

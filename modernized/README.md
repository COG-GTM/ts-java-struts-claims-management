# NorthStar Claims — Wave 0

Requires Maven and JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`). From the repository:

```
make -C modernized build
make -C modernized test
make -C modernized seed   # stop the modern server first
make -C modernized run
```

Open http://localhost:8081/claims/login.do. Use supervisor/supervisor (or the preserved adjuster*/legacy* prefix rule). The database is `modernized/target/db/northstar.*`; startup **never** seeds. Override with `java -Dclaims.db.path=/absolute/path/northstar -jar target/claims.jar` from `modernized/`. Seed accepts the same system property through Maven `-Dclaims.db.path=...`. Do not open either file database while its server owns it.

## Area stream conventions

* Controllers: `com.northstar.claims.web.<area>`; templates: `src/main/resources/templates/<area>/`.
* Preserve URLs, request parameters, HTTP status, JSP view marker paths, and ApplicationResources keys exactly. Validation is a 200 forward, not a redirect.
* Domain beans live in `domain`. Inject repositories (`repository.*DAO`), service beans, and `support.ClaimsSupport` using constructors. No singleton `getInstance()` methods or manually constructed DAOs remain. DAO method signatures and raw list/map conventions are retained to ease action ports; SQL and ordering are unchanged.
* Inline action SQL belongs to each area stream, **not** this foundation. Use Spring JDBC; never introduce another connection pool. `ClaimsSupport` exposes the old parsing, formatting and scalar/list helpers with their old fallbacks.
* Reuse `fragments/layout :: header`, `nav`, `audit`, `footer` and `fragments/ns :: field(name,value,type)`, `view(path)`, `errors(list)`. Field fragment values should be the original JSP EL values, without pre-formatting. The `ns` bean emits already-escaped HTML for `th:utext`.
* Workbench owns `/workbench/view.do` after Wave 0; its existing controller is the read-only state probe, not a second mapping to duplicate.

## Parity

Stop both servers before running these commands. Legacy always uses JDK 17; modern always uses JDK 21.

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 python3 modernized/parity/snapshot_legacy.py
make -C modernized replay AREA=foundation
make -C modernized replay AREA=intake
make -C modernized parity
```

Snapshots are committed, so children normally only need `build` and `replay`. Replay restores **each** scenario's legacy pre-state, starts/stops the modern jar, logs in, and invokes capture's exact extraction and serialization. Areas: foundation, policy, intake, workbench, settlement, payment, reports, all. Full parity instead seeds once and runs the ordered stateful chain; it returns nonzero until every JSON and index match. Wave 0 only implements the login scenario; other area failures are expected.

Server logs: `modernized/parity/{legacy,modern}.log`. Full-run JSON/diffs are retained under `modernized/target/parity.*`. Scripts stop only processes they start and refuse to drive an already-running target, to avoid corrupting unrelated developer sessions.

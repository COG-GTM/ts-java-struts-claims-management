# NorthStar Claims Management

NorthStar Claims Management is a deliberately dated Apache Struts 1.3.10
property and casualty claims system. It uses Servlet 2.4 deployment
descriptors, JSP runtime compilation, raw JDBC, and an HSQLDB file database.
The source reflects the conventions and maintenance history of a long-lived
enterprise application.

## Module map

The default Struts module contains policy search and viewing, FNOL intake,
claim workbench, settlement, payment, and reporting screens. The `/admin`
module contains adjuster and reference-data screens. Data access is split
between raw JDBC DAOs and several direct-JDBC workflow actions.

## Build and run

```text
make build
make test
make seed
make run
```

The application is served at `http://localhost:8080/claims/`. The default
operator login is `supervisor` / `supervisor`. Maven is the supported build;
`build.xml` is retained as the official historical Ant build.

## Database reset

The database is stored under `target/db/northstar`. `make seed` recreates the
schema and loads the fixed literal data. `make clean` removes generated
database files and build output.

## Transcript harness

`make capture` resets the database, starts Jetty, logs in, executes the fixed
workflow scenario list, writes normalized JSON transcripts under
`transcripts/`, and shuts Jetty down. The transcripts are fixtures for a
replay harness and expose semantic values through the `<ns:field>` extraction
spans.

## Known issues / technical debt

The application declares a container-managed JNDI datasource but retains a
DriverManager fallback because deployment environments differ. Several
actions contain direct SQL, SQL construction is inconsistent, and the
connection pool is hand-rolled. JSPs use shared includes rather than a
proper layout framework. The Maven Jetty run emits duplicate JSTL scanning
warnings. Authentication uses plaintext-style adjuster credentials.

This repository is the legacy source of truth. Modernization work belongs in
a separate target repository and should not rewrite this baseline in place.

## Settlement slice rehearsal (branch `rehearsal`)

The `rehearsal` branch carries one modernised slice next to the untouched
Struts baseline so that specification, tests, service and matrix version
together (see `docs/decisions/ADR-001-settlement-boundary.md` for why this
departs from the separate-repository advice above). Nothing under `src/main`
changes; the additions are:

| Path | Purpose |
| --- | --- |
| `docs/analysis/settlement-slice.md` | current-state analysis of the settlement screens |
| `docs/specs/SPEC-SETTLE-001.md`, `docs/specs/OPEN-QUESTIONS.md` | versioned rules, one open SME question |
| `docs/decisions/ADR-001-settlement-boundary.md` | Struts-to-Spring boundary decision |
| `docs/changes/CHG-001-invalid-deductible.md` | the one approved behaviour change |
| `docs/KNOWN_LEGACY_QUIRKS.md` | legacy behaviours kept on purpose |
| `src/test/java/com/northstar/claims/SettlementCharacterizationTest.java` | legacy characterisation tests pinned to the transcripts |
| `services/settlement-service/` | Spring Boot 3.5 / Java 21 settlement service |
| `parity/replay.py`, `parity/routes.json` | replays `transcripts/` against the service |
| `tools/traceability.py`, `docs/TRACEABILITY.md` | rule to spec version to commit to test to parity verdict |

```
make service-test          # service unit and controller tests (Java 21)
make service-run           # service on http://localhost:8083
make parity                # replay transcripts, writes parity/report.md
make traceability          # regenerate docs/TRACEABILITY.md
```

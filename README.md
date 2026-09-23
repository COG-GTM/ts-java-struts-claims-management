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

The application is served at `http://localhost:8080/claims/`. Operator logins
come from the seeded ADJUSTER rows, whose credentials are stored as salted
PBKDF2 hashes; the local demo account is `supervisor` / `supervisor` and should
be rotated before any shared deployment. Maven is the supported build;
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
warnings. Authentication resolves adjuster credentials from the database.

This repository is the legacy source of truth. Modernization work belongs in
a separate target repository and should not rewrite this baseline in place.

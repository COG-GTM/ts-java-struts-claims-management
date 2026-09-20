# settlement-service

The settlement module of the NorthStar Claims monolith, extracted per
`docs/specs/SPEC-SETTLE-001.md` and `docs/decisions/ADR-001-settlement-boundary.md`.
Spring Boot 3 on Java 21, port 8083, PostgreSQL schema `settlement` managed by Flyway.

## Routes

| Route | Legacy action | Forward it declares |
| --- | --- | --- |
| `POST /claims/settlement/calculate.do` | `SettlementCalculateAction` | `/WEB-INF/jsp/settlement/calculate.jsp` |
| `POST /claims/settlement/save.do` | `SettlementSaveAction` | `/WEB-INF/jsp/settlement/save.jsp` |
| `POST /claims/settlement/detail.do` | `SettlementDetailAction` | `/WEB-INF/jsp/settlement/detail.jsp` |

Responses are not JSON: each carries the `<!-- ns:view PATH -->` marker and the
`<span id="f_NAME">` field spans of the legacy screens, so `parity/replay.py` compares the
service and the transcripts with one parser. Failures answer HTTP 200 with the
`/WEB-INF/jsp/error.jsp` marker, matching the Struts `global-exceptions` entry (SETTLE-R18).

## Behaviour notes

- Arithmetic is the legacy binary-double arithmetic, including
  `Math.round(amount * 100.0) / 100.0`; a move to `BigDecimal` would change results
  (SETTLE-R28, OQ-05).
- `calculate` falls back to a 10000.00 limit for an unknown claim, `save` fails
  (SETTLE-R49, SETTLE-R15, OQ-03).
- An invalid non-blank deductible follows CHG-001 (SETTLE-R18 v2) on `calculate`: HTTP 200,
  the calculate screen, the key `settlement.deductible.invalid` and no settlement. `save` keeps
  the current SETTLE-R18 error page, because OQ-15(a) leaves that route undecided. The
  redisplayed spans are empty, the conservative reading of OQ-15(c).
- The operator is the fixed `supervisor` of the transcripts and the date the fixed
  `2019-04-01` of the legacy action (SETTLE-R20, SETTLE-R40, SETTLE-R41).

## Commands

```
make service-build   # package the jar (Java 21)
make service-test    # unit tests, each named after the SETTLE-R rules it covers
make service-lint    # checkstyle
make sast            # spotbugs + findsecbugs
make up / make down  # compose stack (NS and PORT_OFFSET for a second stack)
make parity          # replay the settlement transcripts, writes parity/report.md
```

## Data

Flyway migrations create the policy, claim and settlement tables the screens touch and seed
policies 9001/9002, claims 119/120 and the two settlement rows with the values
`com.northstar.claims.util.DatabaseBootstrap` loads from `src/main/resources/db/seed.sql`.

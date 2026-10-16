.PHONY: build run seed test capture clean service-build service-test service-run payment-build payment-test payment-run payment-run-parity payment-scenarios payment-parity parity traceability payment-spec-proof payment-spec-check payment-spec-html

# Settlement service (ADR-001): Spring Boot 3.5 on Java 21, port 8083.
SERVICE_DIR = services/settlement-service
SERVICE_JAVA_HOME = /usr/lib/jvm/java-21-openjdk-amd64

build:
	mvn -B clean package

run:
	mvn jetty:run

seed:
	mvn -q org.codehaus.mojo:exec-maven-plugin:3.3.0:java

test:
	mvn -B clean verify

capture:
	python3 tools/capture/capture.py

service-build:
	cd $(SERVICE_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B clean package -DskipTests

service-test:
	cd $(SERVICE_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B clean verify

service-run:
	cd $(SERVICE_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B spring-boot:run

PAYMENT_DIR = services/payment-service

payment-build:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B clean package -DskipTests

payment-test:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B clean verify

payment-run:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B spring-boot:run

# The payment service with the parity profile: adds the loopback-only
# POST /parity/reset and GET /parity/rows that make payment-parity uses (ADR-002).
payment-run-parity:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B spring-boot:run -Dspring-boot.run.profiles=parity

# Regenerates parity/preconditions/payment/*.sql and the payment_scenarios block
# of parity/routes.json from SPEC-PAY-001-live.json, the spec and the transcripts.
payment-scenarios:
	python3 tools/parity/payment_scenarios.py

payment-parity:
	python3 tools/parity/payment_scenarios.py --check
	python3 tools/parity/payment_parity.py

# Replays the settlement transcripts against a running settlement service (make
# service-run) and the payment scenarios against a running payment service
# (make payment-run-parity); both write parity/report.{json,md,html}.
parity:
	python3 tools/parity/settlement_parity.py
	python3 tools/parity/payment_scenarios.py --check
	python3 tools/parity/payment_parity.py

# Regenerates docs/TRACEABILITY.md from the spec, the git history, the tests,
# parity/routes.json and parity/report.json. Needs nothing running.
traceability:
	python3 tools/traceability.py

# SPEC-PAY-001 live proof: resets the database to the seed, starts the legacy
# app on 8080 (stop any running one first), replays the numbered requests and
# writes docs/specs/SPEC-PAY-001-live.json.
payment-spec-proof:
	python3 tools/spec/pay_live_proof.py

# Same replay, but fails instead of overwriting when the legacy responses or
# PAYMENT rows differ from the committed SPEC-PAY-001-live.json.
payment-spec-check:
	python3 tools/spec/pay_live_proof.py --check

# Writes the self-contained docs/specs/SPEC-PAY-001.html from the Markdown spec,
# the live proof, the transcripts and the cited legacy source lines.
payment-spec-html:
	python3 tools/spec/build_spec_html.py

clean:
	mvn -q clean
	rm -rf data target/db

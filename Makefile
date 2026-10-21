.PHONY: build run seed test capture clean service-build service-test service-run parity traceability payment-spec-proof payment-spec-check payment-spec-html payment-build payment-test payment-run payment-run-parity changes

# Settlement service (ADR-001): Spring Boot 3.5 on Java 21, port 8083.
SERVICE_DIR = services/settlement-service
SERVICE_JAVA_HOME = /usr/lib/jvm/java-21-openjdk-amd64

# Payment service (ADR-002): Spring Boot 3.5 on Java 21, port 8084.
PAYMENT_DIR = services/payment-service

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

payment-build:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B package -DskipTests

# No clean: a running payment-run-parity serves from target/.
payment-test:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B verify

payment-run:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B spring-boot:run

# The payment service with the loopback-only /parity endpoints that make parity
# uses to apply each scenario's precondition (parity/preconditions/payment.sql).
payment-run-parity:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B spring-boot:run -Dspring-boot.run.arguments=--parity.enabled=true

# Replays every slice's scenarios against its running service: settlement
# (make service-run, 8083) then payment (make payment-run-parity, 8084).
# Writes parity/report.json, report.md and report.html.
parity:
	python3 tools/parity/settlement_parity.py
	python3 tools/parity/payment_parity.py

# Regenerates docs/TRACEABILITY.md from the spec, the git history, the tests,
# parity/routes.json and parity/report.json. Needs nothing running.
traceability:
	python3 tools/traceability.py

# Impact block and HTML twin of every docs/changes/CHG-nnn record (after make parity).
changes:
	python3 tools/changes.py

# SPEC-PAY-001 live proof: every case on a fresh seeded database against the
# legacy app (mvn jetty:run, port 8080; stop any running app first).
payment-spec-proof:
	python3 tools/spec/payment_proof.py

# Re-runs the same requests and fails if legacy behaviour moved.
payment-spec-check:
	python3 tools/spec/payment_proof.py --check

# Checks every SPEC-PAY-001 citation and regenerates docs/specs/SPEC-PAY-001.html.
payment-spec-html:
	python3 tools/spec/spec_html.py

clean:
	mvn -q clean
	rm -rf data target/db

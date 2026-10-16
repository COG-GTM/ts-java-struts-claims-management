.PHONY: build run seed test capture clean service-build service-test service-run payment-build payment-test payment-run payment-run-parity payment-legacy-observe parity traceability

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
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B clean package -DskipTests

payment-test:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B clean verify

payment-run:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B spring-boot:run

# The payment service with the parity profile, which adds
# POST /parity/reset?precondition=<file> so make parity can put each payment
# scenario on its own database precondition (parity/preconditions/payment).
payment-run-parity:
	cd $(PAYMENT_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B spring-boot:run -Dspring-boot.run.profiles=parity

# Records the legacy response for each approved difference that has no
# transcript (parity/legacy/<scenario>.json). Seeds the database and starts its
# own Jetty on 8080, as make capture does, so stop make run first.
payment-legacy-observe:
	python3 tools/parity/legacy_observe.py

# Replays the settlement transcripts against a running settlement service
# (make service-run, 8083) and the payment transcripts and approved payment
# differences against a running payment service (make payment-run-parity,
# 8084). Both write into the same report (report.json, report.md and the
# interactive report.html), which is removed first so a run that stops early
# cannot leave the other slice's verdicts from an earlier run looking fresh.
parity:
	rm -f parity/report.json parity/report.md parity/report.html
	python3 tools/parity/settlement_parity.py
	python3 tools/parity/payment_parity.py

# Regenerates docs/TRACEABILITY.md from both specs, the git history, the tests,
# parity/routes.json and parity/report.json. Needs nothing running.
traceability:
	python3 tools/traceability.py

clean:
	mvn -q clean
	rm -rf data target/db

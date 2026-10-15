.PHONY: build run seed test capture clean service-build service-test service-run parity traceability spec-history

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

# Replays the settlement transcripts against a running service (make service-run).
parity:
	python3 tools/parity/settlement_parity.py

# Regenerates docs/TRACEABILITY.md from the spec, the git history, the tests,
# parity/routes.json and parity/report.json. Needs nothing running.
traceability:
	python3 tools/traceability.py

# Regenerates docs/specs/SPEC-SETTLE-001-history.md from the git history of the
# spec, the tests and parity/routes.json. Needs the full history, nothing running.
spec-history:
	python3 tools/spec_history.py

clean:
	mvn -q clean
	rm -rf data target/db

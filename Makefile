.PHONY: build run seed test capture clean service-build service-test service-run parity traceability payment-spec-proof payment-spec-html

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

# SPEC-PAY-001 live proof: resets the database to the seed, starts the legacy
# app on 8080 (stop any running one first), replays the numbered requests and
# writes docs/specs/SPEC-PAY-001-live.json.
payment-spec-proof:
	python3 tools/spec/pay_live_proof.py

# Writes the self-contained docs/specs/SPEC-PAY-001.html from the Markdown spec,
# the live proof, the transcripts and the cited legacy source lines.
payment-spec-html:
	python3 tools/spec/build_spec_html.py

clean:
	mvn -q clean
	rm -rf data target/db

.PHONY: build run seed test capture clean service-build service-test service-run parity

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

clean:
	mvn -q clean
	rm -rf data target/db

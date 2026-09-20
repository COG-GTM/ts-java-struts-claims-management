.PHONY: build run seed test capture clean \
	service-build service-test service-run parity traceability

SERVICE_DIR = services/settlement-service
SERVICE_PORT ?= 8083
SERVICE_JAVA_HOME ?= /usr/lib/jvm/java-21-openjdk-amd64
SERVICE_MVN = JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B -f $(SERVICE_DIR)/pom.xml

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

clean:
	mvn -q clean
	rm -rf data target/db

service-build:
	$(SERVICE_MVN) -DskipTests clean package

service-test:
	$(SERVICE_MVN) test

service-run:
	cd $(SERVICE_DIR) && JAVA_HOME=$(SERVICE_JAVA_HOME) SERVER_PORT=$(SERVICE_PORT) \
		mvn -B -q spring-boot:run

parity:
	python3 parity/replay.py --base-url http://localhost:$(SERVICE_PORT) \
		--module settlement --report parity/report.md --json parity/report.json

traceability:
	python3 tools/traceability.py

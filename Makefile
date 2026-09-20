.PHONY: build run seed test capture clean up down parity service-build service-test service-lint sast

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

# ---------------------------------------------------------------------------
# Settlement extraction (SPEC-SETTLE-001): services/settlement-service.
#
# The legacy build above stays on Java 7/Maven; the service needs Java 21, so
# SERVICE_JAVA_HOME points at it. NS names the compose stack and PORT_OFFSET
# shifts the published ports, so a second stack runs beside the first:
#
#   make up
#   NS=b PORT_OFFSET=100 make up      # settlement 8183, postgres 5532
# ---------------------------------------------------------------------------

SERVICE_DIR       ?= services/settlement-service
SERVICE_JAVA_HOME ?= /usr/lib/jvm/java-21-openjdk-amd64
SERVICE_MVN        = JAVA_HOME=$(SERVICE_JAVA_HOME) mvn -B -f $(SERVICE_DIR)/pom.xml

NS          ?= a
PORT_OFFSET ?= 0
SETTLEMENT_HOST_PORT = $(shell expr 8083 + $(PORT_OFFSET))
POSTGRES_HOST_PORT   = $(shell expr 5432 + $(PORT_OFFSET))
COMPOSE = NS=$(NS) SETTLEMENT_HOST_PORT=$(SETTLEMENT_HOST_PORT) POSTGRES_HOST_PORT=$(POSTGRES_HOST_PORT) \
	docker compose -f docker-compose.yml

service-build:
	$(SERVICE_MVN) -DskipTests package

service-test:
	$(SERVICE_MVN) test

service-lint:
	$(SERVICE_MVN) checkstyle:check

sast:
	$(SERVICE_MVN) -DskipTests compile com.github.spotbugs:spotbugs-maven-plugin:check

up: service-build
	$(COMPOSE) up -d --build
	@echo "settlement-service on http://localhost:$(SETTLEMENT_HOST_PORT), postgres on $(POSTGRES_HOST_PORT)"

down:
	$(COMPOSE) down -v

parity:
	python3 parity/replay.py --base-url http://localhost:$(SETTLEMENT_HOST_PORT)

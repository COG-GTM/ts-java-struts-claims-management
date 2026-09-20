.PHONY: build run seed test capture clean
.PHONY: up down parity service-build service-test service-lint sast

# --- settlement-service stack (services/settlement-service) --------------------
# NS is the compose namespace, PORT_OFFSET shifts the host ports so two stacks
# can run side by side: make up NS=settlement PORT_OFFSET=100 -> service on 8183.
NS ?= settlement
PORT_OFFSET ?= 0
MODULE ?= settlement
SERVICE_PORT := $(shell echo $$((8083 + $(PORT_OFFSET))))
POSTGRES_PORT := $(shell echo $$((5433 + $(PORT_OFFSET))))
SERVICE_DIR := services/settlement-service
SERVICE_MVN ?= mvn -B -f $(SERVICE_DIR)/pom.xml
COMPOSE := NS=$(NS) SERVICE_HOST_PORT=$(SERVICE_PORT) POSTGRES_HOST_PORT=$(POSTGRES_PORT) \
	MAVEN_MIRROR=$(MAVEN_MIRROR) docker compose -p $(NS) -f docker-compose.yml

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

# --- settlement-service targets -------------------------------------------------
up:
	$(COMPOSE) up -d --build --wait

down:
	$(COMPOSE) down -v --remove-orphans

parity:
	python3 parity/replay.py --base-url http://localhost:$(SERVICE_PORT) \
		--module $(MODULE) --report parity/report.md

service-build:
	$(SERVICE_MVN) -DskipTests clean package

service-test:
	$(SERVICE_MVN) test

service-lint:
	$(SERVICE_MVN) checkstyle:check

sast:
	$(SERVICE_MVN) -DskipTests compile spotbugs:check

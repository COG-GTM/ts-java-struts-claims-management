JAVA_HOME := /usr/lib/jvm/java-21-openjdk-amd64
export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(PATH)
AREA ?= foundation
.PHONY: build test seed run replay parity
build:
	mvn -B package
test:
	mvn -B test
seed:
	mvn -q compile exec:java -Dexec.mainClass=com.northstar.claims.support.DatabaseBootstrap
run:
	java -jar target/claims.jar
replay:
	python3 parity/replay.py --area $(AREA)
parity:
	bash parity/full_parity.sh

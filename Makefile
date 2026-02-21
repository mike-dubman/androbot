SHELL := /bin/bash

.PHONY: help doctor build install test-unit test-device test ci quick

help:
	@echo "Targets:"
	@echo "  make doctor      - check required local tools"
	@echo "  make build       - build debug APK"
	@echo "  make install     - install debug APK via adb"
	@echo "  make test-unit   - run JVM unit tests"
	@echo "  make test-device - run instrumentation tests"
	@echo "  make test        - run unit + instrumentation tests"
	@echo "  make ci          - run Docker local CI"
	@echo "  make quick       - build + install + unit tests"
	@echo ""
	@echo "Optional: DEVICE=<adb-serial> make install"

doctor:
	@./scripts/dev.sh doctor

build:
	@./scripts/dev.sh build

install:
	@DEVICE="$(DEVICE)" ./scripts/dev.sh install

test-unit:
	@./scripts/dev.sh test-unit

test-device:
	@DEVICE="$(DEVICE)" ./scripts/dev.sh test-device

test:
	@DEVICE="$(DEVICE)" ./scripts/dev.sh test

ci:
	@./scripts/dev.sh ci

quick:
	@DEVICE="$(DEVICE)" ./scripts/dev.sh build
	@DEVICE="$(DEVICE)" ./scripts/dev.sh install
	@./scripts/dev.sh test-unit

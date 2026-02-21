SHELL := /bin/bash

.PHONY: help doctor build install deploy test-unit test-device test ci quick release release-tag

help:
	@echo "Targets:"
	@echo "  make doctor      - check required local tools"
	@echo "  make build       - build debug APK"
	@echo "  make install     - install debug APK via adb"
	@echo "  make deploy      - deploy debug APK to real phone via adb TCP"
	@echo "  make test-unit   - run JVM unit tests"
	@echo "  make test-device - run instrumentation tests"
	@echo "  make test        - run unit + instrumentation tests"
	@echo "  make ci          - run Docker local CI"
	@echo "  make quick       - build + install + unit tests"
	@echo "  make release     - create git tag and GitHub release with APK"
	@echo ""
	@echo "Optional: DEVICE=<adb-serial> make install"
	@echo "Optional: PHONE_IP=<phone-lan-ip> PHONE_PORT=5555 make deploy"
	@echo "Required for release: VERSION=0.2.0 make release"

doctor:
	@./scripts/dev.sh doctor

build:
	@./scripts/dev.sh build

install:
	@DEVICE="$(DEVICE)" ./scripts/dev.sh install

deploy:
	@PHONE_IP="$(PHONE_IP)" PHONE_PORT="$(PHONE_PORT)" ./scripts/dev.sh deploy

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

release:
	@VERSION="$(VERSION)" ./scripts/release.sh

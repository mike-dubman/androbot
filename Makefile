SHELL := /bin/bash

.PHONY: help doctor build build-release install deploy emu-up emu-down emu-logs test-unit test-device test ci quick release release-cli release-tag

help:
	@echo "Targets:"
	@echo "  make doctor      - check required local tools"
	@echo "  make build       - build debug APK"
	@echo "  make build-release - build release APK"
	@echo "  make install     - install debug APK via adb"
	@echo "  make deploy      - deploy debug APK to real phone via adb TCP"
	@echo "  make emu-up      - start emulator container with web UI (advanced debug)"
	@echo "  make emu-down    - stop emulator container"
	@echo "  make emu-logs    - follow emulator container logs"
	@echo "  make test-unit   - run JVM unit tests"
	@echo "  make test-device - run instrumentation tests"
	@echo "  make test        - run unit + instrumentation tests"
	@echo "  make ci          - run Docker local CI"
	@echo "  make quick       - build + install + unit tests"
	@echo "  make release     - create git tag and GitHub release with APK"
	@echo "  make release-cli - trigger GitHub release workflow_dispatch via gh"
	@echo ""
	@echo "Optional: APK_PATH=<path-to-apk> DEVICE=<adb-serial> make install"
	@echo "Optional: APK_PATH=<path-to-apk> PHONE_IP=<phone-lan-ip> PHONE_PORT=5555 make deploy"
	@echo "Required for release: VERSION=0.2.0 make release"
	@echo "Required for release-cli: VERSION=0.2.0 [RELEASE_PUBLISH=draft|published] make release-cli"

doctor:
	@./scripts/dev.sh doctor

build:
	@./scripts/dev.sh build

build-release:
	@./scripts/dev.sh build-release

install:
	@APK_PATH="$(APK_PATH)" DEVICE="$(DEVICE)" ./scripts/dev.sh install

deploy:
	@APK_PATH="$(APK_PATH)" PHONE_IP="$(PHONE_IP)" PHONE_PORT="$(PHONE_PORT)" ./scripts/dev.sh deploy

emu-up:
	@./scripts/compose.sh -f docker-compose.ci.yml up -d emulator
	@echo "Emulator UI: http://localhost:6080"

emu-down:
	@./scripts/compose.sh -f docker-compose.ci.yml stop emulator

emu-logs:
	@./scripts/compose.sh -f docker-compose.ci.yml logs -f emulator

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

release-cli:
	@if [ -z "$(VERSION)" ]; then \
	  echo "VERSION is required. Example: VERSION=0.2.0 make release-cli"; \
	  exit 1; \
	fi
	@PUBLISH="$${RELEASE_PUBLISH:-draft}"; \
	if [ "$$PUBLISH" != "draft" ] && [ "$$PUBLISH" != "published" ]; then \
	  echo "RELEASE_PUBLISH must be draft or published"; \
	  exit 1; \
	fi; \
	if [ -n "$(RELEASE_NOTES)" ]; then \
	  gh workflow run release.yml -f version="$(VERSION)" -f publish="$$PUBLISH" -f notes="$(RELEASE_NOTES)"; \
	else \
	  gh workflow run release.yml -f version="$(VERSION)" -f publish="$$PUBLISH"; \
	fi

.DEFAULT_GOAL := help

HERMES_TEST_IMAGE ?= nousresearch/hermes-agent:latest
.PHONY: ingress-test
ingress-test: ## Run ingress tests in an isolated Hermes container (no network)
	@tar -cf - server | docker run --rm -i --network none --entrypoint /bin/sh $(HERMES_TEST_IMAGE) -c 'mkdir -p /tmp/cordlet && tar -xf - -C /tmp/cordlet && cd /tmp/cordlet && PYTHONPATH=/opt/hermes /opt/hermes/.venv/bin/python -m unittest discover -s server/tests -v'

PROJECT := Cordlet
SERVICE := dev
COMPOSE ?= docker compose
GRADLE := ./gradlew --no-daemon --console=plain
APK := app/build/outputs/apk/debug/app-debug.apk
# Build outside /workspace: its Compose bind mount can be stale on some hosts.
BUILD_DIR := /tmp/cordlet-build

# Match bind-mounted file ownership to the current host user.
# Command-line overrides still work: make build LOCAL_UID=1234 LOCAL_GID=1234
LOCAL_UID := $(shell id -u)
LOCAL_GID := $(shell id -g)
export LOCAL_UID LOCAL_GID

# Run all Android tooling in Docker. The host only needs Docker + Compose + Make.
RUN := $(COMPOSE) exec $(SERVICE)
BUILD_RUN := $(COMPOSE) exec -T $(SERVICE)

.PHONY: help up down build rebuild shell doctor test run apk apk-path install lint clean logs ps ensure-up sync-sources

help: ## Show available commands
	@printf '%s\n' \
		'$(PROJECT) development commands' \
		'--------------------------------' \
		'Host requirements: Docker, Docker Compose, and Make.' \
		'' \
		'  make up       Build and start the Android development container' \
		'  make shell    Open a shell inside the development container' \
		'  make test     Run unit tests on current host sources inside Docker' \
		'  make run      Build and copy the debug APK to the host' \
		'  make apk      Build the debug APK (alias of run)' \
		'  make apk-path Print the absolute host APK path' \
		'  make install  Build and install via container ADB if a device is reachable' \
		'  make lint     Run Android lint inside Docker' \
		'  make clean    Clean Gradle build outputs' \
		'  make doctor   Print the container toolchain versions' \
		'  make logs     Follow development-container logs' \
		'  make ps       Show project containers' \
		'  make down     Stop and remove project containers' \
		'  make rebuild  Rebuild the development image without cache'

up: ## Build and start the development container
	@$(COMPOSE) up -d --build $(SERVICE)
	@printf 'Cordlet development container is ready. Try: make test\n'

down: ## Stop the development environment
	@$(COMPOSE) down --remove-orphans

build: ## Build the development image
	@$(COMPOSE) build $(SERVICE)

rebuild: ## Rebuild the development image without cache
	@$(COMPOSE) build --no-cache $(SERVICE)

ensure-up:
	@$(COMPOSE) up -d $(SERVICE)

shell: ensure-up ## Open an interactive shell in the development container
	@$(RUN) bash

doctor: ensure-up ## Show Java, Android SDK, ADB, and Gradle versions
	@$(RUN) bash -lc 'id && java -version && printf "\nAndroid sdkmanager: " && sdkmanager --version && printf "\n" && adb version && if [ -x ./gradlew ]; then printf "\n"; $(GRADLE) --version; else printf "\nGradle wrapper: not created yet\n"; fi'

# Transfer only build inputs, not .git, local.properties, or host build outputs.
# The ignored discord.local.properties is copied only into the transient build
# snapshot (never into the development image or tracked sources).
# The container-only snapshot also makes deleted host sources disappear on the next run
# without ever deleting anything inside the host bind mount.
sync-sources: ensure-up
	@set -eu; archive=$$(mktemp); trap 'rm -f "$$archive"' EXIT; \
		if [ -f discord.local.properties ]; then \
			tar -cf "$$archive" gradlew gradle settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts app/src discord.local.properties; \
		else \
			tar -cf "$$archive" gradlew gradle settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts app/src; \
		fi; \
		$(BUILD_RUN) sh -c 'rm -rf "$(BUILD_DIR)" && mkdir -p "$(BUILD_DIR)"'; \
		$(BUILD_RUN) tar -xf - -C '$(BUILD_DIR)' < "$$archive"

test: sync-sources ## Run unit tests
	@$(BUILD_RUN) sh -c 'cd "$(BUILD_DIR)" && $(GRADLE) test'

run: sync-sources ## Build the debug APK and copy it to the host
	@$(BUILD_RUN) sh -c 'cd "$(BUILD_DIR)" && $(GRADLE) :app:assembleDebug'
	@set -eu; mkdir -p '$(dir $(APK))'; output=$$(mktemp '$(dir $(APK)).app-debug.XXXXXXXX'); \
		trap 'rm -f "$$output"' EXIT; \
		$(BUILD_RUN) cat '$(BUILD_DIR)/$(APK)' > "$$output"; \
		chmod 644 "$$output"; mv -f "$$output" '$(APK)'; \
		printf 'APK: %s\n' '$(abspath $(APK))'

apk: run ## Build the debug APK

apk-path: ## Print the absolute host path of the debug APK
	@printf '%s\n' '$(abspath $(APK))'

install: run ## Install the debug APK through container ADB when reachable
	@$(BUILD_RUN) sh -c 'state=$$(adb get-state 2>/dev/null) || state=; \
		if [ "$$state" != device ]; then \
			echo "No Android device reachable by ADB in the container. Copy $(abspath $(APK)) to your phone and install it manually (see README)." >&2; exit 1; \
		fi; cd "$(BUILD_DIR)" && adb install -r "$(APK)"'

lint: sync-sources ## Run Android lint
	@$(BUILD_RUN) sh -c 'cd "$(BUILD_DIR)" && $(GRADLE) :app:lintDebug'

clean: ensure-up ## Clean Gradle build outputs
	@$(BUILD_RUN) sh -c 'rm -rf "$(BUILD_DIR)"'
	@rm -rf 'app/build'

logs: ## Follow development-container logs
	@$(COMPOSE) logs -f $(SERVICE)

ps: ## Show project containers
	@$(COMPOSE) ps

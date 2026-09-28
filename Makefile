.DEFAULT_GOAL := help

PROJECT := Cordlet
SERVICE := dev
COMPOSE ?= docker compose
GRADLE := ./gradlew --no-daemon --console=plain

# Match bind-mounted file ownership to the current host user.
# Command-line overrides still work: make build LOCAL_UID=1234 LOCAL_GID=1234
LOCAL_UID := $(shell id -u)
LOCAL_GID := $(shell id -g)
export LOCAL_UID LOCAL_GID

# Run all Android tooling in Docker. The host only needs Docker + Compose + Make.
RUN := $(COMPOSE) exec $(SERVICE)

.PHONY: help up down build rebuild shell doctor test run apk lint clean logs ps ensure-up

help: ## Show available commands
	@printf '%s\n' \
		'$(PROJECT) development commands' \
		'--------------------------------' \
		'Host requirements: Docker, Docker Compose, and Make.' \
		'' \
		'  make up       Build and start the Android development container' \
		'  make shell    Open a shell inside the development container' \
		'  make test     Run unit tests inside Docker' \
		'  make run      Build the debug APK inside Docker' \
		'  make apk      Build the debug APK (alias of run)' \
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

test: ensure-up ## Run unit tests
	@$(RUN) bash -lc 'test -x ./gradlew || { echo "Gradle wrapper missing. Scaffold the Android project first." >&2; exit 2; }; $(GRADLE) test'

run: ensure-up ## Build the debug APK
	@$(RUN) bash -lc 'test -x ./gradlew || { echo "Gradle wrapper missing. Scaffold the Android project first." >&2; exit 2; }; $(GRADLE) :app:assembleDebug'

apk: run ## Build the debug APK

lint: ensure-up ## Run Android lint
	@$(RUN) bash -lc 'test -x ./gradlew || { echo "Gradle wrapper missing. Scaffold the Android project first." >&2; exit 2; }; $(GRADLE) :app:lintDebug'

clean: ensure-up ## Clean Gradle outputs
	@$(RUN) bash -lc 'test -x ./gradlew || { echo "Gradle wrapper missing. Scaffold the Android project first." >&2; exit 2; }; $(GRADLE) clean'

logs: ## Follow development-container logs
	@$(COMPOSE) logs -f $(SERVICE)

ps: ## Show project containers
	@$(COMPOSE) ps

# Cordlet

An open-source Android home-screen shortcut for sending text and voice messages to a configured Discord destination.

Cordlet is intentionally destination-agnostic. The initial configuration will point at a private Discord conversation with Wren, but the project itself should support any valid Discord destination and bot configuration.

## Project status

**Phase:** concept and interaction design

**Selected direction:** Utility Strip — a compact horizontal widget with:

- A text field for short messages
- Enter-to-send behavior
- A microphone button
- Tap once to record; tap again to send
- Minimal home-screen footprint

Project notes live in [`docs/architecture.md`](docs/architecture.md) and [`docs/plan-installable-v0.md`](docs/plan-installable-v0.md). The interactive browser preview is [`docs/widget-preview.html`](docs/widget-preview.html).

## Container-first development

The host only needs **Docker**, **Docker Compose**, and **Make**. Java, the Android command-line tools, SDK platform, build tools, ADB, and Gradle dependencies stay inside the development container.

```bash
# Build and start the Android development environment
make up

# Confirm the container toolchain
make doctor

# Run tests once the Android project has been scaffolded
make test

# Build the debug APK
make run

# Open a development shell when needed
make shell
```

Gradle and Android metadata use named Docker volumes, so repeated builds retain their caches without polluting the host. Run `make help` for the complete command list.

## Development and releases

- Branch from `main` for each change: `feature/<short-name>` for features, `bug/<short-name>` for fixes.
- Commit and push the branch, then open a pull request into `main`. Review and run `make test` and `make run` before merging. Do not develop directly on `main`.
- Keep `main` as the release-ready branch. Create releases **only from `main`**, marked with version tags such as `v0.1.0` after verification; do not tag feature branches.
- For a new change, start from updated `main`, create a new branch, and repeat. No force-push to `main`.

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/<short-name>
# Make changes, verify, and commit.
git push -u origin HEAD
# Open a PR targeting main; merge after review.
# Once main is verified for release:
git switch main
git pull --ff-only origin main
git tag -a v0.1.0 -m "Cordlet v0.1.0"
git push origin v0.1.0
```

## MVP

1. Configure a Discord destination and authentication securely.
2. Open Cordlet from an Android home-screen widget.
3. Type and send a text message quickly.
4. Record and send a Discord-compatible voice message.
5. Show clear sending, success, and failure states.
6. Avoid storing Discord credentials directly in source control or exported backups.

## Important Android constraint

Traditional Android App Widgets use `RemoteViews` and cannot host a normal editable text field or perform full audio recording directly inside the widget. The likely production design is:

- A compact home-screen widget matching the Utility Strip appearance
- Tapping the text area opens a small, fast compose activity
- Tapping the microphone opens a recording activity or foreground recording flow
- The activity sends the resulting message or voice attachment to Discord

The preview represents the intended experience, not a claim that every control can run directly inside `RemoteViews`. Android remains Android: even the shortcut needs paperwork.

## Proposed stack

- Kotlin
- Jetpack Compose for the compact compose/recording activity
- Jetpack Glance or `RemoteViews` for the home-screen widget
- Android `MediaRecorder` for voice capture
- Discord REST API or a configurable relay endpoint for delivery
- Android Keystore / encrypted local storage for non-exportable configuration

## Security direction

A Discord bot token must not be embedded in the APK. Preferred production options:

1. **Relay service:** Cordlet authenticates to a small server that owns the Discord credential.
2. **User-supplied webhook:** simpler, but the webhook remains a sensitive bearer credential on the device.

The relay model is recommended for a public open-source release.

## Current decisions

- Project name: **Cordlet**
- Product scope: open-source Discord shortcut, not Wren-specific
- Initial design: **Utility Strip**
- Initial message types: text and voice
- Initial platform: Android

## Next decisions

- Direct Discord API versus relay service
- Minimum supported Android version
- Widget size and resize behavior
- Text-entry activity style: dialog, bottom sheet, or full screen
- Voice format and Discord attachment behavior
- Authentication and onboarding flow

## Repository layout

```text
cordlet/
├── .dockerignore
├── Dockerfile
├── Makefile
├── README.md
├── docker-compose.yml
└── docs/
    ├── architecture.md
    ├── plan-installable-v0.md
    └── widget-preview.html
```

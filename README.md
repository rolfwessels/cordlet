# Cordlet

An open-source Android home-screen shortcut for sending text and voice messages to a configured Discord destination.

Cordlet is intentionally destination-agnostic. The current test build posts as a configured sender bot into a shared Discord server channel and explicitly mentions a configured recipient bot. It does not post into a DM between other users or bots.

## Project status

**Phase:** text-to-Discord prototype on `feature/discord-text` (not a tagged release). Widget rendering still needs phone verification.

**Current behavior:** The widget's text area opens the activity with its field focused; Glance cannot offer inline typing. Type a message there and the mint control becomes **Send**. Tap it or press keyboard Send to POST as the configured bot into a shared server channel, mentioning the configured recipient bot. Success clears the text; failure retains it. The widget mic target also opens the activity; empty-field mic feedback remains visual-only—no recording or voice upload yet. A successful Discord POST does not prove that the recipient bot processed or replied to it.

**Future product direction:** Each widget instance can select a saved destination profile (bot identity, channel, icon/label); the current `DiscordDestination` is one compile-time test profile, not multi-widget routing yet.

- A text field for short messages
- Enter-to-send behavior
- A microphone button
- Tap once to record; tap again to send
- Minimal home-screen footprint

Project notes live in [`docs/architecture.md`](docs/architecture.md) and [`docs/plan-installable-v0.md`](docs/plan-installable-v0.md). The interactive browser preview is [`docs/widget-preview.html`](docs/widget-preview.html).

## Future per-widget destinations

The current debug build injects **one** `DiscordDestination(botToken, channelId, recipientBotId)` from the ignored local file; every widget opens that one composer. For multi-widget support, introduce saved destination profiles with a stable profile ID, channel ID, bot identity/credential reference, label, and icon. Persist a mapping from Android `appWidgetId` to profile ID and pass the selected ID when opening the activity; never assume a process-global destination. Widget setup and credential provisioning will need on-device storage or a relay—do not repeat the compile-time token pattern for multiple published widgets. Voice is a separate milestone.

## Local test-bot setup

1. Invite both a **sender bot** and a recipient bot to a shared server channel. The sender needs **View Channel** and **Send Messages**; the recipient must be configured to process messages from other bots. A bot's own messages cannot be used to wake itself. A guild ID or a private DM channel belonging to other participants is not a send destination for the sender bot.
2. Fill the Git-ignored `discord.local.properties` in the repository root: `botToken` is the sender's token, `channelId` is the shared **text channel** ID, and `recipientBotId` is the bot to mention. The file is excluded from the Docker image; `make test`/`make run` copy it only into the build snapshot.
3. Run `make test && make run`, privately install the debug APK, tap the widget text area, type a harmless message, and tap **Send** or keyboard Send. Confirm that the sender bot's message appears in the shared channel with a recipient mention. A recipient reply requires its own bot-message admission policy and is **not** proved by this APK build.

**Security warning:** This is a *test-only compromise*. The bot token is compiled into the **debug APK**, and anyone with that APK (including recipients of a Discord upload/link) can extract and misuse it. The ignored config protects Git, **not the APK**. Use a disposable, minimally privileged bot, do not share the configured APK, and rotate its token after testing. A production version needs a relay or device-local provisioning—not an embedded token. Release builds deliberately omit the test credential.

## Container-first development

The host only needs **Docker**, **Docker Compose**, and **Make**. Java, the Android command-line tools, SDK platform, build tools, ADB, and Gradle dependencies stay inside the development container.

```bash
# Build and start the Android development environment
make up

# Confirm the container toolchain
make doctor

# Run unit tests on the current host sources
make test

# Build the debug APK and copy it to the host
make run
make apk-path

# Open a development shell when needed
make shell
```

`make apk-path` prints the absolute path to `app/build/outputs/apk/debug/app-debug.apk` on the host. `make run` builds in the container and copies the APK to that path; it does not launch the app. The build and test targets transfer only the Gradle project inputs and `app/src` into a fresh container-only snapshot before each invocation. This avoids relying on a stale Compose bind mount, without deleting or overwriting host sources. Gradle and Android metadata use named Docker volumes, so repeated builds retain their caches without polluting the host. Run `make help` for the complete command list.

### Install on a phone

Enable Developer options and USB debugging on an Android 8.0 (API 26) or newer phone, connect it, and authorize the computer's ADB key on the phone. If the development container can see the device through ADB, run `make install`; it builds the APK and runs `adb install -r` inside the container. Docker does not automatically pass host USB devices into containers, so a phone attached to the host may still be unreachable inside the container. If so, run `make run`, copy the host APK shown by `make apk-path` to the phone (USB file transfer, cloud storage, or another trusted method), and open it on the phone to install. Android may ask you to allow installation from that file manager. No host Android SDK or ADB is required for this fallback.

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

A Discord bot token must not be embedded in a **distributed** APK. This branch makes a temporary exception only for a disposable, privately tested debug bot; see the warning above. Preferred production options:

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

# Cordlet Architecture

## Purpose

Cordlet is an open-source Android home-screen shortcut for quickly composing text or voice messages for a configured Discord destination.

The first milestone is deliberately smaller: installable Android **v0** with the Utility Strip interface, no Discord connection, no credentials, and no network traffic.

## Product boundary

Cordlet eventually has two visible surfaces:

1. **Home-screen widget** — compact entry point that resembles the Utility Strip.
2. **Compose activity** — a small native Android screen containing the real editable text field and microphone action.

A standard Android App Widget cannot host a normal editable `TextField`/`EditText`. Glance and `RemoteViews` widgets render restricted controls rather than a full Compose UI. Therefore, the widget will display a field-shaped shortcut; tapping it opens the compose activity, where text entry and later audio capture happen properly.

Pretending otherwise would produce a beautiful mockup and a useless widget. Android has rules because apparently joy needed governance.

## v0 architecture

```text
Android launcher
      │
      ├── Cordlet app icon ────────┐
      │                            ▼
      └── Cordlet home widget ──> MainActivity
                                   │
                                   ▼
                            Utility Strip UI
                            ├── editable text field
                            └── microphone button
```

### v0 behavior

- The application installs and launches on a physical Android phone.
- The activity displays the selected Utility Strip design.
- The text field accepts local input for the current activity session.
- The microphone button provides visual feedback only.
- The home-screen widget can be added and opens the activity.
- No text or audio leaves the device.
- No Discord, webhook, relay, HTTP client, persistence, analytics, or background service exists.
- The manifest requests neither `INTERNET` nor `RECORD_AUDIO` permission.

## Technology

- **Language:** Kotlin 2.4.10
- **Build:** Gradle 9.6.0 with Android Gradle Plugin 9.4.0
- **Runtime:** JDK 17
- **Android:** compile/target SDK 36; minimum SDK 26
- **Activity UI:** Jetpack Compose
- **Widget UI:** Jetpack Glance AppWidget 1.2.0
- **Testing:** JUnit for JVM tests; AndroidX Compose tests when device/emulator testing is introduced
- **Development:** Docker Compose and Make; host requires no Android SDK or JDK

## Planned source layout

```text
cordlet/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/io/github/rolfwessels/cordlet/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ui/
│       │   │   │   ├── CordletApp.kt
│       │   │   │   ├── UtilityStrip.kt
│       │   │   │   └── theme/CordletTheme.kt
│       │   │   └── widget/
│       │   │       ├── CordletWidget.kt
│       │   │       └── CordletWidgetReceiver.kt
│       │   └── res/
│       │       ├── drawable/
│       │       ├── mipmap-anydpi-v26/
│       │       ├── values/
│       │       └── xml/cordlet_widget_info.xml
│       └── test/java/io/github/rolfwessels/cordlet/
│           └── ui/ComposerStateTest.kt
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── docs/
    ├── architecture.md
    └── plan-installable-v0.md
```

## Component responsibilities

### `MainActivity`

- Hosts the Compose application.
- Accepts optional launch intent indicating text or voice entry.
- Owns no Discord or transport logic.

### `CordletApp`

- Defines the screen surface and top-level state wiring.
- Keeps v0 input in memory only.
- Displays the Utility Strip.

### `UtilityStrip`

- Renders the text field and microphone button.
- Exposes callbacks rather than performing side effects.
- Remains previewable and testable.

### `CordletWidget`

- Renders a field-shaped shortcut and microphone affordance using Glance-supported controls.
- Opens `MainActivity` when either area is tapped.
- Does not pretend to provide inline editing.

## State model

For v0, state is intentionally tiny:

```text
ComposerState
├── message: String
└── microphonePressed: Boolean
```

No repository, database, or dependency-injection framework is justified yet. Those would be architecture cosplay.

## Build and delivery

- `make up` starts the Android development container.
- `make test` runs local JVM tests.
- `make run` builds `app-debug.apk`.
- The APK is emitted under `app/build/outputs/apk/debug/` in the bind-mounted project.
- Installation can use `adb install -r` when a phone is attached, or the APK can be copied to the phone manually.

## Future transport architecture

Discord integration is explicitly deferred. The preferred production design is:

```text
Cordlet Android app
        │ HTTPS + Cordlet user auth
        ▼
Small relay service
        │ brokered Discord credential
        ▼
Discord REST API / configured destination
```

The Discord bot token must never be embedded in the APK. A public APK can be decompiled; obfuscation is not a vault, merely a speed bump wearing sunglasses.

## Security invariants

- No upstream Discord credential in source, Gradle files, APK resources, or device logs.
- No network permission before networking exists.
- No microphone permission before recording exists.
- No audio persistence without explicit user action and lifecycle cleanup.
- All eventual network destinations must be configurable and visibly disclosed.
- Sensitive configuration must use Android Keystore-backed storage or remain server-side.

## Architectural decisions

1. **Use a compose activity for real input.** Android widgets cannot provide the required editable control.
2. **Use Glance for the widget shell.** It keeps widget code Kotlin-first without implying full Compose capability.
3. **Keep v0 offline.** Installation and UI are validated before credentials or transport complicate debugging.
4. **Use one app module initially.** Extra modules add ceremony before there is enough code to separate.
5. **Use a relay later.** Shipping a Discord credential in an open-source Android app is not security; it is a donation.

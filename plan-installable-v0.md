# Installable Android v0 Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Produce an installable Cordlet v0 APK that shows the Utility Strip text field and microphone button, includes an addable home-screen widget, and performs no Discord or network activity.

**Architecture:** A single Android application module uses Jetpack Compose for the real editable UI and Jetpack Glance for a home-screen widget shell. The widget launches the activity because Android widgets cannot contain a normal editable text field. v0 keeps state in memory and requests no network or microphone permission.

**Tech stack:** Kotlin 2.4.10, Gradle 9.6.0, Android Gradle Plugin 9.4.0, JDK 17, compile/target SDK 36, min SDK 26, Compose UI 1.12.1, Material 3 1.4.0, Activity Compose 1.13.0, Glance AppWidget 1.2.0, JUnit 4.13.2.

---

## Acceptance criteria

- `make up` starts the Android development container.
- `make test` passes from the host without a local JDK or Android SDK.
- `make run` produces `app/build/outputs/apk/debug/app-debug.apk`.
- The APK installs on an Android 8.0/API 26 or newer phone.
- Launching Cordlet shows the Utility Strip with one editable text field and one microphone button.
- Typing changes only in-memory state.
- Tapping the microphone button gives visual feedback but does not record.
- Cordlet can be added as a home-screen widget.
- Tapping either widget region opens the activity.
- `AndroidManifest.xml` contains neither `android.permission.INTERNET` nor `android.permission.RECORD_AUDIO`.
- No Discord dependency, endpoint, token, webhook, or network client exists.

## Explicit non-goals

- Sending text
- Recording or uploading audio
- Discord authentication
- Relay-service integration
- Message persistence
- Background work
- Notifications
- Settings or onboarding
- Release signing or Play Store packaging

---

### Task 1: Create the Gradle project skeleton

**Objective:** Create a buildable single-module Android project that matches the container toolchain.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Modify: `.dockerignore`
- Modify: `.gitignore`

**Steps:**

1. Define repositories and include `:app` in `settings.gradle.kts`.
2. Pin AGP `9.4.0`, Kotlin `2.4.10`, and the Android/Compose dependencies in `gradle/libs.versions.toml`.
3. Configure Gradle wrapper `9.6.0` with `distributionSha256Sum` from the official Gradle release checksum.
4. Configure `app` with namespace/application ID `io.github.rolfwessels.cordlet`, compile/target SDK 36, min SDK 26, Java/Kotlin target 17, and Compose enabled.
5. Add standard Android and Gradle outputs to `.gitignore` and `.dockerignore`.
6. Run `make doctor`.
7. Run `make run` and verify it fails only because the manifest/application source does not exist yet—not because of Gradle, Java, or SDK incompatibility.
8. Commit:

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle app .gitignore .dockerignore
git commit -m "build: scaffold Android project"
```

---

### Task 2: Add the minimal Android application shell

**Objective:** Build an installable APK with Cordlet application metadata and an empty launcher activity.

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/io/github/rolfwessels/cordlet/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: launcher icon resources under `app/src/main/res/mipmap-*` and `drawable/`

**Steps:**

1. Create an application manifest with exported launcher `MainActivity`.
2. Do **not** declare `INTERNET` or `RECORD_AUDIO`.
3. Add the Cordlet application label and theme.
4. Add a minimal `MainActivity` that calls `setContent`.
5. Run `make run`.
6. Verify `app/build/outputs/apk/debug/app-debug.apk` exists.
7. Inspect the merged manifest:

```bash
make shell
# inside the container
./gradlew :app:processDebugMainManifest
```

Expected: launcher activity exists; network and microphone permissions are absent.

8. Commit:

```bash
git add app/src/main
git commit -m "feat: add installable Android shell"
```

---

### Task 3: Define composer state with TDD

**Objective:** Define the tiny in-memory state used by the Utility Strip without adding storage or networking.

**Files:**
- Test: `app/src/test/java/io/github/rolfwessels/cordlet/ui/ComposerStateTest.kt`
- Create: `app/src/main/java/io/github/rolfwessels/cordlet/ui/ComposerState.kt`

**Step 1: Write failing tests**

Test that:

- Initial message is empty.
- Updating the message stores exactly the typed value.
- Pressing the microphone toggles visual pressed state.
- State contains no send/network behavior.

**Step 2: Verify RED**

Run:

```bash
make test
```

Expected: compilation failure because `ComposerState` does not exist.

**Step 3: Implement minimally**

Use an immutable state value and pure update functions. No ViewModel, repository, database, coroutine, or dependency injection.

**Step 4: Verify GREEN**

Run:

```bash
make test
```

Expected: all JVM tests pass.

**Step 5: Commit**

```bash
git add app/src/main/java/io/github/rolfwessels/cordlet/ui/ComposerState.kt app/src/test
git commit -m "feat: add local composer state"
```

---

### Task 4: Implement the Utility Strip activity UI

**Objective:** Match the selected preview with a real text field and microphone button.

**Files:**
- Create: `app/src/main/java/io/github/rolfwessels/cordlet/ui/CordletApp.kt`
- Create: `app/src/main/java/io/github/rolfwessels/cordlet/ui/UtilityStrip.kt`
- Create: `app/src/main/java/io/github/rolfwessels/cordlet/ui/theme/CordletTheme.kt`
- Modify: `app/src/main/java/io/github/rolfwessels/cordlet/MainActivity.kt`
- Test: `app/src/test/java/io/github/rolfwessels/cordlet/ui/ComposerStateTest.kt`

**Steps:**

1. Add a Compose preview for the empty Utility Strip.
2. Render a dark rounded strip, Cordlet icon, single-line text field, and mint microphone button.
3. Wire text changes to local state.
4. Wire microphone taps to visual pressed state only.
5. Add accessibility descriptions for the text field and microphone button.
6. Ensure keyboard action does not send or call a network API.
7. Run `make test`.
8. Run `make run`.
9. Verify the debug APK still exists.
10. Commit:

```bash
git add app/src/main/java/io/github/rolfwessels/cordlet
git commit -m "feat: add Utility Strip interface"
```

---

### Task 5: Add the home-screen widget shell

**Objective:** Provide an addable widget that resembles the Utility Strip and opens the activity.

**Files:**
- Create: `app/src/main/java/io/github/rolfwessels/cordlet/widget/CordletWidget.kt`
- Create: `app/src/main/java/io/github/rolfwessels/cordlet/widget/CordletWidgetReceiver.kt`
- Create: `app/src/main/res/xml/cordlet_widget_info.xml`
- Create: `app/src/main/res/drawable/cordlet_widget_preview.xml` or preview image asset
- Modify: `app/src/main/AndroidManifest.xml`

**Steps:**

1. Register a Glance `AppWidgetReceiver` in the manifest.
2. Configure a minimum 4×1-style widget size and resize behavior.
3. Render a field-shaped text shortcut and microphone button with Glance-supported components.
4. Make both regions launch `MainActivity`; pass an optional `entry_mode` extra (`text` or `voice`).
5. Do not add editing, recording, storage, or networking to the widget.
6. Run `make test`.
7. Run `make run`.
8. Inspect the merged manifest and confirm only the widget receiver and launcher activity were added.
9. Commit:

```bash
git add app/src/main/java/io/github/rolfwessels/cordlet/widget app/src/main/res/xml app/src/main/AndroidManifest.xml
git commit -m "feat: add Cordlet home-screen widget"
```

---

### Task 6: Add APK and install helpers

**Objective:** Make the first phone installation boring and repeatable—the highest compliment available to build tooling.

**Files:**
- Modify: `Makefile`
- Modify: `README.md`

**Steps:**

1. Keep `make run` as the debug APK build command.
2. Add `make apk-path` to print the absolute APK path.
3. Add `make install` to run `adb install -r app/build/outputs/apk/debug/app-debug.apk` inside the container when a device is reachable.
4. Document USB debugging and manual APK-copy fallback.
5. Run `make help` and verify all targets are listed.
6. Run `make test`.
7. Run `make run`.
8. If a phone is attached, run `make install`; otherwise copy the APK to the phone and install manually.
9. Commit:

```bash
git add Makefile README.md
git commit -m "build: add APK installation workflow"
```

---

### Task 7: Verify v0 acceptance criteria

**Objective:** Prove the resulting APK is installable, offline, and limited to the agreed UI shell.

**Files:**
- Modify if needed: `README.md`

**Steps:**

1. Run `make test`; expected: all tests pass.
2. Run `make run`; expected: `BUILD SUCCESSFUL`.
3. Confirm APK exists at `app/build/outputs/apk/debug/app-debug.apk`.
4. Inspect the merged manifest for forbidden permissions.
5. Install the APK on a physical phone.
6. Launch Cordlet and type into the field.
7. Tap the microphone button and confirm visual feedback only.
8. Add the Cordlet widget to the home screen.
9. Tap the widget field and microphone areas; both should open Cordlet.
10. Disable network access or use airplane mode and repeat the UI checks; behavior must be unchanged.
11. Record the tested Android device/model/API in `README.md`.
12. Commit documentation updates:

```bash
git add README.md
git commit -m "docs: record Android v0 verification"
```

## Definition of done

v0 is complete only when the APK is built through Docker, installed on a physical phone, the activity and widget behave as specified, all tests pass, and the merged manifest proves that Cordlet has no network or microphone permission.

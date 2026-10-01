# Ephemeral quick voice capture

## Current scope (`feature/voice-recorder`, 0.4.1-quickvoice / code 6)

Every widget/composer microphone tap creates a **new private capture** and starts immediately once microphone permission is granted and the recorder is foreground. Saved history is never the default mic route. The unchanged widget text region still focuses the text composer; imported identity, icons and real waveform remain unchanged.

- Each launch owns a random safe session ID, `files/voice/<session-id>.m4a` and an atomic synced `<session-id>.properties` request-identity sidecar. Existing `latest.*` files are left untouched, not overwritten or deleted. Recreation restores only its explicitly saved session ID.
- Five active minutes / 4 MiB capture limit. Pause/resume and optional Finish & review/playback remain, but **Send works while recording**, paused or saved when configured, microphone-permitted and nonempty. One tap finalizes MPEG-4 and uploads in the same flow. No mandatory pause/review step.
- The foreground recording window uses `FLAG_KEEP_SCREEN_ON`; foreground upload also keeps it awake. Pause/background/stop clear it. No WAKE_LOCK permission or foreground recording service. Leaving foreground finalizes and releases capture rather than recording in the background.
- Upload locks repeat Send, playback and Discard. An independently owned per-note coroutine can finish after another fresh capture launches; its result cannot mutate, delete or close the new note. Process death ends uploads; no durable automatic outbox.
- The existing endpoint receives raw `audio/mp4`, bearer auth and stable `X-Cordlet-Request-ID`. Bounds: 10 MiB request / 8192-byte receipt, 10-second connect / 180-second read timeout; redirects refused.
- Only verified HTTP 202, `status: accepted` and the exact request ID, then persisted acceptance, closes the displayed recorder automatically. Failure keeps that note on screen with the same identity for explicit retry. Detached acceptance never closes a newer capture.
- Notes remain app-private unless explicitly sent. Nothing automatically discards unsent, failed, unreadable or accepted audio. Explicit Discard affects only the displayed note and is blocked during its upload. Retained files have **no automatic expiry or aggregate quota**: many abandoned notes can use storage. There is no history browser/recovery UI in this scope; opening a fresh mic does not recover old notes. Same-note task recreation can restore a retained note, but archival recovery after task loss is not exposed. Clear app data/uninstall removes all private notes and configuration.
- The recorder and widget launch activity are non-exported. The direct widget activity PendingIntent creates a fresh trusted launch nonce at tap time, avoiding receiver/service activity trampolines. Exported composer intents remain text-only.

## Verification

JVM policy/regression and source guards cover freshness, send eligibility, exact acceptance, independent durable identities, keep-awake policy and lifecycle wiring. They are not device instrumentation. Container tests, lint, APK build and merged-manifest inspection must pass.

**Installed-phone quick-capture send and auto-close PASSED (user-confirmed):** the user sent “Okay, the first it looks pretty good. I'm going to click send now.” as a transcribed voice input in the existing DM, then confirmed in a second voice note: “It did indeed close the screen, I like that.” This verifies phone voice delivery and automatic exit for this build. Screen-timeout prevention, every fresh-launch case and failure/background retry checks remain unverified unless separately confirmed.

## Phone checklist (pending for this build)

1. Install Android 8/API 26+ with existing private config/VPN. Check unchanged text focus/Send and icons.
2. Tap widget/composer mic repeatedly: each opens fresh recording, including after acceptance and after an older failed note. Grant/deny permission. Waveform follows actual audio.
3. Speak and tap Send without Pause: finalize, upload, then auto-exit only after matching acceptance; verify one reply in the intended Discord DM.
4. Pause/resume and optional saved playback still work. Screen stays lit during recording and foreground upload, but not paused/idle or background.
5. Offline/timeout/invalid receipt: remain on the same note, retry same ID. A second mic tap leaves old files intact and creates independent audio/identity.
6. Launch a new capture while an older upload is running; its later acceptance/failure must not close/change/discard the new capture.
7. Home/Back/lock/rotate/process death: no background microphone; readable finalized audio or unreadable audio remains retained. Recreate a task to verify same-note recovery. No automatic replay after process death.
8. Verify five-minute limit, narrow cells, large text and accessibility on a real device.

## Deferred

History/recovery browser, aggregate storage management, durable background outbox, profiles, in-app replies and export are not implemented.

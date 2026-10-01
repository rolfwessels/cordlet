# Cordlet

Android home-screen shortcut for sending text to private Hermes ingress, with replies in the existing Discord DM. Current branch: `feature/voice-recorder`; this is a proof build, not a tagged release.

## Current APK behavior

The unchanged widget text area launches the Compose activity with its field focused. Type a message and tap **Send** or keyboard Send. Cordlet posts JSON `request_id` and `text` to the imported endpoint with `Authorization: Bearer <device token>`. Success requires HTTP **202**, JSON `status: "accepted"`, and a matching `request_id`; it means queue admission, **not** agent completion or Discord delivery. Accepted input clears; errors retain it. Unchanged-text retries reuse the ID; editing or acceptance generates a new one. Text and retry identity survive activity recreation, but this is not a durable background outbox. Redirects are refused.

The widget mic and empty composer’s mic start **a fresh ephemeral voice capture on every tap** (`0.4.1-quickvoice`, code 6), immediately once microphone permission is granted and the recorder is foreground. Saved history is never the default mic route. Each launch has independent app-private AAC/M4A audio and identity paths; older failed/unsent notes are neither overwritten nor automatically discarded. Capture is bounded to five active minutes / 4 MiB. The recording window and foreground upload keep the screen awake with `FLAG_KEEP_SCREEN_ON`, not WAKE_LOCK or a foreground service. Leaving foreground finalizes/releases recording and stops playback. Recreation restores only the activity’s explicitly saved session ID. The recorder and nonce-generating widget launch activity are private; exported text intents cannot initiate capture.

**One tap Send while recording** finalizes and uploads the same note; Pause/review/playback remain optional. Send also works paused/saved when configured, permitted and nonempty. The unchanged endpoint receives streamed `audio/mp4`, bearer auth and stable `X-Cordlet-Request-ID`; redirects are refused, empty/greater-than-10-MiB requests rejected, and read timeout is 180 seconds. Failed/ambiguous uploads stay on the same note with the same retry ID. Independent process-owned uploads can finish after a new mic launch without affecting the new capture. Only exact matching HTTP 202 admission followed by persisted acceptance automatically closes the displayed recorder, never a newer note. No premature close on failure. Upload locks repeat Send, playback and Discard; accepted notes cannot resend.

There is no automatic retention expiry/aggregate quota, history browser or durable background outbox. Retained private files, including old `latest.*`, are left intact; a new mic tap does not reopen them. Same-note task recreation can restore its audio/identity, but recovery after task loss has no UI in this scope. Explicit Discard affects only the displayed note; clear app data/uninstall removes all notes/configuration. Server deduplication is finite/process-local, not an exactly-once guarantee.

Widget/app colors, real waveform, imported offline bot icon and text flow are preserved. Replies are read in Discord, not Cordlet. The installed-phone config import/text path, quick-capture voice delivery, automatic exit after acceptance and visible Discord transcript echo were user-confirmed. Screen-awake prevention and exhaustive fresh-launch/background/failure cases still need separate device checks.

## Private device configuration

No credential is compiled into either APK variant. The old `discord.local.properties` file is ignored and is **not read or transferred** by the build. Existing old configured APKs still expose their compiled bot credentials: rotate those separately.

1. Privately transfer a JSON file to the phone through a trusted channel. Do not put it in Git, public links, or chat:
   ```json
   {
     "endpoint": "http://hermes.bot.sels.co.za/cordlet/messages",
     "token": "<privately provisioned device bearer token>",
     "botName": "Hermes",
     "botIconBase64": null
   }
   ```
2. Open Cordlet, tap **Import private config**, and choose the file using Android's document picker (SAF). The app requests no broad storage permission or persistent document access. **Replace private config** imports a replacement later.
3. Endpoint, token, bot name and optional icon are stored only as AES-GCM ciphertext in app-private preferences. The encryption key resides in AndroidKeyStore; backups are disabled. Uninstalling/clearing app data requires reimport. No token is displayed or logged. Remove the plaintext import file and its cloud copy after provisioning; encryption does not protect those external copies.
4. Enable the phone's private VPN and send a unique harmless message. Confirm acceptance in the app **and exactly one reply in the intended private Discord DM**.

Configuration schema: `endpoint` and `token` are required strings. `botName` is an optional string (default `Hermes`), 1–64 characters without control characters. `botIconBase64` is an optional string or `null` (default `null`), containing raw standard base64 of a PNG or JPEG, without whitespace, a URL or a data-URI prefix. The compressed image must be at most **32 KiB (32768 bytes)** and decoded dimensions **1–256 pixels on each axis**; prepare a 128-pixel PNG for a small avatar. The entire UTF-8 JSON file must be at most **64 KiB (65536 bytes)**. Image signature and decoded bounds are checked before allocating pixels, and invalid images fail import without replacing existing configuration. Older files remain compatible. The small circular avatar appears beside the bot name on text and recorder screens, with a monogram fallback. This is a **single-file offline icon**: no public image hosting or network fetch, and neither the icon nor token is bundled in the APK. Remove the private provisioning file after import.

Cleartext is denied by default and allowed only for the exact `hermes.bot.sels.co.za` domain (no subdomains). Config validation permits that exact HTTP endpoint or HTTPS `/cordlet/messages` endpoints without embedded credentials, query, or fragment. This HTTP proof relies on the private phone VPN for transport protection: do not expose it publicly, disable the VPN for sending, or use Tailscale Funnel. The phone receives a scoped ingress device credential, never Discord credentials or Hermes's general API key.

Limits match the ingress contract: nonblank text, at most 4000 UTF-16 code units (conservative for non-BMP text), 8192 UTF-8 bytes including JSON. Server deduplication is process-local and finite; retries across server restarts/eviction are not an exactly-once guarantee.

## Container-first development

Host requirements: **Docker, Docker Compose, Make**. Java, SDK, Gradle, ADB and build tools remain in Docker.

```sh
make up
make doctor
make test
make lint
make run
make apk-path
make shell
```

`make test` and `make run` transfer only Gradle build inputs and `app/src` into a fresh container snapshot, avoiding stale bind mounts and excluding credentials. `make run` copies the debug APK to host `app/build/outputs/apk/debug/app-debug.apk`; it does not launch the app. `make ingress-test` separately tests the server in an isolated Hermes container.

Install on Android **8.0/API 26 or newer**. If a phone is reachable through container ADB, use `make install`; otherwise privately copy the APK to the phone and open it, enabling installation from that file manager when prompted. No host Android tooling is needed.

## Proof and roadmap

See [`docs/private-hermes-poc.md`](docs/private-hermes-poc.md) for verified server proof and remaining phone gates, [`server/README.md`](server/README.md) for the ingress contract, and [`docs/architecture.md`](docs/architecture.md) for historical design notes. The browser previews in `docs/` are illustrative, not device screenshots.

See [`docs/voice-ux-next.md`](docs/voice-ux-next.md) for the implemented recorder scope and physical-phone checklist. The current proof includes voice upload to the companion ingress, where transcription precedes admission; installed-phone basic voice delivery and auto-close are user-confirmed; full failure/lifecycle checks remain pending. Polished settings, multiple profiles/widget destinations, durable automatic outbox, and in-app replies remain out of scope. Server credentials and authorized DM destination stay on the server.

## Development and releases

Work on feature branches from updated `main`; review and verify `make test`/`make run` before merging. Release tags belong only on verified `main`, never feature branches. No force-push to `main`.

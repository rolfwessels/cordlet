# Cordlet

Android home-screen shortcut for sending text to private Hermes ingress, with replies in the existing Discord DM. Current branch: `feature/private-hermes-ingress`; this is a proof build, not a tagged release.

## Current APK behavior

The unchanged widget text area launches the Compose activity with its field focused. Type a message and tap **Send** or keyboard Send. Cordlet posts JSON `request_id` and `text` to the imported endpoint with `Authorization: Bearer <device token>`. Success requires HTTP **202**, JSON `status: "accepted"`, and a matching `request_id`; it means queue admission, **not** agent completion or Discord delivery. Accepted input clears; errors retain it. Unchanged-text retries reuse the ID; editing or acceptance generates a new one. Text and retry identity survive activity recreation, but this is not a durable background outbox. Redirects are refused.

The mic still provides visual feedback only: no recording or voice upload. Widget geometry/artwork and composer controls are unchanged. Replies are read in Discord, not Cordlet. Phone install, widget layout, SAF import, Keystore operation, and end-to-end phone delivery still require device verification.

## Private device configuration

No credential is compiled into either APK variant. The old `discord.local.properties` file is ignored and is **not read or transferred** by the build. Existing old configured APKs still expose their compiled bot credentials: rotate those separately.

1. Privately transfer a JSON file to the phone through a trusted channel. Do not put it in Git, public links, or chat:
   ```json
   {
     "endpoint": "http://hermes.bot.sels.co.za/cordlet/messages",
     "token": "<privately provisioned device bearer token>"
   }
   ```
2. Open Cordlet, tap **Import private config**, and choose the file using Android's document picker (SAF). The app requests no broad storage permission or persistent document access. **Replace private config** imports a replacement later.
3. Endpoint and token are stored only as AES-GCM ciphertext in app-private preferences. The encryption key resides in AndroidKeyStore; backups are disabled. Uninstalling/clearing app data requires reimport. No token is displayed or logged. Remove the plaintext import file and its cloud copy after provisioning; encryption does not protect those external copies.
4. Enable the phone's private VPN and send a unique harmless message. Confirm acceptance in the app **and exactly one reply in the intended private Discord DM**.

Cleartext is denied by default and allowed only for the exact `hermes.bot.sels.co.za` domain (no subdomains). Config validation permits that exact HTTP endpoint or HTTPS `/cordlet/messages` endpoints without embedded credentials, query, or fragment. This HTTP proof relies on the private phone VPN for transport protection: do not expose it publicly, disable the VPN for sending, or use Tailscale Funnel. The phone receives a scoped ingress device credential, never Discord credentials or Hermes's general API key.

Limits match the ingress contract: nonblank text, at most 4000 UTF-16 code units (conservative for non-BMP text), 8192 UTF-8 bytes including JSON. Server deduplication is process-local and finite; retries across server restarts/eviction are not an exactly-once guarantee.

## Container-first development

Host requirements: **Docker, Docker Compose, Make**. Java, SDK, Gradle, ADB and build tools remain in Docker.

```sh
make up
make doctor
make test
make run
make apk-path
make shell
```

`make test` and `make run` transfer only Gradle build inputs and `app/src` into a fresh container snapshot, avoiding stale bind mounts and excluding credentials. `make run` copies the debug APK to host `app/build/outputs/apk/debug/app-debug.apk`; it does not launch the app. `make ingress-test` separately tests the server in an isolated Hermes container.

Install on Android **8.0/API 26 or newer**. If a phone is reachable through container ADB, use `make install`; otherwise privately copy the APK to the phone and open it, enabling installation from that file manager when prompted. No host Android tooling is needed.

## Proof and roadmap

See [`docs/private-hermes-poc.md`](docs/private-hermes-poc.md) for verified server proof and remaining phone gates, [`server/README.md`](server/README.md) for the ingress contract, and [`docs/architecture.md`](docs/architecture.md) for historical design notes. The browser previews in `docs/` are illustrative, not device screenshots.

Voice, polished settings, multiple profiles/widget destinations, durable outbox, and in-app replies are out of scope for this proof. Server credentials and authorized DM destination stay on the server.

## Development and releases

Work on feature branches from updated `main`; review and verify `make test`/`make run` before merging. Release tags belong only on verified `main`, never feature branches. No force-push to `main`.

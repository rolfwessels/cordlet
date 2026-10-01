# Install Cordlet ingress on another Hermes instance

This optional plugin adds **POST `/cordlet/messages`** to Hermes's API server. It authenticates a scoped device key and submits text to the configured owner's **existing Discord DM session**. Discord remains the reply surface. No Discord token belongs on the phone, no second agent loop is created, and no core Hermes patch is required.

## Prerequisites and compatibility

- A working Hermes gateway with the Discord adapter connected, an authorized owner, and an existing private DM with that bot.
- Hermes API server enabled (`API_SERVER_ENABLED=true`, appropriate `API_SERVER_HOST` and `API_SERVER_KEY`). Default API port is **8642**, not dashboard port **9119**. Keep the general API key server-side.
- Plugin support including `register_platform_handler("api_server", ...)`, gateway runner owner authorization, and synthetic-event FIFO admission receipts. These are Hermes internals, not a stable portable protocol: run the tests and Plugin Doctor against your actual Hermes image/version before installation and after upgrades. Missing authorization support fails closed.
- Private phone reachability (for example Tailscale on phone and host/proxy). Do not use public Funnel or publish the whole API. Native Android does not require permissive CORS.

Reference: [Hermes API server docs](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server). Use the documentation for your runtime when enabling the API or restarting Docker deployments.

## Tested compatibility

The successful live deployment reports **Hermes Agent v0.21.1 (2026.9.7), upstream `a25967b4`**. Server tests passed using `nousresearch/hermes-agent@sha256:3f37990271dee44b9dec6a927268533e3a900a26bd0075f6c36bd548cdd23ff0`. The default `latest` image is mutable: pin `HERMES_TEST_IMAGE` to this digest for reproduction, and revalidate against the actual deployment before upgrading.

## 1. Choose the identity and create private config

Find `chat_id` and `user_id` from the **existing owner's Discord DM origin/session metadata**. Do not substitute the guild ID, bot ID, or a shared channel. Ensure the gateway already authorizes that user. The phone cannot select another identity or destination.

Create `$HERMES_HOME/cordlet-ingress.json` **outside this repository**, with owner-only permissions (0600):

```json
{
  "token": "<generate a cryptographically random device key locally>",
  "chat_id": "<your existing Discord DM chat ID>",
  "user_id": "<your authorized Discord user ID>",
  "user_name": "Cordlet user",
  "chat_name": "Cordlet DM"
}
```

Placeholders are not usable credentials. Generate at least 24 characters of cryptographic randomness (for example Python `secrets.token_urlsafe(32)`) directly into the private file, without printing the value in chat/logs. IDs must be 17–20 decimal digits. Optional `CORDLET_INGRESS_CONFIG` selects another file; otherwise the active Hermes home is used. In Docker, put config and plugin in the **persistent mounted Hermes home**, not just a transient container layer. Config changes require a gateway restart because config is loaded at startup.

## 2. Install, validate, enable, restart

Copy both files from `server/cordlet_ingress/` into `$HERMES_HOME/plugins/cordlet-ingress/`:

- `__init__.py`
- `plugin.yaml`

Inside your Hermes environment, with the correct active `HERMES_HOME`:

```sh
hermes plugins doctor --ci "$HERMES_HOME/plugins/cordlet-ingress"
hermes plugins enable cordlet-ingress
```

Restart the gateway through your deployment's normal mechanism (for Docker, restart the appropriate container/service). Editing an installed plugin does not attach routes to an already running, frozen HTTP router. Install this in the intended profile only.

## 3. Route only the device endpoint

Your private proxy must route **exact path `/cordlet/messages` to API port 8642**. Keep the dashboard router on port 9119 and retain private-network/IP restrictions. A domain which serves the dashboard does not automatically serve this plugin. Preserve the Authorization header and body; no redirects. Do not expose other API paths merely to make Cordlet work.

Prefer private HTTPS. The current Android proof permits HTTP only for the exact endpoint `http://hermes.bot.sels.co.za/cordlet/messages`. Other installations should use an HTTPS hostname and import that URL. If deliberately supporting another private HTTP hostname, update Android's config validation **and** scoped network-security XML and rebuild; do not enable global cleartext. HTTP is not itself encrypted; Tailscale protects only the segments actually inside its tunnel. Protect any proxy-to-container hop separately as appropriate.

## 4. Prove server routing before phone setup

From the repo, `make ingress-test` runs the tests in a disposable Hermes Docker image without networking or host installs. Set `HERMES_TEST_IMAGE` to your compatible runtime image. Tests include real aiohttp requests, owner checks and busy FIFO behavior, but mocked adapters do not prove Discord delivery.

Inside the running Hermes environment, run the repo's credential-safe probe using that environment's Python:

```sh
python /path/to/cordlet/server/probe.py
```

It **reads** the private config locally but does not print the key. It sends a real harmless unique marker and reports admission. Verify one corresponding reply in the intended DM. HTTP 202 alone is not delivery. Avoid repeated probes unless you want more messages.

## 5. Provision and test Android

Privately create a second JSON file outside the repository containing only:

```json
{
  "endpoint": "https://your-private-host/cordlet/messages",
  "token": "<same device key as the server config>"
}
```

Build with `make test` and `make run`; install the resulting APK. Transfer this config through a private trusted channel, not a public link or Discord chat. Open Cordlet → **Import private config** → choose the file. App-private preferences hold AES-GCM ciphertext; AndroidKeyStore holds its key. No device key or Discord credential is compiled into the APK. Backups are disabled. Delete plaintext phone/cloud provisioning copies after import. Uninstall/clear-data requires reimport.

Send a unique harmless message from the installed phone APK. Verify all three: app says **Accepted by Hermes; reply in Discord**, composer clears, and exactly one reply arrives in the intended DM. The current deployment's basic installed-phone path was user-confirmed; other deployments must repeat this proof.

To rotate the device key, update the private server file, restart the gateway and privately reimport the replacement phone config. This proof supports one configured owner/device credential per installation.

## Request and response contract

- Header: `Authorization: Bearer <device-key>` (never the general Hermes API key).
- JSON fields **only**: `request_id` (1–80 ASCII letters/digits/underscore/hyphen) and nonblank `text` (maximum 4000 characters).
- Maximum body: 8192 bytes. Android conservatively counts UTF-16 code units.
- HTTP 202 body: `{"request_id":"your-id","status":"accepted","duplicate":false}`. Retrying the same ID/text returns accepted with `duplicate:true`; reuse with different text returns 409.
- 400 invalid payload; 401 missing/bad device auth; 403 owner unauthorized; 409 ID conflict; 413 oversized body; 503 adapter/authorization support/admission unavailable.
- Synthetic input has no native Discord message ID or reaction/reply reference. Gateway commands/approval/clarify controls are disabled; owner authorization is checked explicitly before internal dispatch.

## Troubleshooting and limitations

- GET 405 is expected; POST 401 without a key is expected. Neither proves authenticated delivery.
- 404: check route/proxy port, installed plugin, active profile, and restart.
- 401 with imported config: compare provisioning locally; never paste keys into chat.
- 403: fix authorization of the configured owner, not a global auth bypass.
- 503: inspect gateway adapter/admission logs with credentials redacted.
- Acceptance is not completion; model execution or Discord delivery can fail afterward. There is no delivery-status API.
- Deduplication is process-local, bounded to 512 accepted IDs and lost on restart/eviction. No durable exactly-once guarantee or durable background Android outbox.
- No custom rate limiting; keep private and use manual sends. Hardening is future work.
- Android import/basic send was tested on the user's phone; offline retries, all widget/launcher behavior and upgrade compatibility are not universally verified.

For project progress and historical proof details see [`../docs/private-hermes-poc.md`](../docs/private-hermes-poc.md). No live keys, private config, APKs or credential-bearing logs belong in Git.

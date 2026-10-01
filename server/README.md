# Cordlet ingress server proof

A small Hermes plugin adds `POST /cordlet/messages` to the existing API server and submits a normalized text event through the Discord adapter. It does not use Discord REST as a sender bot and does not create another agent/API conversation.

## Request

- Header: `Authorization: Bearer <device token>` (not Hermes's general API key).
- JSON fields only: `request_id` (1–80 ASCII letters/digits/underscore/hyphen), `text` (nonblank, maximum 4000 characters).
- Maximum request body: 8192 bytes.
- Destination and authorized owner are fixed by server configuration; caller cannot override them.
- `202 accepted` means queued/admitted, not agent completion or Discord delivery.
- `401`: bad/missing auth; `400`: invalid payload; `409`: ID reused with different text; `413`: oversized body; `503`: Discord adapter missing or admission refused.

## Tests

From the repository: `make ingress-test`. This transfers the current server source into a disposable Hermes Docker image and runs unittest without networking or host installs. The tests exercise a real aiohttp endpoint but replace the live Discord adapter with a recording test adapter. They are NOT proof of Discord delivery. `HERMES_TEST_IMAGE` can select another compatible Hermes image.

Plugin validation inside Hermes:

```sh
HERMES_HOME=/opt/data /opt/hermes/.venv/bin/hermes plugins doctor --ci /opt/data/projects/cordlet/server/cordlet_ingress
```

## Deployment shape

Copy `server/cordlet_ingress/__init__.py` and `plugin.yaml` into `$HERMES_HOME/plugins/cordlet-ingress/`. Create `$HERMES_HOME/cordlet-ingress.json` with mode 0600, containing `token`, `chat_id`, `user_id`, and optional `user_name`/`chat_name`. Use a cryptographically random device token; do not commit this file. The IDs must designate the authorized owner's private DM. Confirm them from the existing session origin, not a guild/server ID.

Enable with `hermes plugins enable cordlet-ingress`. The API server must already be enabled. Restart the gateway to attach the route; changing the plugin on disk does not attach it to a frozen, running HTTP router. No core Hermes patches are needed. Optional `CORDLET_INGRESS_CONFIG` selects another config file; otherwise the active Hermes home scopes it.

After restart, run inside Hermes:

```sh
HERMES_HOME=/opt/data /opt/hermes/.venv/bin/python /opt/data/projects/cordlet/server/probe.py
```

This makes a REAL harmless host-originated request and prints only a unique proof ID and HTTP/admission result. Find that ID in the active DM session and verify the reply arrives once in Discord. It deliberately does not read or print the token. Do not run repeated probes while debugging unless you want multiple messages.

## Limitations and next gate

- Process-local deduplication retains 512 accepted IDs; restart/eviction forgets them. No durable exactly-once guarantee.
- No completion/delivery-status API yet. Acceptance can precede authorization/agent/send failure.
- No custom rate limiting yet; keep the route private and use only manual probes.
- Synthetic events have no native Discord message ID, so native reactions/reply references on the input are unavailable.
- Phone text cannot execute slash commands or resolve gateway approval/clarify controls in this proof.
- The route checks the configured owner's authorization explicitly, then sends a synthetic event with gateway controls disabled. This uses the runner's FIFO while busy instead of steering into the active turn. Missing authorization support fails closed with 503; revoked/unauthorized owner returns 403.
- The route uses the normal Discord session key and queue, but live processing/delivery must still be proven.
- Tailscale/private HTTPS and phone reachability are not configured. Do not publish the entire Hermes API to the Internet. When attaching Tailscale, prefer exposing only the Cordlet route through a narrow proxy; never give the phone the general API key.

See `../docs/private-hermes-poc.md` for authoritative progress and scope.

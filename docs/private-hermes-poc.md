# Private Hermes ingress: proof of concept

## Current checkpoint — read this first

**Local proof PASSED.** The custom ingress accepts HTTP 202, FIFO-queues a separate user turn in the existing DM session, and Wren replies in Discord. The user confirmed receipt. Eleven container tests pass. Earlier sections below retain historical failures; their old restart/pending statements are superseded by this checkpoint.

**Next:** establish private phone connectivity over Tailscale, then wire Cordlet's Send button. Do not redesign the working ingress. Needed from the user: whether the Docker host and Android phone are already connected to the same tailnet. Do not request passwords, Tailscale auth keys, or device credentials in chat. Any login/approval remains a user action.

## Product goal — do not lose this context

Cordlet exists to remove the friction of opening Discord, finding the agent, and composing a message. The goal is ONE-CLICK access: tap the home-screen shortcut, type, send; Wren replies in the user's private Discord DM. Replies inside Cordlet, voice, and multi-destination onboarding are future features, not prerequisites.

The previous prototype went too deep before validating its critical assumption. Perry sending to a shared server channel does not enter the user's DM conversation. A successful build or Discord POST was not evidence of success. This iteration must prove the receiving route BEFORE building phone features.

## Agreed approach

- Start from current main on feature/private-hermes-ingress.
- Use a small authenticated private HTTP ingress, reachable from Android through Tailscale.
- Keep Discord credentials on the server. Do not compile them into the APK.
- Prefer the gateway's normal inbound processing and outbound Discord reply path.
- Preserve the existing DM session if supported cleanly. If session bridging becomes a rabbit hole, stop and discuss before implementing a workaround; do not silently change the destination or requirement.
- Keep project context and verified results in these docs, not solely in chat history.
- Do not expose a public endpoint or use Tailscale Funnel. Private access and authentication are mandatory even for the proof.

## Scope and proof gates (in order)

1. **Host-to-gateway proof, before Android changes.** Submit a unique harmless message through authenticated HTTP; verify processing and one reply in the intended private Discord DM. Record exact routing semantics and whether it shares the active DM session.
2. **Phone network proof.** Repeat from the user's phone over Tailscale. No APK rebuild required to prove connectivity first.
3. **Cordlet proof.** Replace/wrap the current Send transport with configurable URL and device credential. Use the installed APK to send a unique text and verify one reply in the intended DM.
4. **Harden after the route works.** Improve setup, durable retry/deduplication, secure credential storage, and destination profiles incrementally. Basic auth, bounded requests, and no accidental public exposure are not deferred.

Each gate must distinguish: request accepted; gateway processed the input; correct session selected; reply delivered. A mocked integration test cannot pass the live delivery gate. Phone-only checks require the user and must remain pending until actually exercised.

## Initial design hypothesis — not yet verified

Android -> HTTPS over Tailscale -> narrow Cordlet ingress -> Hermes gateway -> private Discord DM reply.

Hermes has a bearer-authenticated API server (API_SERVER_KEY), but normal API requests return replies to the HTTP client and are not automatically Discord inbound turns. Exposing the standard API alone does not prove the desired route. Prefer a scoped device credential and fixed server-side destination rather than granting the phone general API administration access.

A minimal submission should contain message text and a client-generated request ID. Server-side routing should own the authorized identity and DM destination; do not trust caller-supplied user/session IDs. Avoid processing the bot's own Discord messages or disabling its loop guard. Account for concurrent DM turns using the gateway's existing queue/locking mechanisms.

## Non-goals

- Voice recording, in-app reply UI, polished onboarding, multi-widget destinations.
- A general relay framework or Discord user-account automation.
- Changes to the gateway's own-message guard.
- Claiming success from HTTP 200, an APK build, or a reply in a shared channel.

## Current verified status

- Pulled main at f8d580c (prototype Discord text send; routing unresolved).
- Created feature/private-hermes-ingress from that commit; initial goal/scope committed as 38f599d.
- Implemented `server/cordlet_ingress`: a native Hermes plugin adding POST /cordlet/messages on the existing API server, using a separate device bearer credential and fixed server-side owner/DM destination.
- Normalized events are submitted to the Discord adapter's normal admission/queue path, not a separate API agent conversation. Tests confirm the derived key matches the existing DM key. Actual session execution/delivery remains unverified.
- `make ingress-test`: 8 tests passed in a disposable network-disabled Hermes container, after observing the initial missing-implementation failures. Covers auth, fixed routing, admission semantics, duplicates/conflicts, input validation, malformed JSON, missing Discord adapter, and retry after refused admission.
- Plugin Doctor passed for both the repository artifact and installed copy.
- Copied the plugin to /opt/data/plugins/cordlet-ingress, enabled it in the default profile, and created /opt/data/cordlet-ingress.json with a random credential and mode 0600. Verified enabled/configured state without printing the credential. No core patches, Android changes, public exposure, or gateway restart performed.
- Existing API health returns HTTP 200 on loopback port 8642. The new route currently returns HTTP 404 because the live router predates plugin installation.
- Executed the real host probe: ID cordlet-proof-f93b6fc0600a4b8d8f03abe3dc157c26, HTTP 404; NOT admitted and NOT a successful delivery test.
- **Immediate blocker: gateway restart required.** This is the live gateway serving the current conversation. Ask the user to send /restart, then continue with the live host probe; do not mark gate 1 passed until its reply appears in the intended DM.
- After restart: run `HERMES_HOME=/opt/data /opt/hermes/.venv/bin/python /opt/data/projects/cordlet/server/probe.py`; verify the unique message and response in the active DM transcript plus actual Discord delivery. A 202 alone is not proof.
- Tailscale CLI was absent inside the Hermes container; host installation/network still needs discovery. Phone gate and APK gate remain pending.
- See [server operational notes](../server/README.md) for request contract, test/deployment commands, and explicit proof limitations (including process-local dedupe and no delivery-status endpoint).

## Live proof follow-up: busy-session false failure

- After the user restarted the gateway, POST /cordlet/messages without auth returned 401: the live plugin route was attached.
- Real authenticated probe `cordlet-proof-e656e54ce3b14224ad50631e4a42e255` reached the active agent; it replied with that ID and `received`. SQLite records the assistant reply in the same active DM session `20261001_064717_a5f42d7f`. Gateway logs at 07:07:52 UTC report final delivery confirmed for the intended Discord DM.
- HOWEVER the HTTP request returned 503. Gate 1 is not passed: acceptance semantics were wrong, and the input steered the active turn instead of producing a separate queued user turn.
- Root cause: `GatewayBusySessionMixin._handle_active_session_busy_message` can steer normal events without setting `_gateway_accepted`; `BasePlatformAdapter.handle_message` returns without repairing that receipt. The ingress interpreted false as refusal even though processing happened. Do not retry that old ID on the old plugin; it was not deduplicated.
- Fix: explicitly invoke the runner's owner-authorization gate (403 on denial, 503 if absent), then submit a synthetic internal event with `allow_gateway_control=False`. The runner queues that event via FIFO rather than steering. This deliberately moves cold-path authorization into the ingress because internal events bypass the cold authorization check. No gateway core patch or own-message-guard change.
- Three regression tests failed before the fix. `make ingress-test` now passes 11 tests, including execution of Hermes's actual busy-session handler and FIFO admission code, owner denial, and fail-closed absence of authorization support.
- Updated plugin copied to /opt/data/plugins/cordlet-ingress; installed source hash matches the repository and Plugin Doctor passed. Another gateway restart is required before the fixed live probe. Phone/Tailscale/Android work remains deferred until clean HTTP acceptance plus separate DM turn and delivered reply are verified.

## Fixed live probe — awaiting queued turn completion

- Gateway PID is now 36895 (previous observed PID was 35382); the user resumed after restart.
- Executed host probe `cordlet-proof-1c6976d61aca4511b258d121fc60ce92`: HTTP 202, status accepted, duplicate false.
- This probe was submitted while this DM turn was active. End the current turn so the FIFO can process it; do not wait inside the active turn for its own follow-up.
- Gate 1 remains pending until the probe produces a separate user turn and exactly one delivered reply here. Next verification: inspect this ID in the active DM transcript and gateway delivery evidence. Do not resend the probe.

## Local proof PASSED

- Fixed live probe `cordlet-proof-1c6976d61aca4511b258d121fc60ce92`: HTTP 202, accepted, duplicate false, submitted during an active DM turn.
- Read-only SQLite verification found one exact probe user message followed by one exact `received` assistant reply, both in the existing DM session `20261001_064717_a5f42d7f`.
- The user confirmed the proof reply appeared in Discord. Gate 1 (local HTTP -> queued separate turn -> same-session Discord reply) is passed.
- No public exposure or Android transport changes were needed for this proof.
- Next gate: private Tailscale connectivity from the phone. Keep the existing working ingress; do not redesign it or switch to a webhook without a new reason.

## References

- [Prior handoff and failure analysis](next.md)
- [Hermes API server documentation](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server)

## Resume checklist

Read this document and docs/next.md first. Inspect actual branch and working-tree state. Continue at the earliest unverified proof gate. Record blockers and execution evidence here before expanding scope. Never put credentials in docs, logs, source, or chat.

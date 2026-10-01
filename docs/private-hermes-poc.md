# Private Hermes ingress: proof of concept

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
- Created feature/private-hermes-ingress from that commit.
- No ingress implemented or phone-to-DM delivery proven yet.
- Tailscale CLI was absent inside the Hermes container; host installation/network still needs discovery.

## References

- [Prior handoff and failure analysis](next.md)
- [Hermes API server documentation](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server)

## Resume checklist

Read this document and docs/next.md first. Inspect actual branch and working-tree state. Continue at the earliest unverified proof gate. Record blockers and execution evidence here before expanding scope. Never put credentials in docs, logs, source, or chat.

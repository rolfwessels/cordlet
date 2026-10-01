# Next: private, same-session delivery

> **Active iteration:** read [private-hermes-poc.md](private-hermes-poc.md) first. The agreed priority is one-click phone access, replies in the private DM, and proof of the HTTP receiving route before Android changes. The notes below explain the previous Discord failure; they are not a mandate to build every candidate architecture.

## Where we stopped

Cordlet is a container-built Android widget and compose activity. Tapping the widget opens the activity with text entry focused; **Send** uses the configured bot credential to POST to one configured Discord channel and mention one configured recipient bot. Voice recording and per-widget destination setup are not implemented. The debug APK embeds the test bot token and was transferred privately through the Dropbox `Wren-Access` app folder. This is a phone-test prototype, not a release.

The current test configuration sends as **Perry** to the shared server's `#general` channel and mentions **Wren**. This does **not** meet the actual goal: a quick private message from the phone that Wren receives and answers **in the existing Discord DM session with the user**. A guild-channel conversation has its own Hermes session, even if bot-authored messages are admitted. The user does not want their conversations posted to `#general`.

## What is established—and what is not

- The app builds and its automated tests passed; the private APK upload was downloaded back and byte-verified. This verifies the artifact, **not** phone-to-Hermes delivery.
- Wren's gateway originally rejected messages authored by other bots. The local gateway was configured to admit explicitly mentioned bot messages (`DISCORD_ALLOW_BOTS=mentions`, with `discord.bots_require_inline_mention=true`). This is **not** a bridge into the user's DM session. No successful Cordlet-to-this-DM end-to-end test is recorded.
- A brokered request from the Hermes host could post to this DM channel, but Agent Vault made that request as Wren. It did not prove that Perry's phone credential could use this DM channel.
- Hermes's Discord adapter ignores messages authored by its **own** bot to prevent feedback loops. Switching the APK to Wren's token and this DM channel would need an intentionally narrow self-message intake exception; it has **not** been implemented or tested. Wren's raw token is not retrievable by the agent from Agent Vault and should not be shipped in an extractable APK.
- No private relay, inbound port, VPN, webhook-to-session integration, or other ingress path was implemented. Hermes is not publicly exposed today. Do not assume a public port is required; compare private and authenticated options first.

## Decision to make next

Design the shortest safe path from a phone to **this DM conversation and its session**, without posting message content in a shared server channel or embedding Wren's main bot token in a distributed APK. Have multiple reviewers challenge the options before building. Candidate directions to evaluate, **not commitments**:

1. A dedicated sender credential plus a private Discord location, with an authenticated, narrowly scoped gateway bridge into the existing DM session. Verify session identity, reply destination, duplicate handling, and bot-loop prevention.
2. A small authenticated HTTPS relay/inbound endpoint reachable privately (for example through a VPN/tunnel rather than exposing Hermes publicly), which routes to the intended session and posts replies here. Specify how the phone authenticates and how replay/rate limits are handled.
3. Another Discord-supported way to make the message genuinely arrive in this DM as an authorized inbound turn, without automating a human Discord account or giving the phone Wren's main bot token.

For each proposal, show the exact trust boundary, who holds which credential, how it selects the **existing** DM session, how responses appear **here**, and what breaks when the phone or gateway is offline. Keep Cordlet open-source and destination-agnostic; future widgets should each have a configurable destination and icon.

## Acceptance test before calling it done

From the **installed phone APK**, send a unique harmless text; confirm the input and POST outcome on the phone, then confirm the exact message enters **this** Hermes DM session and that Wren replies **in this DM**, once, without a server-channel copy or feedback loop. A Discord HTTP 2xx, a message visible somewhere else, or a separate Hermes session is not success. Do not mark an architecture as working until this device-to-session test passes.

Do not change the gateway's own-message guard, swap to Wren's token, expose Hermes to the Internet, or merge this prototype as a finished messaging feature merely to make the test pass.

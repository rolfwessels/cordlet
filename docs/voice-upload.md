# Voice upload: implementation and proof gates

Cordlet uploads AAC in an M4A/MP4 container to the **same** configured `POST /cordlet/messages` endpoint. Existing JSON text requests remain supported; no extra private proxy route is needed.

## Wire contract

- `Authorization: Bearer <scoped device key>`; same server-owned identity and DM as text.
- `Content-Type: audio/mp4` with raw file bytes (not base64 or multipart).
- `X-Cordlet-Request-ID`: 1–80 ASCII letters, digits, underscores or hyphens. Reuse the ID and exact bytes after an ambiguous failure; different payload with the same ID is a conflict.
- Audio is limited to 10 MiB, AAC-only audio streams in actual MP4/M4A, finite positive duration ≤300 seconds. `ffprobe` is required; MIME/extension alone is not validation.
- HTTP 202 requires `status: accepted` and matching `request_id`. This means the transcribed input was admitted to the configured DM, not that a reply was delivered.
- 400 invalid ID; 401 bad key; 403 unauthorized owner; 409 conflicting ID; 413 body too large; 415 unsupported audio MIME; 422 invalid/inaudible media; 503 busy or unavailable transcription/admission.

## Hermes runtime requirements

STT must be enabled and provisioned. This deployment uses Hermes's existing local STT (`base` model). The real `tools.transcription_tools.transcribe_audio(path, None, "gateway")` recognized a generated six-second AAC clip as “Cordlet Voice Delivery Proof. Please reply with Voice Note Received.” No transcript is fabricated by the ingress.

The server checks auth before buffering/storing/transcribing, creates a private server-generated temporary file, validates it with bounded `ffprobe`, then transcribes. After rechecking owner authorization, it posts a **bot-generated transcript record** via the existing Hermes Discord adapter to the server-configured DM **before dispatching that note**:

```text
🎙️ Heard from your voice note:
Automatic transcription; may contain mistakes.
> What STT heard…
```

This is not a user-authored Discord message or a native user voice bubble. Audio is not mirrored to Discord. Long transcripts use individually quoted chunks below Discord's limit, counting UTF-16 conservatively; markdown is escaped and mentions are made inert because the adapter's `send()` has no per-send mention override. JSON text submissions do not echo. The original transcript is separately queued as quoted text with gateway controls disabled into the same DM, preserving the existing busy FIFO and avoiding native media-event merging. Temporary audio is removed after worker completion.

Echo and gateway admission are **not atomic**. Process-local receipt states prevent accepted retries from echoing/dispatching again. Explicit non-admission can retry dispatch using the cached input without STT or another echo. An ambiguous/failed (including partial) echo returns sanitized `503 voice_echo_uncertain`, never dispatches, and does not resend on unchanged retry. An exception during dispatch without a positive admission receipt returns `503 voice_admission_uncertain` and does not redispatch; a positive receipt is retained even if dispatch then raises. These uncertain states require operator reconciliation in the DM, not blind new-ID retries (which could duplicate an answer). This trades automatic recovery for avoiding duplicate side effects. Receipt states, including failures, share the 512-entry process-local cache: restart/eviction loses them, so exactly-once echo/answers after restart are **not guaranteed**.

One STT worker is allowed. A 120-second response deadline returns a retryable failure, but cannot kill a Python thread. The slot and file remain owned until that worker finishes; a permanently hung worker requires gateway recovery. Dedupe is still bounded/process-local and lost on restart/eviction: no durable exactly-once claim.

Install the updated plugin and restart the gateway using `server/README.md`. A disk copy or CLI restart success alone does not prove the route changed—read back the actual live endpoint.

## Current checkpoint

- Real local AAC transcription succeeded.
- 36 server tests passed, including actual AAC probing plus mocked STT/admission tests.
- Independent server security/logic review found no blocking defects.
- First live attempt before the restarted gateway was loaded returned 413; later live audio request returned **HTTP 202 with matching ID** for `cordlet-voice-proof-c68d35e19f324b22b29f42e9ea793f51`.
- The corresponding transcribed clip arrived as a separate input in the existing DM and Wren replied **“Voice Note Received.”** This closes the host-originated audio → local STT → same-DM reply proof.
- **45 server tests pass**, including AAC probing, transcript echo/order, chunking, mention neutralization, dedupe and failure-state handling.
- **93 Android tests pass** with no failures/errors/skips; lint and the delivered `0.4.1-quickvoice` APK build passed. Source-level independent reviews passed before phone handoff.
- **User-confirmed phone proof:** recorded voice notes reached the existing DM; Send closed the capture screen automatically. After server-side echo deployment, the user confirmed the visible transcript format and approved the completed flow.
- Screen-timeout prevention, exhaustive fresh-launch combinations, failure retries and background/process-death cases still need separate on-device checks. Passing the basic flow does not verify all lifecycle cases.

## Phone acceptance checklist

Record a unique spoken phrase, tap Send, verify acceptance and one corresponding answer in the existing DM. Repeat with a saved note, a temporary VPN failure then unchanged retry, and background/reopen while sending. Failed upload must retain the note and identity; accepted note must not be automatically resubmitted. Test those on-device before claiming phone reliability.

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

The server checks auth before buffering/storing/transcribing, creates a private server-generated temporary file, validates it with bounded `ffprobe`, then transcribes before dispatch. It queues quoted text with gateway controls disabled into the same DM. This deliberately avoids native media-event merging while busy. Temporary audio is removed after worker completion; the upload is not mirrored as a native Discord audio message.

One STT worker is allowed. A 120-second response deadline returns a retryable failure, but cannot kill a Python thread. The slot and file remain owned until that worker finishes; a permanently hung worker requires gateway recovery. Dedupe is still bounded/process-local and lost on restart/eviction: no durable exactly-once claim.

Install the updated plugin and restart the gateway using `server/README.md`. A disk copy or CLI restart success alone does not prove the route changed—read back the actual live endpoint.

## Current checkpoint

- Real local AAC transcription succeeded.
- 36 server tests passed, including actual AAC probing plus mocked STT/admission tests.
- Independent server security/logic review found no blocking defects.
- First live attempt before the restarted gateway was loaded returned 413; later live audio request returned **HTTP 202 with matching ID** for `cordlet-voice-proof-c68d35e19f324b22b29f42e9ea793f51`.
- The corresponding transcribed clip arrived as a separate input in the existing DM and Wren replied **“Voice Note Received.”** This closes the host-originated audio → local STT → same-DM reply proof.
- Android Send is implemented and the `0.4.0-voice-send` APK built. **83 Android tests passed**, lint passed with no errors; independent client review is pending at this checkpoint.
- Installed-phone voice upload/reply and upload lifecycle checks still require the user's device proof.

## Phone acceptance checklist

Record a unique spoken phrase, tap Send, verify acceptance and one corresponding answer in the existing DM. Repeat with a saved note, a temporary VPN failure then unchanged retry, and background/reopen while sending. Failed upload must retain the note and identity; accepted note must not be automatically resubmitted. Test those on-device before claiming phone reliability.

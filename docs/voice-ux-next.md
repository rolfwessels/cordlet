# Voice recording and Send milestone

## Current scope (`feature/voice-recorder`, 0.4.0-voice-send / code 5)

The widget text region opens the focused text composer; text sending is unchanged. Both mic actions open the non-exported recorder. Imported bot name/offline icon and the real multi-bar amplitude waveform are preserved.

- Permission-gated foreground AAC/M4A capture in app-private `files/voice/latest.m4a`; five active minutes and 4 MiB capture limit. Pause/resume, Finish & review, playback and explicit Discard remain available. No storage permission or recording service.
- Leaving foreground pauses capture/stops playback; `onStop` finalizes MPEG-4. Saved audio is recovered after reopening. Unreadable audio/metadata is blocked and kept until explicit Discard. No automatic overwrite.
- Send from paused or saved review finalizes the recorder before reading bytes. Raw fixed-length streamed POST goes to the existing imported `/cordlet/messages` endpoint with bearer authentication, `audio/mp4`, and `X-Cordlet-Request-ID`; no multipart/base64/alternate endpoint. Client request bound is 10 MiB, response bound 8192 bytes, connect timeout 10 seconds and read timeout 180 seconds. Redirects are refused.
- Atomic synced private `latest.properties` stores the stable per-note identity and accepted flag. Failures keep file and ID; a matching HTTP 202 accepted receipt is the only success. Acceptance is persisted, disables repeat Send, and keeps audio for playback until Discard. Only Discard permits a new note/identity.
- Uploading locks playback, Discard and repeat Send. A process-owned session/coroutine survives activity recreation/reopening, so another activity cannot overwrite an in-flight file. Process death stops work; reopening recovers the same note/ID without auto-upload. Retry after uncertain acceptance uses the same ID. Server deduplication is finite/process-local, so server restart/eviction is not an exactly-once guarantee.
- Companion ingress transcribes before admission; acceptance means reply should be read in Discord, not that agent work/reply delivery has finished.

## Verification

JVM regression tests exercise request validation, exact receipts, durable identity/acceptance, failure retry identity, upload state and corrupt metadata. Source/manifest guards cover Android wiring, lifecycle finalization and streamed transport; they are not device instrumentation. Container lint/build and APK inspection must also pass. **Installed-phone voice capture, send, transcription/reply and process/lifecycle proof remain pending.** Prior text phone proof does not prove voice.

## Phone checklist (pending)

1. Install on Android 8/API 26+ with existing private config/VPN. Check widget text focus and unchanged text Send; imported avatar/name still render.
2. Grant/deny mic permission; verify waveform responds to speech, freezes on Pause, and elapsed excludes paused time. Finish and play audible recording.
3. Send a short unique harmless voice note from Pause: capture must finalize, upload state lock controls and repeat taps, acceptance display only after matching receipt, and one expected reply appear in the intended Discord DM.
4. After acceptance, reopen and force-stop/reopen: audio remains playable, Send remains disabled. Discard then record a new note.
5. Offline/missing-config/timeout/invalid receipt: audio remains playable; retry uses unchanged ID. Check no false acceptance, duplicate send or premature deletion.
6. Navigate Back/Home/lock screen during recording and uploading; reopen from widget repeatedly. Recording must finalize on Stop; an in-process upload must remain locked. Kill the process mid-upload, reopen and explicitly retry the retained note; account for server dedupe limits.
7. Interrupt capture abruptly: recover valid notes, otherwise retain unreadable file with blocked capture until Discard. Rotate in recording/review/upload states.
8. Exercise five-minute capture bound, small-screen scrolling, large fonts, accessibility and narrow widget cells. No phone behavior is claimed from the build alone.

## Deferred

Automatic durable background outbox, multiple simultaneous notes/profiles, in-app replies and export are not implemented. No background recording; voice data stays private apart from explicit authenticated Send.

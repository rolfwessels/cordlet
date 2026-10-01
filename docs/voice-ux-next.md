# Voice recorder milestone

## Implemented scope (`feature/voice-recorder`)

The widget text region still opens the focused text composer. The widget mic and empty composer mic open a separate recorder activity. Existing private-ingress text sending is unchanged.

- Microphone permission is requested on first recorder entry; recording starts only when permission is granted and the activity is visible. Denial leaves a retry instruction; permanently denied permission requires Android app settings.
- Record AAC audio in an M4A file at `files/voice/latest.m4a` in app-private storage. Only one note is retained; no storage permission, export, or background recording service is provided. App data clearing/uninstall removes it, and app backup is disabled.
- Show recording/paused/saved state, an active-time timer, and a microphone meter based on sampled `MediaRecorder.maxAmplitude` (not a decorative waveform).
- Pause/resume, Finish & review, play/stop playback and Discard are available. Recording is limited to five active minutes and 4 MiB; paused time does not count in the app timer.
- Losing foreground pauses capture immediately and stops playback; `onStop` finalizes and releases the recorder before background process death can occur. Returning opens saved review, not a resumable recording. Reopening recovers a valid saved note rather than automatically overwriting it. Rotation and repeat widget taps retain the current recorder session. Abrupt process death before finalization is best-effort recovery, not a guarantee: unreadable audio stays on disk in a blocked error state, requiring explicit Discard before a new recording. The exported text activity ignores voice launch extras; widget/composer actions directly launch the non-exported recorder.
- **Send is disabled and labeled “Audio upload not available yet.”** No audio upload, transcription, sending progress, or voice delivery to Wren is implemented. Text networking remains available separately; “local-only” applies to recorded audio, not the whole app.
- Compose and widget artwork/text share resource colors. Browser sketches are illustrations, not physical-phone screenshots.

## Physical-phone checklist (pending)

Install the debug APK on Android 8.0/API 26 or newer. Build/lint/unit tests do not prove microphone, launcher, or lifecycle behavior on a real phone.

1. Add the widget; check narrow and tall launcher cells. Tap its text area: confirm keyboard focus, typing and the existing text Send flow. With text present, the composer action should send text, not open the recorder.
2. Tap the widget mic and empty composer mic: confirm both open the dedicated recorder. Grant microphone permission; verify actual capture, elapsed time and level response to speech/silence.
3. Deny permission on a clean install, retry, and test permanent denial via Settings. Confirm a helpful message and no capture without permission.
4. Pause, wait, resume and Finish & review. Check paused time is excluded, playback is audible, and Send remains disabled with the upload-unavailable label.
5. Rotate during recording/paused/review states and tap the widget mic repeatedly. Confirm no duplicate recorder, reset, or accidental overwrite.
6. Press Home, lock the screen and switch apps: capture must stop/finalize and playback must stop. Return to saved review, not Resume. Use Back to leave, reopen and confirm recoverable audio is kept.
7. Force-stop after finishing a note, reopen, and play it. Separately interrupt active recording; expect only best-effort recovery and an honest error if unreadable. Verify saved and unreadable notes block automatic recording until explicit Discard; unreadable files must not be silently deleted or overwritten.
8. Discard and reopen; confirm the note is gone and a new recording can start. Record to the five-minute limit; confirm automatic finalization and usable review.
9. Enable airplane mode: record, pause, review and discard without importing private config. Voice must remain local and never report delivery. Re-enable the private VPN separately for the existing text-send proof.
10. Check small-screen scrolling, large font size, accessibility labels and touch targets on the installed APK.

## Later, separately approved work

Voice upload, transcription, backend processing and delivery semantics remain undecided. Do not imply that the “Voice note to Wren” heading represents actual delivery. Choose and prove a transport before enabling Send; preserve the fast text-widget path.

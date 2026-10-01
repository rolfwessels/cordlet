package io.github.rolfwessels.cordlet.voice

import java.util.UUID

fun newCaptureId(): String = UUID.randomUUID().toString()
fun canSendCapture(phase: VoicePhase, upload: VoiceUploadState, configured: Boolean, permitted: Boolean, audioMs: Long) =
    configured && permitted && audioMs > 0 && upload.canSend(phase)
fun shouldFinishCapture(visible: Boolean, accepted: Boolean) = visible && accepted
fun keepCaptureAwake(visible: Boolean, phase: VoicePhase, uploading: Boolean) =
    visible && (phase == VoicePhase.RECORDING || uploading)

package io.github.rolfwessels.cordlet.voice

/** Only finalized/safely finalizable notes can send; acceptance lasts until explicit Discard. */
data class VoiceUploadState(val uploading: Boolean = false, val accepted: Boolean = false) {
    val controlsLocked get() = uploading
    fun canSend(phase: VoicePhase) = !uploading && !accepted && (phase == VoicePhase.RECORDING || phase == VoicePhase.SAVED || phase == VoicePhase.PAUSED)
    fun begin() = if (uploading || accepted) this else copy(uploading = true)
    fun completed(accepted: Boolean) = copy(uploading = false, accepted = accepted)
}

package io.github.rolfwessels.cordlet.ui

/** In-memory composer input and microphone button feedback only. */
data class ComposerState(
    val message: String = "",
    val microphonePressed: Boolean = false,
) {
    fun withMessage(typed: String): ComposerState = copy(message = typed)

    fun toggleMicrophonePressed(): ComposerState = copy(microphonePressed = !microphonePressed)
}

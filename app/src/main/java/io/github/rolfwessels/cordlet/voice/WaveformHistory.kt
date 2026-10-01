package io.github.rolfwessels.cordlet.voice

private const val WAVEFORM_SAMPLES = 35

/** Bounded, immutable microphone history; only active capture may append samples. */
data class WaveformHistory(val samples: List<Float> = List(WAVEFORM_SAMPLES) { 0f }) {
    fun sample(level: Float, phase: VoicePhase): WaveformHistory {
        if (phase != VoicePhase.RECORDING) return this
        val bounded = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        return WaveformHistory(samples.drop(1) + bounded)
    }
    fun reset() = WaveformHistory()
}

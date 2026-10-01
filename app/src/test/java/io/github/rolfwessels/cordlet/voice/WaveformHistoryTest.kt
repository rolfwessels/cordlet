package io.github.rolfwessels.cordlet.voice

import org.junit.Assert.*
import org.junit.Test

class WaveformHistoryTest {
    @Test fun startsWithThirtyFiveSilentSamples() {
        assertEquals(List(35) { 0f }, WaveformHistory().samples)
    }
    @Test fun scrollsRealSamplesInOrderAndDropsOldest() {
        var history = WaveformHistory()
        repeat(40) { history = history.sample(it / 40f, VoicePhase.RECORDING) }
        assertEquals((5 until 40).map { it / 40f }, history.samples)
    }
    @Test fun silenceNeverInventsActivity() {
        var history = WaveformHistory()
        repeat(50) { history = history.sample(amplitudeLevel(0), VoicePhase.RECORDING) }
        assertEquals(List(35) { 0f }, history.samples)
    }
    @Test fun pausedAndInactivePhasesFreezeHistory() {
        val history = WaveformHistory().sample(0.7f, VoicePhase.RECORDING)
        for (phase in listOf(VoicePhase.PAUSED, VoicePhase.READY, VoicePhase.SAVED, VoicePhase.ERROR)) {
            assertEquals(history, history.sample(1f, phase))
        }
    }
    @Test fun resetClearsPreviousRecording() {
        val history = WaveformHistory().sample(1f, VoicePhase.RECORDING).reset()
        assertEquals(List(35) { 0f }, history.samples)
    }
    @Test fun clampsLevelAndRejectsNonfiniteActivity() {
        var history = WaveformHistory().sample(-1f, VoicePhase.RECORDING).sample(2f, VoicePhase.RECORDING)
        history = history.sample(Float.NaN, VoicePhase.RECORDING)
        assertEquals(listOf(0f, 1f, 0f), history.samples.takeLast(3))
    }
}

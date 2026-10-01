package io.github.rolfwessels.cordlet.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceStateTest {
    @Test fun voiceRouteIsDistinctFromFocusedText() {
        assertEquals(EntryMode.VOICE, entryMode(voice = true, focusText = true))
        assertEquals(EntryMode.TEXT_FOCUSED, entryMode(false, true))
        assertEquals(EntryMode.TEXT, entryMode(false, false))
    }
    @Test fun timerCountsOnlyActiveSegments() {
        val recording = VoiceClock().start(1000)
        val paused = recording.pause(4500)
        assertEquals(3500L, paused.elapsed(9000))
        assertEquals(5500L, paused.start(10000).elapsed(12000))
    }
    @Test fun repeatedPauseAndResumeDoNotResetClock() {
        val recording = VoiceClock().start(10).start(20)
        assertEquals(90L, recording.pause(100).pause(200).elapsed(500))
    }
    @Test fun savedRecordingPreventsAutomaticOverwrite() {
        assertFalse(canAutoStart(VoicePhase.SAVED))
        assertFalse(canAutoStart(VoicePhase.PAUSED))
        assertFalse(canAutoStart(VoicePhase.RECORDING))
        assertTrue(canAutoStart(VoicePhase.READY))
    }
    @Test fun amplitudeClampsRealInput() {
        assertEquals(0f, amplitudeLevel(0), 0f)
        assertEquals(0f, amplitudeLevel(-1), 0f)
        assertEquals(1f, amplitudeLevel(32767), 0f)
        assertEquals(1f, amplitudeLevel(99999), 0f)
        assertTrue(amplitudeLevel(1000) > 0f)
    }
    @Test fun recordingStateOnlyAllowsLegalPauseAndResume() {
        assertEquals(VoicePhase.RECORDING, transition(VoicePhase.READY, VoiceEvent.START))
        assertEquals(VoicePhase.PAUSED, transition(VoicePhase.RECORDING, VoiceEvent.PAUSE))
        assertEquals(VoicePhase.RECORDING, transition(VoicePhase.PAUSED, VoiceEvent.RESUME))
        assertEquals(VoicePhase.READY, transition(VoicePhase.READY, VoiceEvent.RESUME))
    }
    @Test fun finalizedNoteCannotRestartUntilExplicitDiscard() {
        assertEquals(VoicePhase.SAVED, transition(VoicePhase.PAUSED, VoiceEvent.SAVE))
        assertEquals(VoicePhase.SAVED, transition(VoicePhase.SAVED, VoiceEvent.START))
        assertEquals(VoicePhase.READY, transition(VoicePhase.SAVED, VoiceEvent.DISCARD))
    }
    @Test fun unreadableNoteBlocksRecordingUntilExplicitDiscard() {
        val error = VoicePhase.values().firstOrNull { it.name == "ERROR" }
        assertNotNull("Unreadable audio needs a retained error state", error)
        val retained = requireNotNull(error)
        assertFalse(canAutoStart(retained))
        assertEquals(retained, transition(retained, VoiceEvent.START))
        assertEquals(retained, transition(retained, VoiceEvent.RESUME))
        assertEquals(VoicePhase.READY, transition(retained, VoiceEvent.DISCARD))
    }
    @Test fun recordingLimitIsBoundedToFiveMinutes() {
        assertEquals(300000L, MAX_RECORDING_MS)
        assertFalse(limitReached(299999))
        assertTrue(limitReached(300000))
    }
}

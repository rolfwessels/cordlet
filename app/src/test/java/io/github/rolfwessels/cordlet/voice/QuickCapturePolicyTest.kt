package io.github.rolfwessels.cordlet.voice

import org.junit.Assert.*
import org.junit.Test

class QuickCapturePolicyTest {
    @Test fun freshIdsAreSafeDistinctAndNeverHistory() {
        val ids = (1..100).map { newCaptureId() }
        assertEquals(100, ids.toSet().size)
        ids.forEach { assertTrue(it.matches(Regex("[A-Za-z0-9_-]{1,80}"))) }
    }
    @Test fun sendRequiresConfiguredNonemptyPermittedAudio() {
        for (phase in listOf(VoicePhase.RECORDING, VoicePhase.PAUSED, VoicePhase.SAVED)) {
            assertTrue(canSendCapture(phase, VoiceUploadState(), true, true, 1))
            assertFalse(canSendCapture(phase, VoiceUploadState(), false, true, 1))
            assertFalse(canSendCapture(phase, VoiceUploadState(), true, false, 1))
            assertFalse(canSendCapture(phase, VoiceUploadState(), true, true, 0))
            assertFalse(canSendCapture(phase, VoiceUploadState(uploading = true), true, true, 1))
            assertFalse(canSendCapture(phase, VoiceUploadState(accepted = true), true, true, 1))
        }
        assertFalse(canSendCapture(VoicePhase.ERROR, VoiceUploadState(), true, true, 1))
        assertFalse(canSendCapture(VoicePhase.READY, VoiceUploadState(), true, true, 1))
    }
    @Test fun onlyAcceptedForegroundNoteFinishes() {
        assertTrue(shouldFinishCapture(true, true))
        assertFalse(shouldFinishCapture(true, false))
        assertFalse(shouldFinishCapture(false, true))
    }
    @Test fun keepAwakeOnlyDuringForegroundRecordingOrUpload() {
        for (phase in VoicePhase.entries) {
            assertEquals(phase == VoicePhase.RECORDING, keepCaptureAwake(true, phase, false))
            assertTrue(keepCaptureAwake(true, phase, true))
            assertFalse(keepCaptureAwake(false, phase, true))
            assertFalse(keepCaptureAwake(false, phase, false))
        }
    }
}

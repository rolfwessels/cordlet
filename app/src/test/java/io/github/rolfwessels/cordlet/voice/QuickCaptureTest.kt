package io.github.rolfwessels.cordlet.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QuickCaptureTest {
    private fun source(name: String) = File("src/main/java/io/github/rolfwessels/cordlet/$name").readText()
    @Test fun recordingCanSendWithoutMandatoryPause() {
        assertTrue(VoiceUploadState().canSend(VoicePhase.RECORDING))
    }
    @Test fun freshLaunchIsNotLatestHistory() {
        val session = source("voice/VoiceSession.kt")
        assertFalse(session.contains("latest.m4a"))
        assertTrue(session.contains("val sessionId: String"))
        assertTrue(source("voice/RecorderActivity.kt").contains("VoiceSession.fresh(applicationContext)"))
    }
    @Test fun newIntentStartsDistinctCaptureWithoutDiscardingOldNote() {
        val activity = source("voice/RecorderActivity.kt").substringAfter("override fun onNewIntent")
            .substringBefore("override fun onStop")
        assertTrue(activity.contains("openFreshCapture()"))
        assertFalse(activity.contains("discard()"))
    }
    @Test fun widgetGeneratesNonceAtTapTime() {
        val widget = source("widget/CordletWidget.kt")
        assertTrue(widget.contains("actionStartActivity(Intent(context, WidgetVoiceLaunchActivity::class.java))"))
        assertTrue(source("widget/WidgetVoiceLaunchActivity.kt").contains("RecorderActivity.captureIntent(this)"))
    }
    @Test fun onlyVerifiedAcceptanceAutoFinishesCurrentActivity() {
        val activity = source("voice/RecorderActivity.kt")
        assertTrue(activity.contains("session.upload.accepted"))
        assertTrue(activity.contains("shouldFinishCapture"))
        assertTrue(activity.contains("LaunchedEffect(session"))
    }
    @Test fun keepAwakeIsWindowScopedAndClearedOnStop() {
        val activity = source("voice/RecorderActivity.kt")
        assertTrue(activity.contains("FLAG_KEEP_SCREEN_ON"))
        assertTrue(activity.substringAfter("override fun onStop()").contains("clearFlags"))
    }
}

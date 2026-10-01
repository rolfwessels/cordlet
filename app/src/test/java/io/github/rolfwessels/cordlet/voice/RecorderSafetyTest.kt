package io.github.rolfwessels.cordlet.voice

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** Source/manifest guards for Android lifecycle and intent wiring unavailable in JVM tests. */
class RecorderSafetyTest {
    private fun source(name: String) = File("src/main/java/io/github/rolfwessels/cordlet/$name").readText()

    @Test fun stoppedActivityFinalizesBeforeProcessCanBeKilled() {
        val activity = source("voice/RecorderActivity.kt")
        val onStop = activity.substringAfter("override fun onStop()").substringBefore("override fun onDestroy()")
        assertTrue("onStop must finalize, not merely pause", onStop.contains("session.close()"))
    }

    @Test fun unreadableRecoveryNeverDeletesOrOverwritesTheNote() {
        val session = source("voice/VoiceSession.kt")
        val recovery = session.substringAfter("private fun recover()").substringBefore("@Suppress")
        assertFalse("Only explicit Discard may delete audio", recovery.contains("file.delete()"))
        assertTrue("Unreadable notes require a blocked error state", recovery.contains("VoicePhase.ERROR"))
        assertTrue("Start must always return after recovering an existing file",
            session.contains("if (file.exists()) { recover(); return }"))
    }

    @Test fun exportedComposerDoesNotAcceptVoiceLaunchExtras() {
        val main = source("MainActivity.kt")
        assertFalse("Public intents must not trigger recording", main.contains("EXTRA_VOICE"))
        val route = main.substringAfter("private fun route(").substringBefore("companion object")
        assertFalse("Only the composer click may launch recorder", route.contains("RecorderActivity"))
        assertTrue(main.contains("onVoice ="))
        assertTrue(source("widget/CordletWidget.kt").contains("RecorderActivity::class.java"))
    }

    @Test fun recorderRemainsNonExported() {
        val activities = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("activity")
        val ns = "http://schemas.android.com/apk/res/android"
        val recorder = (0 until activities.length).map { activities.item(it) }.first {
            it.attributes.getNamedItemNS(ns, "name").nodeValue == ".voice.RecorderActivity"
        }
        assertEquals("false", recorder.attributes.getNamedItemNS(ns, "exported").nodeValue)
    }
}

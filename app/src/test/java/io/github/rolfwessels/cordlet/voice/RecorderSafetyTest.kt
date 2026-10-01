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

    @Test fun waveformUsesRealSamplesAndResetsOnNewRecordingAndDiscard() {
        val session = source("voice/VoiceSession.kt")
        assertTrue(session.contains("history = history.sample(level, phase)"))
        assertTrue(session.substringAfter("fun start()").substringBefore("fun tick()").contains("history = history.reset()"))
        assertTrue(session.substringAfter("fun discard()").substringBefore("fun background()").contains("history = history.reset()"))
        assertTrue(source("voice/RecorderScreen.kt").contains("session.history.samples"))
    }

    @Test fun recorderControlsAreCompactSingleLineAndFixedHeight() {
        val screen = source("voice/RecorderScreen.kt")
        assertTrue(screen.contains("VoicePhase.READY -> \"Start\""))
        assertFalse(screen.contains("heightIn(min = 58.dp)"))
        assertTrue(screen.contains("height(58.dp)"))
        assertTrue(screen.contains("maxLines = 1"))
    }

    @Test fun recorderReadsImportedNameInsteadOfHardcodedIdentity() {
        val activity = source("voice/RecorderActivity.kt")
        assertTrue(activity.contains("PrivateConfigStore(applicationContext).load()"))
        assertTrue(activity.contains("botName = config?.botName ?: \"Hermes\""))
        assertTrue(source("voice/RecorderScreen.kt").contains("BotIdentity(botName, botIconBase64, prefix = \"Voice note to \""))
        assertFalse(source("voice/RecorderScreen.kt").contains("Voice note to Wren"))
    }

    @Test fun optionalNameIsStrictlyParsedAndIncludedInEncryptedPayload() {
        val store = source("ingress/PrivateConfigStore.kt")
        assertTrue(store.contains("!data.has(\"botName\") || data.get(\"botName\") is String"))
        assertTrue(store.contains("if (data.has(\"botName\")) data.getString(\"botName\") else \"Hermes\""))
        assertTrue(store.contains(".put(\"botName\", config.botName)"))
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

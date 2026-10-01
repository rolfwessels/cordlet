package io.github.rolfwessels.cordlet.voice

import io.github.rolfwessels.cordlet.ingress.IngressConfig
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VoiceSendTest {
    @Test fun uploadLocksControlsAndAcceptanceBlocksRepeatSend() {
        val idle = VoiceUploadState()
        assertTrue(idle.canSend(VoicePhase.SAVED))
        assertTrue(idle.canSend(VoicePhase.PAUSED))
        assertTrue(idle.canSend(VoicePhase.RECORDING))
        val sending = idle.begin()
        assertTrue(sending.controlsLocked)
        assertFalse(sending.canSend(VoicePhase.SAVED))
        assertEquals(sending, sending.begin())
        assertTrue(sending.completed(false).canSend(VoicePhase.SAVED))
        assertFalse(sending.completed(true).canSend(VoicePhase.SAVED))
        assertFalse(sending.completed(true).controlsLocked)
    }
    @Test fun uploadTransportIsStreamedBoundedAndNeverRedirected() {
        val client = java.io.File("src/main/java/io/github/rolfwessels/cordlet/voice/VoiceMessageClient.kt").let { if (it.exists()) it.readText() else "" }
        assertTrue(client.contains("instanceFollowRedirects = false"))
        assertTrue(client.contains("readTimeout = 180_000"))
        assertTrue(client.contains("setFixedLengthStreamingMode(prepared.length)"))
        assertTrue(client.contains("X-Cordlet-Request-ID"))
        assertTrue(client.contains("file.inputStream().use"))
        assertFalse(client.contains("file.readBytes()"))
    }
    @Test fun corruptIdentityNeverSilentlyChangesRetryId() {
        val root = Files.createTempDirectory("cordlet-note").toFile()
        try {
            val file = java.io.File(root, "note.properties").apply { writeText("broken") }
            try { NoteRequestStore(file).identity(); fail("corrupt metadata replaced") } catch (_: IllegalArgumentException) { }
        } finally { root.deleteRecursively() }
    }

    @Test fun rawAudioUsesExistingEndpointAndBearer() {
        val request = prepareVoice(IngressConfig("https://example.org/cordlet/messages", "dummy-token"), "note_1", 42)
        assertEquals("https://example.org/cordlet/messages", request.endpoint)
        assertEquals("Bearer dummy-token", request.authorization)
        assertEquals("audio/mp4", request.contentType)
        assertEquals("note_1", request.requestId)
        assertEquals(42L, request.length)
    }
    @Test fun emptyAndOversizeNotesAreRejected() {
        for (size in listOf(0L, -1L, MAX_VOICE_BYTES + 1)) {
            try { prepareVoice(IngressConfig("https://example.org/cordlet/messages", "dummy-token"), "note_1", size); fail("size $size accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(MAX_VOICE_BYTES, prepareVoice(IngressConfig("https://example.org/cordlet/messages", "dummy-token"), "note_1", MAX_VOICE_BYTES).length)
    }
    @Test fun invalidRequestIdIsRejectedBeforeSending() {
        try { prepareVoice(IngressConfig("https://example.org/cordlet/messages", "dummy-token"), "bad\r\nid", 42); fail("header injection") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun acceptanceRequiresExactReceipt() {
        assertTrue(voiceAccepted(202, "accepted", "note_1", "note_1"))
        for (code in listOf(200, 201, 400, 503)) assertFalse(voiceAccepted(code, "accepted", "note_1", "note_1"))
        assertFalse(voiceAccepted(202, "queued", "note_1", "note_1"))
        assertFalse(voiceAccepted(202, "accepted", "other", "note_1"))
        assertFalse(voiceAccepted(202, null, null, "note_1"))
    }
    @Test fun identitySurvivesReopenAndFailureRetry() {
        val root = Files.createTempDirectory("cordlet-note").toFile()
        try {
            val file = java.io.File(root, "note.properties")
            val first = NoteRequestStore(file).identity()
            assertEquals(first, NoteRequestStore(file).identity())
            assertFalse(NoteRequestStore(file).isAccepted())
            assertEquals(first, NoteRequestStore(file).identity())
        } finally { root.deleteRecursively() }
    }
    @Test fun acceptedStateIsDurableAndDiscardCreatesNewIdentity() {
        val root = Files.createTempDirectory("cordlet-note").toFile()
        try {
            val file = java.io.File(root, "note.properties")
            val store = NoteRequestStore(file)
            val first = store.identity()
            store.markAccepted(first)
            assertTrue(NoteRequestStore(file).isAccepted())
            assertEquals(first, NoteRequestStore(file).identity())
            store.discard()
            assertFalse(NoteRequestStore(file).isAccepted())
            assertNotEquals(first, NoteRequestStore(file).identity())
        } finally { root.deleteRecursively() }
    }
    @Test fun mismatchingAcceptanceCannotMarkNoteAccepted() {
        val root = Files.createTempDirectory("cordlet-note").toFile()
        try {
            val store = NoteRequestStore(java.io.File(root, "note.properties"))
            store.identity()
            try { store.markAccepted("wrong"); fail("wrong ID accepted") } catch (_: IllegalArgumentException) { }
            assertFalse(store.isAccepted())
        } finally { root.deleteRecursively() }
    }
}

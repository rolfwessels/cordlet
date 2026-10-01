package io.github.rolfwessels.cordlet.ingress

import org.junit.Assert.*
import org.junit.Test

class IngressTest {
    private val config = IngressConfig("http://hermes.bot.sels.co.za/cordlet/messages", "device-token")
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected invalid input rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun preparesExactTextAndBearer() {
        val result = prepareMessage(config, "id-1", " hello \n")
        assertEquals(" hello \n", result.text)
        assertEquals("Bearer device-token", result.authorization)
        assertEquals("id-1", result.requestId)
    }
    @Test fun rejectsBlankText() = rejects { prepareMessage(config, "id", " \n") }
    @Test fun rejectsLongText() = rejects { prepareMessage(config, "id", "x".repeat(4001)) }
    @Test fun rejectsOversizedUtf8Body() = rejects { prepareMessage(config, "id", "漢".repeat(3000)) }
    @Test fun rejectsInvalidId() = rejects { prepareMessage(config, "bad id", "hello") }
    @Test fun rejectsEmptyToken() = rejects { prepareMessage(config.copy(token = ""), "id", "hello") }
    @Test fun rejectsHeaderInjection() = rejects { prepareMessage(config.copy(token = "a\r\nb"), "id", "hello") }
    @Test fun rejectsOtherCleartextHost() = rejects { prepareMessage(config.copy(endpoint = "http://example.com/cordlet/messages"), "id", "hello") }
    @Test fun rejectsEndpointCredentials() = rejects { prepareMessage(config.copy(endpoint = "https://user:pass@example.com/cordlet/messages"), "id", "hello") }
    @Test fun acceptsMatching202() { assertTrue(isAccepted(202, "accepted", "id", "id")) }
    @Test fun rejectsOtherSuccessCodes() { for (code in listOf(200, 201, 204)) assertFalse(isAccepted(code, "accepted", "id", "id")) }
    @Test fun rejectsWrongStatus() { assertFalse(isAccepted(202, "queued", "id", "id")) }
    @Test fun rejectsWrongId() { assertFalse(isAccepted(202, "accepted", "other", "id")) }
    @Test fun rejectsMissingReceipt() { assertFalse(isAccepted(202, null, null, "id")) }
    @Test fun retriesKeepId() { val state = RequestIdentity("hello", "id"); assertEquals(state, state.edited("hello")) }
    @Test fun editsReplaceId() { val state = RequestIdentity("hello", "id"); assertNotEquals("id", state.edited("other").requestId) }
    @Test fun acceptedReplacesIdEvenForSameText() { val state = RequestIdentity("hello", "id"); assertNotEquals("id", state.accepted().requestId) }
}

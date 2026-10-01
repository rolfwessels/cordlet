package io.github.rolfwessels.cordlet.ingress

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class BotIconTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    private fun encoded(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected invalid icon rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun oldConfigHasNoIcon() { assertNull(IngressConfig(DEFAULT_ENDPOINT, "test-token").botIconBase64) }
    @Test fun acceptsPngMagic() { assertArrayEquals(png, decodeBotIconBytes(encoded(png))) }
    @Test fun acceptsJpegMagic() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
        assertArrayEquals(jpeg, decodeBotIconBytes(encoded(jpeg)))
    }
    @Test fun acceptsExactlyThirtyTwoKiB() {
        val bytes = png.copyOf(32768)
        assertEquals(32768, decodeBotIconBytes(encoded(bytes)).size)
    }
    @Test fun rejectsMoreThanThirtyTwoKiB() { rejects { decodeBotIconBytes(encoded(png.copyOf(32769))) } }
    @Test fun rejectsOversizedEncodedInputBeforeDecode() { rejects { decodeBotIconBytes("A".repeat(43693)) } }
    @Test fun rejectsInvalidBase64() { rejects { decodeBotIconBytes("%%%") } }
    @Test fun rejectsEmptyAndTruncatedMagic() {
        for (bytes in listOf(byteArrayOf(), png.copyOf(7), byteArrayOf(0xff.toByte(), 0xd8.toByte()))) {
            rejects { decodeBotIconBytes(encoded(bytes)) }
        }
    }
    @Test fun rejectsOtherFormatsAndUrls() {
        for (value in listOf(encoded("GIF89a".toByteArray()), "https://example.com/icon.png", "data:image/png;base64,AAAA")) {
            rejects { decodeBotIconBytes(value) }
        }
    }
    @Test fun permitsPositiveBoundsThroughTwoHundredFiftySix() {
        validateBotIconBounds(1, 1)
        validateBotIconBounds(256, 256)
    }
    @Test fun rejectsInvalidOrExcessiveBounds() {
        for ((width, height) in listOf(0 to 1, 1 to 0, -1 to -1, 257 to 1, 1 to 257, Int.MAX_VALUE to 1)) {
            rejects { validateBotIconBounds(width, height) }
        }
    }
    @Test fun configValidationRejectsNonImageIcon() {
        rejects { validateConfig(IngressConfig(DEFAULT_ENDPOINT, "test-token", botIconBase64 = encoded("not an image".toByteArray()))) }
    }
}

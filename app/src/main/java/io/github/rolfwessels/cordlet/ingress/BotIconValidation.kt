package io.github.rolfwessels.cordlet.ingress

import java.util.Base64

const val MAX_CONFIG_BYTES = 65536
const val MAX_BOT_ICON_BYTES = 32768
private const val MAX_BOT_ICON_BASE64_CHARS = 43692

/** Strict raw base64 only, never a URL/data URI. Bound input before allocation. */
fun decodeBotIconBytes(encoded: String): ByteArray {
    require(encoded.isNotEmpty() && encoded.length <= MAX_BOT_ICON_BASE64_CHARS) { "Invalid bot icon" }
    val bytes = try { Base64.getDecoder().decode(encoded) }
    catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid bot icon") }
    require(bytes.size <= MAX_BOT_ICON_BYTES) { "Invalid bot icon" }
    val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    val png = bytes.size >= pngMagic.size && pngMagic.indices.all { bytes[it] == pngMagic[it] }
    val jpeg = bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
    require(png || jpeg) { "Bot icon must be PNG or JPEG" }
    return bytes
}

fun validateBotIconBounds(width: Int, height: Int) {
    require(width in 1..256 && height in 1..256) { "Bot icon must be at most 256 × 256 pixels" }
}

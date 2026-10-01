package io.github.rolfwessels.cordlet.ingress

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Probe decoded bounds before allocating pixels, even for valid image signatures. */
fun decodeBotIcon(encoded: String): Bitmap {
    val bytes = decodeBotIconBytes(encoded)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outMimeType in listOf("image/png", "image/jpeg")) { "Invalid bot icon" }
    validateBotIconBounds(bounds.outWidth, bounds.outHeight)
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IllegalArgumentException("Invalid bot icon")
    if (bitmap.width !in 1..256 || bitmap.height !in 1..256) {
        bitmap.recycle()
        throw IllegalArgumentException("Invalid bot icon")
    }
    return bitmap
}

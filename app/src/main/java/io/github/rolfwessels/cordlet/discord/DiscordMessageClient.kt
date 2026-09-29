package io.github.rolfwessels.cordlet.discord

import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface SendOutcome {
    data object Sent : SendOutcome
    data class Failed(val message: String) : SendOutcome
}

/** Discord bot REST transport. The debug APK itself contains the test credential. */
class DiscordMessageClient {
    suspend fun send(destination: DiscordDestination, text: String): SendOutcome {
        val prepared = when (val result = prepareMessage(destination, text)) {
            is PreparedMessage -> result
            PreparationError.EmptyMessage -> return SendOutcome.Failed("Type a message first")
            PreparationError.TooLong -> return SendOutcome.Failed("Discord messages are limited to 2000 characters")
            PreparationError.NotConfigured -> return SendOutcome.Failed("Bot token and channel ID are not configured")
            PreparationError.InvalidChannel -> return SendOutcome.Failed("Invalid channel ID")
        }
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://discord.com/api/v10/channels/${prepared.destination.channelId}/messages")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", "Bot ${prepared.destination.botToken}")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("User-Agent", "Cordlet/0.1 (Android ${Build.VERSION.SDK_INT})")
                connection.doOutput = true
                // Do not turn typed text into Discord mentions or mass notifications.
                val body = JSONObject()
                    .put("content", prepared.content)
                    .put("allowed_mentions", JSONObject().put("parse", JSONArray()))
                    .toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                if (code in 200..299) SendOutcome.Sent else SendOutcome.Failed(when (code) {
                    401 -> "Bot token rejected (401)"
                    403 -> "Bot cannot send to that channel (403)"
                    404 -> "Channel not found or inaccessible (404)"
                    429 -> "Discord rate limit reached; wait and retry (429)"
                    else -> "Discord rejected the message (HTTP $code)"
                })
            } catch (_: Exception) {
                SendOutcome.Failed("Network error; check your connection and retry")
            } finally {
                connection?.disconnect()
            }
        }
    }
}

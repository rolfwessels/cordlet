package io.github.rolfwessels.cordlet.voice

import io.github.rolfwessels.cordlet.ingress.IngressConfig
import io.github.rolfwessels.cordlet.ingress.SendOutcome
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class VoiceMessageClient {
    suspend fun send(config: IngressConfig?, requestId: String, file: File): SendOutcome {
        if (config == null) return SendOutcome.Failed("Import private config first; recording kept")
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val prepared = prepareVoice(config, requestId, file.length())
                connection = URL(prepared.endpoint).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 180_000
                connection.setRequestProperty("Authorization", prepared.authorization)
                connection.setRequestProperty("Content-Type", prepared.contentType)
                connection.setRequestProperty("X-Cordlet-Request-ID", prepared.requestId)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(prepared.length)
                file.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
                val code = connection.responseCode
                if (code != 202) {
                    SendOutcome.Failed(when (code) {
                        401 -> "Device token rejected; import config again"
                        409 -> "Request ID conflict; recording kept. Do not retry with a new ID"
                        413 -> "Recording too large; kept locally"
                        422 -> "Transcription failed or audio invalid; recording kept"
                        503 -> "Ingress or transcription unavailable; retry later"
                        in 300..399 -> "Redirect refused; check configured endpoint"
                        else -> "Voice rejected (HTTP $code); recording kept"
                    })
                } else {
                    val response = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 8192) { "Oversize receipt" }
                            output.write(buffer, 0, count)
                        }
                        JSONObject(String(output.toByteArray(), Charsets.UTF_8))
                    }
                    if (voiceAccepted(code, response.optString("status"), response.optString("request_id"), requestId)) SendOutcome.Accepted
                    else SendOutcome.Failed("Invalid acceptance receipt; recording kept for same-ID retry")
                }
            } catch (_: Exception) {
                SendOutcome.Failed("Network or receipt error; recording kept. Check VPN and retry")
            } finally { connection?.disconnect() }
        }
    }
}

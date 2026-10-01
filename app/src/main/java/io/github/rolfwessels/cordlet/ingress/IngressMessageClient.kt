package io.github.rolfwessels.cordlet.ingress

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface SendOutcome {
    data object Accepted : SendOutcome
    data class Failed(val message: String) : SendOutcome
}

class IngressMessageClient {
    suspend fun send(config: IngressConfig?, request: RequestIdentity): SendOutcome {
        if (config == null) return SendOutcome.Failed("Import private config first")
        val prepared = try { prepareMessage(config, request.requestId, request.text) }
        catch (error: IllegalArgumentException) { return SendOutcome.Failed(error.message ?: "Invalid message or config") }
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(prepared.endpoint).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", prepared.authorization)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                val bytes = prepared.body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                if (code != 202) {
                    SendOutcome.Failed(when (code) {
                        401 -> "Device token rejected (401); import config again"
                        403 -> "Owner not authorized (403)"
                        409 -> "Request ID conflict (409); edit text before retrying"
                        413 -> "Message too large (413)"
                        503 -> "Ingress unavailable (503); retry later"
                        in 300..399 -> "Redirect refused; check the configured endpoint"
                        else -> "Ingress rejected the message (HTTP $code)"
                    })
                } else {
                    val response = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 8192)
                            output.write(buffer, 0, count)
                        }
                        JSONObject(String(output.toByteArray(), Charsets.UTF_8))
                    }
                    if (isAccepted(code, response.optString("status"), response.optString("request_id"), prepared.requestId)) {
                        SendOutcome.Accepted
                    } else SendOutcome.Failed("Invalid acceptance receipt; text kept for retry")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { SendOutcome.Failed("Network or receipt error; check private VPN and retry") }
            finally { connection?.disconnect() }
        }
    }
}

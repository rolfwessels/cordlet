package io.github.rolfwessels.cordlet.ingress

import java.net.URI
import java.util.UUID

const val DEFAULT_ENDPOINT = "http://hermes.bot.sels.co.za/cordlet/messages"
data class IngressConfig(val endpoint: String, val token: String)
data class PreparedMessage(val endpoint: String, val authorization: String, val requestId: String, val text: String, val body: String)
data class RequestIdentity(val text: String = "", val requestId: String = UUID.randomUUID().toString()) {
    fun edited(value: String): RequestIdentity = if (value == text) this else RequestIdentity(value)
    fun accepted(): RequestIdentity = RequestIdentity(text)
}
fun validateConfig(config: IngressConfig) {
    val uri = try { URI(config.endpoint) } catch (_: Exception) { throw IllegalArgumentException("Invalid ingress endpoint") }
    require(uri.host != null && uri.userInfo == null && uri.fragment == null && uri.query == null && uri.path == "/cordlet/messages") { "Invalid ingress endpoint" }
    require(uri.scheme == "https" || config.endpoint == DEFAULT_ENDPOINT) { "HTTP is permitted only for the private Hermes hostname" }
    require(config.token.isNotBlank() && config.token.length <= 4096 && config.token.all { it.code in 33..126 }) { "Invalid device token" }
}
private fun quote(value: String): String = buildString {
    append('"')
    value.forEach { c ->
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}
fun prepareMessage(config: IngressConfig, requestId: String, text: String): PreparedMessage {
    validateConfig(config)
    require(requestId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid request ID" }
    require(text.isNotBlank()) { "Type a message first" }
    require(text.length <= 4000) { "Messages are limited to 4000 characters" }
    val body = "{\"request_id\":${quote(requestId)},\"text\":${quote(text)}}"
    require(body.toByteArray(Charsets.UTF_8).size <= 8192) { "Message exceeds the 8192-byte request limit" }
    return PreparedMessage(config.endpoint, "Bearer ${config.token}", requestId, text, body)
}
fun isAccepted(code: Int, status: String?, responseId: String?, requestId: String): Boolean =
    code == 202 && status == "accepted" && responseId == requestId

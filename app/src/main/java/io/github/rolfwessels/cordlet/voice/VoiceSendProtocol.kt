package io.github.rolfwessels.cordlet.voice

import io.github.rolfwessels.cordlet.ingress.IngressConfig
import io.github.rolfwessels.cordlet.ingress.validateConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

const val MAX_VOICE_BYTES = 10L * 1024 * 1024
data class PreparedVoice(val endpoint: String, val authorization: String, val requestId: String, val length: Long, val contentType: String = "audio/mp4")
fun prepareVoice(config: IngressConfig, requestId: String, length: Long): PreparedVoice {
    validateConfig(config)
    require(requestId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid request ID" }
    require(length in 1..MAX_VOICE_BYTES) { "Recording must be nonempty and at most 10 MiB" }
    return PreparedVoice(config.endpoint, "Bearer ${config.token}", requestId, length)
}
fun voiceAccepted(code: Int, status: String?, responseId: String?, requestId: String): Boolean =
    code == 202 && status == "accepted" && responseId == requestId

/** Atomic, synced sidecar. Invalid metadata blocks sending instead of changing the retry ID. */
class NoteRequestStore(private val file: File) {
    private fun load(): Properties = Properties().apply {
        if (file.exists()) {
            file.inputStream().use { load(it) }
            require(getProperty("request_id", "").matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Recording identity unreadable; file kept" }
            require(getProperty("accepted") in listOf("true", "false")) { "Recording receipt unreadable; file kept" }
        }
    }
    private fun save(properties: Properties) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { properties.store(it, null); it.fd.sync() }
        check(temporary.renameTo(file)) { "Could not persist recording identity" }
    }
    fun identity(): String {
        val data = load()
        if (!file.exists()) {
            data.setProperty("request_id", UUID.randomUUID().toString())
            data.setProperty("accepted", "false")
            save(data)
        }
        return data.getProperty("request_id")
    }
    fun isAccepted(): Boolean = load().getProperty("accepted") == "true"
    fun markAccepted(requestId: String) {
        require(identity() == requestId) { "Mismatched recording identity" }
        val data = load()
        data.setProperty("accepted", "true")
        save(data)
    }
    fun discard() {
        check(!file.exists() || file.delete()) { "Could not discard recording identity" }
        val temporary = File(file.parentFile, file.name + ".tmp")
        check(!temporary.exists() || temporary.delete()) { "Could not discard temporary identity" }
    }
}

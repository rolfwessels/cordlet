package io.github.rolfwessels.cordlet.ingress

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Only encrypted config is persisted. The AES key never leaves AndroidKeyStore. */
class PrivateConfigStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("private_ingress", Context.MODE_PRIVATE)
    private val alias = "cordlet_ingress_aes_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private fun parse(json: String): IngressConfig {
        val data = JSONObject(json)
        require(data.get("endpoint") is String && data.get("token") is String)
        require(!data.has("botName") || data.get("botName") is String)
        require(!data.has("botIconBase64") || data.isNull("botIconBase64") || data.get("botIconBase64") is String)
        val botName = if (data.has("botName")) data.getString("botName") else "Hermes"
        val icon = if (data.has("botIconBase64") && !data.isNull("botIconBase64")) data.getString("botIconBase64") else null
        return IngressConfig(data.getString("endpoint"), data.getString("token"), botName, icon).also { config ->
            validateConfig(config)
            // Full decode rejects corrupt images; bounds are checked before pixel allocation.
            icon?.let { decodeBotIcon(it).recycle() }
        }
    }
    fun load(): IngressConfig? = try {
        prefs.getString("ciphertext", null)?.let { encrypted ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(prefs.getString("iv", null) ?: error("Missing IV"), Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            parse(String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8))
        }
    } catch (_: Exception) { null }

    fun import(uri: Uri): IngressConfig {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_CONFIG_BYTES) { "Config is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot open config")
        val config = parse(String(bytes, Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = JSONObject().put("endpoint", config.endpoint).put("token", config.token)
            .put("botName", config.botName).put("botIconBase64", config.botIconBase64 ?: JSONObject.NULL).toString()
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
        check(load() == config) { "Config verification failed" }
        return config
    }
}

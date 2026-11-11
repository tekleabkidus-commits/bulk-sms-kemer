package bulksms.test.mark.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens are AES-GCM encrypted using a non-exportable Android Keystore key. */
class GatewayPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gateway_account_v1", Context.MODE_PRIVATE)
    var serverUrl: String
        get() = prefs.getString("url", "") ?: ""
        set(value) { prefs.edit().putString("url", value.trim().trimEnd('/')).apply() }
    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(value) { prefs.edit().putString("email", value).apply() }
    var deviceId: Long
        get() = prefs.getLong("device_id", 0L)
        set(value) { prefs.edit().putLong("device_id", value).apply() }
    var simId: Int
        get() = prefs.getInt("sim_id", -1)
        set(value) { prefs.edit().putInt("sim_id", value).apply() }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    var userToken: String
        get() = decrypt(prefs.getString("user_token", null))
        set(value) { prefs.edit().putString("user_token", encrypt(value)).apply() }
    var deviceToken: String
        get() = decrypt(prefs.getString("device_token", null))
        set(value) { prefs.edit().putString("device_token", encrypt(value)).apply() }

    fun clearCredentials() {
        enabled = false
        prefs.edit().remove("user_token").remove("device_token").remove("device_id")
            .remove("email").remove("sim_id").apply()
    }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val old = ks.getKey("kemer_sms_v1", null)
        if (old is SecretKey) return old
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder("kemer_sms_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }
    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE,key())
        val bytes = c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes,Base64.NO_WRAP)
    }
    private fun decrypt(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val bytes=Base64.decode(raw,Base64.NO_WRAP)
            val c=Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            String(c.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}


package top.fumiama.copymangaweb.tool

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalCredentialStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("local_login", Context.MODE_PRIVATE)
    private val alias = "copymanga_local_login_key"

    fun save(account: String, password: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal("$account\n$password".toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putBoolean("enabled", true)
            .apply()
    }

    fun load(): Pair<String, String>? {
        if (!preferences.getBoolean("enabled", false)) return null
        return try {
            val iv = Base64.decode(preferences.getString("iv", null), Base64.NO_WRAP)
            val data = Base64.decode(preferences.getString("data", null), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val parts = cipher.doFinal(data).toString(Charsets.UTF_8).split('\n', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }
}

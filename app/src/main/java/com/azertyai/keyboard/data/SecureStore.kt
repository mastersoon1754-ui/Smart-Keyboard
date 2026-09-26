package com.azertyai.keyboard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys stay in app-private storage, encrypted with a key that never leaves
 * the Android Keystore. They are not backed up with the rest of the app.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()

    fun geminiKey(): String = read(KEY_GEMINI)

    fun mistralKey(): String = read(KEY_MISTRAL)

    fun setGeminiKey(value: String) = write(KEY_GEMINI, value)

    fun setMistralKey(value: String) = write(KEY_MISTRAL, value)

    fun hasGeminiKey(): Boolean = prefs.contains(KEY_GEMINI)

    fun hasMistralKey(): Boolean = prefs.contains(KEY_MISTRAL)

    fun clear() {
        synchronized(lock) {
            prefs.edit().clear().commit()
        }
    }

    private fun read(name: String): String {
        synchronized(lock) {
            val encoded = prefs.getString(name, null) ?: return ""
            return try {
                decrypt(encoded)
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun write(name: String, value: String) {
        synchronized(lock) {
            val editor = prefs.edit()
            if (value.isBlank()) editor.remove(name) else editor.putString(name, encrypt(value.trim()))
            editor.commit()
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_SIZE) return ""
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val FILE = "azerty_secure"
        const val KEY_GEMINI = "gemini"
        const val KEY_MISTRAL = "mistral"
        const val ALIAS = "azerty_ai_master"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

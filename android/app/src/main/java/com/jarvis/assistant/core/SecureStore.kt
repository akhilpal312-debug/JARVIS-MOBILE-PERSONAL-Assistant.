package com.jarvis.assistant.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption for saved memories, with the key held by the Android
 * Keystore and never exported. Values are stored as "v1:base64(iv|ciphertext)".
 * If the keystore is unavailable the caller decides whether to store plaintext.
 */
object SecureStore {

    private const val ALIAS = "jarvis_memory_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            val combined = ByteArray(iv.size + encrypted.size)
            iv.copyInto(combined, 0)
            encrypted.copyInto(combined, iv.size)
            "v1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (error: Exception) {
            "plain:" + plain
        }
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith("v1:")) return stored.removePrefix("plain:")
        return try {
            val combined = Base64.decode(stored.removePrefix("v1:"), Base64.NO_WRAP)
            if (combined.size <= IV_BYTES) return ""
            val iv = combined.copyOfRange(0, IV_BYTES)
            val cipherText = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (error: Exception) {
            ""
        }
    }

    fun isEncrypted(stored: String): Boolean = stored.startsWith("v1:")
}

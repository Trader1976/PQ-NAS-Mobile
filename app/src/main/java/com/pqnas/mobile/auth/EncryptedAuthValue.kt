package com.pqnas.mobile.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal object EncryptedAuthValue {
    private const val KEY_ALIAS = "dna_nexus_auth_store_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFIX = "enc:v1:"

    fun isEncrypted(value: String): Boolean =
        value.startsWith(PREFIX)

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        if (isEncrypted(value)) return value

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        return "$PREFIX$ivB64:$ciphertextB64"
    }

    fun decryptOrLegacy(value: String): String {
        if (value.isBlank()) return ""
        if (!isEncrypted(value)) return value

        return runCatching {
            val payload = value.removePrefix(PREFIX)
            val splitAt = payload.indexOf(':')
            if (splitAt <= 0 || splitAt >= payload.lastIndex) return ""

            val iv = Base64.decode(payload.substring(0, splitAt), Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.substring(splitAt + 1), Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
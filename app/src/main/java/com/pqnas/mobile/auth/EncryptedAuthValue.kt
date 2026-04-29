package com.pqnas.mobile.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal object EncryptedAuthValue {
    private const val LEGACY_KEY_ALIAS_V1 = "dna_nexus_auth_store_v1"
    private const val CURRENT_KEY_ALIAS_V2 = "dna_nexus_auth_store_v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFIX_V1 = "enc:v1:"
    private const val PREFIX_V2 = "enc:v2:"
    private const val CURRENT_AES_KEY_BITS = 256

    fun isEncrypted(value: String): Boolean =
        value.startsWith(PREFIX_V1) || value.startsWith(PREFIX_V2)

    fun needsUpgrade(value: String): Boolean =
        value.startsWith(PREFIX_V1)

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        if (isEncrypted(value)) return value

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey(
                alias = CURRENT_KEY_ALIAS_V2,
                keySizeBits = CURRENT_AES_KEY_BITS
            )
        )

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        return "$PREFIX_V2$ivB64:$ciphertextB64"
    }

    fun decryptOrLegacy(value: String): String {
        if (value.isBlank()) return ""

        val alias = when {
            value.startsWith(PREFIX_V2) -> CURRENT_KEY_ALIAS_V2
            value.startsWith(PREFIX_V1) -> LEGACY_KEY_ALIAS_V1
            else -> return value
        }

        val prefix = when {
            value.startsWith(PREFIX_V2) -> PREFIX_V2
            else -> PREFIX_V1
        }

        val key = getExistingKey(alias) ?: return ""

        return runCatching {
            val payload = value.removePrefix(prefix)
            val splitAt = payload.indexOf(':')
            if (splitAt <= 0 || splitAt >= payload.lastIndex) return ""

            val iv = Base64.decode(payload.substring(0, splitAt), Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.substring(splitAt + 1), Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getExistingKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun getOrCreateKey(
        alias: String,
        keySizeBits: Int
    ): SecretKey {
        getExistingKey(alias)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(keySizeBits)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

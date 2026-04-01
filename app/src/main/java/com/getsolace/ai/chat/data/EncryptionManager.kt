package com.getsolace.ai.chat.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption manager.
 * Mirrors iOS EncryptionManager.swift (CryptoKit AES.GCM).
 * Uses Android Keystore for key management.
 */
object EncryptionManager {

    private const val KEY_ALIAS    = "RunMateVaultKey"
    private const val KEYSTORE     = "AndroidKeyStore"
    private const val ALGO         = "AES/GCM/NoPadding"
    private const val TAG_LEN_BITS = 128
    private const val IV_LEN       = 12

    // ── Key management ────────────────────────────────────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .also { it.init(spec) }
            .generateKey()
    }

    // ── Encrypt ───────────────────────────────────────────────────────────────

    /**
     * Returns IV + ciphertext concatenated.
     */
    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv         = cipher.iv          // 12 bytes
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext              // prepend IV
    }

    // ── Decrypt ───────────────────────────────────────────────────────────────

    /**
     * Input is IV + ciphertext (as produced by encrypt).
     */
    fun decrypt(data: ByteArray): ByteArray {
        val iv         = data.copyOfRange(0, IV_LEN)
        val ciphertext = data.copyOfRange(IV_LEN, data.size)
        val cipher     = Cipher.getInstance(ALGO)
        val spec       = GCMParameterSpec(TAG_LEN_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(ciphertext)
    }
}

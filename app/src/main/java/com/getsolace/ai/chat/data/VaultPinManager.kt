package com.getsolace.ai.chat.data

import android.content.Context
import java.security.MessageDigest

object VaultPinManager {
    private const val PREFS = "vault_pin_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_DISCLAIMER_SHOWN = "disclaimer_shown"

    fun hasPin(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_PIN_HASH)

    fun savePin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PIN_HASH, sha256(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PIN_HASH, null) ?: return false
        return stored == sha256(pin)
    }

    fun hasShownDisclaimer(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISCLAIMER_SHOWN, false)

    fun markDisclaimerShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DISCLAIMER_SHOWN, true).apply()
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

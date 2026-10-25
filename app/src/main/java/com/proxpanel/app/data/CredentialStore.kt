package com.proxpanel.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores Proxmox passwords in EncryptedSharedPreferences when "save login" is enabled.
 * Passwords are keyed by server profile id; a legacy single-slot key remains for migration.
 */
class CredentialStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "proxpanel_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun savePassword(password: String, profileId: String? = null) {
        prefs.edit().apply {
            putString(KEY_PASSWORD, password)
            if (!profileId.isNullOrBlank()) {
                putString(keyFor(profileId), password)
            }
        }.apply()
    }

    fun loadPassword(profileId: String? = null): String {
        if (!profileId.isNullOrBlank()) {
            val keyed = prefs.getString(keyFor(profileId), null)
            if (!keyed.isNullOrEmpty()) return keyed
        }
        return prefs.getString(KEY_PASSWORD, "").orEmpty()
    }

    fun clearPassword(profileId: String? = null) {
        prefs.edit().apply {
            if (!profileId.isNullOrBlank()) {
                remove(keyFor(profileId))
            } else {
                remove(KEY_PASSWORD)
            }
        }.apply()
    }

    fun clearAllPasswords() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it == KEY_PASSWORD || it.startsWith(KEY_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    companion object {
        private const val KEY_PASSWORD = "password"
        private const val KEY_PREFIX = "password_"
        private fun keyFor(profileId: String) = KEY_PREFIX + profileId
    }
}

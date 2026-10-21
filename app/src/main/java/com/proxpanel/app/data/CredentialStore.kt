package com.proxpanel.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Proxmox password in EncryptedSharedPreferences when "save login" is enabled.
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

    fun savePassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun loadPassword(): String = prefs.getString(KEY_PASSWORD, "").orEmpty()

    fun clearPassword() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val KEY_PASSWORD = "password"
    }
}

package com.proxpanel.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "proxpanel")

data class AuthSession(
    val ticket: String?,
    val csrf: String?,
    val username: String?,
)

class SessionStore(private val context: Context) {
    private object Keys {
        val proxmoxHost = stringPreferencesKey("proxmox_host")
        val username = stringPreferencesKey("username")
        val realm = stringPreferencesKey("realm")
        val verifyTls = booleanPreferencesKey("verify_tls")
        val rememberCredentials = booleanPreferencesKey("remember_credentials")
        val ticket = stringPreferencesKey("pve_ticket")
        val csrf = stringPreferencesKey("pve_csrf")
        val authUsername = stringPreferencesKey("auth_username")
        // Legacy keys from ProxPanel-proxy era — cleared on save.
        val panelBaseUrl = stringPreferencesKey("panel_base_url")
        val sessionCookie = stringPreferencesKey("session_cookie")
    }

    val loginPrefs: Flow<LoginPrefs> = context.dataStore.data.map { prefs ->
        LoginPrefs(
            proxmoxHost = prefs[Keys.proxmoxHost].orEmpty(),
            username = prefs[Keys.username].orEmpty(),
            realm = prefs[Keys.realm] ?: "pam",
            verifyTls = prefs[Keys.verifyTls] ?: true,
            rememberCredentials = prefs[Keys.rememberCredentials] ?: true,
        )
    }

    val authSession: Flow<AuthSession> = context.dataStore.data.map { prefs ->
        AuthSession(
            ticket = prefs[Keys.ticket],
            csrf = prefs[Keys.csrf],
            username = prefs[Keys.authUsername],
        )
    }

    suspend fun saveLoginPrefs(prefs: LoginPrefs) {
        context.dataStore.edit {
            it[Keys.proxmoxHost] = prefs.proxmoxHost.trim().trimEnd('/')
            it[Keys.username] = prefs.username.trim()
            it[Keys.realm] = prefs.realm
            it[Keys.verifyTls] = prefs.verifyTls
            it[Keys.rememberCredentials] = prefs.rememberCredentials
            it.remove(Keys.panelBaseUrl)
            it.remove(Keys.sessionCookie)
        }
    }

    suspend fun saveAuth(ticket: String?, csrf: String?, username: String?) {
        context.dataStore.edit {
            if (ticket.isNullOrBlank()) it.remove(Keys.ticket) else it[Keys.ticket] = ticket
            if (csrf.isNullOrBlank()) it.remove(Keys.csrf) else it[Keys.csrf] = csrf
            if (username.isNullOrBlank()) it.remove(Keys.authUsername) else it[Keys.authUsername] = username
            it.remove(Keys.sessionCookie)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.ticket)
            it.remove(Keys.csrf)
            it.remove(Keys.authUsername)
            it.remove(Keys.sessionCookie)
        }
    }
}

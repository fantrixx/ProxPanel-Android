package com.proxpanel.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.R
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.ApiException
import com.proxpanel.app.data.CredentialStore
import com.proxpanel.app.data.LoginPrefs
import com.proxpanel.app.data.ServerProfile
import com.proxpanel.app.data.ServerProfileStore
import com.proxpanel.app.data.SessionStore
import com.proxpanel.app.util.L10n
import com.proxpanel.app.util.normalizeBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val proxmoxHost: String = "",
    val username: String = "",
    val password: String = "",
    val realm: String = "pam",
    val verifyTls: Boolean = false,
    val rememberCredentials: Boolean = true,
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
    val pending: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    val checkingSession: Boolean = true,
)

class LoginViewModel(
    private val api: ApiClient,
    private val sessionStore: SessionStore,
    private val credentialStore: CredentialStore,
    private val serverProfileStore: ServerProfileStore,
    private val l10n: L10n,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = sessionStore.loginPrefs.first()
            val auth = sessionStore.authSession.first()
            val profiles = serverProfileStore.list()
            val activeId = serverProfileStore.activeId()
            val savedPassword = if (prefs.rememberCredentials) {
                credentialStore.loadPassword(activeId)
            } else {
                ""
            }
            _state.update {
                it.copy(
                    proxmoxHost = prefs.proxmoxHost,
                    username = prefs.username,
                    password = savedPassword,
                    realm = prefs.realm,
                    verifyTls = prefs.verifyTls,
                    rememberCredentials = prefs.rememberCredentials,
                    profiles = profiles,
                    activeProfileId = activeId,
                )
            }
            if (prefs.proxmoxHost.isNotBlank() && !auth.ticket.isNullOrBlank()) {
                api.configure(
                    baseUrl = prefs.proxmoxHost,
                    verifyTls = prefs.verifyTls,
                    ticket = auth.ticket,
                    csrf = auth.csrf,
                    username = auth.username ?: prefs.username,
                )
                runCatching { api.me() }
                    .onSuccess { _state.update { s -> s.copy(signedIn = true, checkingSession = false) } }
                    .onFailure {
                        sessionStore.clearSession()
                        api.clearSessionLocal()
                        _state.update { s -> s.copy(checkingSession = false) }
                    }
            } else {
                _state.update { it.copy(checkingSession = false) }
            }
        }
        viewModelScope.launch {
            serverProfileStore.profiles.collect { list ->
                _state.update { it.copy(profiles = list) }
            }
        }
    }

    fun update(transform: (LoginUiState) -> LoginUiState) {
        _state.update(transform)
    }

    fun selectProfile(profile: ServerProfile) {
        viewModelScope.launch {
            serverProfileStore.setActive(profile.id)
            val password = credentialStore.loadPassword(profile.id)
            _state.update {
                it.copy(
                    activeProfileId = profile.id,
                    proxmoxHost = profile.host,
                    username = profile.username,
                    realm = profile.realm,
                    verifyTls = profile.verifyTls,
                    password = password,
                    rememberCredentials = password.isNotBlank() || it.rememberCredentials,
                    error = null,
                )
            }
        }
    }

    fun signIn() {
        val s = _state.value
        val host = normalizeBaseUrl(s.proxmoxHost)
        if (host.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = l10n.get(R.string.login_error_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(pending = true, error = null) }
            try {
                api.login(
                    proxmoxHost = host,
                    username = s.username.trim(),
                    password = s.password,
                    realm = s.realm,
                    verifyTls = s.verifyTls,
                )
                sessionStore.saveLoginPrefs(
                    LoginPrefs(
                        proxmoxHost = host,
                        username = s.username.trim(),
                        realm = s.realm,
                        verifyTls = s.verifyTls,
                        rememberCredentials = s.rememberCredentials,
                    ),
                )
                val profile = serverProfileStore.upsertFromLogin(
                    host = host,
                    username = s.username.trim(),
                    realm = s.realm,
                    verifyTls = s.verifyTls,
                )
                if (s.rememberCredentials) {
                    credentialStore.savePassword(s.password, profile.id)
                } else {
                    credentialStore.clearPassword(profile.id)
                }
                _state.update {
                    it.copy(
                        pending = false,
                        signedIn = true,
                        activeProfileId = profile.id,
                        password = if (s.rememberCredentials) s.password else "",
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(pending = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        pending = false,
                        error = e.message ?: l10n.get(R.string.login_error_sign_in_failed),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            api: ApiClient,
            store: SessionStore,
            credentials: CredentialStore,
            profiles: ServerProfileStore,
            l10n: L10n,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LoginViewModel(api, store, credentials, profiles, l10n) as T
        }
    }
}

package com.proxpanel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.AccentChoice
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.AppLanguage
import com.proxpanel.app.data.CredentialStore
import com.proxpanel.app.data.LoginPrefs
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.data.RefreshInterval
import com.proxpanel.app.data.ServerProfile
import com.proxpanel.app.data.ServerProfileStore
import com.proxpanel.app.data.SessionStore
import android.content.Context
import com.proxpanel.app.util.LocaleHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accent: AccentChoice = AccentChoice.Orange,
    val language: AppLanguage = AppLanguage.System,
    val refreshInterval: RefreshInterval = RefreshInterval.Default,
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
)

class SettingsViewModel(
    private val prefsStore: PrefsStore,
    private val serverProfileStore: ServerProfileStore,
    private val sessionStore: SessionStore,
    private val credentialStore: CredentialStore,
    private val api: ApiClient,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        prefsStore.accentChoice,
        prefsStore.appLanguage,
        prefsStore.refreshInterval,
        serverProfileStore.profiles,
        serverProfileStore.activeProfileId,
    ) { accent, language, interval, profiles, activeId ->
        SettingsUiState(
            accent = accent,
            language = language,
            refreshInterval = interval,
            profiles = profiles,
            activeProfileId = activeId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setAccent(choice: AccentChoice) {
        viewModelScope.launch { prefsStore.setAccentChoice(choice) }
    }

    fun setLanguage(context: Context, language: AppLanguage, onApplied: () -> Unit) {
        viewModelScope.launch {
            prefsStore.setAppLanguage(language)
            LocaleHelper.set(context.applicationContext, language)
            onApplied()
        }
    }

    fun setRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch { prefsStore.setRefreshInterval(interval) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            credentialStore.clearPassword(id)
            serverProfileStore.delete(id)
        }
    }

    /** Switch active profile and sign out so login can reconnect. */
    fun switchProfile(profile: ServerProfile, onSignedOut: () -> Unit) {
        viewModelScope.launch {
            serverProfileStore.setActive(profile.id)
            sessionStore.saveLoginPrefs(
                LoginPrefs(
                    proxmoxHost = profile.host,
                    username = profile.username,
                    realm = profile.realm,
                    verifyTls = profile.verifyTls,
                    rememberCredentials = credentialStore.loadPassword(profile.id).isNotBlank(),
                ),
            )
            runCatching { api.logout() }
            sessionStore.clearSession()
            api.clearSessionLocal()
            onSignedOut()
        }
    }

    companion object {
        fun factory(
            prefsStore: PrefsStore,
            serverProfileStore: ServerProfileStore,
            sessionStore: SessionStore,
            credentialStore: CredentialStore,
            api: ApiClient,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(prefsStore, serverProfileStore, sessionStore, credentialStore, api) as T
        }
    }
}

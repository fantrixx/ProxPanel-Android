package com.proxpanel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.AccentChoice
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.data.RefreshInterval
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accent: AccentChoice = AccentChoice.Orange,
    val refreshInterval: RefreshInterval = RefreshInterval.Default,
)

class SettingsViewModel(
    private val prefsStore: PrefsStore,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        prefsStore.accentChoice,
        prefsStore.refreshInterval,
    ) { accent, interval ->
        SettingsUiState(accent = accent, refreshInterval = interval)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setAccent(choice: AccentChoice) {
        viewModelScope.launch { prefsStore.setAccentChoice(choice) }
    }

    fun setRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch { prefsStore.setRefreshInterval(interval) }
    }

    companion object {
        fun factory(prefsStore: PrefsStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(prefsStore) as T
        }
    }
}

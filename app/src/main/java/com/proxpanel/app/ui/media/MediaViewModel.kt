package com.proxpanel.app.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.R
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.util.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MediaTab { Iso, Template }

data class MediaUiState(
    val tab: MediaTab = MediaTab.Iso,
    val items: List<MediaItem> = emptyList(),
    val viewMode: NodesViewMode = NodesViewMode.Cards,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class MediaViewModel(
    private val api: ApiClient,
    private val l10n: L10n,
    private val prefsStore: PrefsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefsStore.nodesViewMode.collect { mode ->
                _state.update { it.copy(viewMode = mode) }
            }
        }
        refresh(initial = true)
    }

    fun setViewMode(mode: NodesViewMode) {
        viewModelScope.launch { prefsStore.setNodesViewMode(mode) }
    }

    fun setTab(tab: MediaTab) {
        _state.update { it.copy(tab = tab) }
        refresh()
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            val content = if (_state.value.tab == MediaTab.Iso) "iso" else "vztmpl"
            _state.update {
                it.copy(
                    loading = initial && it.items.isEmpty(),
                    refreshing = !initial,
                    error = null,
                )
            }
            try {
                val items = api.mediaItems(content)
                _state.update {
                    it.copy(items = items, loading = false, refreshing = false, error = null)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = e.message ?: l10n.get(R.string.error_failed))
                }
            }
        }
    }

    companion object {
        fun factory(api: ApiClient, l10n: L10n, prefsStore: PrefsStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MediaViewModel(api, l10n, prefsStore) as T
        }
    }
}

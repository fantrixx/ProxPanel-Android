package com.proxpanel.app.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.R
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.ClusterResource
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.data.toNodeOrNull
import com.proxpanel.app.data.toStorageOrNull
import com.proxpanel.app.util.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
    val nodes: List<ClusterResource> = emptyList(),
    val storages: List<ClusterResource> = emptyList(),
    val viewMode: NodesViewMode = NodesViewMode.Cards,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class StorageViewModel(
    private val api: ApiClient,
    private val l10n: L10n,
    private val prefsStore: PrefsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

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

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = initial && it.storages.isEmpty() && it.nodes.isEmpty(),
                    refreshing = !initial,
                    error = null,
                )
            }
            try {
                val res = api.resources().resources
                _state.update {
                    it.copy(
                        nodes = res.mapNotNull { r -> r.toNodeOrNull() },
                        storages = res.mapNotNull { r -> r.toStorageOrNull() },
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
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
                StorageViewModel(api, l10n, prefsStore) as T
        }
    }
}

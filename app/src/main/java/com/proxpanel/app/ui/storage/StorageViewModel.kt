package com.proxpanel.app.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.ClusterResource
import com.proxpanel.app.data.toNodeOrNull
import com.proxpanel.app.data.toStorageOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
    val nodes: List<ClusterResource> = emptyList(),
    val storages: List<ClusterResource> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class StorageViewModel(private val api: ApiClient) : ViewModel() {
    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    init {
        refresh(initial = true)
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
                    it.copy(loading = false, refreshing = false, error = e.message ?: "Failed")
                }
            }
        }
    }

    companion object {
        fun factory(api: ApiClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StorageViewModel(api) as T
        }
    }
}

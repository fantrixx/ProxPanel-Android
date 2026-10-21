package com.proxpanel.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.PveTask
import com.proxpanel.app.data.toGuestOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val tasks: List<PveTask> = emptyList(),
    val guests: Map<String, TaskGuestRef> = emptyMap(),
    val query: String = "",
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val expandedUpid: String? = null,
    val logLines: List<String> = emptyList(),
    val logLoading: Boolean = false,
)

class TasksViewModel(private val api: ApiClient) : ViewModel() {
    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun filtered(): List<PveTask> {
        val q = _state.value.query.trim().lowercase()
        val guests = _state.value.guests
        return _state.value.tasks.filter { task ->
            if (q.isEmpty()) return@filter true
            val copy = describeTask(task, guests)
            copy.title.lowercase().contains(q) ||
                copy.kindLabel.lowercase().contains(q) ||
                copy.node.lowercase().contains(q) ||
                copy.targetTag.orEmpty().lowercase().contains(q) ||
                copy.userLabel.orEmpty().lowercase().contains(q) ||
                task.type.orEmpty().lowercase().contains(q) ||
                task.status.orEmpty().lowercase().contains(q) ||
                task.upid.lowercase().contains(q)
        }
    }

    fun toggleExpand(task: PveTask) {
        val current = _state.value.expandedUpid
        if (current == task.upid) {
            _state.update { it.copy(expandedUpid = null, logLines = emptyList()) }
            return
        }
        _state.update { it.copy(expandedUpid = task.upid, logLines = emptyList(), logLoading = true) }
        viewModelScope.launch {
            val node = task.node ?: parseUpid(task.upid)["node"]
            if (node.isNullOrBlank()) {
                _state.update { it.copy(logLoading = false, logLines = emptyList()) }
                return@launch
            }
            val lines = runCatching { api.taskLog(node, task.upid) }.getOrDefault(emptyList())
            _state.update {
                if (it.expandedUpid == task.upid) {
                    it.copy(logLoading = false, logLines = lines)
                } else {
                    it
                }
            }
        }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = initial && it.tasks.isEmpty(),
                    refreshing = !initial,
                    error = null,
                )
            }
            try {
                val guests = runCatching {
                    api.resources().resources.mapNotNull { it.toGuestOrNull() }
                        .associate { g ->
                            g.vmid.toString() to TaskGuestRef(
                                name = g.name,
                                kind = if (g.type == "lxc") "CT" else "VM",
                            )
                        }
                }.getOrDefault(_state.value.guests)
                val tasks = api.tasks()
                _state.update {
                    it.copy(
                        tasks = tasks,
                        guests = guests,
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TasksViewModel(api) as T
        }
    }
}

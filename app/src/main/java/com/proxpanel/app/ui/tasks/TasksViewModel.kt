package com.proxpanel.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.R
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.data.PveTask
import com.proxpanel.app.data.toGuestOrNull
import com.proxpanel.app.util.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val tasks: List<PveTask> = emptyList(),
    val guests: Map<String, TaskGuestRef> = emptyMap(),
    val query: String = "",
    val statusFilter: TaskStatusFilter = TaskStatusFilter.All,
    val kindFilter: TaskKindFilter = TaskKindFilter.All,
    val viewMode: NodesViewMode = NodesViewMode.Cards,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val expandedUpid: String? = null,
    val logLines: List<String> = emptyList(),
    val logLoading: Boolean = false,
)

class TasksViewModel(
    private val api: ApiClient,
    private val l10n: L10n,
    private val prefsStore: PrefsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

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

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun setStatusFilter(filter: TaskStatusFilter) = _state.update {
        it.copy(statusFilter = if (it.statusFilter == filter) TaskStatusFilter.All else filter)
    }

    fun setKindFilter(filter: TaskKindFilter) = _state.update {
        it.copy(kindFilter = if (it.kindFilter == filter) TaskKindFilter.All else filter)
    }

    fun clearFilters() = _state.update {
        it.copy(
            query = "",
            statusFilter = TaskStatusFilter.All,
            kindFilter = TaskKindFilter.All,
        )
    }

    fun filtered(): List<PveTask> {
        val q = _state.value.query.trim().lowercase()
        val guests = _state.value.guests
        val statusFilter = _state.value.statusFilter
        val kindFilter = _state.value.kindFilter
        return _state.value.tasks.filter { task ->
            val copy = describeTask(task, guests)
            val statusOk = when (statusFilter) {
                TaskStatusFilter.All -> true
                TaskStatusFilter.Running -> copy.outcome == TaskOutcome.Running
                TaskStatusFilter.Ok -> copy.outcome == TaskOutcome.Ok
                TaskStatusFilter.Fail -> copy.outcome == TaskOutcome.Fail
            }
            if (!statusOk) return@filter false
            if (!kindFilter.matches(copy.kind)) return@filter false
            if (q.isEmpty()) return@filter true
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
                    it.copy(loading = false, refreshing = false, error = e.message ?: l10n.get(R.string.error_failed))
                }
            }
        }
    }

    companion object {
        fun factory(api: ApiClient, l10n: L10n, prefsStore: PrefsStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TasksViewModel(api, l10n, prefsStore) as T
        }
    }
}

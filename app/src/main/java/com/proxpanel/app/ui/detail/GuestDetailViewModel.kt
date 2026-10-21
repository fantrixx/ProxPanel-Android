package com.proxpanel.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.ApiException
import com.proxpanel.app.data.BackupStorage
import com.proxpanel.app.data.GuestDetail
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.data.Snapshot
import com.proxpanel.app.jobs.JobTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PowerConfirm(
    val action: String,
    val title: String,
    val body: String,
    val confirm: String,
    val danger: Boolean = false,
)

enum class DetailTab { Overview, Snapshots, Backups, Resources }

data class DetailUiState(
    val guest: GuestItem,
    val detail: GuestDetail? = null,
    val tab: DetailTab = DetailTab.Overview,
    val snapshots: List<Snapshot> = emptyList(),
    val backups: List<MediaItem> = emptyList(),
    val backupStorages: List<BackupStorage> = emptyList(),
    val coresInput: String = "",
    val memoryInput: String = "",
    val swapInput: String = "",
    val newSnapName: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val backupRunning: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val confirm: PowerConfirm? = null,
)

class GuestDetailViewModel(
    private val api: ApiClient,
    private val jobTracker: JobTracker,
    guest: GuestItem,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState(guest = guest))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    private var localBackupStarted = false
    private var backupSeenOnApi = false

    init {
        refresh()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(2_500)
                refresh(silent = true)
                syncBackupRunning()
            }
        }
    }

    fun setTab(tab: DetailTab) {
        _state.update { it.copy(tab = tab) }
        when (tab) {
            DetailTab.Snapshots -> loadSnapshots()
            DetailTab.Backups -> loadBackups()
            else -> Unit
        }
    }

    fun setSnapName(value: String) = _state.update { it.copy(newSnapName = value) }
    fun setCores(value: String) = _state.update { it.copy(coresInput = value) }
    fun setMemory(value: String) = _state.update { it.copy(memoryInput = value) }
    fun setSwap(value: String) = _state.update { it.copy(swapInput = value) }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = it.detail == null, error = null) }
            val g = _state.value.guest
            try {
                val detail = api.guest(g.node, g.type, g.vmid)
                val updated = g.copy(
                    name = detail.status.name?.ifBlank { null } ?: g.name,
                    status = detail.status.status,
                    cpu = detail.status.cpu,
                    mem = detail.status.mem,
                    maxmem = detail.status.maxmem,
                    disk = detail.status.disk,
                    maxdisk = detail.status.maxdisk,
                    uptime = detail.status.uptime,
                    ips = detail.ips.ifEmpty { g.ips },
                    lock = detail.status.lock,
                )
                val cores = detail.config["cores"]?.toString()?.trim('"')
                    ?: detail.status.cpus?.toInt()?.toString().orEmpty()
                val memory = detail.config["memory"]?.toString()?.trim('"')
                    ?: detail.status.maxmem?.let { (it / (1024 * 1024)).toString() }.orEmpty()
                val swap = detail.config["swap"]?.toString()?.trim('"').orEmpty()
                val current = _state.value
                val stores = if (current.backupStorages.isEmpty() || !silent) {
                    runCatching { api.backupStorages() }.getOrDefault(current.backupStorages)
                } else {
                    current.backupStorages
                }
                _state.update {
                    it.copy(
                        guest = updated,
                        detail = detail,
                        backupStorages = stores,
                        loading = false,
                        error = null,
                        coresInput = it.coresInput.ifBlank { cores },
                        memoryInput = it.memoryInput.ifBlank { memory },
                        swapInput = it.swapInput.ifBlank { swap },
                    )
                }
            } catch (e: Exception) {
                if (!silent) {
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Failed to load guest")
                    }
                }
            }
        }
    }

    fun loadSnapshots() {
        viewModelScope.launch {
            val g = _state.value.guest
            runCatching { api.snapshots(g.node, g.type, g.vmid) }
                .onSuccess { list -> _state.update { it.copy(snapshots = list) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun loadBackups() {
        viewModelScope.launch {
            val g = _state.value.guest
            try {
                val backups = api.guestBackups(g.node, g.type, g.vmid)
                val stores = api.backupStorages()
                _state.update { it.copy(backups = backups, backupStorages = stores) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun createSnapshot() {
        val name = _state.value.newSnapName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Snapshot-Name erforderlich") }
            return
        }
        viewModelScope.launch {
            val g = _state.value.guest
            _state.update { it.copy(busy = true, error = null) }
            try {
                api.createSnapshot(g.node, g.type, g.vmid, name)
                _state.update {
                    it.copy(busy = false, message = "Snapshot erstellt", newSnapName = "")
                }
                loadSnapshots()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun deleteSnapshot(name: String) {
        viewModelScope.launch {
            val g = _state.value.guest
            _state.update { it.copy(busy = true, error = null) }
            try {
                api.deleteSnapshot(g.node, g.type, g.vmid, name)
                _state.update { it.copy(busy = false, message = "Snapshot gelöscht") }
                loadSnapshots()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun rollbackSnapshot(name: String) {
        viewModelScope.launch {
            val g = _state.value.guest
            _state.update { it.copy(busy = true, error = null) }
            try {
                api.rollbackSnapshot(g.node, g.type, g.vmid, name)
                _state.update { it.copy(busy = false, message = "Rollback gestartet") }
                delay(800)
                refresh(silent = true)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun saveResources() {
        val s = _state.value
        val cores = s.coresInput.toIntOrNull()
        val memory = s.memoryInput.toIntOrNull()
        val swap = s.swapInput.toIntOrNull()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                api.updateGuestConfig(
                    node = s.guest.node,
                    type = s.guest.type,
                    vmid = s.guest.vmid,
                    cores = cores,
                    memoryMb = memory,
                    swapMb = swap,
                )
                _state.update { it.copy(busy = false, message = "Ressourcen gespeichert") }
                refresh(silent = true)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun startBackup(
        storage: String,
        mode: String = "snapshot",
        compress: String = "zstd",
    ) {
        viewModelScope.launch {
            val g = _state.value.guest
            localBackupStarted = true
            backupSeenOnApi = false
            _state.update { it.copy(busy = true, backupRunning = true, error = null) }
            try {
                jobTracker.startGuestBackup(
                    node = g.node,
                    type = g.type,
                    vmid = g.vmid,
                    name = g.name,
                    storage = storage,
                    mode = mode,
                    compress = compress,
                )
                _state.update {
                    it.copy(busy = false, backupRunning = true, message = "Backup gestartet")
                }
            } catch (e: Exception) {
                localBackupStarted = false
                _state.update {
                    it.copy(busy = false, backupRunning = false, error = e.message)
                }
            }
        }
    }

    fun restoreBackup(archive: String, targetVmid: Int, storage: String?, force: Boolean) {
        viewModelScope.launch {
            val g = _state.value.guest
            _state.update { it.copy(busy = true, error = null) }
            try {
                jobTracker.trackRestore(
                    node = g.node,
                    type = g.type,
                    vmid = targetVmid,
                    name = g.name,
                    archive = archive,
                    storage = storage,
                    force = force,
                )
                _state.update {
                    it.copy(busy = false, message = "Restore gestartet → VMID $targetVmid")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun deleteBackup(item: MediaItem) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                api.deleteBackup(item.node, item.storage, item.volid)
                _state.update { it.copy(busy = false, message = "Backup gelöscht") }
                loadBackups()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    private suspend fun syncBackupRunning() {
        val g = _state.value.guest
        val apiRunning = runCatching {
            api.tasks(limit = 80).any { task ->
                if (!task.type.equals("vzdump", ignoreCase = true)) return@any false
                if (task.endtime != null && task.endtime != 0L) return@any false
                val vmid = task.id?.toIntOrNull()
                    ?: task.upid.split(':').getOrNull(6)?.toIntOrNull()
                val nodeOk = task.node == null || task.node == g.node
                nodeOk && vmid == g.vmid
            }
        }.getOrDefault(false)

        if (apiRunning) backupSeenOnApi = true
        if (backupSeenOnApi && !apiRunning) {
            localBackupStarted = false
            backupSeenOnApi = false
            val wasRunning = _state.value.backupRunning
            _state.update {
                it.copy(
                    backupRunning = false,
                    message = if (wasRunning) "Backup fertig" else it.message,
                )
            }
            if (wasRunning) loadBackups()
            return
        }
        val running = apiRunning || localBackupStarted
        if (running != _state.value.backupRunning) {
            _state.update { it.copy(backupRunning = running) }
        }
    }

    fun consoleUrl(): String {
        val g = _state.value.guest
        return api.consoleWebUrl(g.node, g.type, g.vmid)
    }

    fun requestAction(action: String) {
        val name = _state.value.guest.name
        val confirm = when (action) {
            "shutdown" -> PowerConfirm(
                action = action,
                title = "Herunterfahren?",
                body = "„$name“ wird ordentlich heruntergefahren. Ungespeicherte Daten können verloren gehen.",
                confirm = "Herunterfahren",
            )
            "stop" -> PowerConfirm(
                action = action,
                title = "Force Stop?",
                body = "„$name“ wird hart gestoppt (wie Strom ziehen). Datenverlust möglich.",
                confirm = "Stoppen",
                danger = true,
            )
            "reboot" -> PowerConfirm(
                action = action,
                title = "Neustart?",
                body = "„$name“ wird neu gestartet. Laufende Dienste werden kurz unterbrochen.",
                confirm = "Neustart",
            )
            else -> null
        }
        if (confirm != null) {
            _state.update { it.copy(confirm = confirm) }
        } else {
            runAction(action)
        }
    }

    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    fun confirmAction() {
        val action = _state.value.confirm?.action ?: return
        _state.update { it.copy(confirm = null) }
        runAction(action)
    }

    private fun runAction(action: String) {
        viewModelScope.launch {
            val g = _state.value.guest
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                api.action(g.node, g.type, g.vmid, action)
                _state.update {
                    it.copy(busy = false, message = "${action.replaceFirstChar { c -> c.uppercase() }} requested")
                }
                delay(800)
                refresh(silent = true)
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Action failed") }
            }
        }
    }

    companion object {
        fun factory(api: ApiClient, jobTracker: JobTracker, guest: GuestItem) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GuestDetailViewModel(api, jobTracker, guest) as T
            }
    }
}

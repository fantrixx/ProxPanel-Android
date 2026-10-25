package com.proxpanel.app.ui.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.BackupOverviewGuest
import com.proxpanel.app.data.BackupStorage
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.R
import com.proxpanel.app.jobs.JobTracker
import com.proxpanel.app.util.L10n
import com.proxpanel.app.util.formatBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class BackupsUiState(
    val guests: List<BackupOverviewGuest> = emptyList(),
    val storages: List<BackupStorage> = emptyList(),
    val viewMode: NodesViewMode = NodesViewMode.Cards,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Keys like "node/vmid" for guests with an active vzdump. */
    val runningKeys: Set<String> = emptySet(),
    val busyKey: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class BackupsViewModel(
    private val api: ApiClient,
    private val jobTracker: JobTracker,
    private val l10n: L10n,
    private val prefsStore: PrefsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupsUiState())
    val state: StateFlow<BackupsUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    /** Optimistically marked until API reports the task, then until it finishes. */
    private val localRunning = mutableSetOf<String>()
    private val seenOnApi = mutableSetOf<String>()
    @Volatile private var listScrolling = false
    private val scrollLock = Any()
    private var heldState: BackupsUiState? = null

    fun setListScrolling(scrolling: Boolean) {
        synchronized(scrollLock) {
            if (listScrolling == scrolling) return
            listScrolling = scrolling
            if (!scrolling) {
                val pending = heldState
                heldState = null
                if (pending != null) _state.value = pending
            }
        }
    }

    private fun publishBackground(transform: (BackupsUiState) -> BackupsUiState) {
        synchronized(scrollLock) {
            val base = heldState ?: _state.value
            val next = transform(base)
            if (listScrolling) heldState = next else {
                heldState = null
                _state.value = next
            }
        }
    }

    init {
        viewModelScope.launch {
            prefsStore.nodesViewMode.collect { mode ->
                _state.update { it.copy(viewMode = mode) }
            }
        }
        refresh(initial = true)
        startPolling()
    }

    fun setViewMode(mode: NodesViewMode) {
        viewModelScope.launch { prefsStore.setNodesViewMode(mode) }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = initial && it.guests.isEmpty(),
                    refreshing = !initial,
                    error = null,
                )
            }
            try {
                val storages = api.backupStorages()
                val guests = api.backupsOverview()
                val running = syncRunningKeys()
                _state.update {
                    it.copy(
                        storages = storages,
                        guests = guests,
                        runningKeys = running,
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

    fun startBackup(
        guest: BackupOverviewGuest,
        storage: String,
        mode: String = "snapshot",
        compress: String = "zstd",
    ) {
        val key = guestKey(guest.node, guest.vmid)
        viewModelScope.launch {
            localRunning += key
            _state.update {
                it.copy(
                    busyKey = key,
                    runningKeys = it.runningKeys + key,
                    error = null,
                    message = null,
                )
            }
            try {
                prefsStore.setLastBackupStorage(storage)
                jobTracker.startGuestBackup(
                    node = guest.node,
                    type = guest.type,
                    vmid = guest.vmid,
                    name = guest.name,
                    storage = storage,
                    mode = mode,
                    compress = compress,
                )
                _state.update {
                    it.copy(
                        busyKey = null,
                        message = l10n.get(R.string.toast_backup_started_for, guest.name),
                    )
                }
            } catch (e: Exception) {
                localRunning -= key
                seenOnApi -= key
                _state.update {
                    it.copy(
                        busyKey = null,
                        runningKeys = it.runningKeys - key,
                        error = e.message ?: l10n.get(R.string.error_backup_failed),
                    )
                }
            }
        }
    }

    suspend fun loadGuestBackupHistory(guest: BackupOverviewGuest) =
        api.guestBackups(guest.node, guest.type, guest.vmid)

    fun restoreBackup(
        guest: BackupOverviewGuest,
        archive: String,
        targetVmid: Int,
        storage: String?,
        force: Boolean,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busyKey = guestKey(guest.node, guest.vmid), error = null) }
            try {
                jobTracker.trackRestore(
                    node = guest.node,
                    type = guest.type,
                    vmid = targetVmid,
                    name = guest.name,
                    archive = archive,
                    storage = storage,
                    force = force,
                )
                _state.update {
                    it.copy(busyKey = null, message = l10n.get(R.string.toast_restore_started_vmid, targetVmid))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busyKey = null, error = e.message ?: l10n.get(R.string.error_restore_failed))
                }
            }
        }
    }

    fun deleteBackup(guest: BackupOverviewGuest, volid: String, storage: String) {
        viewModelScope.launch {
            _state.update { it.copy(busyKey = guestKey(guest.node, guest.vmid), error = null) }
            try {
                api.deleteBackup(guest.node, storage, volid)
                _state.update { it.copy(busyKey = null, message = l10n.get(R.string.toast_backup_deleted)) }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busyKey = null, error = e.message ?: l10n.get(R.string.error_delete_failed))
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(2_500)
                val prev = _state.value.runningKeys
                val running = runCatching { syncRunningKeys() }.getOrNull() ?: continue
                if (running == prev) continue
                val finished = prev - running
                publishBackground {
                    it.copy(
                        runningKeys = running,
                        message = when {
                            finished.isNotEmpty() && it.message == null -> l10n.get(R.string.toast_backup_finished)
                            else -> it.message
                        },
                    )
                }
                if (finished.isNotEmpty()) {
                    runCatching {
                        val guests = api.backupsOverview()
                        publishBackground { it.copy(guests = guests) }
                    }
                }
            }
        }
    }

    private suspend fun syncRunningKeys(): Set<String> {
        val apiKeys = detectRunningBackupKeys()
        seenOnApi += apiKeys
        val done = localRunning.filter { it in seenOnApi && it !in apiKeys }.toSet()
        localRunning -= done
        seenOnApi -= done
        return apiKeys + localRunning
    }

    private suspend fun detectRunningBackupKeys(): Set<String> {
        return api.tasks(limit = 80)
            .asSequence()
            .filter { it.type.equals("vzdump", ignoreCase = true) }
            .filter { it.endtime == null || it.endtime == 0L }
            .mapNotNull { task ->
                val node = task.node ?: return@mapNotNull null
                val vmid = task.id?.toIntOrNull()
                    ?: parseVmidFromUpid(task.upid)
                    ?: return@mapNotNull null
                guestKey(node, vmid)
            }
            .toSet()
    }

    companion object {
        fun guestKey(node: String, vmid: Int) = "$node/$vmid"

        private fun parseVmidFromUpid(upid: String): Int? {
            // UPID:node:pid:pstart:starttime:vzdump:vmid:user@realm:
            val parts = upid.split(':')
            return parts.getOrNull(6)?.toIntOrNull()
        }

        fun factory(api: ApiClient, jobTracker: JobTracker, l10n: L10n, prefsStore: PrefsStore) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BackupsViewModel(api, jobTracker, l10n, prefsStore) as T
            }
    }
}

fun formatBackupTime(ctime: Long?): String {
    if (ctime == null || ctime <= 0) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(ctime * 1000))
}

fun formatBackupSize(size: Long?): String = formatBytes(size)

fun backupFileName(volid: String): String =
    if (volid.contains(':')) volid.substringAfter(':') else volid.substringAfterLast('/')

fun backupFormatLabel(item: com.proxpanel.app.data.MediaItem?): String {
    if (item == null) return "—"
    item.format?.takeIf { it.isNotBlank() }?.let { return it }
    val file = backupFileName(item.volid)
    val match = Regex("""\.(vma\.(zst|lzo|gz)|tar\.(zst|lzo|gz|bz2)|tgz|zst|gz|lzo)$""", RegexOption.IGNORE_CASE)
        .find(file)
    return match?.value?.removePrefix(".") ?: "—"
}

fun backupLocation(item: com.proxpanel.app.data.MediaItem?): String {
    if (item == null) return "—"
    return buildString {
        append(item.storage)
        if (item.node.isNotBlank()) append(" @ ${item.node}")
    }
}

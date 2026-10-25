package com.proxpanel.app.ui.guests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.AccentChoice
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.ApiException
import com.proxpanel.app.data.AuthUser
import com.proxpanel.app.data.GuestIconRecord
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.data.GuestRates
import com.proxpanel.app.data.guestIconKey
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.data.toGuestOrNull
import com.proxpanel.app.data.toNodeOrNull
import com.proxpanel.app.data.toStorageOrNull
import com.proxpanel.app.R
import com.proxpanel.app.jobs.JobTracker
import com.proxpanel.app.jobs.ToastKind
import com.proxpanel.app.util.L10n
import com.proxpanel.app.ui.components.ClusterOverview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class GuestKindFilter {
    All,
    Ct,
    Vm,
}

enum class GuestStatusFilter {
    All,
    Running,
    Stopped,
}

data class GuestPowerConfirm(
    val guest: GuestItem,
    val action: String,
    val title: String,
    val body: String,
    val confirm: String,
    val danger: Boolean = false,
)

data class PendingPower(
    val action: String,
    val startedAt: Long = System.currentTimeMillis(),
)

private data class CounterSample(
    val t: Long,
    val netin: Long,
    val netout: Long,
    val diskread: Long,
    val diskwrite: Long,
)

data class GuestsUiState(
    val user: AuthUser? = null,
    val guests: List<GuestItem> = emptyList(),
    val backupStorages: List<String> = emptyList(),
    val rates: Map<String, GuestRates> = emptyMap(),
    val cpuHistory: Map<String, List<Float>> = emptyMap(),
    val pinnedKeys: Set<String> = emptySet(),
    val guestIcons: Map<String, GuestIconRecord> = emptyMap(),
    val lastBackupStorage: String? = null,
    val accent: AccentChoice = AccentChoice.Orange,
    val viewMode: NodesViewMode = NodesViewMode.Cards,
    val cluster: ClusterOverview = ClusterOverview(),
    val kindFilter: GuestKindFilter = GuestKindFilter.All,
    val statusFilter: GuestStatusFilter = GuestStatusFilter.All,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val refreshPulse: Boolean = false,
    val busyKey: String? = null,
    val pendingPower: Map<String, PendingPower> = emptyMap(),
    val confirm: GuestPowerConfirm? = null,
    val error: String? = null,
    val actionMessage: String? = null,
)

class GuestsViewModel(
    private val api: ApiClient,
    private val jobTracker: JobTracker,
    private val prefsStore: PrefsStore,
    private val l10n: L10n,
) : ViewModel() {
    private val _state = MutableStateFlow(GuestsUiState())
    val state: StateFlow<GuestsUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    private var pendingJob: Job? = null
    private val refreshMutex = Mutex()
    private val prevCounters = mutableMapOf<String, CounterSample>()
    private val cpuBuffers = mutableMapOf<String, ArrayDeque<Float>>()
    private var silentPollCount = 0
    /** While true, background state is buffered and flushed once scrolling stops. */
    @Volatile private var listScrolling = false
    private val scrollLock = Any()
    private var heldState: GuestsUiState? = null

    /** Background/live updates — held while scrolling so Lazy lists don't recompose mid-fling. */
    private fun publishBackground(transform: (GuestsUiState) -> GuestsUiState) {
        synchronized(scrollLock) {
            val base = heldState ?: _state.value
            val next = transform(base)
            if (listScrolling) {
                heldState = next
            } else {
                heldState = null
                _state.value = next
            }
        }
    }

    /** Immediate UI (dialogs, taps, filters) — always reaches Compose. */
    private fun publishNow(transform: (GuestsUiState) -> GuestsUiState) {
        synchronized(scrollLock) {
            val base = heldState ?: _state.value
            val next = transform(base)
            heldState = if (listScrolling) next else null
            _state.value = next
        }
    }

    private fun currentState(): GuestsUiState = synchronized(scrollLock) {
        heldState ?: _state.value
    }

    init {
        viewModelScope.launch {
            prefsStore.pinnedGuestKeys.collect { keys ->
                publishBackground { state ->
                    val sorted = state.guests.sortedWith(
                        compareByDescending<GuestItem> { it.key in keys }
                            .thenByDescending { it.isRunning }
                            .thenBy { it.name.lowercase() },
                    )
                    state.copy(pinnedKeys = keys, guests = sorted)
                }
            }
        }
        viewModelScope.launch {
            prefsStore.lastBackupStorage.collect { storage ->
                publishBackground { it.copy(lastBackupStorage = storage) }
            }
        }
        viewModelScope.launch {
            prefsStore.accentChoice.collect { accent ->
                publishBackground { it.copy(accent = accent) }
            }
        }
        viewModelScope.launch {
            prefsStore.nodesViewMode.collect { mode ->
                // View mode change must apply immediately even mid-scroll.
                publishNow { it.copy(viewMode = mode) }
            }
        }
        viewModelScope.launch {
            prefsStore.guestIcons.collect { icons ->
                publishBackground { it.copy(guestIcons = icons) }
            }
        }
        refresh(initial = true)
        // Restart auto-refresh whenever the user changes the interval in Settings.
        viewModelScope.launch {
            prefsStore.refreshInterval
                .map { it.millis }
                .distinctUntilChanged()
                .collectLatest { millis ->
                    pollJob?.cancel()
                    if (millis <= 0L) return@collectLatest
                    pollJob = viewModelScope.launch {
                        while (isActive) {
                            delay(millis)
                            refresh(silent = true)
                        }
                    }
                }
        }
        pendingJob = viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                if (_state.value.pendingPower.isNotEmpty()) {
                    reconcilePendingPower()
                }
            }
        }
    }

    fun togglePinned(guest: GuestItem) {
        viewModelScope.launch { prefsStore.togglePinned(guest.key) }
    }

    fun setGuestIcon(guest: GuestItem, record: GuestIconRecord?) {
        viewModelScope.launch {
            prefsStore.setGuestIcon(guestIconKey(guest.node, guest.type, guest.vmid), record)
        }
    }

    fun guestIconRecord(guest: GuestItem): GuestIconRecord? =
        currentState().guestIcons[guestIconKey(guest.node, guest.type, guest.vmid)]

    fun setAccent(choice: AccentChoice) {
        viewModelScope.launch { prefsStore.setAccentChoice(choice) }
    }

    fun setViewMode(mode: NodesViewMode) {
        viewModelScope.launch { prefsStore.setNodesViewMode(mode) }
    }

    fun clearFilters() {
        publishNow {
            it.copy(kindFilter = GuestKindFilter.All, statusFilter = GuestStatusFilter.All)
        }
    }

    fun setKindFilter(filter: GuestKindFilter) {
        publishNow {
            it.copy(kindFilter = if (it.kindFilter == filter) GuestKindFilter.All else filter)
        }
    }

    fun setStatusFilter(filter: GuestStatusFilter) {
        publishNow {
            it.copy(statusFilter = if (it.statusFilter == filter) GuestStatusFilter.All else filter)
        }
    }

    fun clearMessage() = publishNow { it.copy(actionMessage = null, error = null) }

    fun refreshIfEmpty() {
        if (currentState().guests.isEmpty()) refresh(initial = true)
    }

    /**
     * Freeze Compose-facing state while the Entries list is scrolling.
     * Live poll / prefs are held and flushed once after scroll settles —
     * features stay intact, mid-fling recomposition stops.
     */
    fun setListScrolling(scrolling: Boolean) {
        synchronized(scrollLock) {
            if (listScrolling == scrolling) return
            listScrolling = scrolling
            if (!scrolling) {
                val pending = heldState
                heldState = null
                if (pending != null) {
                    _state.value = pending
                }
            }
        }
    }

    fun refresh(initial: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            refreshMutex.withLock {
                if (!silent) {
                    publishNow {
                        it.copy(
                            loading = initial || it.guests.isEmpty(),
                            refreshing = !initial && it.guests.isNotEmpty(),
                            refreshPulse = !initial && it.guests.isNotEmpty(),
                            error = null,
                        )
                    }
                }
                var lastError: String? = null
                repeat(2) { attempt ->
                    try {
                        val me = if (silent) null else runCatching { api.me() }.getOrNull()
                        val res = api.resources()
                        val prev = currentState()
                        val pinned = prev.pinnedKeys
                        val guests = res.resources.mapNotNull { it.toGuestOrNull() }
                            .sortedWith(
                                compareByDescending<GuestItem> { it.key in pinned }
                                    .thenByDescending { it.isRunning }
                                    .thenBy { it.name.lowercase() },
                            )
                        // Storages rarely change — skip on silent polls (extra API + recomposition).
                        val storages = if (silent && prev.backupStorages.isNotEmpty()) {
                            prev.backupStorages
                        } else {
                            runCatching {
                                api.backupStorages().map { it.storage }.distinct()
                            }.getOrDefault(prev.backupStorages)
                        }
                        val (rates, history) = computeRatesAndSparks(guests)
                        val cluster = computeClusterOverview(res.resources, guests)
                        val guestsOut = if (guests == prev.guests) prev.guests else guests
                        val ratesOut = if (rates == prev.rates) prev.rates else rates
                        val historyOut = if (history == prev.cpuHistory) prev.cpuHistory else history
                        val clusterOut = if (cluster == prev.cluster) prev.cluster else cluster
                        val next = prev.copy(
                            user = me ?: prev.user,
                            guests = guestsOut,
                            backupStorages = storages,
                            rates = ratesOut,
                            cpuHistory = historyOut,
                            cluster = clusterOut,
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                        if (silent) {
                            silentPollCount++
                            val statusChanged = guests.size != prev.guests.size ||
                                guests.zip(prev.guests).any { (a, b) ->
                                    a.key != b.key ||
                                        a.status != b.status ||
                                        a.lock != b.lock ||
                                        a.qmpstatus != b.qmpstatus ||
                                        a.name != b.name
                                }
                            val pushMetrics = silentPollCount % 2 == 0
                            if (!statusChanged && !pushMetrics) {
                                return@withLock
                            }
                            publishBackground { latest ->
                                next.copy(
                                    kindFilter = latest.kindFilter,
                                    statusFilter = latest.statusFilter,
                                    viewMode = latest.viewMode,
                                    confirm = latest.confirm,
                                    busyKey = latest.busyKey,
                                    pendingPower = latest.pendingPower,
                                    actionMessage = latest.actionMessage,
                                    refreshing = latest.refreshing,
                                    loading = latest.loading,
                                    refreshPulse = latest.refreshPulse,
                                )
                            }
                            return@withLock
                        }
                        publishNow { next }
                        viewModelScope.launch {
                            delay(700)
                            publishBackground { it.copy(refreshPulse = false) }
                        }
                        reconcilePendingPower()
                        return@withLock
                    } catch (e: ApiException) {
                        lastError = e.message
                    } catch (e: Exception) {
                        lastError = e.message ?: l10n.get(R.string.error_load_failed)
                    }
                    if (attempt == 0) delay(400)
                }
                publishBackground {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        refreshPulse = false,
                        error = if (silent && it.guests.isNotEmpty()) it.error else lastError,
                    )
                }
            }
        }
    }

    private fun computeRatesAndSparks(
        guests: List<GuestItem>,
    ): Pair<Map<String, GuestRates>, Map<String, List<Float>>> {
        val now = System.currentTimeMillis()
        val rates = mutableMapOf<String, GuestRates>()
        val history = mutableMapOf<String, List<Float>>()
        for (g in guests) {
            val id = g.resourceId.ifBlank { "${g.type}/${g.vmid}" }
            val sample = CounterSample(
                t = now,
                netin = g.netin ?: 0L,
                netout = g.netout ?: 0L,
                diskread = g.diskread ?: 0L,
                diskwrite = g.diskwrite ?: 0L,
            )
            val last = prevCounters[id]
            if (last != null && now - last.t >= 900) {
                val dt = (now - last.t) / 1000.0
                if (dt > 0) {
                    rates[g.key] = GuestRates(
                        netin = ((sample.netin - last.netin) / dt).coerceAtLeast(0.0),
                        netout = ((sample.netout - last.netout) / dt).coerceAtLeast(0.0),
                        diskread = ((sample.diskread - last.diskread) / dt).coerceAtLeast(0.0),
                        diskwrite = ((sample.diskwrite - last.diskwrite) / dt).coerceAtLeast(0.0),
                    )
                }
                prevCounters[id] = sample
            } else if (last == null) {
                prevCounters[id] = sample
            }

            val cpuPct = ((g.cpu ?: 0.0) * 100.0).toFloat().coerceIn(0f, 100f)
            val buf = cpuBuffers.getOrPut(g.key) { ArrayDeque() }
            buf.addLast(cpuPct)
            while (buf.size > 16) buf.removeFirst()
            history[g.key] = buf.toList()
        }
        return rates to history
    }

    private fun computeClusterOverview(
        resources: List<com.proxpanel.app.data.ClusterResource>,
        guests: List<GuestItem>,
    ): ClusterOverview {
        val nodes = resources.mapNotNull { it.toNodeOrNull() }
        val online = nodes.count { it.status.equals("online", ignoreCase = true) }
        val storages = resources.mapNotNull { it.toStorageOrNull() }
        val total = storages.mapNotNull { it.maxdisk }.sum()
        val used = storages.mapNotNull { it.disk }.sum()
        return ClusterOverview(
            nodesOnline = online,
            nodesTotal = nodes.size,
            guestsRunning = guests.count { it.isRunning },
            guestsTotal = guests.size,
            storageFree = if (total > 0) (total - used).coerceAtLeast(0) else null,
            storageTotal = total.takeIf { it > 0 },
        )
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { api.logout() }
            onDone()
        }
    }

    fun requestPower(guest: GuestItem, action: String) {
        val confirm = when (action) {
            "shutdown" -> GuestPowerConfirm(
                guest = guest,
                action = action,
                title = l10n.get(R.string.confirm_shutdown_title),
                body = l10n.get(R.string.confirm_shutdown_body, guest.name),
                confirm = l10n.get(R.string.confirm_shutdown_confirm),
            )
            "reboot" -> GuestPowerConfirm(
                guest = guest,
                action = action,
                title = l10n.get(R.string.confirm_reboot_title),
                body = l10n.get(R.string.confirm_reboot_body, guest.name),
                confirm = l10n.get(R.string.confirm_reboot_confirm),
            )
            "stop" -> GuestPowerConfirm(
                guest = guest,
                action = action,
                title = l10n.get(R.string.confirm_force_stop_title),
                body = l10n.get(R.string.confirm_force_stop_body, guest.name),
                confirm = l10n.get(R.string.confirm_force_stop_confirm),
                danger = true,
            )
            else -> null
        }
        if (confirm != null) {
            publishNow { it.copy(confirm = confirm) }
        } else {
            runPower(guest, action)
        }
    }

    fun dismissConfirm() = publishNow { it.copy(confirm = null) }

    fun confirmPower() {
        val c = currentState().confirm ?: return
        publishNow { it.copy(confirm = null) }
        runPower(c.guest, c.action)
    }

    private fun guestKey(guest: GuestItem) = guest.key

    private fun runPower(guest: GuestItem, action: String) {
        val actionKey = "${guest.node}/${guest.vmid}/$action"
        val gKey = guestKey(guest)
        viewModelScope.launch {
            publishNow {
                it.copy(
                    busyKey = actionKey,
                    pendingPower = it.pendingPower + (gKey to PendingPower(action)),
                    error = null,
                    actionMessage = null,
                )
            }
            try {
                api.action(guest.node, guest.type, guest.vmid, action)
                val msg = when (action) {
                    "start" -> l10n.get(R.string.toast_power_start, guest.name)
                    "shutdown" -> l10n.get(R.string.toast_power_shutdown, guest.name)
                    "reboot" -> l10n.get(R.string.toast_power_reboot, guest.name)
                    "stop" -> l10n.get(R.string.toast_power_stop, guest.name)
                    else -> l10n.get(R.string.toast_action_requested, action)
                }
                jobTracker.toast(ToastKind.Ok, msg)
                publishNow { it.copy(busyKey = null, actionMessage = msg) }
                delay(1_200)
                refresh(silent = true)
                reconcilePendingPower()
            } catch (e: Exception) {
                val err = e.message ?: l10n.get(R.string.error_action_failed)
                jobTracker.toast(ToastKind.Err, err)
                publishNow {
                    it.copy(
                        busyKey = null,
                        pendingPower = it.pendingPower - gKey,
                        error = err,
                    )
                }
            }
        }
    }

    private suspend fun reconcilePendingPower() {
        val snap = currentState()
        val pending = snap.pendingPower
        if (pending.isEmpty()) return
        val guests = snap.guests.associateBy { guestKey(it) }
        val tasks = runCatching { api.tasks(limit = 60) }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val next = pending.toMutableMap()

        pending.forEach { (key, info) ->
            val guest = guests[key]
            val vmid = key.substringAfterLast('/')
            val age = now - info.startedAt
            if (age > 3 * 60_000L) {
                next.remove(key)
                return@forEach
            }

            val qmp = guest?.qmpstatus.orEmpty().lowercase()
            val status = guest?.status.orEmpty().lowercase()

            val related = tasks.filter {
                (it.id == vmid || it.upid.contains(":$vmid:")) &&
                    com.proxpanel.app.ui.tasks.matchesPowerAction(it.type, info.action)
            }
            val runningTask = related.any { t ->
                t.endtime == null || t.endtime == 0L ||
                    t.status.equals("running", ignoreCase = true)
            }
            val finishedTask = related.any { t ->
                (t.endtime != null && t.endtime > 0L) &&
                    !t.status.equals("running", ignoreCase = true)
            }

            when (info.action) {
                "reboot" -> {
                    when {
                        qmp == "shutdown" || qmp == "prelaunch" -> Unit
                        runningTask -> Unit
                        finishedTask && age > 4_000L && status == "running" &&
                            qmp != "shutdown" && qmp != "prelaunch" -> next.remove(key)
                        guest?.isRunning == true && age > 8_000L && !runningTask &&
                            qmp != "shutdown" && qmp != "prelaunch" -> next.remove(key)
                        age > 90_000L -> next.remove(key)
                    }
                }
                "shutdown", "stop" -> {
                    when {
                        qmp == "shutdown" && status != "stopped" -> Unit
                        guest != null && !guest.isRunning && age > 2_000L -> next.remove(key)
                        finishedTask && age > 3_000L -> next.remove(key)
                        runningTask -> Unit
                    }
                }
                "start" -> {
                    when {
                        qmp == "prelaunch" -> Unit
                        guest?.isRunning == true && qmp != "prelaunch" && age > 2_000L -> next.remove(key)
                        finishedTask && age > 3_000L -> next.remove(key)
                        runningTask -> Unit
                    }
                }
                else -> if (age > 30_000L) next.remove(key)
            }
        }

        tasks.filter {
            (it.endtime == null || it.endtime == 0L) &&
                com.proxpanel.app.ui.tasks.matchesPowerAction(it.type, "reboot")
        }.forEach { task ->
            val vmid = task.id ?: parseVmid(task.upid) ?: return@forEach
            val node = task.node ?: return@forEach
            val key = "$node/$vmid"
            if (key !in next) {
                next[key] = PendingPower("reboot", now - 1_000L)
            }
        }

        if (next != pending) {
            publishBackground { it.copy(pendingPower = next) }
            if (next.size < pending.size) refresh(silent = true)
        }
    }

    private fun parseVmid(upid: String): String? =
        upid.split(':').getOrNull(6)?.takeIf { it.all(Char::isDigit) }

    fun startBackup(guest: GuestItem, storage: String) {
        val key = "${guest.node}/${guest.vmid}/backup"
        viewModelScope.launch {
            publishNow { it.copy(busyKey = key, error = null, actionMessage = null) }
            try {
                prefsStore.setLastBackupStorage(storage)
                jobTracker.startGuestBackup(
                    node = guest.node,
                    type = guest.type,
                    vmid = guest.vmid,
                    name = guest.name,
                    storage = storage,
                )
                publishNow {
                    it.copy(busyKey = null, actionMessage = l10n.get(R.string.toast_backup_started_for, guest.name))
                }
            } catch (e: Exception) {
                publishNow {
                    it.copy(busyKey = null, error = e.message ?: l10n.get(R.string.error_backup_failed))
                }
            }
        }
    }

    /** One-tap backup using remembered storage, or first available. */
    fun quickBackup(guest: GuestItem): Boolean {
        val snap = currentState()
        val preferred = snap.lastBackupStorage
        val storages = snap.backupStorages
        val storage = when {
            preferred != null && preferred in storages -> preferred
            storages.size == 1 -> storages.first()
            else -> null
        } ?: return false
        startBackup(guest, storage)
        return true
    }

    fun backupProgress(guest: GuestItem) =
        jobTracker.backupProgressFor(guest.node, guest.vmid)

    companion object {
        fun factory(api: ApiClient, jobTracker: JobTracker, prefsStore: PrefsStore, l10n: L10n) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GuestsViewModel(api, jobTracker, prefsStore, l10n) as T
            }
    }
}

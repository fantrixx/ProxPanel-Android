package com.proxpanel.app.ui.guests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.ApiException
import com.proxpanel.app.data.AuthUser
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.data.GuestRates
import com.proxpanel.app.data.toGuestOrNull
import com.proxpanel.app.jobs.JobTracker
import com.proxpanel.app.jobs.ToastKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class GuestTypeFilter {
    All,
    Ct,
    Vm,
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
    val typeFilter: GuestTypeFilter = GuestTypeFilter.All,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val busyKey: String? = null,
    val pendingPower: Map<String, PendingPower> = emptyMap(),
    val confirm: GuestPowerConfirm? = null,
    val error: String? = null,
    val actionMessage: String? = null,
)

class GuestsViewModel(
    private val api: ApiClient,
    private val jobTracker: JobTracker,
) : ViewModel() {
    private val _state = MutableStateFlow(GuestsUiState())
    val state: StateFlow<GuestsUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    private var pendingJob: Job? = null
    private val refreshMutex = Mutex()
    private val prevCounters = mutableMapOf<String, CounterSample>()
    private val cpuBuffers = mutableMapOf<String, ArrayDeque<Float>>()

    init {
        refresh(initial = true)
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                refresh(silent = true)
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

    fun setTypeFilter(filter: GuestTypeFilter) {
        _state.update { it.copy(typeFilter = filter) }
    }

    fun clearMessage() = _state.update { it.copy(actionMessage = null, error = null) }

    fun refreshIfEmpty() {
        if (_state.value.guests.isEmpty()) refresh(initial = true)
    }

    fun refresh(initial: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            refreshMutex.withLock {
                if (!silent) {
                    _state.update {
                        it.copy(
                            loading = initial || it.guests.isEmpty(),
                            refreshing = !initial && it.guests.isNotEmpty(),
                            error = null,
                        )
                    }
                }
                var lastError: String? = null
                repeat(2) { attempt ->
                    try {
                        val me = runCatching { api.me() }.getOrNull()
                        val res = api.resources()
                        val guests = res.resources.mapNotNull { it.toGuestOrNull() }
                            .sortedWith(
                                compareByDescending<GuestItem> { it.isRunning }
                                    .thenBy { it.name.lowercase() },
                            )
                        val storages = runCatching {
                            api.backupStorages().map { it.storage }.distinct()
                        }.getOrDefault(_state.value.backupStorages)
                        val (rates, history) = computeRatesAndSparks(guests)
                        _state.update {
                            it.copy(
                                user = me ?: it.user,
                                guests = guests,
                                backupStorages = storages,
                                rates = rates,
                                cpuHistory = history,
                                loading = false,
                                refreshing = false,
                                error = null,
                            )
                        }
                        reconcilePendingPower()
                        return@withLock
                    } catch (e: ApiException) {
                        lastError = e.message
                    } catch (e: Exception) {
                        lastError = e.message ?: "Failed to load"
                    }
                    if (attempt == 0) delay(400)
                }
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
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
                title = "Herunterfahren?",
                body = "„${guest.name}“ wird ordentlich heruntergefahren. Ungespeicherte Daten können verloren gehen.",
                confirm = "Herunterfahren",
            )
            "reboot" -> GuestPowerConfirm(
                guest = guest,
                action = action,
                title = "Neustart?",
                body = "„${guest.name}“ wird neu gestartet. Laufende Dienste werden kurz unterbrochen.",
                confirm = "Neustart",
            )
            "stop" -> GuestPowerConfirm(
                guest = guest,
                action = action,
                title = "Force Stop?",
                body = "„${guest.name}“ wird hart gestoppt (wie Strom ziehen). Datenverlust möglich.",
                confirm = "Stoppen",
                danger = true,
            )
            else -> null
        }
        if (confirm != null) {
            _state.update { it.copy(confirm = confirm) }
        } else {
            runPower(guest, action)
        }
    }

    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    fun confirmPower() {
        val c = _state.value.confirm ?: return
        _state.update { it.copy(confirm = null) }
        runPower(c.guest, c.action)
    }

    private fun guestKey(guest: GuestItem) = guest.key

    private fun runPower(guest: GuestItem, action: String) {
        val actionKey = "${guest.node}/${guest.vmid}/$action"
        val gKey = guestKey(guest)
        viewModelScope.launch {
            _state.update {
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
                    "start" -> "${guest.name}: Start gestartet"
                    "shutdown" -> "${guest.name}: Shutdown gestartet"
                    "reboot" -> "${guest.name}: Neustart gestartet"
                    "stop" -> "${guest.name}: Stop gestartet"
                    else -> "${guest.name}: $action"
                }
                jobTracker.toast(ToastKind.Ok, msg)
                _state.update { it.copy(busyKey = null, actionMessage = msg) }
                delay(1_200)
                refresh(silent = true)
                reconcilePendingPower()
            } catch (e: Exception) {
                val err = e.message ?: "Aktion fehlgeschlagen"
                jobTracker.toast(ToastKind.Err, err)
                _state.update {
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
        val pending = _state.value.pendingPower
        if (pending.isEmpty()) return
        val guests = _state.value.guests.associateBy { guestKey(it) }
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
            _state.update { it.copy(pendingPower = next) }
            if (next.size < pending.size) refresh(silent = true)
        }
    }

    private fun parseVmid(upid: String): String? =
        upid.split(':').getOrNull(6)?.takeIf { it.all(Char::isDigit) }

    fun startBackup(guest: GuestItem, storage: String) {
        val key = "${guest.node}/${guest.vmid}/backup"
        viewModelScope.launch {
            _state.update { it.copy(busyKey = key, error = null, actionMessage = null) }
            try {
                jobTracker.startGuestBackup(
                    node = guest.node,
                    type = guest.type,
                    vmid = guest.vmid,
                    name = guest.name,
                    storage = storage,
                )
                _state.update {
                    it.copy(busyKey = null, actionMessage = "Backup gestartet für ${guest.name}")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busyKey = null, error = e.message ?: "Backup fehlgeschlagen")
                }
            }
        }
    }

    fun backupProgress(guest: GuestItem) =
        jobTracker.backupProgressFor(guest.node, guest.vmid)

    companion object {
        fun factory(api: ApiClient, jobTracker: JobTracker) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GuestsViewModel(api, jobTracker) as T
        }
    }
}

package com.proxpanel.app.jobs

import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.OkResponse
import com.proxpanel.app.util.formatDuration
import com.proxpanel.app.util.parseProgressFromLog
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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

enum class JobKind { Backup, Restore, Create, Task }

data class ActiveJob(
    val id: String,
    val kind: JobKind,
    val title: String,
    val detail: String,
    val node: String,
    val upid: String = "",
    val vmid: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
)

data class JobSnapshot(
    val running: Boolean = true,
    val failed: Boolean = false,
    val ok: Boolean = false,
    val progress: Float? = null,
    val elapsedSec: Long = 0,
    val etaSec: Long? = null,
    val exitstatus: String = "",
    val latestLog: String? = null,
)

enum class ToastKind { Ok, Err, Info }

data class AppToast(
    val id: String,
    val kind: ToastKind,
    val text: String,
)

/**
 * App-wide job tracking (backup/restore/create) with live % from task logs,
 * plus ephemeral toasts — mirrors ProxPanel web ActiveJobs + Toasts.
 */
class JobTracker(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _jobs = MutableStateFlow<List<ActiveJob>>(emptyList())
    val jobs: StateFlow<List<ActiveJob>> = _jobs.asStateFlow()

    private val _snapshots = MutableStateFlow<Map<String, JobSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, JobSnapshot>> = _snapshots.asStateFlow()

    private val _toasts = MutableStateFlow<List<AppToast>>(emptyList())
    val toasts: StateFlow<List<AppToast>> = _toasts.asStateFlow()

    private val pollJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    fun toast(kind: ToastKind, text: String) {
        val id = UUID.randomUUID().toString()
        _toasts.update { it + AppToast(id, kind, text) }
        scope.launch {
            delay(if (kind == ToastKind.Err) 12_000 else 5_200)
            dismissToast(id)
        }
    }

    fun dismissToast(id: String) {
        _toasts.update { list -> list.filterNot { it.id == id } }
    }

    fun trackJob(
        kind: JobKind,
        title: String,
        detail: String,
        node: String,
        upid: String = "",
        vmid: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val job = ActiveJob(
            id = id,
            kind = kind,
            title = title,
            detail = detail,
            node = node,
            upid = upid,
            vmid = vmid,
        )
        _jobs.update { prev ->
            val rest = if (upid.isNotBlank()) prev.filter { it.upid != upid } else prev
            rest + job
        }
        _snapshots.update {
            it + (id to JobSnapshot(running = true, latestLog = if (upid.isBlank()) "Starting…" else null))
        }
        startPolling(id)
        return id
    }

    fun attachJobUpid(id: String, upid: String) {
        _jobs.update { list ->
            list.map { if (it.id == id) it.copy(upid = upid, error = null) else it }
        }
        startPolling(id)
    }

    fun failJob(id: String, error: String) {
        _jobs.update { list ->
            list.map { if (it.id == id) it.copy(error = error) else it }
        }
        _snapshots.update { snaps ->
            val cur = snaps[id] ?: JobSnapshot()
            snaps + (id to cur.copy(running = false, failed = true, ok = false, exitstatus = error, latestLog = error))
        }
    }

    fun dismissJob(id: String) {
        pollJobs.remove(id)?.cancel()
        _jobs.update { it.filterNot { j -> j.id == id } }
        _snapshots.update { it - id }
    }

    fun backupProgressFor(node: String, vmid: Int): GuestJobProgress? {
        val job = _jobs.value.asReversed().firstOrNull { j ->
            (j.kind == JobKind.Backup || j.kind == JobKind.Restore) &&
                j.node == node &&
                j.vmid == vmid.toString()
        } ?: return null
        val snap = _snapshots.value[job.id]
        val failed = job.error != null || (snap?.failed == true)
        val running = !failed && (snap == null || snap.running || job.upid.isBlank())
        return GuestJobProgress(
            job = job,
            running = running,
            failed = failed,
            progress = when {
                failed -> null
                snap?.ok == true -> 100f
                else -> snap?.progress
            },
            label = when {
                failed -> job.error ?: "Backup failed"
                snap?.progress != null -> "${snap.progress.toInt()}%"
                job.upid.isBlank() -> "Starting…"
                else -> "Backing up…"
            },
        )
    }

    suspend fun startGuestBackup(
        node: String,
        type: String,
        vmid: Int,
        name: String?,
        storage: String,
        mode: String = "snapshot",
        compress: String = "zstd",
    ): OkResponse {
        val label = name?.ifBlank { null } ?: "Guest $vmid"
        val kindLabel = if (type == "lxc") "CT" else "VM"
        val jobId = trackJob(
            kind = JobKind.Backup,
            title = "Backup · $label",
            detail = "$kindLabel $vmid → $storage · $node",
            node = node,
            upid = "",
            vmid = vmid.toString(),
        )
        return try {
            val res = api.startBackup(node, vmid, storage, mode = mode, compress = compress)
            val upid = res.upid
            if (upid.isNullOrBlank()) {
                val msg = "Keine Task-ID von Proxmox"
                failJob(jobId, msg)
                toast(ToastKind.Err, msg)
                throw IllegalStateException(msg)
            }
            attachJobUpid(jobId, upid)
            toast(ToastKind.Ok, "Backup gestartet")
            res
        } catch (e: Exception) {
            val message = e.message ?: "Backup fehlgeschlagen"
            if (message != "Keine Task-ID von Proxmox") {
                failJob(jobId, message)
                toast(ToastKind.Err, message)
            }
            throw e
        }
    }

    suspend fun trackRestore(
        node: String,
        type: String,
        vmid: Int,
        name: String?,
        archive: String,
        storage: String?,
        force: Boolean,
    ): OkResponse {
        val label = name?.ifBlank { null } ?: "Guest $vmid"
        val jobId = trackJob(
            kind = JobKind.Restore,
            title = "Restore · $label",
            detail = "→ VMID $vmid · $node",
            node = node,
            upid = "",
            vmid = vmid.toString(),
        )
        return try {
            val res = api.restoreBackup(node, type, vmid, archive, storage, force)
            val upid = res.upid
            if (!upid.isNullOrBlank()) attachJobUpid(jobId, upid)
            toast(ToastKind.Ok, "Restore gestartet")
            res
        } catch (e: Exception) {
            failJob(jobId, e.message ?: "Restore fehlgeschlagen")
            toast(ToastKind.Err, e.message ?: "Restore fehlgeschlagen")
            throw e
        }
    }

    fun trackCreate(node: String, type: String, vmid: Int, upid: String?) {
        val kindLabel = if (type == "lxc") "CT" else "VM"
        trackJob(
            kind = JobKind.Create,
            title = "Create · $kindLabel $vmid",
            detail = node,
            node = node,
            upid = upid.orEmpty(),
            vmid = vmid.toString(),
        )
        toast(ToastKind.Ok, "$kindLabel $vmid wird erstellt")
    }

    private fun startPolling(id: String) {
        pollJobs[id]?.cancel()
        pollJobs[id] = scope.launch {
            var doneAt: Long? = null
            while (isActive) {
                val job = _jobs.value.find { it.id == id } ?: break
                val now = System.currentTimeMillis()
                val elapsed = ((doneAt ?: now) - job.startedAt) / 1000L

                if (job.error != null) {
                    _snapshots.update {
                        it + (id to JobSnapshot(
                            running = false,
                            failed = true,
                            elapsedSec = elapsed,
                            exitstatus = job.error,
                            latestLog = job.error,
                        ))
                    }
                    break
                }

                if (job.upid.isBlank()) {
                    val recovered = recoverUpid(job)
                    if (recovered != null) {
                        // Continue in this poll loop with the recovered UPID (don't restart).
                        _jobs.update { list ->
                            list.map { if (it.id == id) it.copy(upid = recovered, error = null) else it }
                        }
                    }
                    _snapshots.update {
                        it + (id to JobSnapshot(
                            running = true,
                            elapsedSec = elapsed,
                            latestLog = if (recovered == null) "Starting…" else null,
                        ))
                    }
                    delay(2_000)
                    continue
                }

                val status = runCatching { api.taskStatus(job.node, job.upid) }.getOrNull()
                val statusStr = status?.status?.lowercase().orEmpty()
                val running = statusStr != "stopped"
                val exitstatus = status?.exitstatus.orEmpty()
                val failed = !running && exitstatus.isNotBlank() && !exitstatus.equals("OK", ignoreCase = true)
                val ok = !running && !failed

                val logLines = if (running || failed) {
                    runCatching { api.taskLog(job.node, job.upid, limit = 40) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val progress = when {
                    failed -> null
                    ok -> 100f
                    else -> parseProgressFromLog(logLines)
                }
                val latestLog = logLines.asReversed().firstOrNull { it.isNotBlank() }
                val eta = if (running && progress != null && progress >= 5f && progress < 100f) {
                    ((elapsed * (100f - progress)) / progress).toLong()
                } else {
                    null
                }

                if (!running && doneAt == null) doneAt = now

                _snapshots.update {
                    it + (id to JobSnapshot(
                        running = running,
                        failed = failed,
                        ok = ok,
                        progress = progress,
                        elapsedSec = elapsed,
                        etaSec = eta,
                        exitstatus = exitstatus,
                        latestLog = latestLog,
                    ))
                }

                if (!running) {
                    if (ok) {
                        delay(12_000)
                        dismissJob(id)
                    }
                    break
                }
                delay(2_000)
            }
        }
    }

    /** Returns recovered UPID or null. Does not restart polling. */
    private suspend fun recoverUpid(job: ActiveJob): String? = mutex.withLock {
        if (job.upid.isNotBlank()) return@withLock job.upid
        val tasks = runCatching { api.tasks(30) }.getOrDefault(emptyList())
        val match = tasks.firstOrNull { t ->
            if (t.upid.isBlank()) return@firstOrNull false
            if (job.vmid != null && t.id != job.vmid && !t.upid.contains(":${job.vmid}:")) return@firstOrNull false
            if (!t.node.isNullOrBlank() && t.node != job.node) return@firstOrNull false
            val type = t.type.orEmpty().lowercase()
            when (job.kind) {
                JobKind.Backup -> type.contains("vzdump")
                JobKind.Restore -> type.contains("restore")
                JobKind.Create -> type.contains("create") || type.contains("clone")
                JobKind.Task -> true
            } && (t.status.equals("running", ignoreCase = true) || t.endtime == null || t.endtime == 0L)
        }
        match?.upid
    }
}

data class GuestJobProgress(
    val job: ActiveJob,
    val running: Boolean,
    val failed: Boolean,
    val progress: Float?,
    val label: String,
)

fun kindLabel(kind: JobKind, phase: String): String {
    val base = when (kind) {
        JobKind.Backup -> "Backup"
        JobKind.Restore -> "Restore"
        JobKind.Create -> "Create"
        JobKind.Task -> "Task"
    }
    return when (phase) {
        "ok" -> "$base finished"
        "fail" -> "$base failed"
        else -> base
    }
}

fun formatJobMeta(snap: JobSnapshot, job: ActiveJob): String = when {
    snap.running && snap.progress != null ->
        "${snap.progress.toInt()}% · ${formatDuration(snap.elapsedSec)}"
    snap.running && job.upid.isNotBlank() ->
        "In progress · ${formatDuration(snap.elapsedSec)}"
    snap.running ->
        "Starting… · ${formatDuration(snap.elapsedSec)}"
    snap.failed ->
        "Failed after ${formatDuration(snap.elapsedSec)}" +
            if (snap.exitstatus.isNotBlank()) " · ${snap.exitstatus}" else ""
    else -> "Done in ${formatDuration(snap.elapsedSec)}"
}

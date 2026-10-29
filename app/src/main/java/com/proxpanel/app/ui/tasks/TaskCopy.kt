package com.proxpanel.app.ui.tasks

import com.proxpanel.app.data.PveTask

data class TaskGuestRef(val name: String, val kind: String)

enum class TaskKind { Backup, Restore, Power, Snapshot, Migrate, Clone, Host, Other }

enum class TaskOutcome { Running, Ok, Fail }

enum class TaskStatusFilter { All, Running, Ok, Fail }

enum class TaskKindFilter {
    All, Backup, Restore, Power, Snapshot, Migrate, Clone, Host;

    fun matches(kind: TaskKind): Boolean = when (this) {
        All -> true
        Backup -> kind == TaskKind.Backup
        Restore -> kind == TaskKind.Restore
        Power -> kind == TaskKind.Power
        Snapshot -> kind == TaskKind.Snapshot
        Migrate -> kind == TaskKind.Migrate
        Clone -> kind == TaskKind.Clone
        Host -> kind == TaskKind.Host
    }
}

fun parseUpid(upid: String): Map<String, String?> {
    if (!upid.startsWith("UPID:")) return emptyMap()
    val parts = upid.split(':')
    return mapOf(
        "node" to parts.getOrNull(1),
        "type" to parts.getOrNull(5),
        "id" to parts.getOrNull(6)?.takeIf { it.isNotBlank() && it != "-" },
        "user" to parts.drop(7).joinToString(":").removeSuffix(":").ifBlank { null },
    )
}

fun taskOutcome(task: PveTask): TaskOutcome {
    val s = task.status.orEmpty().lowercase()
    return when {
        s == "running" -> TaskOutcome.Running
        s.isEmpty() || s == "ok" || s == "stopped" || s.contains("warn") -> TaskOutcome.Ok
        else -> TaskOutcome.Fail
    }
}

fun taskKind(type: String?): TaskKind {
    val t = type.orEmpty().lowercase()
    return when {
        t == "vzdump" -> TaskKind.Backup
        t.contains("restore") -> TaskKind.Restore
        t.contains("start") || t.contains("stop") || t.contains("shutdown") ||
            t.contains("reboot") || t.contains("reset") || t.contains("suspend") ||
            t.contains("resume") -> TaskKind.Power
        t.contains("snapshot") || t.contains("rollback") -> TaskKind.Snapshot
        t.contains("migrat") -> TaskKind.Migrate
        t.contains("clone") -> TaskKind.Clone
        t == "aptupdate" || t == "aptupgrade" || t.contains("update") -> TaskKind.Host
        else -> TaskKind.Other
    }
}

fun taskKindLabel(type: String?): String {
    val t = type.orEmpty().lowercase()
    return when {
        t == "vzdump" -> "Backup"
        t.contains("restore") -> "Restore"
        t.contains("shutdown") -> "Shutdown"
        t.contains("reboot") || t.contains("reset") -> "Restart"
        t.contains("start") -> "Start"
        t.contains("stop") -> "Stop"
        t.contains("suspend") -> "Pause"
        t.contains("resume") -> "Resume"
        t.contains("delsnapshot") -> "Snapshot löschen"
        t.contains("rollback") -> "Rollback"
        t.contains("snapshot") -> "Snapshot"
        t.contains("clone") || t == "imgcopy" -> "Kopie"
        t.contains("migrat") -> "Migration"
        t.contains("move") -> "Disk verschieben"
        t == "aptupdate" -> "Paketliste"
        t == "aptupgrade" -> "Upgrade"
        t.isBlank() -> "Task"
        else -> t.replace(Regex("^qm"), "VM ").replace(Regex("^vz"), "CT ")
    }
}

fun formatTaskUser(user: String?): String? {
    if (user.isNullOrBlank()) return null
    return if (user.endsWith("@pam")) user.removeSuffix("@pam") else user
}

data class TaskCopy(
    val title: String,
    val kind: TaskKind,
    val kindLabel: String,
    val outcome: TaskOutcome,
    val node: String,
    val userLabel: String?,
    val targetTag: String?,
    val statusNote: String?,
    val typeKey: String,
)

fun describeTask(task: PveTask, guests: Map<String, TaskGuestRef>): TaskCopy {
    val parsed = parseUpid(task.upid)
    val type = (task.type ?: parsed["type"]).orEmpty().lowercase()
    val node = task.node ?: parsed["node"] ?: "Host"
    val who = formatTaskUser(task.user ?: parsed["user"])
    val id = task.id ?: parsed["id"]
    val known = id?.let { guests[it] }
    val kindHint = when {
        known != null -> known.kind
        type.startsWith("qm") -> "VM"
        type.startsWith("vz") -> "CT"
        else -> null
    }
    val name = known?.name
        ?: if (id != null && id.all { it.isDigit() }) {
            kindHint?.let { "$it $id" } ?: "#$id"
        } else {
            null
        }
    val tag = if (id != null && id.all { it.isDigit() }) {
        kindHint?.let { "$it $id" } ?: "#$id"
    } else {
        id
    }
    val outcome = taskOutcome(task)
    val running = outcome == TaskOutcome.Running
    val fail = outcome == TaskOutcome.Fail

    fun verb(doing: String, done: String, failed: String) = when {
        running -> doing
        fail -> failed
        else -> done
    }

    val title = when (type) {
        "vzdump" -> verb(
            if (name != null) "Backup von $name…" else "Backup läuft…",
            if (name != null) "$name wurde gesichert" else "Backup fertig",
            if (name != null) "Backup von $name fehlgeschlagen" else "Backup fehlgeschlagen",
        )
        "qmrestore", "vzrestore" -> verb(
            if (name != null) "Stelle $name wieder her…" else "Restore läuft…",
            if (name != null) "$name wiederhergestellt" else "Restore fertig",
            if (name != null) "Restore von $name fehlgeschlagen" else "Restore fehlgeschlagen",
        )
        "qmstart", "vzstart", "startall" -> verb(
            if (name != null) "Starte $name…" else "Start läuft…",
            if (name != null) "$name gestartet" else "Start fertig",
            if (name != null) "Start von $name fehlgeschlagen" else "Start fehlgeschlagen",
        )
        "qmstop", "vzstop", "stopall" -> verb(
            if (name != null) "Stoppe $name…" else "Stop läuft…",
            if (name != null) "$name gestoppt" else "Stop fertig",
            if (name != null) "Stop von $name fehlgeschlagen" else "Stop fehlgeschlagen",
        )
        "qmshutdown", "vzshutdown" -> verb(
            if (name != null) "Fahre $name herunter…" else "Shutdown läuft…",
            if (name != null) "$name heruntergefahren" else "Shutdown fertig",
            if (name != null) "Shutdown von $name fehlgeschlagen" else "Shutdown fehlgeschlagen",
        )
        "qmreboot", "qmreset", "vzreboot" -> verb(
            if (name != null) "Starte $name neu…" else "Neustart läuft…",
            if (name != null) "$name neu gestartet" else "Neustart fertig",
            if (name != null) "Neustart von $name fehlgeschlagen" else "Neustart fehlgeschlagen",
        )
        "qmsnapshot", "vzsnapshot" -> verb(
            if (name != null) "Snapshot von $name…" else "Snapshot läuft…",
            if (name != null) "Snapshot von $name gespeichert" else "Snapshot fertig",
            if (name != null) "Snapshot von $name fehlgeschlagen" else "Snapshot fehlgeschlagen",
        )
        "qmdelsnapshot", "vzdelsnapshot" -> verb(
            if (name != null) "Lösche Snapshot von $name…" else "Snapshot löschen…",
            if (name != null) "Snapshot von $name gelöscht" else "Snapshot gelöscht",
            if (name != null) "Snapshot-Löschen fehlgeschlagen" else "Löschen fehlgeschlagen",
        )
        "qmrollback", "vzrollback" -> verb(
            if (name != null) "Rollback von $name…" else "Rollback läuft…",
            if (name != null) "$name zurückgesetzt" else "Rollback fertig",
            if (name != null) "Rollback von $name fehlgeschlagen" else "Rollback fehlgeschlagen",
        )
        "qmclone", "vzclone", "imgcopy" -> verb(
            if (name != null) "Kopiere $name…" else "Kopie läuft…",
            if (name != null) "$name kopiert" else "Kopie fertig",
            if (name != null) "Kopie von $name fehlgeschlagen" else "Kopie fehlgeschlagen",
        )
        "migrate", "qmigrate" -> verb(
            if (name != null) "Migriere $name…" else "Migration läuft…",
            if (name != null) "$name migriert" else "Migration fertig",
            if (name != null) "Migration von $name fehlgeschlagen" else "Migration fehlgeschlagen",
        )
        "aptupdate" -> verb(
            "Prüfe Updates auf $node…",
            "Paketliste auf $node aktualisiert",
            "Update-Check auf $node fehlgeschlagen",
        )
        else -> {
            val label = type.ifBlank { "task" }
                .replace(Regex("^qm"), "VM ")
                .replace(Regex("^vz"), "CT ")
            verb(
                if (name != null) "$label für $name…" else "$label läuft…",
                if (name != null) "$label für $name fertig" else "$label fertig",
                if (name != null) "$label für $name fehlgeschlagen" else "$label fehlgeschlagen",
            )
        }
    }

    val statusNote = if (fail) {
        val raw = task.status?.trim().orEmpty()
        when {
            raw.isEmpty() || raw.equals("ok", true) || raw.equals("running", true) -> "Fehlgeschlagen"
            else -> raw
        }
    } else {
        null
    }

    return TaskCopy(
        title = title,
        kind = taskKind(type),
        kindLabel = taskKindLabel(type),
        outcome = outcome,
        node = node,
        userLabel = who,
        targetTag = tag,
        statusNote = statusNote,
        typeKey = type.ifBlank { "task" },
    )
}

fun isPowerTaskType(type: String?): Boolean {
    val t = type.orEmpty().lowercase()
    return t.contains("reboot") || t.contains("reset") || t.contains("shutdown") ||
        t.contains("start") || t.contains("stop")
}

fun matchesPowerAction(type: String?, action: String): Boolean {
    val t = type.orEmpty().lowercase()
    return when (action) {
        "reboot" -> t.contains("reboot") || t.contains("reset")
        "shutdown" -> t.contains("shutdown")
        "stop" -> t.contains("stop") && !t.contains("shutdown")
        "start" -> t.contains("start")
        else -> false
    }
}

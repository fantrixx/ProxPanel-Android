package com.proxpanel.app.util

import androidx.core.text.HtmlCompat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Proxmox guest notes/description are often HTML (`<div>…</div>`) for the web UI.
 * Strip tags/entities so the Android app shows readable plain text.
 */
fun htmlToPlainText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val trimmed = raw.trim()
    if (!trimmed.contains('<') && !trimmed.contains('&')) return trimmed
    return HtmlCompat.fromHtml(trimmed, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trim()
}

fun jsonConfigString(el: JsonElement?): String =
    when (el) {
        is JsonPrimitive -> el.contentOrNull.orEmpty()
        null -> ""
        else -> el.toString().trim('"')
    }

fun formatBytes(n: Long?): String {
    if (n == null) return "—"
    val abs = kotlin.math.abs(n.toDouble())
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var v = abs
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    val digits = when {
        i == 0 -> 0
        v >= 10 -> 1
        else -> 2
    }
    return "%.${digits}f %s".format(v, units[i])
}

fun formatBytesRate(n: Double?): String {
    if (n == null || n.isNaN() || n < 0) return "—"
    return "${formatBytes(n.toLong())}/s"
}

fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "—"
    val s = seconds
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "${h}h ${m}m ${sec}s"
        m > 0 -> "${m}m ${sec}s"
        else -> "${sec}s"
    }
}

/** Parse last `12%` / `status: 12%` style progress from Proxmox task log lines. */
fun parseProgressFromLog(lines: List<String>): Float? {
    for (i in lines.indices.reversed()) {
        val text = lines[i]
        val match = PROGRESS_RE.find(text) ?: continue
        val pct = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: continue
        return pct.coerceIn(0f, 100f)
    }
    return null
}

private val PROGRESS_RE = Regex("""(?:status:\s*)?(\d+(?:\.\d+)?)\s*%""", RegexOption.IGNORE_CASE)

/**
 * Resolve the status shown on cards / badges from Proxmox + local pending actions.
 * Mirrors ProxPanel web `guestVisualStatus`.
 */
fun guestVisualStatus(
    status: String?,
    qmpstatus: String? = null,
    lock: String? = null,
    pending: String? = null,
): String {
    val lockL = lock.orEmpty().lowercase()
    if (lockL.contains("creat") || lockL == "clone") return "creating"
    if (lockL.contains("migrat")) return "migrating"
    if (lockL == "suspended" || lockL.contains("suspend")) return "suspended"

    val qmp = qmpstatus.orEmpty().lowercase()
    if (qmp == "shutdown") return "shutting down"
    if (qmp == "prelaunch") return "starting"
    if (qmp == "paused") return "paused"
    if (qmp == "suspended") return "suspended"

    val pend = pending.orEmpty().lowercase()
    if (pend.isNotBlank()) return pend

    val s = status.orEmpty().lowercase()
    return s.ifBlank { "unknown" }
}

fun statusLabel(status: String?): String = when ((status ?: "").lowercase()) {
    "running" -> "running"
    "stopped" -> "stopped"
    "paused" -> "paused"
    "suspended" -> "suspended"
    "online" -> "online"
    "offline" -> "offline"
    "shutting down" -> "shutting down"
    "stopping" -> "stopping"
    "starting" -> "starting"
    "rebooting" -> "rebooting"
    "creating" -> "creating"
    "migrating" -> "migrating"
    else -> status ?: "unknown"
}

fun isTransitioningStatus(visual: String): Boolean = when (visual.lowercase()) {
    "shutting down", "stopping", "starting", "rebooting", "creating", "migrating" -> true
    else -> false
}

fun pendingLabelForAction(action: String): String = when (action) {
    "shutdown" -> "shutting down"
    "stop" -> "stopping"
    "start" -> "starting"
    "reboot" -> "rebooting"
    else -> action
}

fun formatPct(fraction: Double?): String {
    if (fraction == null) return "—"
    return "%.1f %%".format(fraction * 100)
}

fun cpuPct(cpu: Double?): Float {
    if (cpu == null) return 0f
    return (cpu * 100.0).coerceIn(0.0, 100.0).toFloat()
}

fun usagePct(used: Long?, max: Long?): Float {
    if (used == null || max == null || max <= 0) return 0f
    return ((used.toDouble() / max) * 100.0).coerceIn(0.0, 100.0).toFloat()
}

fun formatUptime(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "—"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

fun normalizeBaseUrl(raw: String): String {
    var s = raw.trim().trimEnd('/')
    if (s.isEmpty()) return s
    if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
        s = "https://$s"
    }
    // Same as ProxPanel web: default API port 8006 when none was typed.
    val hasExplicitPort = Regex("""^https?://[^/?#]+:\d+""", RegexOption.IGNORE_CASE).containsMatchIn(s)
    val url = runCatching { java.net.URI(s) }.getOrNull() ?: return s
    val host = url.host ?: return s
    val scheme = (url.scheme ?: "https").lowercase()
    val port = when {
        hasExplicitPort && url.port > 0 -> url.port
        else -> 8006
    }
    return "$scheme://$host:$port"
}

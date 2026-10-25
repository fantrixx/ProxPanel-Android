package com.proxpanel.app.ui.backups

import android.content.Context
import com.proxpanel.app.R
import com.proxpanel.app.data.BackupOverviewGuest
import com.proxpanel.app.data.BackupScheduleJob
import java.util.Locale

/** Human-readable schedule for Proxmox systemd-calendar / short forms. */
fun formatBackupSchedule(raw: String?, context: Context): String {
    if (raw.isNullOrBlank()) return context.getString(R.string.backups_schedule_unknown_when)
    val s = raw.trim()
    // Common short forms: "sun 02:00", "mon..fri 23:30", "*-*-* 02:00:00"
    val dayMap = mapOf(
        "mon" to context.getString(R.string.dow_mon),
        "tue" to context.getString(R.string.dow_tue),
        "wed" to context.getString(R.string.dow_wed),
        "thu" to context.getString(R.string.dow_thu),
        "fri" to context.getString(R.string.dow_fri),
        "sat" to context.getString(R.string.dow_sat),
        "sun" to context.getString(R.string.dow_sun),
    )
    val lower = s.lowercase(Locale.ROOT)

    // "mon..fri 23:00" / "sun 02:00"
    Regex("""^([a-z]{3})(?:\.\.([a-z]{3}))?\s+(\d{1,2}:\d{2})(?::\d{2})?$""")
        .matchEntire(lower)
        ?.let { m ->
            val from = dayMap[m.groupValues[1]] ?: m.groupValues[1]
            val to = m.groupValues[2].takeIf { it.isNotBlank() }?.let { dayMap[it] ?: it }
            val time = m.groupValues[3]
            return if (to != null) {
                context.getString(R.string.backups_schedule_range, from, to, time)
            } else {
                context.getString(R.string.backups_schedule_on_day, from, time)
            }
        }

    // "*-*-* 02:00:00" daily
    Regex("""^\*-\*-\*\s+(\d{1,2}:\d{2})(?::\d{2})?$""")
        .matchEntire(lower)
        ?.let { m ->
            return context.getString(R.string.backups_schedule_daily, m.groupValues[1])
        }

    // fallback: keep original, lightly cleaned
    return s
}

fun formatBackupSelection(
    job: BackupScheduleJob,
    guests: List<BackupOverviewGuest>,
    context: Context,
): String {
    if (job.allGuests) return context.getString(R.string.backups_schedule_all_guests)
    job.pool?.takeIf { it.isNotBlank() }?.let {
        return context.getString(R.string.backups_schedule_pool, it)
    }
    val ids = job.selection
        ?.split(',', ';', ' ')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.distinct()
        .orEmpty()
    if (ids.isEmpty()) return context.getString(R.string.backups_schedule_selection_unknown)
    val byId = guests.associateBy { it.vmid }
    val names = ids.map { id ->
        byId[id]?.name?.takeIf { it.isNotBlank() } ?: id.toString()
    }
    return when {
        names.size <= 3 -> context.getString(
            R.string.backups_schedule_guests_named,
            names.joinToString(", "),
        )
        else -> context.getString(
            R.string.backups_schedule_guests_count,
            names.size,
            names.take(2).joinToString(", "),
        )
    }
}

fun formatBackupMode(job: BackupScheduleJob, context: Context): String {
    val mode = when (job.mode?.lowercase()) {
        "snapshot" -> context.getString(R.string.backups_mode_snapshot)
        "suspend" -> context.getString(R.string.backups_mode_suspend)
        "stop" -> context.getString(R.string.backups_mode_stop)
        null, "" -> null
        else -> job.mode
    }
    val compress = job.compress?.takeIf { it.isNotBlank() }
    return listOfNotNull(mode, compress).joinToString(" · ")
        .ifBlank { context.getString(R.string.empty_dash) }
}

package com.proxpanel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Error
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.proxpanel.app.R
import com.proxpanel.app.jobs.ActiveJob
import com.proxpanel.app.jobs.AppToast
import com.proxpanel.app.jobs.JobKind
import com.proxpanel.app.jobs.JobSnapshot
import com.proxpanel.app.jobs.JobTracker
import com.proxpanel.app.jobs.ToastKind
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.util.formatDuration
import kotlin.math.roundToInt

@Composable
fun JobProgressBar(
    progress: Float?,
    running: Boolean,
    failed: Boolean,
    ok: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = when {
        failed -> ProxBad
        ok -> ProxGood
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track),
    ) {
        if (progress == null && running) {
            IndeterminateSweep(color = color)
        } else {
            val pct = ((progress ?: if (failed) 100f else 8f) / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun IndeterminateSweep(color: Color) {
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0) }
    val transition = rememberInfiniteTransition(label = "job-sweep")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep-t",
    )
    val barWidth = (widthPx * 0.4f).roundToInt().coerceAtLeast(1)
    val travel = (widthPx - barWidth).coerceAtLeast(0)
    val x = (t * travel).roundToInt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .onSizeChanged { widthPx = it.width },
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x, 0) }
                .width(with(density) { barWidth.toDp() })
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

@Composable
fun ActiveJobsBanner(
    tracker: JobTracker,
    onOpenTasks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val jobs by tracker.jobs.collectAsState()
    val snaps by tracker.snapshots.collectAsState()
    if (jobs.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            if (jobs.size == 1) {
                val job = jobs.first()
                val snap = snaps[job.id] ?: JobSnapshot(running = true)
                JobBannerRow(
                    job = job,
                    snap = snap,
                    onDismiss = { tracker.dismissJob(job.id) },
                    onOpenTasks = onOpenTasks,
                )
            } else {
                MultiJobBanner(
                    jobs = jobs,
                    snaps = snaps,
                    onDismiss = tracker::dismissJob,
                    onOpenTasks = onOpenTasks,
                )
            }
        }
    }
}

@Composable
private fun MultiJobBanner(
    jobs: List<ActiveJob>,
    snaps: Map<String, JobSnapshot>,
    onDismiss: (String) -> Unit,
    onOpenTasks: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    val list = jobs.mapNotNull { snaps[it.id] }
    val runningCount = list.count { it.running }.coerceAtLeast(
        if (list.size < jobs.size) jobs.size - list.count { !it.running } else 0,
    )
    val activeRunning = list.any { it.running } || list.size < jobs.size
    val failedCount = list.count { it.failed }
    val doneCount = list.count { it.ok }
    val progresses = list.mapNotNull { it.progress }
    val avgProgress = if (progresses.isNotEmpty()) progresses.average().toFloat() else null
    val allFailed = list.size == jobs.size && failedCount == jobs.size && !activeRunning

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = ProxSpace.screen, vertical = ProxSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JobStatusIcon(running = activeRunning, failed = allFailed)
            Spacer(Modifier.width(ProxSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            activeRunning -> if (runningCount == 1) {
                                stringResource(R.string.jobs_active_one)
                            } else {
                                stringResource(R.string.jobs_active_many, runningCount.coerceAtLeast(1))
                            }
                            failedCount > 0 -> if (failedCount == 1) {
                                stringResource(R.string.jobs_failed_one)
                            } else {
                                stringResource(R.string.jobs_failed_many, failedCount)
                            }
                            else -> {
                                val n = doneCount.coerceAtLeast(jobs.size)
                                if (n == 1) {
                                    stringResource(R.string.jobs_finished_one)
                                } else {
                                    stringResource(R.string.jobs_finished_many, n)
                                }
                            }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!expanded) {
                    Spacer(Modifier.height(ProxSpace.sm))
                    JobProgressBar(
                        progress = avgProgress,
                        running = activeRunning,
                        failed = allFailed,
                        ok = !activeRunning && !allFailed,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = jobs.first().title + if (jobs.size > 1) {
                            " · " + stringResource(R.string.jobs_more, jobs.size - 1)
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onOpenTasks != null) {
                TextButton(onClick = onOpenTasks) {
                    Text(stringResource(R.string.nav_tasks))
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                jobs.forEach { job ->
                    val snap = snaps[job.id] ?: JobSnapshot(running = true)
                    JobBannerRow(
                        job = job,
                        snap = snap,
                        onDismiss = { onDismiss(job.id) },
                        onOpenTasks = null,
                        compact = true,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun JobBannerRow(
    job: ActiveJob,
    snap: JobSnapshot,
    onDismiss: () -> Unit,
    onOpenTasks: (() -> Unit)?,
    compact: Boolean = false,
) {
    val phase = when {
        snap.running -> "run"
        snap.failed -> "fail"
        else -> "ok"
    }
    val title = if (snap.running) job.title else localizedKindLabel(job.kind, phase)
    val pulse = rememberInfiniteTransition(label = "job-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-a",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ProxSpace.screen,
                vertical = if (compact) ProxSpace.sm else ProxSpace.md,
            ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.graphicsLayer { alpha = if (snap.running) pulseAlpha else 1f }) {
                JobStatusIcon(running = snap.running, failed = snap.failed)
            }
            Spacer(Modifier.width(ProxSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = job.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(ProxSpace.sm))
                JobProgressBar(
                    progress = snap.progress,
                    running = snap.running,
                    failed = snap.failed,
                    ok = snap.ok,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = localizedJobMeta(snap, job),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (snap.etaSec != null) {
                        Text(
                            text = stringResource(R.string.job_eta_left, formatDuration(snap.etaSec)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!snap.latestLog.isNullOrBlank() && (snap.running || snap.failed)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = snap.latestLog,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (snap.failed) ProxBad else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_dismiss))
            }
        }
        if (onOpenTasks != null) {
            TextButton(onClick = onOpenTasks) { Text(stringResource(R.string.jobs_open_tasks)) }
        }
    }
}

@Composable
private fun JobStatusIcon(running: Boolean, failed: Boolean) {
    when {
        running -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        failed -> Icon(Icons.Default.Error, contentDescription = null, tint = ProxBad, modifier = Modifier.size(20.dp))
        else -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ProxGood, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun JobToastHost(
    tracker: JobTracker,
    modifier: Modifier = Modifier,
) {
    val toasts by tracker.toasts.collectAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProxSpace.screen),
        verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        toasts.takeLast(3).forEach { toast ->
            JobToastChip(toast = toast, onDismiss = { tracker.dismissToast(toast.id) })
        }
    }
}

@Composable
private fun JobToastChip(toast: AppToast, onDismiss: () -> Unit) {
    val border = when (toast.kind) {
        ToastKind.Ok -> ProxGood.copy(alpha = 0.35f)
        ToastKind.Err -> ProxBad.copy(alpha = 0.35f)
        ToastKind.Info -> MaterialTheme.colorScheme.outline
    }
    val fg = when (toast.kind) {
        ToastKind.Ok -> ProxGood
        ToastKind.Err -> ProxBad
        ToastKind.Info -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.clickable(onClick = onDismiss),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, border),
        shadowElevation = 6.dp,
    ) {
        Text(
            text = toast.text,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun GuestBackupProgressBlock(
    progress: Float?,
    label: String,
    failed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = if (failed) ProxBad else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!failed) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (failed) {
                        stringResource(R.string.backup_failed)
                    } else {
                        stringResource(R.string.backup_in_progress)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        JobProgressBar(
            progress = if (failed) null else progress,
            running = !failed,
            failed = failed,
            ok = false,
            modifier = Modifier.height(6.dp),
        )
    }
}

@Composable
private fun localizedKindLabel(kind: JobKind, phase: String): String {
    val base = when (kind) {
        JobKind.Backup -> "Backup"
        JobKind.Restore -> "Restore"
        JobKind.Create -> "Create"
        JobKind.Task -> "Task"
    }
    return when (phase) {
        "ok" -> stringResource(R.string.job_kind_finished, base)
        "fail" -> stringResource(R.string.job_kind_failed, base)
        else -> base
    }
}

@Composable
private fun localizedJobMeta(snap: JobSnapshot, job: ActiveJob): String = when {
    snap.running && snap.progress != null ->
        "${snap.progress.toInt()}% · ${formatDuration(snap.elapsedSec)}"
    snap.running && job.upid.isNotBlank() ->
        stringResource(R.string.job_meta_in_progress, formatDuration(snap.elapsedSec))
    snap.running ->
        stringResource(R.string.job_meta_starting, formatDuration(snap.elapsedSec))
    snap.failed ->
        stringResource(R.string.job_meta_failed_after, formatDuration(snap.elapsedSec)) +
            if (snap.exitstatus.isNotBlank()) " · ${snap.exitstatus}" else ""
    else -> stringResource(R.string.job_meta_done_in, formatDuration(snap.elapsedSec))
}

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    barHeight: androidx.compose.ui.unit.Dp = 4.dp,
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
            .height(barHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(track),
    ) {
        if (progress == null && running) {
            IndeterminateSweep(color = color, barHeight = barHeight)
        } else {
            val pct = ((progress ?: if (failed) 100f else 8f) / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(barHeight)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun IndeterminateSweep(
    color: Color,
    barHeight: androidx.compose.ui.unit.Dp = 4.dp,
) {
    val scrolling = com.proxpanel.app.ui.util.LocalListScrollSession.current.scrolling
    if (scrolling) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.45f)),
        )
        return
    }
    AnimatedIndeterminateSweep(color = color, barHeight = barHeight)
}

@Composable
private fun AnimatedIndeterminateSweep(
    color: Color,
    barHeight: androidx.compose.ui.unit.Dp = 4.dp,
) {
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
            .height(barHeight)
            .onSizeChanged { widthPx = it.width },
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x, 0) }
                .width(with(density) { barWidth.toDp() })
                .height(barHeight)
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
    val snapsLive by tracker.snapshots.collectAsState()
    val scrolling = com.proxpanel.app.ui.util.LocalListScrollSession.current.scrolling
    var frozenSnaps by remember { mutableStateOf(snapsLive) }
    LaunchedEffect(scrolling, snapsLive) {
        if (!scrolling) frozenSnaps = snapsLive
    }
    val snaps = if (scrolling) frozenSnaps else snapsLive

    // UI-only hide — jobs keep running in the background.
    var hiddenIds by remember { mutableStateOf(emptySet<String>()) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(jobs.map { it.id }) {
        if (jobs.isEmpty()) {
            hiddenIds = emptySet()
            expanded = false
        } else {
            // Drop stale hide markers; keep hide for still-running dismissed UI.
            hiddenIds = hiddenIds.intersect(jobs.map { it.id }.toSet())
        }
    }
    val visible = jobs.filter { it.id !in hiddenIds }
    if (visible.isEmpty()) return

    val primary = visible.first()
    val primarySnap = snaps[primary.id] ?: JobSnapshot(running = true)
    val list = visible.mapNotNull { snaps[it.id] }
    val activeRunning = list.any { it.running } || list.size < visible.size
    val failedCount = list.count { it.failed }
    val allFailed = list.size == visible.size && failedCount == visible.size && !activeRunning
    val progresses = list.mapNotNull { it.progress }
    val avgProgress = when {
        progresses.isNotEmpty() -> progresses.average().toFloat()
        else -> primarySnap.progress
    }
    val headline = when {
        visible.size == 1 -> if (primarySnap.running) primary.title else localizedKindLabel(
            primary.kind,
            when {
                primarySnap.failed -> "fail"
                else -> "ok"
            },
        )
        activeRunning -> stringResource(R.string.jobs_active_many, visible.size)
        failedCount > 0 -> stringResource(R.string.jobs_failed_many, failedCount)
        else -> stringResource(R.string.jobs_finished_many, visible.size)
    }
    val pctLabel = avgProgress?.let { "${it.toInt()}%" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProxSpace.screen, vertical = ProxSpace.xs),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JobStatusIcon(running = activeRunning, failed = allFailed)
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!expanded) {
                        Text(
                            text = buildString {
                                append(primary.detail.ifBlank { primary.title })
                                if (pctLabel != null) {
                                    append(" · ")
                                    append(pctLabel)
                                }
                                if (visible.size > 1) {
                                    append(" ")
                                    append(stringResource(R.string.jobs_more, visible.size - 1))
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.jobs_collapse else R.string.jobs_expand,
                        ),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = {
                        hiddenIds = hiddenIds + visible.map { it.id }
                        expanded = false
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.jobs_hide_banner),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (!expanded) {
                JobProgressBar(
                    progress = avgProgress,
                    running = activeRunning,
                    failed = allFailed,
                    ok = !activeRunning && !allFailed,
                    barHeight = 3.dp,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .padding(bottom = 6.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    visible.forEach { job ->
                        val snap = snaps[job.id] ?: JobSnapshot(running = true)
                        CompactJobDetails(job = job, snap = snap)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (onOpenTasks != null) {
                        TextButton(
                            onClick = onOpenTasks,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.jobs_open_tasks))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactJobDetails(job: ActiveJob, snap: JobSnapshot) {
    val title = if (snap.running) job.title else localizedKindLabel(
        job.kind,
        when {
            snap.failed -> "fail"
            else -> "ok"
        },
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = job.detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        JobProgressBar(
            progress = snap.progress,
            running = snap.running,
            failed = snap.failed,
            ok = snap.ok,
            barHeight = 3.dp,
        )
        Spacer(Modifier.height(2.dp))
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
            Text(
                text = snap.latestLog,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (snap.failed) ProxBad else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        JobKind.Migrate -> "Migrate"
        JobKind.Clone -> "Clone"
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

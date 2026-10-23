package com.proxpanel.app.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.proxpanel.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.data.PveTask
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.theme.ProxWarn
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filtered = remember(state.tasks, state.query, state.guests) { viewModel.filtered() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ProxTopBar(
                title = stringResource(R.string.tasks_title),
                subtitle = if (state.tasks.isNotEmpty()) {
                    val running = state.tasks.count { taskOutcome(it) == TaskOutcome.Running }
                    val base = if (filtered.size != state.tasks.size) {
                        stringResource(R.string.tasks_subtitle_filtered, filtered.size, state.tasks.size)
                    } else {
                        stringResource(R.string.tasks_subtitle_entries, filtered.size)
                    }
                    if (running > 0) {
                        base + stringResource(R.string.tasks_subtitle_running, running)
                    } else {
                        base
                    }
                } else {
                    null
                },
                embeddedInShell = true,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ProxSearchBar(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.tasks_search_placeholder),
            )
            Spacer(Modifier.height(ProxSpace.sm))
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.tasks.isEmpty() -> ProxLoadingState(label = stringResource(R.string.tasks_loading))
                    filtered.isEmpty() -> ProxEmptyState(
                        title = state.error ?: if (state.query.isNotBlank()) stringResource(R.string.empty_no_results) else stringResource(R.string.tasks_empty),
                        subtitle = stringResource(R.string.tasks_empty_subtitle),
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                    )
                    else -> {
                        LazyColumn(
                            contentPadding = ProxListPadding,
                            verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                        ) {
                            items(filtered, key = { it.upid }) { task ->
                                TaskCard(
                                    task = task,
                                    guests = state.guests,
                                    expanded = state.expandedUpid == task.upid,
                                    logLoading = state.expandedUpid == task.upid && state.logLoading,
                                    logLines = if (state.expandedUpid == task.upid) state.logLines else emptyList(),
                                    onToggle = { viewModel.toggleExpand(task) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: PveTask,
    guests: Map<String, TaskGuestRef>,
    expanded: Boolean,
    logLoading: Boolean,
    logLines: List<String>,
    onToggle: () -> Unit,
) {
    val copy = remember(task, guests) { describeTask(task, guests) }
    val rawDuration = formatTaskDuration(task, copy.outcome == TaskOutcome.Running)
    val duration = if (copy.outcome == TaskOutcome.Running && rawDuration != null) {
        stringResource(R.string.task_duration_so_far, rawDuration)
    } else {
        rawDuration
    }
    val whenLabel = task.starttime?.let {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000))
    }
    val meta = listOfNotNull(
        copy.targetTag,
        copy.node,
        duration,
        copy.userLabel,
        whenLabel,
    )

    ProxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(ProxSpace.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                ) {
                    Text(
                        copy.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            copy.kindLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(ProxSpace.xs))
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                copy.statusNote?.let {
                    Spacer(Modifier.height(ProxSpace.xs))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = ProxBad)
                }
            }
            Spacer(Modifier.width(ProxSpace.sm))
            OutcomeBadge(copy.outcome)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = ProxSpace.md)) {
                when {
                    logLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(ProxSpace.sm))
                            Text(stringResource(R.string.tasks_log_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    logLines.isEmpty() -> Text(
                        stringResource(R.string.tasks_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(ProxSpace.md)) {
                                logLines.takeLast(40).forEach { line ->
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeBadge(outcome: TaskOutcome) {
    val (bg, fg, label) = when (outcome) {
        TaskOutcome.Running -> Triple(ProxWarn.copy(alpha = 0.18f), ProxWarn, stringResource(R.string.task_outcome_running))
        TaskOutcome.Fail -> Triple(ProxBad.copy(alpha = 0.18f), ProxBad, stringResource(R.string.task_outcome_failed))
        TaskOutcome.Ok -> Triple(ProxGood.copy(alpha = 0.18f), ProxGood, stringResource(R.string.task_outcome_done))
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            when (outcome) {
                TaskOutcome.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = fg,
                )
                TaskOutcome.Fail -> Icon(Icons.Default.Error, null, tint = fg, modifier = Modifier.size(12.dp))
                TaskOutcome.Ok -> Icon(Icons.Default.CheckCircle, null, tint = fg, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

private fun formatTaskDuration(task: PveTask, running: Boolean): String? {
    val start = task.starttime ?: return null
    val end = if (running) System.currentTimeMillis() / 1000 else task.endtime ?: return null
    val sec = (end - start).coerceAtLeast(0)
    val text = when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s"
        else -> {
            val h = TimeUnit.SECONDS.toHours(sec)
            val m = TimeUnit.SECONDS.toMinutes(sec) % 60
            "${h}h ${m}m"
        }
    }
    return text
}

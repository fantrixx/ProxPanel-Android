package com.proxpanel.app.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.R
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.data.PveTask
import com.proxpanel.app.ui.components.AppearanceDialog
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
import com.proxpanel.app.ui.util.rememberAppHaptics
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val filtered = remember(
        state.tasks,
        state.query,
        state.guests,
        state.statusFilter,
        state.kindFilter,
    ) { viewModel.filtered() }
    val filtersActive = state.statusFilter != TaskStatusFilter.All ||
        state.kindFilter != TaskKindFilter.All ||
        state.query.isNotBlank()
    var appearanceOpen by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scrollSession = com.proxpanel.app.ui.util.LocalListScrollSession.current
    com.proxpanel.app.ui.util.rememberListScrollFreeze(
        listState = listState,
        gridState = gridState,
        useGrid = state.viewMode == NodesViewMode.Grid,
        onScrolling = scrollSession::update,
    )

    if (appearanceOpen) {
        AppearanceDialog(
            viewMode = state.viewMode,
            onViewMode = {
                haptics.tick()
                viewModel.setViewMode(it)
            },
            onDismiss = { appearanceOpen = false },
        )
    }

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
                infoBody = stringResource(R.string.info_tasks),
                embeddedInShell = true,
                actions = {
                    IconButton(onClick = { appearanceOpen = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.cd_view_options))
                    }
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
            com.proxpanel.app.ui.components.ActiveJobsBelowTitle()
            ProxSearchBar(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.tasks_search_placeholder),
            )
            TaskFilterRow(
                statusFilter = state.statusFilter,
                kindFilter = state.kindFilter,
                onStatus = {
                    haptics.tick()
                    viewModel.setStatusFilter(it)
                },
                onKind = {
                    haptics.tick()
                    viewModel.setKindFilter(it)
                },
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
                        title = when {
                            state.tasks.isEmpty() -> state.error ?: stringResource(R.string.tasks_empty)
                            filtersActive -> stringResource(R.string.tasks_empty_filtered)
                            else -> stringResource(R.string.tasks_empty)
                        },
                        subtitle = if (state.tasks.isEmpty()) {
                            stringResource(R.string.tasks_empty_subtitle)
                        } else {
                            stringResource(R.string.adjust_filters_hint)
                        },
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                        action = if (filtersActive && state.tasks.isNotEmpty()) {
                            {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        haptics.tick()
                                        viewModel.clearFilters()
                                    },
                                    label = { Text(stringResource(R.string.show_all)) },
                                    shape = MaterialTheme.shapes.small,
                                )
                            }
                        } else {
                            null
                        },
                    )
                    else -> {
                        when (state.viewMode) {
                            NodesViewMode.Cards -> {
                                LazyColumn(
                                    state = listState,
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
                                            dense = false,
                                            onToggle = { viewModel.toggleExpand(task) },
                                        )
                                    }
                                }
                            }
                            NodesViewMode.Grid -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    state = gridState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    gridItems(filtered, key = { it.upid }) { task ->
                                        TaskCard(
                                            task = task,
                                            guests = state.guests,
                                            expanded = state.expandedUpid == task.upid,
                                            logLoading = state.expandedUpid == task.upid && state.logLoading,
                                            logLines = if (state.expandedUpid == task.upid) state.logLines else emptyList(),
                                            dense = true,
                                            onToggle = { viewModel.toggleExpand(task) },
                                        )
                                    }
                                }
                            }
                            NodesViewMode.Compact -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                                ) {
                                    items(filtered, key = { it.upid }) { task ->
                                        TaskCompactRow(
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
    }
}

@Composable
private fun TaskFilterRow(
    statusFilter: TaskStatusFilter,
    kindFilter: TaskKindFilter,
    onStatus: (TaskStatusFilter) -> Unit,
    onKind: (TaskKindFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ProxSpace.screen, vertical = ProxSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            listOf(TaskStatusFilter.Running, TaskStatusFilter.Ok, TaskStatusFilter.Fail),
            key = { "status-${it.name}" },
        ) { filter ->
            val (selBg, selFg) = when (filter) {
                TaskStatusFilter.Running -> ProxWarn.copy(alpha = 0.18f) to ProxWarn
                TaskStatusFilter.Fail -> ProxBad.copy(alpha = 0.18f) to ProxBad
                TaskStatusFilter.Ok -> ProxGood.copy(alpha = 0.18f) to ProxGood
                TaskStatusFilter.All -> MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer
            }
            val label = when (filter) {
                TaskStatusFilter.Running -> stringResource(R.string.task_outcome_running)
                TaskStatusFilter.Ok -> stringResource(R.string.task_outcome_done)
                TaskStatusFilter.Fail -> stringResource(R.string.task_outcome_failed)
                TaskStatusFilter.All -> ""
            }
            FilterChip(
                selected = statusFilter == filter,
                onClick = { onStatus(filter) },
                label = { Text(label) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = selBg,
                    selectedLabelColor = selFg,
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        item(key = "filter-sep") {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        items(
            listOf(
                TaskKindFilter.Backup,
                TaskKindFilter.Restore,
                TaskKindFilter.Power,
                TaskKindFilter.Snapshot,
                TaskKindFilter.Migrate,
                TaskKindFilter.Clone,
                TaskKindFilter.Host,
            ),
            key = { "kind-${it.name}" },
        ) { filter ->
            val label = when (filter) {
                TaskKindFilter.Backup -> stringResource(R.string.task_kind_backup)
                TaskKindFilter.Restore -> stringResource(R.string.task_kind_restore)
                TaskKindFilter.Power -> stringResource(R.string.task_kind_power)
                TaskKindFilter.Snapshot -> stringResource(R.string.task_kind_snapshot)
                TaskKindFilter.Migrate -> stringResource(R.string.task_kind_migrate)
                TaskKindFilter.Clone -> stringResource(R.string.task_kind_clone)
                TaskKindFilter.Host -> stringResource(R.string.task_kind_host)
                TaskKindFilter.All -> ""
            }
            FilterChip(
                selected = kindFilter == filter,
                onClick = { onKind(filter) },
                label = { Text(label) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
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
    dense: Boolean,
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
        if (dense) null else copy.userLabel,
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
                        style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        maxLines = if (dense) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
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
                    maxLines = if (dense) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                copy.statusNote?.let {
                    Spacer(Modifier.height(ProxSpace.xs))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = ProxBad, maxLines = 2)
                }
            }
            Spacer(Modifier.width(ProxSpace.sm))
            OutcomeBadge(copy.outcome)
        }

        AnimatedVisibility(visible = expanded) {
            TaskLogBlock(logLoading = logLoading, logLines = logLines)
        }
    }
}

@Composable
private fun TaskCompactRow(
    task: PveTask,
    guests: Map<String, TaskGuestRef>,
    expanded: Boolean,
    logLoading: Boolean,
    logLines: List<String>,
    onToggle: () -> Unit,
) {
    val copy = remember(task, guests) { describeTask(task, guests) }
    val whenLabel = task.starttime?.let {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000))
    }
    ProxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(ProxSpace.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    copy.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(copy.targetTag, copy.node, whenLabel).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutcomeBadge(copy.outcome)
        }
        AnimatedVisibility(visible = expanded) {
            TaskLogBlock(logLoading = logLoading, logLines = logLines)
        }
    }
}

@Composable
private fun TaskLogBlock(logLoading: Boolean, logLines: List<String>) {
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

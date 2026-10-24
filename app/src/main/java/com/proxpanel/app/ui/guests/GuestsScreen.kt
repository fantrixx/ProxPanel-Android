package com.proxpanel.app.ui.guests

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.proxpanel.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.data.GuestIconRecord
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.data.GuestRates
import com.proxpanel.app.data.guestIconKey
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.jobs.GuestJobProgress
import com.proxpanel.app.jobs.JobTracker
import com.proxpanel.app.ui.components.AppearanceDialog
import com.proxpanel.app.ui.components.ClusterOverviewBar
import com.proxpanel.app.ui.components.GuestBackupProgressBlock
import com.proxpanel.app.ui.components.GuestLogo
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.MetricBar
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.components.CpuSparkline
import com.proxpanel.app.ui.components.StatusBadge
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxCt
import com.proxpanel.app.ui.theme.ProxCtContainer
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxMuted
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.theme.ProxVm
import com.proxpanel.app.ui.theme.ProxVmContainer
import com.proxpanel.app.ui.theme.ProxWarn
import com.proxpanel.app.ui.util.rememberAppHaptics
import com.proxpanel.app.util.cpuPct
import com.proxpanel.app.util.formatBytes
import com.proxpanel.app.util.formatBytesRate
import com.proxpanel.app.util.formatPct
import com.proxpanel.app.util.formatUptime
import com.proxpanel.app.util.guestVisualStatus
import com.proxpanel.app.util.isTransitioningStatus
import com.proxpanel.app.util.pendingLabelForAction
import com.proxpanel.app.util.usagePct

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GuestsScreen(
    viewModel: GuestsViewModel,
    jobTracker: JobTracker,
    onOpenGuest: (GuestItem) -> Unit,
    onCreateGuest: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val jobs by jobTracker.jobs.collectAsState()
    val jobSnaps by jobTracker.snapshots.collectAsState()
    var query by remember { mutableStateOf("") }
    val kindFilter = state.kindFilter
    val statusFilter = state.statusFilter
    val guests = remember(state.guests, kindFilter, statusFilter, query) {
        filterGuests(state.guests, kindFilter, statusFilter, query)
    }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var sheetGuest by remember { mutableStateOf<GuestItem?>(null) }
    var iconPickerGuest by remember { mutableStateOf<GuestItem?>(null) }
    var appearanceOpen by remember { mutableStateOf(false) }

    fun iconFor(guest: GuestItem): GuestIconRecord? =
        state.guestIcons[guestIconKey(guest.node, guest.type, guest.vmid)]
    val haptics = rememberAppHaptics()
    val filtersActive =
        kindFilter != GuestKindFilter.All || statusFilter != GuestStatusFilter.All || query.isNotBlank()
    val gridState = rememberLazyGridState()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfEmpty()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    state.confirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(confirm.title) },
            text = { Text(confirm.body) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmPower,
                    colors = if (confirm.danger) {
                        ButtonDefaults.textButtonColors(contentColor = ProxBad)
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                ) { Text(confirm.confirm) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.action_cancel)) }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

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

    iconPickerGuest?.let { g ->
        GuestIconPicker(
            guest = g,
            current = iconFor(g),
            onDismiss = { iconPickerGuest = null },
            onSave = { record ->
                viewModel.setGuestIcon(g, record)
                iconPickerGuest = null
            },
        )
    }

    sheetGuest?.let { g ->
        GuestQuickSheet(
            guest = g,
            iconRecord = iconFor(g),
            storages = state.backupStorages,
            pending = state.pendingPower[g.key],
            backup = viewModel.backupProgress(g),
            busy = state.busyKey?.startsWith(g.key) == true,
            onDismiss = { sheetGuest = null },
            onOpenDetail = { onOpenGuest(g) },
            onChangeLogo = {
                sheetGuest = null
                iconPickerGuest = g
            },
            onPower = { action ->
                sheetGuest = null
                haptics.tick()
                viewModel.requestPower(g, action)
            },
            onBackup = { storage ->
                sheetGuest = null
                haptics.confirm()
                viewModel.startBackup(g, storage)
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGuest,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_create_vm_ct))
            }
        },
        topBar = {
            ProxTopBar(
                title = stringResource(R.string.guests_title),
                subtitle = state.user?.let {
                    "${it.username} · ${it.host.removePrefix("https://").removePrefix("http://")}"
                },
                embeddedInShell = true,
                actions = {
                    IconButton(onClick = { appearanceOpen = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.cd_view_options))
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { viewModel.logout(onLogout) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cd_logout))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProxSearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.guests_search_placeholder),
                )

                if (state.guests.isNotEmpty() || state.cluster.nodesTotal > 0) {
                    Spacer(Modifier.height(ProxSpace.sm))
                    ClusterOverviewBar(
                        overview = state.cluster,
                        pulsing = state.refreshPulse || state.refreshing,
                    )
                }

                if (state.guests.isNotEmpty()) {
                    val filtersDefault =
                        kindFilter == GuestKindFilter.All && statusFilter == GuestStatusFilter.All
                    Text(
                        text = if (query.isBlank() && filtersDefault) {
                            stringResource(R.string.guests_count, state.guests.size)
                        } else {
                            stringResource(R.string.guests_count_filtered, guests.size, state.guests.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ProxSpace.screen + 4.dp, vertical = ProxSpace.sm),
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = ProxSpace.screen, vertical = ProxSpace.xs),
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(
                        listOf(GuestKindFilter.Ct, GuestKindFilter.Vm),
                        key = { "kind-${it.name}" },
                    ) { filter ->
                        val (selBg, selFg) = when (filter) {
                            GuestKindFilter.Vm -> ProxVmContainer to ProxVm
                            GuestKindFilter.Ct -> ProxCtContainer to ProxCt
                            GuestKindFilter.All -> MaterialTheme.colorScheme.primaryContainer to
                                MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        FilterChip(
                            selected = kindFilter == filter,
                            onClick = { viewModel.setKindFilter(filter) },
                            label = {
                                Text(if (filter == GuestKindFilter.Ct) "CT" else "VM")
                            },
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
                        listOf(GuestStatusFilter.Running, GuestStatusFilter.Stopped),
                        key = { "status-${it.name}" },
                    ) { filter ->
                        val (selBg, selFg) = when (filter) {
                            GuestStatusFilter.Running -> ProxGood.copy(alpha = 0.18f) to ProxGood
                            GuestStatusFilter.Stopped -> ProxMuted.copy(alpha = 0.18f) to ProxMuted
                            GuestStatusFilter.All -> MaterialTheme.colorScheme.primaryContainer to
                                MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        FilterChip(
                            selected = statusFilter == filter,
                            onClick = { viewModel.setStatusFilter(filter) },
                            label = {
                                Text(if (filter == GuestStatusFilter.Running) stringResource(R.string.status_running) else stringResource(R.string.status_stopped))
                            },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = selBg,
                                selectedLabelColor = selFg,
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }

                when {
                    state.loading && state.guests.isEmpty() -> ProxLoadingState(label = stringResource(R.string.guests_loading))
                    guests.isEmpty() -> ProxEmptyState(
                        title = when {
                            state.guests.isEmpty() -> state.error ?: stringResource(R.string.guests_empty_none)
                            statusFilter == GuestStatusFilter.Running -> stringResource(R.string.guests_empty_running)
                            statusFilter == GuestStatusFilter.Stopped -> stringResource(R.string.guests_empty_stopped)
                            kindFilter == GuestKindFilter.Vm -> stringResource(R.string.guests_empty_vms)
                            kindFilter == GuestKindFilter.Ct -> stringResource(R.string.guests_empty_cts)
                            else -> stringResource(R.string.empty_no_results)
                        },
                        subtitle = if (state.guests.isEmpty()) {
                            stringResource(R.string.pull_to_refresh_hint)
                        } else {
                            stringResource(R.string.adjust_filters_hint)
                        },
                        icon = Icons.Outlined.Dns,
                        action = if (filtersActive && state.guests.isNotEmpty()) {
                            {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        query = ""
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
                        @Suppress("UNUSED_VARIABLE")
                        val jobTick = jobs.size + jobSnaps.hashCode()
                        when (state.viewMode) {
                            NodesViewMode.Cards -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(guests, key = { "${it.type}-${it.node}-${it.vmid}" }) { guest ->
                                        GuestCard(
                                            guest = guest,
                                            iconRecord = iconFor(guest),
                                            storages = state.backupStorages,
                                            rates = state.rates[guest.key],
                                            cpuHistory = state.cpuHistory[guest.key].orEmpty(),
                                            pinned = guest.key in state.pinnedKeys,
                                            lastBackupStorage = state.lastBackupStorage,
                                            busy = state.busyKey?.startsWith(guest.key) == true,
                                            pending = state.pendingPower[guest.key],
                                            backup = viewModel.backupProgress(guest),
                                            onLogoClick = { iconPickerGuest = guest },
                                            onClick = { onOpenGuest(guest) },
                                            onLongClick = {
                                                haptics.longPress()
                                                sheetGuest = guest
                                            },
                                            onTogglePin = {
                                                haptics.tick()
                                                viewModel.togglePinned(guest)
                                            },
                                            onPower = { action ->
                                                haptics.tick()
                                                viewModel.requestPower(guest, action)
                                            },
                                            onBackup = { storage ->
                                                haptics.confirm()
                                                viewModel.startBackup(guest, storage)
                                            },
                                            onQuickBackup = {
                                                haptics.confirm()
                                                viewModel.quickBackup(guest)
                                            },
                                        )
                                    }
                                }
                            }
                            NodesViewMode.Grid -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    state = gridState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    gridItems(guests, key = { "${it.type}-${it.node}-${it.vmid}" }) { guest ->
                                        GuestTile(
                                            guest = guest,
                                            iconRecord = iconFor(guest),
                                            pending = state.pendingPower[guest.key],
                                            backup = viewModel.backupProgress(guest),
                                            pinned = guest.key in state.pinnedKeys,
                                            busy = state.busyKey?.startsWith(guest.key) == true,
                                            onLogoClick = { iconPickerGuest = guest },
                                            onClick = { onOpenGuest(guest) },
                                            onLongClick = {
                                                haptics.longPress()
                                                sheetGuest = guest
                                            },
                                            onPower = { action ->
                                                haptics.tick()
                                                viewModel.requestPower(guest, action)
                                            },
                                            onQuickBackup = {
                                                haptics.confirm()
                                                if (!viewModel.quickBackup(guest)) {
                                                    sheetGuest = guest
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            NodesViewMode.Compact -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(guests, key = { "${it.type}-${it.node}-${it.vmid}" }) { guest ->
                                        GuestCompactRow(
                                            guest = guest,
                                            iconRecord = iconFor(guest),
                                            pending = state.pendingPower[guest.key],
                                            backup = viewModel.backupProgress(guest),
                                            pinned = guest.key in state.pinnedKeys,
                                            busy = state.busyKey?.startsWith(guest.key) == true,
                                            onLogoClick = { iconPickerGuest = guest },
                                            onClick = { onOpenGuest(guest) },
                                            onLongClick = {
                                                haptics.longPress()
                                                sheetGuest = guest
                                            },
                                            onPower = { action ->
                                                haptics.tick()
                                                viewModel.requestPower(guest, action)
                                            },
                                            onQuickBackup = {
                                                haptics.confirm()
                                                if (!viewModel.quickBackup(guest)) {
                                                    sheetGuest = guest
                                                }
                                            },
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

private fun filterGuests(
    guests: List<GuestItem>,
    kindFilter: GuestKindFilter,
    statusFilter: GuestStatusFilter,
    query: String,
): List<GuestItem> {
    val q = query.trim().lowercase()
    return guests.filter { g ->
        val kindOk = when (kindFilter) {
            GuestKindFilter.All -> true
            GuestKindFilter.Ct -> g.type == "lxc"
            GuestKindFilter.Vm -> g.type == "qemu"
        }
        if (!kindOk) return@filter false
        val statusOk = when (statusFilter) {
            GuestStatusFilter.All -> true
            GuestStatusFilter.Running -> g.isRunning
            GuestStatusFilter.Stopped -> !g.isRunning
        }
        if (!statusOk) return@filter false
        if (q.isEmpty()) return@filter true
        g.name.lowercase().contains(q) ||
            g.vmid.toString().contains(q) ||
            g.node.lowercase().contains(q) ||
            g.status.lowercase().contains(q) ||
            g.kindLabel.lowercase().contains(q) ||
            g.ips.any { it.lowercase().contains(q) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GuestCard(
    guest: GuestItem,
    iconRecord: GuestIconRecord?,
    storages: List<String>,
    rates: GuestRates?,
    cpuHistory: List<Float>,
    pinned: Boolean,
    lastBackupStorage: String?,
    busy: Boolean,
    pending: PendingPower?,
    backup: GuestJobProgress?,
    onLogoClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onPower: (String) -> Unit,
    onBackup: (String) -> Unit,
    onQuickBackup: () -> Unit,
) {
    var backupMenuOpen by remember { mutableStateOf(false) }
    val pendingVisual = pending?.let { pendingLabelForAction(it.action) }
    val visual = guestVisualStatus(guest.status, guest.qmpstatus, guest.lock, pendingVisual)
    val transitioning = isTransitioningStatus(visual)
    val backingUp = backup?.running == true
    val backupFailed = backup?.failed == true
    val actionsLocked = busy || pending != null || backingUp
    val loadFactor = remember(guest.cpu, rates) {
        val cpu = ((guest.cpu ?: 0.0) * 100.0).coerceIn(0.0, 100.0)
        val net = ((rates?.netin ?: 0.0) + (rates?.netout ?: 0.0))
        val netScore = (net / (8 * 1024 * 1024)).coerceIn(0.0, 1.0) * 100.0 // ~8 MiB/s = full
        ((cpu * 0.7) + (netScore * 0.3)).toFloat().coerceIn(0f, 100f)
    }
    val glowTarget = when {
        backupFailed -> ProxBad.copy(alpha = 0.55f)
        backingUp || transitioning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        loadFactor >= 75f -> ProxBad.copy(alpha = 0.18f + (loadFactor - 75f) / 100f)
        loadFactor >= 45f -> ProxWarn.copy(alpha = 0.12f + (loadFactor - 45f) / 200f)
        pinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    val glow by animateColorAsState(glowTarget, tween(420), label = "card-glow")
    val topBarTarget = when {
        backupFailed -> ProxBad
        backingUp -> MaterialTheme.colorScheme.primary
        visual == "creating" || visual == "starting" || visual == "migrating" ->
            MaterialTheme.colorScheme.primary
        visual == "shutting down" || visual == "stopping" || visual == "rebooting" -> ProxWarn
        else -> Color.Transparent
    }
    val topBarColor by animateColorAsState(topBarTarget, tween(360), label = "top-bar")
    val canQuickBackup =
        (lastBackupStorage != null && lastBackupStorage in storages) || storages.size == 1

    ProxCard(borderColor = glow, borderWidth = if (glow.alpha > 0.02f) 1.5.dp else 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (backingUp || backupFailed || transitioning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(topBarColor.copy(alpha = if (backingUp && backup?.progress == null) 0.35f else 0.85f)),
                    ) {
                        if (backingUp && !backupFailed) {
                            val pct = ((backup?.progress ?: 33f) / 100f).coerceIn(0.08f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(pct)
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Spacer(Modifier.height(ProxSpace.sm))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                ) {
                    StatusBadge(
                        status = guest.status,
                        qmpstatus = guest.qmpstatus,
                        lock = guest.lock,
                        pending = pendingVisual,
                    )
                    KindChip(text = guest.kindLabel, kind = guest.type)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (pinned) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (transitioning && !backingUp) {
                    Spacer(Modifier.height(ProxSpace.xs))
                    TransitionChip(
                        text = if (visual == "creating") stringResource(R.string.guests_transition_setting_up) else visual,
                        accent = visual == "creating" || visual == "starting" || visual == "migrating",
                        warn = visual == "shutting down" || visual == "stopping" || visual == "rebooting",
                    )
                }
                if (backingUp) {
                    Spacer(Modifier.height(ProxSpace.xs))
                    TransitionChip(
                        text = "Backup ${backup?.progress?.let { "${it.toInt()}%" } ?: "…"}",
                        accent = true,
                    )
                }
                if (backupFailed) {
                    Spacer(Modifier.height(ProxSpace.xs))
                    TransitionChip(text = stringResource(R.string.backup_failed), warn = false, bad = true)
                }

                Spacer(Modifier.height(ProxSpace.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                ) {
                    GuestLogo(
                        name = guest.name,
                        tags = guest.tags,
                        record = iconRecord,
                        type = guest.type,
                        size = 40.dp,
                        onClick = onLogoClick,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = guest.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${guest.node} · ${guest.vmid}" +
                                (guest.ips.firstOrNull()?.let { " · $it" } ?: "") +
                                if (guest.isRunning) " · ${formatUptime(guest.uptime)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (backingUp) {
                    Spacer(Modifier.height(ProxSpace.md))
                    GuestBackupProgressBlock(
                        progress = backup?.progress,
                        label = backup?.label ?: "…",
                        failed = false,
                    )
                }

                Spacer(Modifier.height(ProxSpace.lg))
                MetricBar(stringResource(R.string.metric_cpu), formatPct(guest.cpu), cpuPct(guest.cpu))
                if (cpuHistory.size >= 2) {
                    Spacer(Modifier.height(6.dp))
                    CpuSparkline(values = cpuHistory)
                }
                Spacer(Modifier.height(ProxSpace.md))
                MetricBar(
                    stringResource(R.string.metric_ram),
                    "${formatBytes(guest.mem)} / ${formatBytes(guest.maxmem)}",
                    usagePct(guest.mem, guest.maxmem),
                )
                Spacer(Modifier.height(ProxSpace.md))
                MetricBar(
                    stringResource(R.string.metric_disk),
                    "${formatBytes(guest.disk)} / ${formatBytes(guest.maxdisk)}",
                    usagePct(guest.disk, guest.maxdisk),
                )

                Spacer(Modifier.height(ProxSpace.md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = formatBytesRate(rates?.netin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = formatBytesRate(rates?.netout),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(ProxSpace.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (guest.isRunning || pending?.action == "reboot" || visual == "shutting down" || visual == "rebooting") {
                GuestActionIcon(
                    icon = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(R.string.power_shutdown),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !actionsLocked,
                    onClick = { onPower("shutdown") },
                )
                GuestActionIcon(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.power_reboot),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !actionsLocked,
                    onClick = { onPower("reboot") },
                )
                GuestActionIcon(
                    icon = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.power_force_stop),
                    tint = ProxBad,
                    enabled = !actionsLocked,
                    onClick = { onPower("stop") },
                )
            } else {
                GuestActionIcon(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.power_start),
                    tint = ProxGood,
                    enabled = !actionsLocked,
                    onClick = { onPower("start") },
                )
            }

            Spacer(Modifier.weight(1f))

            Box {
                GuestActionIcon(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = stringResource(R.string.power_backup),
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = !actionsLocked && storages.isNotEmpty(),
                    onClick = {
                        if (canQuickBackup) onQuickBackup() else backupMenuOpen = true
                    },
                    loading = backingUp,
                    progressLabel = backup?.progress?.let { "${it.toInt()}%" },
                )
                DropdownMenu(
                    expanded = backupMenuOpen,
                    onDismissRequest = { backupMenuOpen = false },
                ) {
                    storages.forEach { storage ->
                        val preferred = storage == lastBackupStorage
                        DropdownMenuItem(
                            text = {
                                Text(if (preferred) "$storage  ★" else storage)
                            },
                            onClick = {
                                backupMenuOpen = false
                                onBackup(storage)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GuestTile(
    guest: GuestItem,
    iconRecord: GuestIconRecord?,
    pending: PendingPower?,
    backup: GuestJobProgress?,
    pinned: Boolean,
    busy: Boolean,
    onLogoClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPower: (String) -> Unit,
    onQuickBackup: () -> Unit,
) {
    val pendingVisual = pending?.let { pendingLabelForAction(it.action) }
    val visual = guestVisualStatus(guest.status, guest.qmpstatus, guest.lock, pendingVisual)
    val transitioning = isTransitioningStatus(visual)
    val backingUp = backup?.running == true
    val locked = busy || pending != null || backingUp

    ProxCard(
        borderColor = when {
            backingUp -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            transitioning -> ProxWarn.copy(alpha = 0.35f)
            pinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else -> Color.Transparent
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    status = guest.status,
                    qmpstatus = guest.qmpstatus,
                    lock = guest.lock,
                    pending = pendingVisual,
                )
                Spacer(Modifier.weight(1f))
                KindChip(text = guest.kindLabel, kind = guest.type)
            }
            Spacer(Modifier.height(ProxSpace.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ProxSpace.xs),
            ) {
                GuestLogo(
                    name = guest.name,
                    tags = guest.tags,
                    record = iconRecord,
                    type = guest.type,
                    size = 32.dp,
                    onClick = onLogoClick,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = guest.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${guest.vmid} · ${guest.node}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (backingUp) {
                Spacer(Modifier.height(ProxSpace.xs))
                Text(
                    text = "Backup ${backup?.progress?.let { "${it.toInt()}%" } ?: "…"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(ProxSpace.sm))
            MetricBar(stringResource(R.string.metric_cpu), formatPct(guest.cpu), cpuPct(guest.cpu))
            Spacer(Modifier.height(ProxSpace.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (guest.isRunning || pending?.action == "reboot") {
                    GuestActionIcon(
                        icon = Icons.Default.RestartAlt,
                        contentDescription = stringResource(R.string.power_reboot),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        enabled = !locked,
                        onClick = { onPower("reboot") },
                    )
                    GuestActionIcon(
                        icon = Icons.Default.PowerSettingsNew,
                        contentDescription = "Shutdown",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        enabled = !locked,
                        onClick = { onPower("shutdown") },
                    )
                } else {
                    GuestActionIcon(
                        icon = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.power_start),
                        tint = ProxGood,
                        enabled = !locked,
                        onClick = { onPower("start") },
                    )
                }
                GuestActionIcon(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = stringResource(R.string.power_backup),
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = !locked,
                    onClick = onQuickBackup,
                    loading = backingUp,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GuestCompactRow(
    guest: GuestItem,
    iconRecord: GuestIconRecord?,
    pending: PendingPower?,
    backup: GuestJobProgress?,
    pinned: Boolean,
    busy: Boolean,
    onLogoClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPower: (String) -> Unit,
    onQuickBackup: () -> Unit,
) {
    val pendingVisual = pending?.let { pendingLabelForAction(it.action) }
    val visual = guestVisualStatus(guest.status, guest.qmpstatus, guest.lock, pendingVisual)
    val backingUp = backup?.running == true
    val locked = busy || pending != null || backingUp

    ProxCard(
        borderColor = if (pinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
        borderWidth = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuestLogo(
                name = guest.name,
                tags = guest.tags,
                record = iconRecord,
                type = guest.type,
                size = 36.dp,
                onClick = onLogoClick,
            )
            Spacer(Modifier.width(ProxSpace.xs))
            StatusBadge(
                status = guest.status,
                qmpstatus = guest.qmpstatus,
                lock = guest.lock,
                pending = pendingVisual,
            )
            Spacer(Modifier.width(ProxSpace.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = guest.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(guest.kindLabel)
                        append(' ')
                        append(guest.vmid)
                        append(" · ")
                        append(guest.node)
                        if (backingUp) {
                            append(" · Backup ")
                            append(backup?.progress?.let { "${it.toInt()}%" } ?: "…")
                        } else if (guest.isRunning) {
                            append(" · CPU ")
                            append(formatPct(guest.cpu))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (guest.isRunning || pending?.action == "reboot") {
                GuestActionIcon(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.power_reboot),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !locked,
                    onClick = { onPower("reboot") },
                )
            } else {
                GuestActionIcon(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.power_start),
                    tint = ProxGood,
                    enabled = !locked,
                    onClick = { onPower("start") },
                )
            }
            GuestActionIcon(
                icon = Icons.Default.CloudUpload,
                contentDescription = stringResource(R.string.power_backup),
                tint = MaterialTheme.colorScheme.primary,
                enabled = !locked,
                onClick = onQuickBackup,
                loading = backingUp,
            )
        }
    }
}

@Composable
private fun TransitionChip(
    text: String,
    accent: Boolean = false,
    warn: Boolean = false,
    bad: Boolean = false,
) {
    if (text.isBlank()) return
    val (bg, fg) = when {
        bad -> ProxBad.copy(alpha = 0.12f) to ProxBad
        accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.primary
        warn -> ProxWarn.copy(alpha = 0.12f) to ProxWarn
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            strokeWidth = 1.5.dp,
            color = fg,
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun GuestActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
    progressLabel: String? = null,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = 0.35f),
        ),
    ) {
        if (loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
                if (progressLabel != null) {
                    Text(progressLabel, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

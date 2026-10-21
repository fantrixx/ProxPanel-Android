package com.proxpanel.app.ui.guests

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.data.GuestRates
import com.proxpanel.app.jobs.GuestJobProgress
import com.proxpanel.app.jobs.JobTracker
import com.proxpanel.app.ui.components.GuestBackupProgressBlock
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.MetricBar
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.components.Sparkline
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
    val typeFilter = state.typeFilter
    val guests = remember(state.guests, typeFilter, query) {
        filterGuests(state.guests, typeFilter, query)
    }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var sheetGuest by remember { mutableStateOf<GuestItem?>(null) }

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
                TextButton(onClick = viewModel::dismissConfirm) { Text("Abbrechen") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    sheetGuest?.let { g ->
        GuestQuickSheet(
            guest = g,
            storages = state.backupStorages,
            pending = state.pendingPower[g.key],
            backup = viewModel.backupProgress(g),
            busy = state.busyKey?.startsWith(g.key) == true,
            onDismiss = { sheetGuest = null },
            onOpenDetail = { onOpenGuest(g) },
            onPower = { action ->
                sheetGuest = null
                viewModel.requestPower(g, action)
            },
            onBackup = { storage ->
                sheetGuest = null
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
                Icon(Icons.Default.Add, contentDescription = "VM / CT erstellen")
            }
        },
        topBar = {
            ProxTopBar(
                title = "Overview",
                subtitle = state.user?.let {
                    "${it.username} · ${it.host.removePrefix("https://").removePrefix("http://")}"
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.logout(onLogout) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Abmelden")
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
                    placeholder = "Name, VMID, IP, Node…",
                )

                if (state.guests.isNotEmpty()) {
                    Text(
                        text = if (query.isBlank() && typeFilter == GuestTypeFilter.All) {
                            "${state.guests.size} Gäste"
                        } else {
                            "${guests.size} von ${state.guests.size}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ProxSpace.screen + 4.dp, vertical = ProxSpace.sm),
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = ProxSpace.screen, vertical = ProxSpace.xs),
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                ) {
                    items(GuestTypeFilter.entries, key = { it.name }) { filter ->
                        val (selBg, selFg) = when (filter) {
                            GuestTypeFilter.Vm -> ProxVmContainer to ProxVm
                            GuestTypeFilter.Ct -> ProxCtContainer to ProxCt
                            GuestTypeFilter.Running -> ProxGood.copy(alpha = 0.18f) to ProxGood
                            GuestTypeFilter.Stopped -> ProxMuted.copy(alpha = 0.18f) to ProxMuted
                            GuestTypeFilter.All -> MaterialTheme.colorScheme.primaryContainer to
                                MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        FilterChip(
                            selected = typeFilter == filter,
                            onClick = { viewModel.setTypeFilter(filter) },
                            label = {
                                Text(
                                    when (filter) {
                                        GuestTypeFilter.All -> "Alle"
                                        GuestTypeFilter.Ct -> "CT"
                                        GuestTypeFilter.Vm -> "VM"
                                        GuestTypeFilter.Running -> "Running"
                                        GuestTypeFilter.Stopped -> "Stopped"
                                    },
                                )
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
                    state.loading && state.guests.isEmpty() -> ProxLoadingState(label = "Gäste laden…")
                    guests.isEmpty() -> ProxEmptyState(
                        title = when {
                            state.guests.isEmpty() -> state.error ?: "Keine Gäste"
                            else -> "Keine Treffer"
                        },
                        subtitle = if (state.guests.isEmpty()) {
                            "Pull to refresh oder Verbindung prüfen"
                        } else {
                            "Filter oder Suche anpassen"
                        },
                        icon = Icons.Outlined.Dns,
                    )
                    else -> {
                        // Keep cards live while job progress / snapshots update.
                        @Suppress("UNUSED_VARIABLE")
                        val jobTick = jobs.size + jobSnaps.hashCode()
                        LazyColumn(
                            state = listState,
                            contentPadding = ProxListPadding,
                            verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(guests, key = { "${it.type}-${it.node}-${it.vmid}" }) { guest ->
                                val gKey = guest.key
                                GuestCard(
                                    guest = guest,
                                    storages = state.backupStorages,
                                    rates = state.rates[gKey],
                                    cpuHistory = state.cpuHistory[gKey].orEmpty(),
                                    busy = state.busyKey?.startsWith(gKey) == true,
                                    pending = state.pendingPower[gKey],
                                    backup = viewModel.backupProgress(guest),
                                    onClick = { onOpenGuest(guest) },
                                    onLongClick = { sheetGuest = guest },
                                    onPower = { action -> viewModel.requestPower(guest, action) },
                                    onBackup = { storage -> viewModel.startBackup(guest, storage) },
                                )
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
    typeFilter: GuestTypeFilter,
    query: String,
): List<GuestItem> {
    val q = query.trim().lowercase()
    return guests.filter { g ->
        val typeOk = when (typeFilter) {
            GuestTypeFilter.All -> true
            GuestTypeFilter.Ct -> g.type == "lxc"
            GuestTypeFilter.Vm -> g.type == "qemu"
            GuestTypeFilter.Running -> g.isRunning
            GuestTypeFilter.Stopped -> !g.isRunning
        }
        if (!typeOk) return@filter false
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
    storages: List<String>,
    rates: GuestRates?,
    cpuHistory: List<Float>,
    busy: Boolean,
    pending: PendingPower?,
    backup: GuestJobProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPower: (String) -> Unit,
    onBackup: (String) -> Unit,
) {
    var backupMenuOpen by remember { mutableStateOf(false) }
    val pendingVisual = pending?.let { pendingLabelForAction(it.action) }
    val visual = guestVisualStatus(guest.status, guest.qmpstatus, guest.lock, pendingVisual)
    val transitioning = isTransitioningStatus(visual)
    val backingUp = backup?.running == true
    val backupFailed = backup?.failed == true
    val actionsLocked = busy || pending != null || backingUp
    val topBarColor = when {
        backupFailed -> ProxBad
        backingUp -> MaterialTheme.colorScheme.primary
        visual == "creating" || visual == "starting" || visual == "migrating" ->
            MaterialTheme.colorScheme.primary
        visual == "shutting down" || visual == "stopping" || visual == "rebooting" -> ProxWarn
        else -> Color.Transparent
    }

    ProxCard {
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
                    if (transitioning && !backingUp) {
                        TransitionChip(
                            text = if (visual == "creating") "Setting up…" else visual,
                            accent = visual == "creating" || visual == "starting" || visual == "migrating",
                            warn = visual == "shutting down" || visual == "stopping" || visual == "rebooting",
                        )
                    }
                    if (backingUp) {
                        TransitionChip(
                            text = "Backup ${backup?.progress?.let { "${it.toInt()}%" } ?: "…"}",
                            accent = true,
                        )
                    }
                    if (backupFailed) {
                        TransitionChip(text = "Backup failed", warn = false, bad = true)
                    }
                }

                Spacer(Modifier.height(ProxSpace.sm))
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

                if (backingUp) {
                    Spacer(Modifier.height(ProxSpace.md))
                    GuestBackupProgressBlock(
                        progress = backup?.progress,
                        label = backup?.label ?: "…",
                        failed = false,
                    )
                }

                Spacer(Modifier.height(ProxSpace.lg))
                MetricBar("CPU", formatPct(guest.cpu), cpuPct(guest.cpu))
                if (cpuHistory.size >= 2) {
                    Spacer(Modifier.height(6.dp))
                    Sparkline(values = cpuHistory)
                }
                Spacer(Modifier.height(ProxSpace.md))
                MetricBar(
                    "RAM",
                    "${formatBytes(guest.mem)} / ${formatBytes(guest.maxmem)}",
                    usagePct(guest.mem, guest.maxmem),
                )
                Spacer(Modifier.height(ProxSpace.md))
                MetricBar(
                    "Disk",
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
                    contentDescription = "Herunterfahren",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !actionsLocked,
                    onClick = { onPower("shutdown") },
                )
                GuestActionIcon(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = "Neustart",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !actionsLocked,
                    onClick = { onPower("reboot") },
                )
                GuestActionIcon(
                    icon = Icons.Default.Stop,
                    contentDescription = "Force Stop",
                    tint = ProxBad,
                    enabled = !actionsLocked,
                    onClick = { onPower("stop") },
                )
            } else {
                GuestActionIcon(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    tint = ProxGood,
                    enabled = !actionsLocked,
                    onClick = { onPower("start") },
                )
            }

            Spacer(Modifier.weight(1f))

            Box {
                GuestActionIcon(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = "Backup",
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = !actionsLocked && storages.isNotEmpty(),
                    onClick = { backupMenuOpen = true },
                    loading = backingUp,
                    progressLabel = backup?.progress?.let { "${it.toInt()}%" },
                )
                DropdownMenu(
                    expanded = backupMenuOpen,
                    onDismissRequest = { backupMenuOpen = false },
                ) {
                    storages.forEach { storage ->
                        DropdownMenuItem(
                            text = { Text(storage) },
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

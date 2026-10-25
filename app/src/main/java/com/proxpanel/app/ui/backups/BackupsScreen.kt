package com.proxpanel.app.ui.backups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.R
import com.proxpanel.app.data.BackupOverviewGuest
import com.proxpanel.app.data.BackupScheduleJob
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.ui.components.AppearanceDialog
import com.proxpanel.app.ui.components.BackupRunningBanner
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxMuted
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(viewModel: BackupsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberAppHaptics()
    var detailsGuest by remember { mutableStateOf<BackupOverviewGuest?>(null) }
    var startGuest by remember { mutableStateOf<BackupOverviewGuest?>(null) }
    var appearanceOpen by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scrollSession = com.proxpanel.app.ui.util.LocalListScrollSession.current
    com.proxpanel.app.ui.util.rememberListScrollFreeze(
        listState = listState,
        gridState = gridState,
        useGrid = state.viewMode == NodesViewMode.Grid,
        onScrolling = { scrolling ->
            scrollSession.update(scrolling)
            viewModel.setListScrolling(scrolling)
        },
    )

    LaunchedEffect(state.message, state.error) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var query by remember { mutableStateOf("") }
    val withBackup = state.guests.count { it.lastBackup != null }
    val filtered = remember(state.guests, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.guests
        else state.guests.filter { g ->
            g.name.lowercase().contains(q) ||
                g.node.lowercase().contains(q) ||
                g.vmid.toString().contains(q) ||
                g.type.lowercase().contains(q) ||
                backupLocation(g.lastBackup).lowercase().contains(q) ||
                backupFormatLabel(g.lastBackup).lowercase().contains(q)
        }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ProxTopBar(
                title = stringResource(R.string.backups_title),
                subtitle = if (state.guests.isNotEmpty()) {
                    stringResource(R.string.backups_subtitle, withBackup, state.guests.size)
                } else {
                    null
                },
                infoBody = stringResource(R.string.info_backups),
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            com.proxpanel.app.ui.components.ActiveJobsBelowTitle()
            ProxSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.backups_search_placeholder),
            )
            if (state.scheduleJobs.isNotEmpty()) {
                Spacer(Modifier.height(ProxSpace.sm))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ProxSpace.screen),
                    verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                ) {
                    Text(
                        stringResource(R.string.backups_schedules_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.backups_schedules_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.scheduleJobs.forEach { job ->
                        ScheduleJobCard(job = job, guests = state.guests)
                    }
                }
                Spacer(Modifier.height(ProxSpace.md))
                Text(
                    stringResource(R.string.backups_guests_section),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = ProxSpace.screen),
                )
            }
            Spacer(Modifier.height(ProxSpace.sm))
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.guests.isEmpty() -> ProxLoadingState(label = stringResource(R.string.backups_loading))
                    filtered.isEmpty() -> ProxEmptyState(
                        title = if (state.guests.isEmpty()) stringResource(R.string.guests_empty_none) else stringResource(R.string.empty_no_results),
                        icon = Icons.Outlined.Inventory2,
                    )
                    else -> {
                        when (state.viewMode) {
                            NodesViewMode.Cards -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                                ) {
                                    items(filtered, key = { "${it.type}-${it.node}-${it.vmid}" }) { guest ->
                                        val key = BackupsViewModel.guestKey(guest.node, guest.vmid)
                                        BackupCard(
                                            guest = guest,
                                            running = key in state.runningKeys,
                                            starting = state.busyKey == key,
                                            onOpenDetails = { detailsGuest = guest },
                                            onBackupNow = { startGuest = guest },
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
                                    gridItems(filtered, key = { "${it.type}-${it.node}-${it.vmid}" }) { guest ->
                                        val key = BackupsViewModel.guestKey(guest.node, guest.vmid)
                                        BackupTile(
                                            guest = guest,
                                            running = key in state.runningKeys,
                                            starting = state.busyKey == key,
                                            onOpenDetails = { detailsGuest = guest },
                                            onBackupNow = { startGuest = guest },
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
                                    items(filtered, key = { "${it.type}-${it.node}-${it.vmid}" }) { guest ->
                                        val key = BackupsViewModel.guestKey(guest.node, guest.vmid)
                                        BackupCompactRow(
                                            guest = guest,
                                            running = key in state.runningKeys,
                                            starting = state.busyKey == key,
                                            onOpenDetails = { detailsGuest = guest },
                                            onBackupNow = { startGuest = guest },
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

    detailsGuest?.let { guest ->
        BackupDetailsDialog(
            guest = guest,
            viewModel = viewModel,
            onDismiss = { detailsGuest = null },
            onBackupNow = {
                detailsGuest = null
                startGuest = guest
            },
        )
    }

    startGuest?.let { guest ->
        StartBackupDialog(
            guest = guest,
            storages = state.storages
                .filter { it.node == guest.node || it.shared }
                .map { it.storage }
                .distinct()
                .ifEmpty { state.storages.map { it.storage }.distinct() },
            busy = state.busyKey == BackupsViewModel.guestKey(guest.node, guest.vmid),
            onDismiss = { startGuest = null },
            onStart = { storage, mode, compress ->
                viewModel.startBackup(guest, storage, mode, compress)
                startGuest = null
            },
        )
    }
}

@Composable
private fun BackupCard(
    guest: BackupOverviewGuest,
    running: Boolean,
    starting: Boolean,
    onOpenDetails: () -> Unit,
    onBackupNow: () -> Unit,
) {
    val kindLabel = if (guest.type == "lxc") "CT" else "VM"
    val last = guest.lastBackup
    ProxCard {
        Column(modifier = Modifier.clickable(onClick = onOpenDetails)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    guest.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(ProxSpace.sm))
                KindChip(text = kindLabel, kind = guest.type)
            }
            Spacer(Modifier.height(ProxSpace.xs))
            Text(
                stringResource(
                    R.string.backups_card_backups_count,
                    guest.node,
                    kindLabel,
                    guest.vmid,
                    guest.backupCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ProxSpace.md))
            DetailRow(stringResource(R.string.backups_last_backup), formatBackupTime(last?.ctime))
            DetailRow(stringResource(R.string.backup_meta_format), backupFormatLabel(last))
            DetailRow(stringResource(R.string.backup_meta_size), formatBackupSize(last?.size))
            DetailRow("Ort", backupLocation(last))
            if (last != null) {
                DetailRow(stringResource(R.string.backups_file), backupFileName(last.volid), mono = true)
            }
        }
        if (running) {
            Spacer(Modifier.height(ProxSpace.md))
            BackupRunningBanner(
                label = if (starting) stringResource(R.string.backup_starting) else stringResource(R.string.backup_running),
            )
        }
        Spacer(Modifier.height(ProxSpace.md))
        OutlinedButton(
            onClick = onBackupNow,
            enabled = !running,
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(ProxSpace.sm))
            Text(if (running) stringResource(R.string.running_ellipsis) else stringResource(R.string.backup_now))
        }
    }
}

@Composable
private fun BackupTile(
    guest: BackupOverviewGuest,
    running: Boolean,
    starting: Boolean,
    onOpenDetails: () -> Unit,
    onBackupNow: () -> Unit,
) {
    val kindLabel = if (guest.type == "lxc") "CT" else "VM"
    val last = guest.lastBackup
    ProxCard {
        Column(modifier = Modifier.clickable(onClick = onOpenDetails)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    guest.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                KindChip(text = kindLabel, kind = guest.type)
            }
            Spacer(Modifier.height(ProxSpace.xs))
            Text(
                "${guest.vmid} · ${guest.node}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(ProxSpace.sm))
            Text(
                formatBackupTime(last?.ctime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${backupFormatLabel(last)} · ${formatBackupSize(last?.size)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (running) {
            Spacer(Modifier.height(ProxSpace.sm))
            BackupRunningBanner(
                label = if (starting) stringResource(R.string.backup_starting) else stringResource(R.string.backup_running),
            )
        }
        Spacer(Modifier.height(ProxSpace.sm))
        OutlinedButton(
            onClick = onBackupNow,
            enabled = !running,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(ProxSpace.xs))
            Text(stringResource(R.string.backup_now), maxLines = 1)
        }
    }
}

@Composable
private fun BackupCompactRow(
    guest: BackupOverviewGuest,
    running: Boolean,
    starting: Boolean,
    onOpenDetails: () -> Unit,
    onBackupNow: () -> Unit,
) {
    val kindLabel = if (guest.type == "lxc") "CT" else "VM"
    val last = guest.lastBackup
    ProxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDetails),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        guest.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(ProxSpace.sm))
                    KindChip(text = kindLabel, kind = guest.type)
                }
                Text(
                    listOf(
                        "${guest.vmid} · ${guest.node}",
                        formatBackupTime(last?.ctime),
                        if (running) {
                            if (starting) stringResource(R.string.backup_starting) else stringResource(R.string.backup_running)
                        } else {
                            null
                        },
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onBackupNow, enabled = !running) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = stringResource(R.string.backup_now),
                    tint = if (running) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier
                .padding(start = ProxSpace.md)
                .weight(1f, fill = false),
        )
    }
}

@Composable
private fun BackupDetailsDialog(
    guest: BackupOverviewGuest,
    viewModel: BackupsViewModel,
    onDismiss: () -> Unit,
    onBackupNow: () -> Unit,
) {
    var history by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var restoreTarget by remember { mutableStateOf<MediaItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
    val scope = rememberCoroutineScope()
    val last = guest.lastBackup

    LaunchedEffect(guest) {
        loading = true
        history = runCatching { viewModel.loadGuestBackupHistory(guest) }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backups_details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "${guest.name} · ${if (guest.type == "lxc") "CT" else "VM"} ${guest.vmid} · ${guest.node}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ProxSpace.md))
                if (last == null) {
                    Text(stringResource(R.string.backups_none_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DetailRow(stringResource(R.string.backup_meta_when), formatBackupTime(last.ctime))
                    DetailRow(stringResource(R.string.backup_meta_size), formatBackupSize(last.size))
                    DetailRow(stringResource(R.string.backup_meta_format), backupFormatLabel(last))
                    DetailRow("Ort", backupLocation(last))
                    DetailRow(stringResource(R.string.backups_file), backupFileName(last.volid), mono = true)
                    DetailRow(stringResource(R.string.backups_volume), last.volid, mono = true)
                    last.notes?.takeIf { it.isNotBlank() }?.let {
                        DetailRow(
                            stringResource(R.string.backups_notes),
                            com.proxpanel.app.util.htmlToPlainText(it),
                        )
                    }
                }
                Spacer(Modifier.height(ProxSpace.lg))
                Text(
                    stringResource(R.string.backups_all_count, guest.backupCount),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(ProxSpace.sm))
                when {
                    loading -> Text(stringResource(R.string.loading_default), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    history.isEmpty() -> Text(stringResource(R.string.backups_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> history.forEach { item ->
                        ProxCard {
                            Text(
                                formatBackupTime(item.ctime),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${backupLocation(item)} · ${backupFormatLabel(item)} · ${formatBackupSize(item.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                backupFileName(item.volid),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                TextButton(onClick = { restoreTarget = item }) { Text(stringResource(R.string.action_restore)) }
                                TextButton(onClick = { deleteTarget = item }) {
                                    Text(stringResource(R.string.action_delete), color = ProxBad)
                                }
                            }
                        }
                        Spacer(Modifier.height(ProxSpace.sm))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBackupNow) { Text(stringResource(R.string.backup_now)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        shape = MaterialTheme.shapes.large,
    )

    restoreTarget?.let { item ->
        RestoreDialog(
            guest = guest,
            item = item,
            onDismiss = { restoreTarget = null },
            onConfirm = { vmid, storage, force ->
                viewModel.restoreBackup(guest, item.volid, vmid, storage, force)
                restoreTarget = null
                onDismiss()
            },
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_backup_delete_title)) },
            text = { Text(stringResource(R.string.confirm_backup_delete_body, backupFileName(item.volid))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackup(guest, item.volid, item.storage)
                        deleteTarget = null
                        scope.launch {
                            history = runCatching { viewModel.loadGuestBackupHistory(guest) }
                                .getOrDefault(emptyList())
                        }
                    },
                ) { Text(stringResource(R.string.action_delete), color = ProxBad) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            shape = MaterialTheme.shapes.large,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartBackupDialog(
    guest: BackupOverviewGuest,
    storages: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onStart: (storage: String, mode: String, compress: String) -> Unit,
) {
    var storage by remember { mutableStateOf(storages.firstOrNull().orEmpty()) }
    var mode by remember { mutableStateOf("snapshot") }
    var compress by remember { mutableStateOf("zstd") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backups_create_title)) },
        text = {
            Column {
                Text(
                    "${guest.name} · ${guest.node}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ProxSpace.md))
                SimpleDropdown(stringResource(R.string.storage_label), storage, storages, !busy) { storage = it }
                Spacer(Modifier.height(ProxSpace.sm))
                SimpleDropdown(
                    stringResource(R.string.backup_mode_label),
                    mode,
                    listOf("snapshot", "suspend", "stop"),
                    !busy,
                ) { mode = it }
                Spacer(Modifier.height(ProxSpace.sm))
                SimpleDropdown(
                    stringResource(R.string.backup_compress_label),
                    compress,
                    listOf("zstd", "gzip", "lzo", "none"),
                    !busy,
                ) { compress = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(storage, mode, compress) },
                enabled = !busy && storage.isNotBlank(),
            ) { Text(if (busy) stringResource(R.string.starting_ellipsis) else stringResource(R.string.backups_start_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun RestoreDialog(
    guest: BackupOverviewGuest,
    item: MediaItem,
    onDismiss: () -> Unit,
    onConfirm: (vmid: Int, storage: String?, force: Boolean) -> Unit,
) {
    var vmid by remember { mutableStateOf(guest.vmid.toString()) }
    var storage by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_restore_title)) },
        text = {
            Column {
                Text(
                    backupFileName(item.volid),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(ProxSpace.md))
                OutlinedTextField(
                    value = vmid,
                    onValueChange = { vmid = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.target_vmid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                Spacer(Modifier.height(ProxSpace.sm))
                OutlinedTextField(
                    value = storage,
                    onValueChange = { storage = it },
                    label = { Text(stringResource(R.string.storage_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = force, onCheckedChange = { force = it })
                    Text(stringResource(R.string.force_overwrite), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = vmid.toIntOrNull() ?: return@TextButton
                    onConfirm(id, storage.ifBlank { null }, force)
                },
            ) { Text(stringResource(R.string.action_restore)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.large,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        expanded = false
                        onSelect(opt)
                    },
                )
            }
        }
    }
}

@Composable
private fun ScheduleJobCard(
    job: BackupScheduleJob,
    guests: List<BackupOverviewGuest>,
) {
    val context = LocalContext.current
    val whenText = remember(job.schedule) { formatBackupSchedule(job.schedule, context) }
    val whatText = remember(job, guests) { formatBackupSelection(job, guests, context) }
    val modeText = remember(job.mode, job.compress) { formatBackupMode(job, context) }
    val whereText = listOfNotNull(
        job.storage?.takeIf { it.isNotBlank() },
        job.node?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.backups_schedule_on_node, it) },
    ).joinToString(" · ").ifBlank { stringResource(R.string.empty_dash) }

    ProxCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (job.enabled) ProxGood else ProxMuted),
            )
            Text(
                text = if (job.enabled) {
                    stringResource(R.string.backups_schedule_active)
                } else {
                    stringResource(R.string.backups_schedule_disabled)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (job.enabled) ProxGood else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(ProxSpace.sm))
        Text(
            text = whenText,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!job.schedule.isNullOrBlank() && whenText != job.schedule) {
            Text(
                text = job.schedule,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(ProxSpace.sm))
        ScheduleMetaRow(stringResource(R.string.backups_schedule_label_what), whatText)
        ScheduleMetaRow(stringResource(R.string.backups_schedule_label_where), whereText)
        ScheduleMetaRow(stringResource(R.string.backups_schedule_label_how), modeText)
        if (!job.notes.isNullOrBlank()) {
            ScheduleMetaRow(
                stringResource(R.string.backups_schedule_label_notes),
                com.proxpanel.app.util.htmlToPlainText(job.notes),
            )
        }
    }
}

@Composable
private fun ScheduleMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ProxSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

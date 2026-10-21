package com.proxpanel.app.ui.backups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.data.BackupOverviewGuest
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.ui.components.BackupRunningBanner
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxSpace
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(viewModel: BackupsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var detailsGuest by remember { mutableStateOf<BackupOverviewGuest?>(null) }
    var startGuest by remember { mutableStateOf<BackupOverviewGuest?>(null) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ProxTopBar(
                title = "Backups",
                subtitle = if (state.guests.isNotEmpty()) {
                    "$withBackup von ${state.guests.size} mit Backup"
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            ProxSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Name, VMID, Node, Format…",
            )
            Spacer(Modifier.height(ProxSpace.sm))
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.guests.isEmpty() -> ProxLoadingState(label = "Backups laden…")
                    filtered.isEmpty() -> ProxEmptyState(
                        title = if (state.guests.isEmpty()) "Keine Gäste" else "Keine Treffer",
                        icon = Icons.Outlined.Inventory2,
                    )
                    else -> {
                        LazyColumn(
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
                "${guest.node} · $kindLabel ${guest.vmid} · ${guest.backupCount} backups",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ProxSpace.md))
            DetailRow("Letztes Backup", formatBackupTime(last?.ctime))
            DetailRow("Format", backupFormatLabel(last))
            DetailRow("Größe", formatBackupSize(last?.size))
            DetailRow("Ort", backupLocation(last))
            if (last != null) {
                DetailRow("Datei", backupFileName(last.volid), mono = true)
            }
        }
        if (running) {
            Spacer(Modifier.height(ProxSpace.md))
            BackupRunningBanner(
                label = if (starting) "Backup wird gestartet…" else "Backup läuft…",
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
            Text(if (running) "Läuft…" else "Backup jetzt")
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
        title = { Text("Backup-Details") },
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
                    Text("Noch kein Backup vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DetailRow("Wann", formatBackupTime(last.ctime))
                    DetailRow("Größe", formatBackupSize(last.size))
                    DetailRow("Format", backupFormatLabel(last))
                    DetailRow("Ort", backupLocation(last))
                    DetailRow("Datei", backupFileName(last.volid), mono = true)
                    DetailRow("Volume", last.volid, mono = true)
                    last.notes?.takeIf { it.isNotBlank() }?.let {
                        DetailRow("Notes", it)
                    }
                }
                Spacer(Modifier.height(ProxSpace.lg))
                Text(
                    "Alle Backups (${guest.backupCount})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(ProxSpace.sm))
                when {
                    loading -> Text("Laden…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    history.isEmpty() -> Text("Keine Einträge.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                TextButton(onClick = { restoreTarget = item }) { Text("Restore") }
                                TextButton(onClick = { deleteTarget = item }) {
                                    Text("Delete", color = ProxBad)
                                }
                            }
                        }
                        Spacer(Modifier.height(ProxSpace.sm))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBackupNow) { Text("Backup jetzt") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
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
            title = { Text("Backup löschen?") },
            text = { Text("„${backupFileName(item.volid)}“ dauerhaft löschen?") },
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
                ) { Text("Löschen", color = ProxBad) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
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
        title = { Text("Backup erstellen") },
        text = {
            Column {
                Text(
                    "${guest.name} · ${guest.node}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ProxSpace.md))
                SimpleDropdown("Storage", storage, storages, !busy) { storage = it }
                Spacer(Modifier.height(ProxSpace.sm))
                SimpleDropdown(
                    "Mode",
                    mode,
                    listOf("snapshot", "suspend", "stop"),
                    !busy,
                ) { mode = it }
                Spacer(Modifier.height(ProxSpace.sm))
                SimpleDropdown(
                    "Compress",
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
            ) { Text(if (busy) "Starting…" else "Starten") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") }
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
        title = { Text("Backup wiederherstellen?") },
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
                    label = { Text("Ziel-VMID") },
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
                    label = { Text("Storage (optional)") },
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
                    Text("Force überschreiben", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = vmid.toIntOrNull() ?: return@TextButton
                    onConfirm(id, storage.ifBlank { null }, force)
                },
            ) { Text("Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
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

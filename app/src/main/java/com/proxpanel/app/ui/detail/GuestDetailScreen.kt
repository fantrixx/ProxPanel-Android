package com.proxpanel.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.ui.backups.backupFileName
import com.proxpanel.app.ui.backups.backupFormatLabel
import com.proxpanel.app.ui.backups.backupLocation
import com.proxpanel.app.ui.backups.formatBackupSize
import com.proxpanel.app.ui.backups.formatBackupTime
import com.proxpanel.app.ui.components.BackupRunningBanner
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.MetricBar
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.components.SectionLabel
import com.proxpanel.app.ui.components.StatusDot
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.util.cpuPct
import com.proxpanel.app.util.formatBytes
import com.proxpanel.app.util.formatPct
import com.proxpanel.app.util.formatUptime
import com.proxpanel.app.util.usagePct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestDetailScreen(
    viewModel: GuestDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message, state.error) {
        state.message?.let { snackbar.showSnackbar(it) }
        state.error?.let { snackbar.showSnackbar(it) }
    }

    state.confirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(confirm.title) },
            text = { Text(confirm.body) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmAction,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ProxTopBar(
                title = state.guest.name,
                subtitle = "${state.guest.node} · ${state.guest.vmid}",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.consoleUrl())))
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Console")
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
            ScrollableTabRow(
                selectedTabIndex = state.tab.ordinal,
                edgePadding = ProxSpace.screen,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
            ) {
                DetailTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    DetailTab.Overview -> "Overview"
                                    DetailTab.Snapshots -> "Snapshots"
                                    DetailTab.Backups -> "Backups"
                                    DetailTab.Resources -> "Resources"
                                },
                            )
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ProxSpace.screen),
                verticalArrangement = Arrangement.spacedBy(ProxSpace.md),
            ) {
                when (state.tab) {
                    DetailTab.Overview -> OverviewTab(state, viewModel, context)
                    DetailTab.Snapshots -> SnapshotsTab(state, viewModel)
                    DetailTab.Backups -> BackupsTab(state, viewModel)
                    DetailTab.Resources -> ResourcesTab(state, viewModel)
                }
                Spacer(Modifier.height(ProxSpace.xxl))
            }
        }
    }
}

@Composable
private fun OverviewTab(
    state: DetailUiState,
    viewModel: GuestDetailViewModel,
    context: Context,
) {
    ProxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(running = state.guest.isRunning, locked = !state.guest.lock.isNullOrBlank())
            Spacer(Modifier.width(ProxSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${state.guest.status.replaceFirstChar { it.uppercase() }} · Up ${formatUptime(state.guest.uptime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            KindChip(text = state.guest.kindLabel, kind = state.guest.type)
        }
    }

    SectionLabel("Resources")
    ProxCard {
        if (state.loading && state.detail == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            MetricBar("CPU", formatPct(state.guest.cpu), cpuPct(state.guest.cpu))
            Spacer(Modifier.height(ProxSpace.md))
            MetricBar(
                "RAM",
                "${formatBytes(state.guest.mem)} / ${formatBytes(state.guest.maxmem)}",
                usagePct(state.guest.mem, state.guest.maxmem),
            )
            Spacer(Modifier.height(ProxSpace.md))
            MetricBar(
                "Disk",
                "${formatBytes(state.guest.disk)} / ${formatBytes(state.guest.maxdisk)}",
                usagePct(state.guest.disk, state.guest.maxdisk),
            )
        }
    }

    SectionLabel("Network")
    ProxCard {
        val ips = state.guest.ips
        if (ips.isEmpty()) {
            Text("Keine IP erkannt", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ips.forEach { ip ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(ip, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { copyText(context, ip) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = { openHttp(context, ip) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open")
                    }
                }
            }
        }
    }

    SectionLabel("Aktionen")
    val enabled = !state.busy && !state.backupRunning
    var backupMenuOpen by remember { mutableStateOf(false) }
    val storages = state.backupStorages.map { it.storage }.distinct()

    ProxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.guest.isRunning) {
                DetailActionIcon(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    enabled = enabled,
                    container = ProxGood.copy(alpha = 0.18f),
                    tint = ProxGood,
                    onClick = { viewModel.requestAction("start") },
                )
            } else {
                DetailActionIcon(
                    icon = Icons.Default.PowerSettingsNew,
                    contentDescription = "Herunterfahren",
                    enabled = enabled,
                    onClick = { viewModel.requestAction("shutdown") },
                )
                DetailActionIcon(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = "Neustart",
                    enabled = enabled,
                    onClick = { viewModel.requestAction("reboot") },
                )
                DetailActionIcon(
                    icon = Icons.Default.Stop,
                    contentDescription = "Force Stop",
                    enabled = enabled,
                    tint = ProxBad,
                    onClick = { viewModel.requestAction("stop") },
                )
            }
            Box {
                DetailActionIcon(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = "Backup",
                    enabled = enabled && storages.isNotEmpty(),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { backupMenuOpen = true },
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
                                viewModel.startBackup(storage)
                            },
                        )
                    }
                }
            }
        }
        if (state.backupRunning) {
            Spacer(Modifier.height(ProxSpace.md))
            BackupRunningBanner(
                label = if (state.busy) "Backup wird gestartet…" else "Backup läuft…",
            )
        }
    }
}

@Composable
private fun DetailActionIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = tint,
            disabledContainerColor = container.copy(alpha = 0.45f),
            disabledContentColor = tint.copy(alpha = 0.35f),
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SnapshotsTab(state: DetailUiState, viewModel: GuestDetailViewModel) {
    SectionLabel("Neuer Snapshot")
    ProxCard {
        OutlinedTextField(
            value = state.newSnapName,
            onValueChange = viewModel::setSnapName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("snap-name") },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        Spacer(Modifier.height(ProxSpace.md))
        Button(
            onClick = viewModel::createSnapshot,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) { Text("Create") }
    }

    SectionLabel("Vorhanden")
    if (state.snapshots.isEmpty()) {
        Text("Keine Snapshots.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        state.snapshots.forEach { snap ->
            ProxCard {
                Text(snap.name, style = MaterialTheme.typography.titleMedium)
                if (!snap.description.isNullOrBlank()) {
                    Text(snap.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    TextButton(onClick = { viewModel.rollbackSnapshot(snap.name) }, enabled = !state.busy) {
                        Text("Rollback")
                    }
                    TextButton(onClick = { viewModel.deleteSnapshot(snap.name) }, enabled = !state.busy) {
                        Text("Delete", color = ProxBad)
                    }
                }
            }
            Spacer(Modifier.height(ProxSpace.sm))
        }
    }
}

@Composable
private fun BackupsTab(state: DetailUiState, viewModel: GuestDetailViewModel) {
    var storage by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("snapshot") }
    var compress by remember { mutableStateOf("zstd") }
    var restoreItem by remember { mutableStateOf<MediaItem?>(null) }
    var deleteItem by remember { mutableStateOf<MediaItem?>(null) }
    val storages = state.backupStorages
        .filter { it.node == state.guest.node || it.shared }
        .map { it.storage }
        .distinct()
        .ifEmpty { state.backupStorages.map { it.storage }.distinct() }

    LaunchedEffect(storages) {
        if (storage.isBlank() && storages.isNotEmpty()) storage = storages.first()
    }

    SectionLabel("Backup starten")
    ProxCard {
        if (state.backupRunning) {
            BackupRunningBanner(
                label = if (state.busy) "Backup wird gestartet…" else "Backup läuft…",
            )
            Spacer(Modifier.height(ProxSpace.md))
        }
        BackupOptionChips("Storage", storages, storage) { storage = it }
        Spacer(Modifier.height(ProxSpace.sm))
        BackupOptionChips("Mode", listOf("snapshot", "suspend", "stop"), mode) { mode = it }
        Spacer(Modifier.height(ProxSpace.sm))
        BackupOptionChips("Compress", listOf("zstd", "gzip", "lzo", "none"), compress) {
            compress = it
        }
        Spacer(Modifier.height(ProxSpace.md))
        OutlinedButton(
            onClick = { viewModel.startBackup(storage, mode, compress) },
            enabled = !state.busy && !state.backupRunning && storage.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                when {
                    state.busy -> "Starting…"
                    state.backupRunning -> "Läuft…"
                    else -> "Backup starten"
                },
            )
        }
    }

    SectionLabel("Vorhanden")
    if (state.backups.isEmpty()) {
        Text("Keine Backups.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        state.backups.forEach { item ->
            ProxCard {
                Text(
                    backupFileName(item.volid),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${formatBackupTime(item.ctime)} · ${formatBackupSize(item.size)} · ${backupFormatLabel(item)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    backupLocation(item),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Row {
                    TextButton(
                        onClick = { restoreItem = item },
                        enabled = !state.busy,
                    ) { Text("Restore") }
                    TextButton(
                        onClick = { deleteItem = item },
                        enabled = !state.busy,
                    ) { Text("Delete", color = ProxBad) }
                }
            }
            Spacer(Modifier.height(ProxSpace.sm))
        }
    }

    restoreItem?.let { item ->
        var targetVmid by remember(item) { mutableStateOf(state.guest.vmid.toString()) }
        var restoreStorage by remember(item) { mutableStateOf("") }
        var force by remember(item) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { restoreItem = null },
            title = { Text("Backup wiederherstellen?") },
            text = {
                Column {
                    Text(backupFileName(item.volid), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(ProxSpace.md))
                    OutlinedTextField(
                        value = targetVmid,
                        onValueChange = { targetVmid = it.filter { c -> c.isDigit() } },
                        label = { Text("Ziel-VMID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(ProxSpace.sm))
                    OutlinedTextField(
                        value = restoreStorage,
                        onValueChange = { restoreStorage = it },
                        label = { Text("Storage (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = force,
                            onCheckedChange = { force = it },
                        )
                        Text("Force überschreiben")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = targetVmid.toIntOrNull() ?: return@TextButton
                        viewModel.restoreBackup(
                            item.volid,
                            id,
                            restoreStorage.ifBlank { null },
                            force,
                        )
                        restoreItem = null
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreItem = null }) { Text("Abbrechen") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Backup löschen?") },
            text = { Text("„${backupFileName(item.volid)}“ dauerhaft löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackup(item)
                        deleteItem = null
                    },
                ) { Text("Löschen", color = ProxBad) }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text("Abbrechen") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }
}

@Composable
private fun BackupOptionChips(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(ProxSpace.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(ProxSpace.xs)) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                label = { Text(opt) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

@Composable
private fun ResourcesTab(state: DetailUiState, viewModel: GuestDetailViewModel) {
    SectionLabel("CPU / Memory")
    ProxCard {
        OutlinedTextField(
            value = state.coresInput,
            onValueChange = viewModel::setCores,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cores") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        Spacer(Modifier.height(ProxSpace.md))
        OutlinedTextField(
            value = state.memoryInput,
            onValueChange = viewModel::setMemory,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Memory (MiB)") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        if (state.guest.type == "lxc") {
            Spacer(Modifier.height(ProxSpace.md))
            OutlinedTextField(
                value = state.swapInput,
                onValueChange = viewModel::setSwap,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Swap (MiB)") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
        }
        Spacer(Modifier.height(ProxSpace.lg))
        Button(
            onClick = viewModel::saveResources,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) { Text("Speichern") }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ip", text))
}

private fun openHttp(context: Context, ip: String) {
    val url = if (ip.startsWith("http")) ip else "http://$ip"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

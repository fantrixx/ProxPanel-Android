package com.proxpanel.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.proxpanel.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.ui.backups.backupFileName
import com.proxpanel.app.ui.backups.backupFormatLabel
import com.proxpanel.app.ui.backups.backupLocation
import com.proxpanel.app.ui.backups.formatBackupSize
import com.proxpanel.app.ui.backups.formatBackupTime
import com.proxpanel.app.ui.components.GuestBackupProgressBlock
import com.proxpanel.app.ui.components.GuestLogo
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.guests.GuestIconPicker
import com.proxpanel.app.ui.components.MetricBar
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.components.StatusBadge
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
    var iconPickerOpen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scrollSession = remember { com.proxpanel.app.ui.util.ListScrollSession() }
    com.proxpanel.app.ui.util.rememberColumnScrollFreeze(
        scrollState = scrollState,
        onScrolling = {
            scrollSession.update(it)
            viewModel.setListScrolling(it)
        },
    )

    if (iconPickerOpen) {
        GuestIconPicker(
            guest = state.guest,
            current = state.guestIcon,
            onDismiss = { iconPickerOpen = false },
            onSave = { record ->
                viewModel.setGuestIcon(record)
                iconPickerOpen = false
            },
        )
    }

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

    androidx.compose.runtime.CompositionLocalProvider(
        com.proxpanel.app.ui.util.LocalListScrollSession provides scrollSession,
    ) {
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
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.action_cancel)) }
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
                infoBody = stringResource(R.string.info_detail),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.consoleUrl())))
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.cd_console))
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
            DetailTabBar(
                selected = state.tab,
                onSelect = viewModel::setTab,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = ProxSpace.screen)
                    .padding(top = ProxSpace.md, bottom = ProxSpace.xxl),
                verticalArrangement = Arrangement.spacedBy(ProxSpace.md),
            ) {
                when (state.tab) {
                    DetailTab.Overview -> OverviewTab(
                        state,
                        viewModel,
                        context,
                        onChangeLogo = { iconPickerOpen = true },
                    )
                    DetailTab.Snapshots -> SnapshotsTab(state, viewModel)
                    DetailTab.Backups -> BackupsTab(state, viewModel)
                    DetailTab.Resources -> ResourcesTab(state, viewModel)
                }
            }
        }
    }
    }
}

@Composable
private fun DetailTabBar(
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ProxSpace.screen, vertical = ProxSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
    ) {
        DetailTab.entries.forEach { tab ->
            val isSelected = selected == tab
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        when (tab) {
                            DetailTab.Overview -> stringResource(R.string.tab_overview)
                            DetailTab.Snapshots -> stringResource(R.string.tab_snapshots)
                            DetailTab.Backups -> stringResource(R.string.tab_backups)
                            DetailTab.Resources -> stringResource(R.string.tab_resources)
                        },
                    )
                },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun OverviewTab(
    state: DetailUiState,
    viewModel: GuestDetailViewModel,
    context: Context,
    onChangeLogo: () -> Unit,
) {
    ProxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuestLogo(
                name = state.guest.name,
                tags = state.guest.tags,
                record = state.guestIcon,
                type = state.guest.type,
                size = 52.dp,
                onClick = onChangeLogo,
            )
            Spacer(Modifier.width(ProxSpace.md))
            StatusBadge(
                status = state.guest.status,
                qmpstatus = state.guest.qmpstatus,
                lock = state.guest.lock,
            )
            Spacer(Modifier.width(ProxSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.uptime_prefix, formatUptime(state.guest.uptime)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            KindChip(text = state.guest.kindLabel, kind = state.guest.type)
        }
    }

    DetailSection(stringResource(R.string.section_resources)) {
        if (state.loading && state.detail == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        } else {
            MetricBar(stringResource(R.string.metric_cpu), formatPct(state.guest.cpu), cpuPct(state.guest.cpu))
            Spacer(Modifier.height(ProxSpace.md))
            MetricBar(
                stringResource(R.string.metric_ram),
                "${formatBytes(state.guest.mem)} / ${formatBytes(state.guest.maxmem)}",
                usagePct(state.guest.mem, state.guest.maxmem),
            )
            Spacer(Modifier.height(ProxSpace.md))
            MetricBar(
                stringResource(R.string.metric_disk),
                "${formatBytes(state.guest.disk)} / ${formatBytes(state.guest.maxdisk)}",
                usagePct(state.guest.disk, state.guest.maxdisk),
            )
        }
    }

    DetailSection(stringResource(R.string.section_network)) {
        val ips = state.guest.ips
        if (ips.isEmpty()) {
            Text(stringResource(R.string.no_ip_detected), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ips.forEachIndexed { index, ip ->
                if (index > 0) Spacer(Modifier.height(ProxSpace.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = ip,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { copyText(context, ip) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                    IconButton(onClick = { openHttp(context, ip) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.action_open))
                    }
                }
            }
        }
    }

    DetailSection(stringResource(R.string.section_meta)) {
        OutlinedTextField(
            value = state.tagsInput,
            onValueChange = viewModel::setTags,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.detail_field_tags)) },
            placeholder = { Text(stringResource(R.string.detail_tags_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        Spacer(Modifier.height(ProxSpace.sm))
        OutlinedTextField(
            value = state.notesInput,
            onValueChange = viewModel::setNotes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.detail_field_notes)) },
            minLines = 2,
            maxLines = 4,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        Spacer(Modifier.height(ProxSpace.sm))
        Button(
            onClick = viewModel::saveTagsAndNotes,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }

    DetailSection(stringResource(R.string.section_actions)) {
        val enabled = !state.busy && !state.backupRunning
        var backupMenuOpen by remember { mutableStateOf(false) }
        var migrateOpen by remember { mutableStateOf(false) }
        var cloneOpen by remember { mutableStateOf(false) }
        var templateConfirm by remember { mutableStateOf(false) }
        val storages = state.backupStorages.map { it.storage }.distinct()
        val targets = state.nodeOptions.filter { it != state.guest.node }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.guest.isRunning) {
                DetailActionIcon(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.power_start),
                    enabled = enabled,
                    container = ProxGood.copy(alpha = 0.18f),
                    tint = ProxGood,
                    onClick = { viewModel.requestAction("start") },
                )
            } else {
                DetailActionIcon(
                    icon = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(R.string.power_shutdown),
                    enabled = enabled,
                    onClick = { viewModel.requestAction("shutdown") },
                )
                DetailActionIcon(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.power_reboot),
                    enabled = enabled,
                    onClick = { viewModel.requestAction("reboot") },
                )
                DetailActionIcon(
                    icon = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.power_force_stop),
                    enabled = enabled,
                    tint = ProxBad,
                    onClick = { viewModel.requestAction("stop") },
                )
            }
            Box {
                DetailActionIcon(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = stringResource(R.string.power_backup),
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

        Spacer(Modifier.height(ProxSpace.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
        ) {
            OutlinedButton(
                onClick = { migrateOpen = true },
                enabled = enabled && targets.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_migrate))
            }
            OutlinedButton(
                onClick = { cloneOpen = true },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_clone))
            }
        }
        Spacer(Modifier.height(ProxSpace.sm))
        OutlinedButton(
            onClick = { templateConfirm = true },
            enabled = enabled && !state.guest.isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_to_template))
        }

        if (state.backupRunning) {
            Spacer(Modifier.height(ProxSpace.md))
            GuestBackupProgressBlock(
                progress = null,
                label = if (state.busy) stringResource(R.string.starting_ellipsis) else stringResource(R.string.backing_up_ellipsis),
            )
        }

        if (migrateOpen) {
            MigrateDialog(
                nodes = targets,
                onlineDefault = state.guest.isRunning,
                onDismiss = { migrateOpen = false },
                onConfirm = { target, online, withDisks ->
                    migrateOpen = false
                    viewModel.migrateGuest(target, online, withDisks)
                },
            )
        }
        if (cloneOpen) {
            CloneDialog(
                viewModel = viewModel,
                nodes = state.nodeOptions,
                currentNode = state.guest.node,
                defaultName = state.guest.name,
                onDismiss = { cloneOpen = false },
                onConfirm = { newid, name, target, full ->
                    cloneOpen = false
                    viewModel.cloneGuest(newid, name, target, full)
                },
            )
        }
        if (templateConfirm) {
            AlertDialog(
                onDismissRequest = { templateConfirm = false },
                title = { Text(stringResource(R.string.confirm_template_title)) },
                text = { Text(stringResource(R.string.confirm_template_body, state.guest.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            templateConfirm = false
                            viewModel.convertToTemplate()
                        },
                    ) {
                        Text(stringResource(R.string.action_to_template))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { templateConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun MigrateDialog(
    nodes: List<String>,
    onlineDefault: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (target: String, online: Boolean, withLocalDisks: Boolean) -> Unit,
) {
    var target by remember { mutableStateOf(nodes.firstOrNull().orEmpty()) }
    var online by remember { mutableStateOf(onlineDefault) }
    var withDisks by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.migrate_title)) },
        text = {
            Column {
                SimpleDropdown(
                    label = stringResource(R.string.migrate_target_node),
                    value = target,
                    options = nodes,
                    onSelect = { target = it },
                )
                Spacer(Modifier.height(ProxSpace.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = online, onCheckedChange = { online = it })
                    Text(stringResource(R.string.migrate_online))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withDisks, onCheckedChange = { withDisks = it })
                    Text(stringResource(R.string.migrate_with_disks))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(target, online, withDisks) },
                enabled = target.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_migrate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun CloneDialog(
    viewModel: GuestDetailViewModel,
    nodes: List<String>,
    currentNode: String,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (newid: Int, name: String?, target: String?, full: Boolean) -> Unit,
) {
    var newid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("$defaultName-clone") }
    var target by remember { mutableStateOf(currentNode) }
    var full by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        newid = viewModel.suggestNextId().toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clone_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = newid,
                    onValueChange = { newid = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.clone_new_vmid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(ProxSpace.sm))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.clone_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(ProxSpace.sm))
                if (nodes.isNotEmpty()) {
                    SimpleDropdown(
                        label = stringResource(R.string.clone_target_node),
                        value = target,
                        options = nodes,
                        onSelect = { target = it },
                    )
                    Spacer(Modifier.height(ProxSpace.sm))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = full, onCheckedChange = { full = it })
                    Text(stringResource(R.string.clone_full_copy))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = newid.toIntOrNull() ?: return@TextButton
                    onConfirm(id, name.trim().ifBlank { null }, target.ifBlank { null }, full)
                },
                enabled = newid.toIntOrNull() != null,
            ) {
                Text(stringResource(R.string.action_clone))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = fieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
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
    DetailSection(stringResource(R.string.snapshot_new_section)) {
        OutlinedTextField(
            value = state.newSnapName,
            onValueChange = viewModel::setSnapName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.snapshot_name_placeholder)) },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        Spacer(Modifier.height(ProxSpace.md))
        Button(
            onClick = viewModel::createSnapshot,
            enabled = !state.busy && state.newSnapName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.small,
        ) { Text(stringResource(R.string.snapshot_create)) }
    }

    DetailSection(stringResource(R.string.snapshot_existing_section)) {
        if (state.snapshots.isEmpty()) {
            EmptyHint(stringResource(R.string.snapshot_empty))
        } else {
            state.snapshots.forEachIndexed { index, snap ->
                if (index > 0) Spacer(Modifier.height(ProxSpace.sm))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(ProxSpace.md),
                ) {
                    Text(snap.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (!snap.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            snap.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(ProxSpace.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm)) {
                        OutlinedButton(
                            onClick = { viewModel.rollbackSnapshot(snap.name) },
                            enabled = !state.busy,
                            shape = MaterialTheme.shapes.small,
                        ) { Text(stringResource(R.string.action_rollback)) }
                        TextButton(
                            onClick = { viewModel.deleteSnapshot(snap.name) },
                            enabled = !state.busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = ProxBad),
                        ) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
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

    DetailSection(stringResource(R.string.backup_start_section)) {
        if (state.backupRunning) {
            GuestBackupProgressBlock(
                progress = null,
                label = if (state.busy) stringResource(R.string.starting_ellipsis) else stringResource(R.string.backing_up_ellipsis),
            )
            Spacer(Modifier.height(ProxSpace.md))
        }
        StorageDropdown(
            value = storage,
            options = storages,
            enabled = !state.busy && !state.backupRunning,
            onSelect = { storage = it },
        )
        Spacer(Modifier.height(ProxSpace.md))
        BackupOptionRow(
            label = stringResource(R.string.backup_mode_label),
            options = listOf("snapshot", "suspend", "stop"),
            selected = mode,
            onSelect = { mode = it },
        )
        Spacer(Modifier.height(ProxSpace.md))
        BackupOptionRow(
            label = stringResource(R.string.backup_compress_label),
            options = listOf("zstd", "gzip", "lzo", "none"),
            selected = compress,
            onSelect = { compress = it },
        )
        Spacer(Modifier.height(ProxSpace.lg))
        Button(
            onClick = { viewModel.startBackup(storage, mode, compress) },
            enabled = !state.busy && !state.backupRunning && storage.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.busy -> stringResource(R.string.starting_ellipsis)
                    state.backupRunning -> stringResource(R.string.running_ellipsis)
                    else -> stringResource(R.string.backup_start_button)
                },
            )
        }
    }

    DetailSection(stringResource(R.string.backup_existing_count, state.backups.size)) {
        if (state.backups.isEmpty()) {
            EmptyHint(stringResource(R.string.backup_empty))
        } else {
            state.backups.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(ProxSpace.sm))
                BackupItemCard(
                    item = item,
                    busy = state.busy,
                    onRestore = { restoreItem = item },
                    onDelete = { deleteItem = item },
                )
            }
        }
    }

    restoreItem?.let { item ->
        var targetVmid by remember(item) { mutableStateOf(state.guest.vmid.toString()) }
        var restoreStorage by remember(item) { mutableStateOf("") }
        var force by remember(item) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { restoreItem = null },
            title = { Text(stringResource(R.string.confirm_restore_title)) },
            text = {
                Column {
                    Text(
                        backupFileName(item.volid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ProxSpace.md))
                    OutlinedTextField(
                        value = targetVmid,
                        onValueChange = { targetVmid = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.target_vmid)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(ProxSpace.sm))
                    OutlinedTextField(
                        value = restoreStorage,
                        onValueChange = { restoreStorage = it },
                        label = { Text(stringResource(R.string.storage_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = force, onCheckedChange = { force = it })
                        Text(stringResource(R.string.force_overwrite))
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
                ) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreItem = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text(stringResource(R.string.confirm_backup_delete_title)) },
            text = { Text(stringResource(R.string.confirm_backup_delete_body, backupFileName(item.volid))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackup(item)
                        deleteItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ProxBad),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            shape = MaterialTheme.shapes.large,
        )
    }
}

@Composable
private fun BackupItemCard(
    item: MediaItem,
    busy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(ProxSpace.md),
    ) {
        Text(
            text = backupFileName(item.volid),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(ProxSpace.sm))
        MetaLine(stringResource(R.string.backup_meta_when), formatBackupTime(item.ctime))
        MetaLine(stringResource(R.string.backup_meta_size), formatBackupSize(item.size))
        MetaLine(stringResource(R.string.backup_meta_format), backupFormatLabel(item))
        MetaLine(stringResource(R.string.backup_meta_location), backupLocation(item))
        Spacer(Modifier.height(ProxSpace.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
        ) {
            OutlinedButton(
                onClick = onRestore,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_restore))
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProxBad),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageDropdown(
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled && options.isNotEmpty()) expanded = it },
    ) {
        OutlinedTextField(
            value = value.ifBlank { if (options.isEmpty()) stringResource(R.string.storage_none) else stringResource(R.string.storage_pick) },
            onValueChange = {},
            readOnly = true,
            enabled = enabled && options.isNotEmpty(),
            label = { Text(stringResource(R.string.storage_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
private fun BackupOptionRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(ProxSpace.sm))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                label = { Text(opt) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.no_options),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResourcesTab(state: DetailUiState, viewModel: GuestDetailViewModel) {
    DetailSection(stringResource(R.string.section_cpu_memory)) {
        OutlinedTextField(
            value = state.coresInput,
            onValueChange = viewModel::setCores,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_cores)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        Spacer(Modifier.height(ProxSpace.md))
        OutlinedTextField(
            value = state.memoryInput,
            onValueChange = viewModel::setMemory,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_memory_mib)) },
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
                label = { Text(stringResource(R.string.field_swap_mib)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
            )
        }
        Spacer(Modifier.height(ProxSpace.lg))
        Button(
            onClick = viewModel::saveResources,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.small,
        ) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ProxCard {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(ProxSpace.md))
        content()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
            .padding(vertical = ProxSpace.xl, horizontal = ProxSpace.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

package com.proxpanel.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.data.CreateGuestResult
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.components.SectionLabel
import com.proxpanel.app.ui.theme.ProxCt
import com.proxpanel.app.ui.theme.ProxCtContainer
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.theme.ProxVm
import com.proxpanel.app.ui.theme.ProxVmContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGuestScreen(
    viewModel: CreateGuestViewModel,
    onBack: () -> Unit,
    onCreated: (CreateGuestResult) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.created) {
        state.created?.let {
            onCreated(it)
            viewModel.clearCreated()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ProxTopBar(
                title = if (state.type == CreateGuestType.Lxc) stringResource(R.string.create_title_lxc) else stringResource(R.string.create_title_vm),
                subtitle = stringResource(R.string.create_subtitle),
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            ProxLoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                label = stringResource(R.string.create_loading_cluster),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ProxSpace.screen)
                .padding(bottom = ProxSpace.xxl),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm)) {
                FilterChip(
                    selected = state.type == CreateGuestType.Lxc,
                    onClick = { viewModel.setType(CreateGuestType.Lxc) },
                    enabled = !state.busy,
                    label = { Text(stringResource(R.string.create_type_container)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ProxCtContainer,
                        selectedLabelColor = ProxCt,
                    ),
                )
                FilterChip(
                    selected = state.type == CreateGuestType.Qemu,
                    onClick = { viewModel.setType(CreateGuestType.Qemu) },
                    enabled = !state.busy,
                    label = { Text("VM") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ProxVmContainer,
                        selectedLabelColor = ProxVm,
                    ),
                )
            }

            Spacer(Modifier.height(ProxSpace.lg))
            SectionLabel(stringResource(R.string.create_section_placement))
            ProxCard {
                DropdownField(
                    label = stringResource(R.string.create_field_node),
                    value = state.node,
                    options = state.nodes,
                    enabled = !state.busy,
                    onSelect = { viewModel.patch(node = it) },
                )
                Spacer(Modifier.height(ProxSpace.md))
                OutlinedTextField(
                    value = state.vmid,
                    onValueChange = { viewModel.patch(vmid = it.filter { c -> c.isDigit() }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("VMID") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(ProxSpace.md))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.patch(name = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (state.type == CreateGuestType.Lxc) R.string.create_field_hostname else R.string.create_field_name)) },
                    placeholder = {
                        Text(stringResource(if (state.type == CreateGuestType.Lxc) R.string.create_placeholder_lxc else R.string.create_placeholder_vm))
                    },
                    singleLine = true,
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors(),
                )
            }

            Spacer(Modifier.height(ProxSpace.lg))
            SectionLabel(
                if (state.type == CreateGuestType.Lxc) stringResource(R.string.create_section_template_disk) else stringResource(R.string.create_section_disk_iso),
            )
            ProxCard {
                if (state.type == CreateGuestType.Lxc) {
                    DropdownField(
                        label = "OS-Template",
                        value = state.ostemplate,
                        options = state.templates.map { it.volid },
                        optionLabel = { it.substringAfterLast('/') },
                        enabled = !state.busy,
                        onSelect = { viewModel.patch(ostemplate = it) },
                    )
                    if (state.templates.isEmpty()) {
                        Spacer(Modifier.height(ProxSpace.sm))
                        Text(
                            stringResource(R.string.create_no_templates_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(ProxSpace.md))
                } else {
                    DropdownField(
                        label = stringResource(R.string.create_field_iso_optional),
                        value = state.iso,
                        options = listOf("") + state.isos.map { it.volid },
                        optionLabel = {
                            if (it.isBlank()) "— später anhängen —" else it.substringAfterLast('/')
                        },
                        enabled = !state.busy,
                        onSelect = { viewModel.patch(iso = it) },
                    )
                    Spacer(Modifier.height(ProxSpace.md))
                }
                DropdownField(
                    label = stringResource(R.string.create_field_disk_storage),
                    value = state.storage,
                    options = state.storages.map { it.storage }.distinct(),
                    enabled = !state.busy,
                    onSelect = { viewModel.patch(storage = it) },
                )
                Spacer(Modifier.height(ProxSpace.md))
                OutlinedTextField(
                    value = state.diskGiB,
                    onValueChange = { viewModel.patch(diskGiB = it.filter { c -> c.isDigit() }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.create_field_disk_gib)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors(),
                )
            }

            if (state.type == CreateGuestType.Lxc) {
                Spacer(Modifier.height(ProxSpace.lg))
                SectionLabel(stringResource(R.string.create_section_access))
                ProxCard {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.patch(password = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.create_field_root_password)) },
                        singleLine = true,
                        enabled = !state.busy,
                        visualTransformation = if (state.showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(ProxSpace.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm)) {
                        TextButton(
                            onClick = { viewModel.patch(showPassword = !state.showPassword) },
                            enabled = !state.busy,
                        ) { Text(if (state.showPassword) stringResource(R.string.create_hide_password) else stringResource(R.string.create_show_password)) }
                        TextButton(
                            onClick = viewModel::generatePassword,
                            enabled = !state.busy,
                        ) { Text(stringResource(R.string.create_generate_password)) }
                    }
                }
            }

            Spacer(Modifier.height(ProxSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.startAfter,
                    onCheckedChange = { viewModel.patch(startAfter = it) },
                    enabled = !state.busy,
                )
                Text(stringResource(R.string.create_start_after), style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedButton(
                onClick = { viewModel.patch(showAdvanced = !state.showAdvanced) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (state.showAdvanced) stringResource(R.string.create_hide_advanced) else stringResource(R.string.create_show_advanced))
            }

            if (state.showAdvanced) {
                Spacer(Modifier.height(ProxSpace.md))
                SectionLabel(stringResource(R.string.create_section_resources))
                ProxCard {
                    OutlinedTextField(
                        value = state.cores,
                        onValueChange = { viewModel.patch(cores = it.filter { c -> c.isDigit() }) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.create_field_cpu_cores)) },
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(ProxSpace.md))
                    OutlinedTextField(
                        value = state.memory,
                        onValueChange = { viewModel.patch(memory = it.filter { c -> c.isDigit() }) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_memory_mib)) },
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors(),
                    )
                    if (state.type == CreateGuestType.Lxc) {
                        Spacer(Modifier.height(ProxSpace.md))
                        OutlinedTextField(
                            value = state.swap,
                            onValueChange = { viewModel.patch(swap = it.filter { c -> c.isDigit() }) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.field_swap_mib)) },
                            singleLine = true,
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.small,
                            colors = fieldColors(),
                        )
                    }
                }
                Spacer(Modifier.height(ProxSpace.md))
                SectionLabel(stringResource(R.string.create_section_network))
                ProxCard {
                    DropdownField(
                        label = stringResource(R.string.create_field_bridge),
                        value = state.bridge,
                        options = state.bridges.map { it.iface }.ifEmpty { listOf(state.bridge.ifBlank { "vmbr0" }) },
                        enabled = !state.busy,
                        onSelect = { viewModel.patch(bridge = it) },
                    )
                    Spacer(Modifier.height(ProxSpace.sm))
                    Text(stringResource(R.string.create_ip_dhcp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.type == CreateGuestType.Lxc) {
                        Spacer(Modifier.height(ProxSpace.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.unprivileged,
                                onCheckedChange = { viewModel.patch(unprivileged = it) },
                                enabled = !state.busy,
                            )
                            Text(stringResource(R.string.create_unprivileged_recommended), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(ProxSpace.xl))
            Button(
                onClick = viewModel::create,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    when {
                        state.busy -> stringResource(R.string.create_busy)
                        state.type == CreateGuestType.Lxc -> stringResource(R.string.create_button_lxc)
                        else -> stringResource(R.string.create_button_vm)
                    },
                )
            }
            Spacer(Modifier.height(ProxSpace.sm))
            OutlinedButton(
                onClick = onBack,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    optionLabel: (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else optionLabel(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

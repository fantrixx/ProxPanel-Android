package com.proxpanel.app.ui.guests

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.proxpanel.app.R
import com.proxpanel.app.data.GuestIconMode
import com.proxpanel.app.data.GuestIconRecord
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.data.guestIconKey
import com.proxpanel.app.data.listServiceCatalog
import com.proxpanel.app.data.resolveServiceIcon
import com.proxpanel.app.data.serviceIconUrl
import com.proxpanel.app.data.suggestServiceIcons
import com.proxpanel.app.ui.components.GuestLogo
import com.proxpanel.app.ui.theme.ProxSpace
import java.io.File
import java.io.FileOutputStream

private enum class PickerTab { Auto, Catalog, Upload, None }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuestIconPicker(
    guest: GuestItem,
    current: GuestIconRecord?,
    onDismiss: () -> Unit,
    onSave: (GuestIconRecord?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val iconKey = guestIconKey(guest.node, guest.type, guest.vmid)
    val safeKey = iconKey.replace(':', '_').replace('/', '_')

    val initialTab = when (GuestIconMode.fromId(current?.mode)) {
        GuestIconMode.Cdn -> PickerTab.Catalog
        GuestIconMode.Upload -> PickerTab.Upload
        GuestIconMode.None -> PickerTab.None
        GuestIconMode.Auto -> PickerTab.Auto
    }
    var tab by remember { mutableStateOf(initialTab) }
    var selectedSlug by remember { mutableStateOf(current?.slug) }
    var uploadPath by remember { mutableStateOf(current?.uri) }
    var search by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val dir = File(context.filesDir, "guest-icons").apply { mkdirs() }
            val ext = context.contentResolver.getType(uri)?.substringAfter('/')?.takeIf { it.isNotBlank() } ?: "jpg"
            val outFile = File(dir, "$safeKey.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            uploadPath = outFile.absolutePath
            tab = PickerTab.Upload
        }
    }

    val detected = remember(guest.name, guest.tags) { resolveServiceIcon(guest.name, guest.tags) }
    val suggestions = remember(guest.name, guest.tags) { suggestServiceIcons(guest.name, guest.tags) }
    val catalog = remember { listServiceCatalog() }
    val filteredCatalog = remember(search, catalog) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) catalog
        else catalog.filter { it.label.lowercase().contains(q) || it.slug.lowercase().contains(q) }
    }

    val previewRecord = when (tab) {
        PickerTab.Auto -> null
        PickerTab.Catalog -> GuestIconRecord(GuestIconMode.Cdn.id, slug = selectedSlug)
        PickerTab.Upload -> GuestIconRecord(GuestIconMode.Upload.id, uri = uploadPath)
        PickerTab.None -> GuestIconRecord(GuestIconMode.None.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ProxSpace.screen)
                .padding(bottom = ProxSpace.xxl),
        ) {
            Text(
                text = stringResource(R.string.guest_icon_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(ProxSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ProxSpace.md)) {
                GuestLogo(
                    name = guest.name,
                    tags = guest.tags,
                    record = previewRecord ?: current,
                    type = guest.type,
                    size = 48.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(guest.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = stringResource(R.string.guest_icon_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(ProxSpace.lg))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm)) {
                FilterChip(
                    selected = tab == PickerTab.Auto,
                    onClick = { tab = PickerTab.Auto },
                    label = { Text(stringResource(R.string.guest_icon_auto)) },
                )
                FilterChip(
                    selected = tab == PickerTab.Catalog,
                    onClick = { tab = PickerTab.Catalog },
                    label = { Text(stringResource(R.string.guest_icon_catalog)) },
                )
                FilterChip(
                    selected = tab == PickerTab.Upload,
                    onClick = { tab = PickerTab.Upload },
                    label = { Text(stringResource(R.string.guest_icon_upload)) },
                )
                FilterChip(
                    selected = tab == PickerTab.None,
                    onClick = { tab = PickerTab.None },
                    label = { Text(stringResource(R.string.guest_icon_none)) },
                )
            }

            Spacer(Modifier.height(ProxSpace.md))

            when (tab) {
                PickerTab.Auto -> {
                    if (detected != null) {
                        Text(
                            text = stringResource(R.string.guest_icon_detected, detected.label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(ProxSpace.sm))
                        AsyncImage(
                            model = serviceIconUrl(detected.slug),
                            contentDescription = detected.label,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(ProxSpace.md))
                        Text(
                            text = stringResource(R.string.guest_icon_suggestions),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(ProxSpace.sm))
                        suggestions.forEach { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        selectedSlug = s.slug
                                        tab = PickerTab.Catalog
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                            ) {
                                AsyncImage(
                                    model = serviceIconUrl(s.slug),
                                    contentDescription = s.label,
                                    modifier = Modifier.size(32.dp),
                                )
                                Column {
                                    Text(s.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        s.reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                PickerTab.Catalog -> {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.guest_icon_search)) },
                    )
                    Spacer(Modifier.height(ProxSpace.sm))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(56.dp),
                        contentPadding = PaddingValues(vertical = ProxSpace.sm),
                        horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                        verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                        modifier = Modifier.height(220.dp),
                    ) {
                        items(filteredCatalog, key = { it.slug }) { item ->
                            val sel = selectedSlug == item.slug
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .then(
                                        if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                        else Modifier,
                                    )
                                    .clickable { selectedSlug = item.slug },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = serviceIconUrl(item.slug),
                                    contentDescription = item.label,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .padding(4.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
                PickerTab.Upload -> {
                    Button(onClick = { photoPicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.guest_icon_upload))
                    }
                    uploadPath?.let { path ->
                        Spacer(Modifier.height(ProxSpace.md))
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                PickerTab.None -> {
                    Text(
                        text = stringResource(R.string.guest_icon_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(ProxSpace.lg))
            Button(
                onClick = {
                    val record = when (tab) {
                        PickerTab.Auto -> null
                        PickerTab.Catalog -> {
                            val slug = selectedSlug ?: return@Button
                            GuestIconRecord(
                                mode = GuestIconMode.Cdn.id,
                                slug = slug,
                                updatedAt = System.currentTimeMillis(),
                            )
                        }
                        PickerTab.Upload -> {
                            val path = uploadPath ?: return@Button
                            GuestIconRecord(
                                mode = GuestIconMode.Upload.id,
                                uri = path,
                                updatedAt = System.currentTimeMillis(),
                            )
                        }
                        PickerTab.None -> GuestIconRecord(
                            mode = GuestIconMode.None.id,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    onSave(record)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.guest_icon_done))
            }
        }
    }
}

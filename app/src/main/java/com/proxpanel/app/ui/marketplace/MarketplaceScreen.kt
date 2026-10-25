package com.proxpanel.app.ui.marketplace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.R
import com.proxpanel.app.data.MarketplaceScript
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.ui.components.AppearanceDialog
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(viewModel: MarketplaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filtered = viewModel.filtered()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val copiedMessage = stringResource(R.string.apps_snackbar_copied)
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
                title = stringResource(R.string.apps_title),
                subtitle = stringResource(R.string.apps_subtitle),
                infoBody = stringResource(R.string.info_apps),
                embeddedInShell = true,
                actions = {
                    IconButton(onClick = { appearanceOpen = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.cd_view_options))
                    }
                    IconButton(onClick = viewModel::refresh) {
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
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.apps_search_placeholder),
            )
            Row(
                modifier = Modifier.padding(horizontal = ProxSpace.screen, vertical = ProxSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
            ) {
                FilterChip(
                    selected = state.sort == MarketplaceSort.Popular,
                    onClick = {
                        haptics.tick()
                        viewModel.setSort(MarketplaceSort.Popular)
                    },
                    label = { Text(stringResource(R.string.apps_sort_popular)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = state.sort == MarketplaceSort.Name,
                    onClick = {
                        haptics.tick()
                        viewModel.setSort(MarketplaceSort.Name)
                    },
                    label = { Text(stringResource(R.string.apps_sort_name)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
            Text(
                stringResource(R.string.apps_install_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ProxSpace.screen),
            )
            Spacer(Modifier.height(ProxSpace.sm))

            when {
                state.loading && filtered.isEmpty() -> ProxLoadingState(label = stringResource(R.string.apps_loading_catalog))
                filtered.isEmpty() -> ProxEmptyState(
                    title = state.error ?: stringResource(R.string.apps_empty),
                    icon = Icons.Outlined.Storefront,
                )
                else -> {
                    val onCopy: (MarketplaceScript) -> Unit = { script ->
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("install", script.command))
                        scope.launch { snackbar.showSnackbar(copiedMessage) }
                    }
                    val onOpen: (MarketplaceScript) -> Unit = { script ->
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(script.pageUrl)),
                        )
                    }
                    when (state.viewMode) {
                        NodesViewMode.Cards -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = ProxListPadding,
                                verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                            ) {
                                items(filtered, key = { it.slug }) { script ->
                                    ScriptCard(
                                        script = script,
                                        dense = false,
                                        onCopy = { onCopy(script) },
                                        onOpen = { onOpen(script) },
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
                                gridItems(filtered, key = { it.slug }) { script ->
                                    ScriptCard(
                                        script = script,
                                        dense = true,
                                        onCopy = { onCopy(script) },
                                        onOpen = { onOpen(script) },
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
                                items(filtered, key = { it.slug }) { script ->
                                    ScriptCompactRow(
                                        script = script,
                                        onCopy = { onCopy(script) },
                                        onOpen = { onOpen(script) },
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
private fun ScriptCard(
    script: MarketplaceScript,
    dense: Boolean,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    ProxCard {
        Row(horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm)) {
            Text(
                script.name,
                style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = if (dense) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KindChip(text = script.kind.uppercase(), kind = script.kind)
        }
        Spacer(Modifier.height(ProxSpace.sm))
        Text(
            script.description.take(if (dense) 100 else 180),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (dense) 3 else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (script.categories.isNotEmpty()) {
            Spacer(Modifier.height(ProxSpace.xs))
            Text(
                script.categories.take(3).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (script.installs > 0) {
            Spacer(Modifier.height(ProxSpace.xs))
            Text(
                stringResource(R.string.apps_installs, formatInstalls(script.installs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy))
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.action_open))
            }
        }
    }
}

@Composable
private fun ScriptCompactRow(
    script: MarketplaceScript,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    ProxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                ) {
                    Text(
                        script.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    KindChip(text = script.kind.uppercase(), kind = script.kind)
                }
                Text(
                    script.description.take(90),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (script.installs > 0) {
                    Text(
                        stringResource(R.string.apps_installs, formatInstalls(script.installs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy))
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.action_open))
            }
        }
    }
}

private fun formatInstalls(count: Int): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)

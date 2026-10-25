package com.proxpanel.app.ui.media

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.R
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.ui.components.AppearanceDialog
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.util.rememberAppHaptics
import com.proxpanel.app.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(viewModel: MediaViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    var query by remember { mutableStateOf("") }
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
    val filtered = remember(state.items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.items
        else state.items.filter {
            it.volid.lowercase().contains(q) ||
                it.node.lowercase().contains(q) ||
                it.storage.lowercase().contains(q)
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
                title = stringResource(R.string.media_title),
                subtitle = stringResource(R.string.media_subtitle),
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
            ProxSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.media_search_placeholder),
            )
            Row(
                modifier = Modifier.padding(horizontal = ProxSpace.screen, vertical = ProxSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
            ) {
                FilterChip(
                    selected = state.tab == MediaTab.Iso,
                    onClick = { viewModel.setTab(MediaTab.Iso) },
                    label = { Text(stringResource(R.string.media_tab_isos)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = state.tab == MediaTab.Template,
                    onClick = { viewModel.setTab(MediaTab.Template) },
                    label = { Text(stringResource(R.string.media_tab_templates)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.items.isEmpty() -> ProxLoadingState()
                    filtered.isEmpty() -> ProxEmptyState(
                        title = state.error
                            ?: if (query.isNotBlank()) stringResource(R.string.empty_no_results) else stringResource(R.string.media_empty),
                        icon = Icons.Outlined.Album,
                    )
                    else -> {
                        when (state.viewMode) {
                            NodesViewMode.Cards -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                                ) {
                                    items(filtered, key = { "${it.node}-${it.volid}" }) { item ->
                                        MediaCard(item = item, dense = false)
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
                                    gridItems(filtered, key = { "${it.node}-${it.volid}" }) { item ->
                                        MediaCard(item = item, dense = true)
                                    }
                                }
                            }
                            NodesViewMode.Compact -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = ProxListPadding,
                                    verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                                ) {
                                    items(filtered, key = { "${it.node}-${it.volid}" }) { item ->
                                        MediaCompactRow(item)
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
private fun MediaCard(item: MediaItem, dense: Boolean) {
    ProxCard {
        Text(
            item.volid.substringAfterLast('/'),
            style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            maxLines = if (dense) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(ProxSpace.xs))
        Text(
            "${item.node} · ${item.storage} · ${formatBytes(item.size)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (dense) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaCompactRow(item: MediaItem) {
    ProxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.volid.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.node} · ${item.storage} · ${formatBytes(item.size)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

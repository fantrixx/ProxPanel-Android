package com.proxpanel.app.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.proxpanel.app.data.ClusterResource
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.ui.components.AppearanceDialog
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.MetricBar
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.components.SectionLabel
import com.proxpanel.app.ui.components.StatusDot
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.util.rememberAppHaptics
import com.proxpanel.app.util.cpuPct
import com.proxpanel.app.util.formatBytes
import com.proxpanel.app.util.formatPct
import com.proxpanel.app.util.formatUptime
import com.proxpanel.app.util.usagePct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(viewModel: StorageViewModel) {
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
    val q = query.trim().lowercase()
    val nodes = remember(state.nodes, q) {
        if (q.isEmpty()) state.nodes
        else state.nodes.filter {
            (it.node ?: it.id).lowercase().contains(q) ||
                it.status.orEmpty().lowercase().contains(q)
        }
    }
    val storages = remember(state.storages, q) {
        if (q.isEmpty()) state.storages
        else state.storages.filter {
            it.storage.orEmpty().lowercase().contains(q) ||
                it.node.orEmpty().lowercase().contains(q) ||
                it.plugintype.orEmpty().lowercase().contains(q) ||
                it.content.orEmpty().lowercase().contains(q)
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
                title = stringResource(R.string.storage_title),
                subtitle = stringResource(R.string.storage_subtitle),
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
                placeholder = stringResource(R.string.storage_search_placeholder),
            )
            Spacer(Modifier.height(ProxSpace.sm))
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> ProxLoadingState()
                    nodes.isEmpty() && storages.isEmpty() -> ProxEmptyState(
                        title = state.error
                            ?: if (q.isNotEmpty()) stringResource(R.string.empty_no_results) else stringResource(R.string.storage_empty_data),
                        icon = Icons.Outlined.Storage,
                    )
                    else -> {
                        when (state.viewMode) {
                            NodesViewMode.Cards -> StorageCardsList(
                                nodes = nodes,
                                storages = storages,
                                error = state.error,
                                listState = listState,
                            )
                            NodesViewMode.Grid -> StorageGridList(
                                nodes = nodes,
                                storages = storages,
                                error = state.error,
                                gridState = gridState,
                            )
                            NodesViewMode.Compact -> StorageCompactList(
                                nodes = nodes,
                                storages = storages,
                                error = state.error,
                                listState = listState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCardsList(
    nodes: List<ClusterResource>,
    storages: List<ClusterResource>,
    error: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = ProxListPadding,
        verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
    ) {
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        if (nodes.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.storage_section_nodes)) }
            items(nodes, key = { "n-${it.id}" }) { node ->
                NodeCard(node = node, compact = false)
            }
        }
        if (storages.isNotEmpty()) {
            item {
                Spacer(Modifier.height(ProxSpace.sm))
                SectionLabel(stringResource(R.string.storage_section_storage))
            }
            items(storages, key = { "s-${it.id}" }) { store ->
                StorageCard(store = store, compact = false)
            }
        }
    }
}

@Composable
private fun StorageGridList(
    nodes: List<ClusterResource>,
    storages: List<ClusterResource>,
    error: String?,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = ProxListPadding,
        verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
        horizontalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        if (nodes.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(stringResource(R.string.storage_section_nodes))
            }
            gridItems(nodes, key = { "n-${it.id}" }) { node ->
                NodeCard(node = node, compact = true)
            }
        }
        if (storages.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(stringResource(R.string.storage_section_storage))
            }
            gridItems(storages, key = { "s-${it.id}" }) { store ->
                StorageCard(store = store, compact = true)
            }
        }
    }
}

@Composable
private fun StorageCompactList(
    nodes: List<ClusterResource>,
    storages: List<ClusterResource>,
    error: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = ProxListPadding,
        verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
    ) {
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        if (nodes.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.storage_section_nodes)) }
            items(nodes, key = { "n-${it.id}" }) { node ->
                NodeCompactRow(node)
            }
        }
        if (storages.isNotEmpty()) {
            item {
                Spacer(Modifier.height(ProxSpace.sm))
                SectionLabel(stringResource(R.string.storage_section_storage))
            }
            items(storages, key = { "s-${it.id}" }) { store ->
                StorageCompactRow(store)
            }
        }
    }
}

@Composable
private fun NodeCard(node: ClusterResource, compact: Boolean) {
    val online = node.status.equals("online", ignoreCase = true)
    ProxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(running = online)
            Spacer(Modifier.width(ProxSpace.md))
            Text(
                node.node ?: node.id,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KindChip(text = stringResource(R.string.storage_chip_node), kind = "node")
        }
        Spacer(Modifier.height(ProxSpace.xs))
        Text(
            "${node.status ?: "unknown"} · ${stringResource(R.string.uptime_prefix, formatUptime(node.uptime))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) ProxSpace.sm else ProxSpace.md))
        MetricBar(stringResource(R.string.metric_cpu), formatPct(node.cpu), cpuPct(node.cpu))
        Spacer(Modifier.height(ProxSpace.sm))
        MetricBar(
            stringResource(R.string.metric_ram),
            "${formatBytes(node.mem)} / ${formatBytes(node.maxmem)}",
            usagePct(node.mem, node.maxmem),
        )
    }
}

@Composable
private fun StorageCard(store: ClusterResource, compact: Boolean) {
    ProxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${store.storage}",
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KindChip(
                text = store.plugintype?.uppercase() ?: "STORE",
                kind = "storage",
            )
        }
        Spacer(Modifier.height(ProxSpace.xs))
        Text(
            listOfNotNull(store.node, store.status, store.content).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) ProxSpace.sm else ProxSpace.md))
        MetricBar(
            stringResource(R.string.metric_used),
            "${formatBytes(store.disk)} / ${formatBytes(store.maxdisk)}",
            usagePct(store.disk, store.maxdisk),
        )
    }
}

@Composable
private fun NodeCompactRow(node: ClusterResource) {
    val online = node.status.equals("online", ignoreCase = true)
    ProxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(running = online)
            Spacer(Modifier.width(ProxSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.node ?: node.id,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatPct(node.cpu)} CPU · ${formatBytes(node.mem)} RAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KindChip(text = stringResource(R.string.storage_chip_node), kind = "node")
        }
    }
}

@Composable
private fun StorageCompactRow(store: ClusterResource) {
    ProxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    store.storage.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        store.node,
                        "${formatBytes(store.disk)} / ${formatBytes(store.maxdisk)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KindChip(
                text = store.plugintype?.uppercase() ?: "STORE",
                kind = "storage",
            )
        }
    }
}

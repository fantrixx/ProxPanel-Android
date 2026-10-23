package com.proxpanel.app.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.res.stringResource
import com.proxpanel.app.R
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.proxpanel.app.util.cpuPct
import com.proxpanel.app.util.formatBytes
import com.proxpanel.app.util.formatPct
import com.proxpanel.app.util.formatUptime
import com.proxpanel.app.util.usagePct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(viewModel: StorageViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ProxTopBar(
                title = stringResource(R.string.storage_title),
                subtitle = stringResource(R.string.storage_subtitle),
                embeddedInShell = true,
                actions = {
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
                        LazyColumn(
                            contentPadding = ProxListPadding,
                            verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                        ) {
                            if (state.error != null) {
                                item {
                                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (nodes.isNotEmpty()) {
                                item { SectionLabel(stringResource(R.string.storage_section_nodes)) }
                                items(nodes, key = { it.id }) { node ->
                                    val online = node.status.equals("online", ignoreCase = true)
                                    ProxCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            StatusDot(running = online)
                                            Spacer(Modifier.width(ProxSpace.md))
                                            Text(
                                                node.node ?: node.id,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            KindChip(text = stringResource(R.string.storage_chip_node), kind = "node")
                                        }
                                        Spacer(Modifier.height(ProxSpace.xs))
                                        Text(
                                            "${node.status ?: "unknown"} · ${stringResource(R.string.uptime_prefix, formatUptime(node.uptime))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(ProxSpace.md))
                                        MetricBar(stringResource(R.string.metric_cpu), formatPct(node.cpu), cpuPct(node.cpu))
                                        Spacer(Modifier.height(ProxSpace.sm))
                                        MetricBar(
                                            stringResource(R.string.metric_ram),
                                            "${formatBytes(node.mem)} / ${formatBytes(node.maxmem)}",
                                            usagePct(node.mem, node.maxmem),
                                        )
                                    }
                                }
                            }
                            if (storages.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(ProxSpace.sm))
                                    SectionLabel(stringResource(R.string.storage_section_storage))
                                }
                                items(storages, key = { it.id }) { store ->
                                    ProxCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${store.storage}",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            KindChip(
                                                text = store.plugintype?.uppercase() ?: "STORE",
                                                kind = "storage",
                                            )
                                        }
                                        Spacer(Modifier.height(ProxSpace.xs))
                                        Text(
                                            listOfNotNull(store.node, store.status, store.content)
                                                .joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(ProxSpace.md))
                                        MetricBar(
                                            stringResource(R.string.metric_used),
                                            "${formatBytes(store.disk)} / ${formatBytes(store.maxdisk)}",
                                            usagePct(store.disk, store.maxdisk),
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
}

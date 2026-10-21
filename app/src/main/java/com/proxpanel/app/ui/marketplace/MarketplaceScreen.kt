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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxEmptyState
import com.proxpanel.app.ui.components.ProxListPadding
import com.proxpanel.app.ui.components.ProxLoadingState
import com.proxpanel.app.ui.components.ProxSearchBar
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.theme.ProxSpace
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(viewModel: MarketplaceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filtered = viewModel.filtered()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ProxTopBar(
                title = "Apps",
                subtitle = "Community Scripts",
                actions = {
                    IconButton(onClick = viewModel::refresh) {
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
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Scripts suchen…",
            )
            Spacer(Modifier.height(ProxSpace.sm))
            Text(
                "Install-Befehl auf dem Proxmox-Host ausführen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ProxSpace.screen),
            )
            Spacer(Modifier.height(ProxSpace.sm))

            when {
                state.loading && filtered.isEmpty() -> ProxLoadingState(label = "Katalog laden…")
                filtered.isEmpty() -> ProxEmptyState(
                    title = state.error ?: "Keine Scripts",
                    icon = Icons.Outlined.Storefront,
                )
                else -> {
                    LazyColumn(
                        contentPadding = ProxListPadding,
                        verticalArrangement = Arrangement.spacedBy(ProxSpace.listGap),
                    ) {
                        items(filtered, key = { it.slug }) { script ->
                            ProxCard {
                                Row(horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm)) {
                                    Text(
                                        script.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    KindChip(text = script.kind.uppercase(), kind = script.kind)
                                }
                                Spacer(Modifier.height(ProxSpace.sm))
                                Text(
                                    script.description.take(180),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (script.categories.isNotEmpty()) {
                                    Spacer(Modifier.height(ProxSpace.xs))
                                    Text(
                                        script.categories.take(3).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("install", script.command))
                                        scope.launch { snackbar.showSnackbar("Befehl kopiert") }
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                    }
                                    IconButton(onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(script.pageUrl)),
                                        )
                                    }) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open")
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

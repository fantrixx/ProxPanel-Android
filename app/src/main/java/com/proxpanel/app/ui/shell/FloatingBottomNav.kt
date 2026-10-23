package com.proxpanel.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.proxpanel.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class HomeTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Overview("overview", R.string.nav_overview, Icons.Default.Dashboard),
    Backups("backups", R.string.nav_backups, Icons.Default.Inventory2),
    Storage("storage", R.string.nav_storage, Icons.Default.Storage),
    Tasks("tasks", R.string.nav_tasks, Icons.AutoMirrored.Filled.ListAlt),
    Media("media", R.string.nav_media, Icons.Default.Dns),
    Apps("apps", R.string.nav_apps, Icons.Default.Storefront),
    Settings("settings", R.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun FloatingBottomNav(
    selectedRoute: String?,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(20.dp, RoundedCornerShape(36.dp), ambientColor = MaterialTheme.colorScheme.scrim.copy(0.35f))
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTab.entries.forEach { tab ->
                PillNavItem(
                    icon = tab.icon,
                    contentDescription = stringResource(tab.labelRes),
                    selected = selectedRoute == tab.route,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun PillNavItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    } else {
        ColorTransparent
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val ColorTransparent = androidx.compose.ui.graphics.Color.Transparent

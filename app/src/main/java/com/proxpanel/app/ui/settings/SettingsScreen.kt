package com.proxpanel.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proxpanel.app.BuildConfig
import com.proxpanel.app.R
import com.proxpanel.app.data.AccentChoice
import com.proxpanel.app.data.RefreshInterval
import com.proxpanel.app.util.hintRes
import com.proxpanel.app.util.labelRes
import com.proxpanel.app.ui.components.ProxCard
import com.proxpanel.app.ui.components.ProxTopBar
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.theme.accentPalette
import com.proxpanel.app.ui.util.rememberAppHaptics

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    var privacyOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ProxTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                embeddedInShell = true,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ProxSpace.screen)
                .padding(bottom = ProxSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(ProxSpace.lg),
        ) {
            Spacer(Modifier.height(ProxSpace.xs))

            SettingsSection(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.settings_refresh_title),
                subtitle = stringResource(R.string.settings_refresh_subtitle),
            ) {
                RefreshIntervalPicker(
                    selected = state.refreshInterval,
                    onSelect = {
                        haptics.tick()
                        viewModel.setRefreshInterval(it)
                    },
                )
            }

            SettingsSection(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_accent_title),
                subtitle = stringResource(R.string.settings_accent_subtitle),
            ) {
                AccentPicker(
                    selected = state.accent,
                    onSelect = {
                        haptics.confirm()
                        viewModel.setAccent(it)
                    },
                )
            }

            SettingsSection(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_app_info_title),
                subtitle = stringResource(R.string.settings_app_info_subtitle),
            ) {
                InfoRow(label = stringResource(R.string.settings_info_app), value = "ProxPanel")
                Spacer(Modifier.height(ProxSpace.sm))
                InfoRow(label = stringResource(R.string.settings_info_version), value = BuildConfig.VERSION_NAME)
                Spacer(Modifier.height(ProxSpace.sm))
                InfoRow(label = stringResource(R.string.settings_info_build), value = BuildConfig.VERSION_CODE.toString())
                Spacer(Modifier.height(ProxSpace.md))
                PrivacyExpandable(
                    expanded = privacyOpen,
                    onToggle = {
                        haptics.tick()
                        privacyOpen = !privacyOpen
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ProxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(ProxSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(ProxSpace.lg))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefreshIntervalPicker(
    selected: RefreshInterval,
    onSelect: (RefreshInterval) -> Unit,
) {
    val pulse by animateFloatAsState(
        targetValue = if (selected == RefreshInterval.Manual) 0.35f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sync-pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * pulse),
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    ),
                ),
            )
            .padding(ProxSpace.md),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(ProxSpace.sm))
                Text(
                    text = stringResource(selected.labelRes()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(selected.hintRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(ProxSpace.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                verticalArrangement = Arrangement.spacedBy(ProxSpace.sm),
            ) {
                RefreshInterval.entries.forEach { interval ->
                    val isSelected = interval == selected
                    val bg by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "interval-bg",
                    )
                    val fg by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        label = "interval-fg",
                    )
                    val scale by animateFloatAsState(
                        if (isSelected) 1.04f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "interval-scale",
                    )
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .clip(MaterialTheme.shapes.small)
                            .background(bg)
                            .clickable { onSelect(interval) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(interval.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                            color = fg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentPicker(
    selected: AccentChoice,
    onSelect: (AccentChoice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AccentChoice.entries.forEach { choice ->
            val palette = accentPalette(choice)
            val isSelected = choice == selected
            val ring by animateColorAsState(
                if (isSelected) palette.primary else Color.Transparent,
                label = "accent-ring",
            )
            val scale by animateFloatAsState(
                if (isSelected) 1.08f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "accent-scale",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, ring, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                        .clickable { onSelect(choice) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(choice.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrivacyExpandable(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .clickable(onClick = onToggle)
            .padding(ProxSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Policy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(ProxSpace.sm))
            Text(
                text = stringResource(R.string.settings_privacy_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(ProxSpace.md))
                Text(
                    text = stringResource(R.string.privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

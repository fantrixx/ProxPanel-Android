package com.proxpanel.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.proxpanel.app.R
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.ui.theme.ProxSpace

@Composable
fun AppearanceDialog(
    viewMode: NodesViewMode,
    onViewMode: (NodesViewMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ViewModeOption(
            mode = NodesViewMode.Cards,
            title = stringResource(R.string.view_mode_cards_title),
            hint = stringResource(R.string.view_mode_cards_hint),
            icon = Icons.Default.ViewAgenda,
        ),
        ViewModeOption(
            mode = NodesViewMode.Grid,
            title = stringResource(R.string.view_mode_grid_title),
            hint = stringResource(R.string.view_mode_grid_hint),
            icon = Icons.Default.GridView,
        ),
        ViewModeOption(
            mode = NodesViewMode.Compact,
            title = stringResource(R.string.view_mode_list_title),
            hint = stringResource(R.string.view_mode_list_hint),
            icon = Icons.AutoMirrored.Filled.ViewList,
        ),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ProxSpace.screen),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ProxSpace.lg),
            ) {
                Text(
                    text = stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.appearance_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ProxSpace.lg))
                Column(verticalArrangement = Arrangement.spacedBy(ProxSpace.sm)) {
                    options.forEach { option ->
                        ViewModeOptionCard(
                            option = option,
                            selected = viewMode == option.mode,
                            onClick = { onViewMode(option.mode) },
                        )
                    }
                }
                Spacer(Modifier.height(ProxSpace.lg))
                Button(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

private data class ViewModeOption(
    val mode: NodesViewMode,
    val title: String,
    val hint: String,
    val icon: ImageVector,
)

@Composable
private fun ViewModeOptionCard(
    option: ViewModeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        label = "view-border",
    )
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "view-bg",
    )
    val scale by animateFloatAsState(
        if (selected) 1.015f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "view-scale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.5.dp, border, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewModePreview(mode = option.mode, selected = selected)
        Spacer(Modifier.width(ProxSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = option.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(ProxSpace.sm))
        if (selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

@Composable
private fun ViewModePreview(mode: NodesViewMode, selected: Boolean) {
    val accent = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val fill = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        fill,
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    ),
                ),
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            NodesViewMode.Cards -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(accent.copy(alpha = 0.55f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accent.copy(alpha = 0.35f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accent.copy(alpha = 0.22f)),
                    )
                }
            }
            NodesViewMode.Grid -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(accent.copy(alpha = 0.45f)),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(accent.copy(alpha = 0.25f)),
                            )
                        }
                    }
                }
            }
            NodesViewMode.Compact -> {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(1f - i * 0.08f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.5f - i * 0.12f)),
                        )
                    }
                }
            }
        }
    }
}

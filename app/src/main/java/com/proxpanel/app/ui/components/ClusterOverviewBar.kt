package com.proxpanel.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.proxpanel.app.R
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.util.formatBytes

data class ClusterOverview(
    val nodesOnline: Int = 0,
    val nodesTotal: Int = 0,
    val guestsRunning: Int = 0,
    val guestsTotal: Int = 0,
    val storageFree: Long? = null,
    val storageTotal: Long? = null,
)

@Composable
fun ClusterOverviewBar(
    overview: ClusterOverview,
    pulsing: Boolean,
    modifier: Modifier = Modifier,
) {
    val pulse by animateFloatAsState(
        targetValue = if (pulsing) 1f else 0.55f,
        animationSpec = tween(450),
        label = "cluster-pulse",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProxSpace.screen)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .graphicsLayer { alpha = 0.7f + pulse * 0.3f },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverviewChip(
            label = stringResource(R.string.cluster_nodes),
            value = if (overview.nodesTotal > 0) {
                "${overview.nodesOnline}/${overview.nodesTotal}"
            } else {
                stringResource(R.string.empty_dash)
            },
            accent = overview.nodesTotal > 0 && overview.nodesOnline == overview.nodesTotal,
        )
        OverviewChip(
            label = stringResource(R.string.cluster_running),
            value = "${overview.guestsRunning}/${overview.guestsTotal}",
            accent = overview.guestsRunning > 0,
        )
        OverviewChip(
            label = stringResource(R.string.cluster_free),
            value = when {
                overview.storageFree != null -> formatBytes(overview.storageFree)
                else -> stringResource(R.string.empty_dash)
            },
            accent = false,
        )
    }
}

@Composable
private fun OverviewChip(label: String, value: String, accent: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) ProxGood else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

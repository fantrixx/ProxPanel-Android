package com.proxpanel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.proxpanel.app.R
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.ui.theme.ProxWarn
import com.proxpanel.app.util.formatBytes

data class ClusterNodeSummary(
    val name: String,
    val online: Boolean,
    val cpuPct: Float? = null,
    val memPct: Float? = null,
)

data class ClusterOverview(
    val nodesOnline: Int = 0,
    val nodesTotal: Int = 0,
    val guestsRunning: Int = 0,
    val guestsTotal: Int = 0,
    val storageFree: Long? = null,
    val storageTotal: Long? = null,
    val storageUsed: Long? = null,
    val haManaged: Int = 0,
    val nodes: List<ClusterNodeSummary> = emptyList(),
    val tasksRunning: Int = 0,
    val tasksFailed: Int = 0,
    val activeJobs: Int = 0,
)

/**
 * Compact cluster dashboard built from `/cluster/resources`, recent tasks, and in-app jobs.
 * Collapsed by default: header + one-line summary; expand for full metrics.
 */
@Composable
fun ClusterOverviewBar(
    overview: ClusterOverview,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    @Suppress("UNUSED_PARAMETER") pulsing: Boolean,
    modifier: Modifier = Modifier,
) {
    val nodesLabel = if (overview.nodesTotal > 0) {
        "${overview.nodesOnline}/${overview.nodesTotal}"
    } else {
        stringResource(R.string.empty_dash)
    }
    val freeLabel = overview.storageFree?.let { formatBytes(it) } ?: stringResource(R.string.empty_dash)
    val collapsedSummary = buildString {
        append(stringResource(R.string.cluster_nodes))
        append(' ')
        append(nodesLabel)
        append(" · ")
        append(stringResource(R.string.cluster_running))
        append(' ')
        append("${overview.guestsRunning}/${overview.guestsTotal}")
        append(" · ")
        append(stringResource(R.string.cluster_free))
        append(' ')
        append(freeLabel)
        if (overview.tasksFailed > 0) {
            append(" · ")
            append(stringResource(R.string.cluster_tasks_failed))
            append(' ')
            append(overview.tasksFailed)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProxSpace.screen)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = ProxSpace.md, vertical = ProxSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cluster_dashboard_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!expanded) {
                    Text(
                        text = collapsedSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.cd_collapse_cluster else R.string.cd_expand_cluster,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = ProxSpace.md, end = ProxSpace.md, bottom = ProxSpace.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                ) {
                    DashMetric(
                        label = stringResource(R.string.cluster_nodes),
                        value = nodesLabel,
                        accent = overview.nodesTotal > 0 && overview.nodesOnline == overview.nodesTotal,
                        warn = overview.nodesTotal > 0 && overview.nodesOnline < overview.nodesTotal,
                        modifier = Modifier.weight(1f),
                    )
                    DashMetric(
                        label = stringResource(R.string.cluster_running),
                        value = "${overview.guestsRunning}/${overview.guestsTotal}",
                        accent = overview.guestsRunning > 0,
                        modifier = Modifier.weight(1f),
                    )
                    DashMetric(
                        label = stringResource(R.string.cluster_free),
                        value = freeLabel,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (overview.storageTotal != null && overview.storageTotal > 0 && overview.storageUsed != null) {
                    Spacer(Modifier.height(ProxSpace.sm))
                    val usedPct = (overview.storageUsed.toDouble() / overview.storageTotal.toDouble() * 100.0)
                        .toFloat()
                        .coerceIn(0f, 100f)
                    MetricBar(
                        label = stringResource(
                            R.string.cluster_storage_used,
                            formatBytes(overview.storageUsed),
                            formatBytes(overview.storageTotal),
                        ),
                        valueText = "${usedPct.toInt()}%",
                        progress = usedPct,
                    )
                }

                if (overview.nodes.isNotEmpty()) {
                    Spacer(Modifier.height(ProxSpace.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
                    ) {
                        overview.nodes.forEach { node ->
                            NodeChip(node = node)
                        }
                    }
                }

                val showJobs = overview.activeJobs > 0 || overview.tasksRunning > 0 || overview.tasksFailed > 0
                val showHa = overview.haManaged > 0
                if (showJobs || showHa) {
                    Spacer(Modifier.height(ProxSpace.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ProxSpace.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showHa) {
                            StatusLine(
                                label = stringResource(R.string.cluster_ha),
                                value = overview.haManaged.toString(),
                                colorGood = true,
                            )
                        }
                        if (overview.activeJobs > 0) {
                            StatusLine(
                                label = stringResource(R.string.cluster_jobs),
                                value = overview.activeJobs.toString(),
                                colorGood = true,
                            )
                        }
                        if (overview.tasksRunning > 0) {
                            StatusLine(
                                label = stringResource(R.string.cluster_tasks_running),
                                value = overview.tasksRunning.toString(),
                                colorGood = true,
                            )
                        }
                        if (overview.tasksFailed > 0) {
                            StatusLine(
                                label = stringResource(R.string.cluster_tasks_failed),
                                value = overview.tasksFailed.toString(),
                                colorBad = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    warn: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                warn -> ProxWarn
                accent -> ProxGood
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NodeChip(node: ClusterNodeSummary) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (node.online) ProxGood else ProxBad),
        )
        Column {
            Text(
                text = node.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                node.cpuPct?.let { add("CPU ${it.toInt()}%") }
                node.memPct?.let { add("RAM ${it.toInt()}%") }
                if (!node.online) add(stringResource(R.string.cluster_node_offline))
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (node.online) MaterialTheme.colorScheme.onSurfaceVariant else ProxBad,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
    colorGood: Boolean = false,
    colorBad: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                colorBad -> ProxBad
                colorGood -> ProxGood
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

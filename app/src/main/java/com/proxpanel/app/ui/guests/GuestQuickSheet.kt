package com.proxpanel.app.ui.guests

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.jobs.GuestJobProgress
import com.proxpanel.app.ui.components.KindChip
import com.proxpanel.app.ui.components.StatusBadge
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxSpace
import com.proxpanel.app.util.formatUptime
import com.proxpanel.app.util.guestVisualStatus
import com.proxpanel.app.util.pendingLabelForAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestQuickSheet(
    guest: GuestItem,
    storages: List<String>,
    pending: PendingPower?,
    backup: GuestJobProgress?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onPower: (String) -> Unit,
    onBackup: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var backupMenu by remember { mutableStateOf(false) }
    val pendingVisual = pending?.let { pendingLabelForAction(it.action) }
    val visual = guestVisualStatus(guest.status, guest.qmpstatus, guest.lock, pendingVisual)
    val running = visual == "running"
    val backingUp = backup?.running == true
    val locked = busy || pending != null || backingUp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ProxSpace.screen)
                .padding(bottom = ProxSpace.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = guest.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${guest.kindLabel} ${guest.vmid} · ${guest.node}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KindChip(text = guest.kindLabel, kind = guest.type)
            }

            Spacer(Modifier.height(ProxSpace.md))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
            ) {
                StatusBadge(
                    status = guest.status,
                    qmpstatus = guest.qmpstatus,
                    lock = guest.lock,
                    pending = pendingVisual,
                )
                Text(
                    text = "Up ${formatUptime(guest.uptime)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (guest.ips.isNotEmpty()) {
                Spacer(Modifier.height(ProxSpace.sm))
                Text(
                    text = guest.ips.take(3).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(ProxSpace.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
            ) {
                if (running || pending?.action == "reboot") {
                    SheetAction(
                        icon = Icons.Default.PowerSettingsNew,
                        label = "Shut down",
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        onClick = { onPower("shutdown") },
                    )
                    SheetAction(
                        icon = Icons.Default.RestartAlt,
                        label = "Restart",
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        onClick = { onPower("reboot") },
                    )
                } else {
                    SheetAction(
                        icon = Icons.Default.PlayArrow,
                        label = "Start",
                        enabled = !locked,
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onPower("start") },
                    )
                }
            }
            Spacer(Modifier.height(ProxSpace.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ProxSpace.sm),
            ) {
                if (running) {
                    SheetAction(
                        icon = Icons.Default.Stop,
                        label = "Force stop",
                        enabled = !locked,
                        danger = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onPower("stop") },
                    )
                }
                SheetAction(
                    icon = Icons.Default.CloudUpload,
                    label = when {
                        backingUp && backup?.progress != null -> "${backup.progress.toInt()}%"
                        backingUp -> "Backup…"
                        else -> "Backup"
                    },
                    enabled = !locked && storages.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    loading = backingUp,
                    onClick = {
                        if (storages.size == 1) onBackup(storages.first())
                        else backupMenu = !backupMenu
                    },
                )
            }

            if (backupMenu) {
                Spacer(Modifier.height(ProxSpace.sm))
                storages.forEach { storage ->
                    TextButton(
                        onClick = {
                            backupMenu = false
                            onBackup(storage)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Backup → $storage")
                    }
                }
            }

            Spacer(Modifier.height(ProxSpace.lg))
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenDetail()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open full details")
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    loading: Boolean = false,
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
        ) {
            SheetLabel(icon, label, loading)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            colors = if (danger) {
                ButtonDefaults.outlinedButtonColors(contentColor = ProxBad)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            border = BorderStroke(
                1.dp,
                if (danger) ProxBad.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
            ),
        ) {
            SheetLabel(icon, label, loading)
        }
    }
}

@Composable
private fun SheetLabel(icon: ImageVector, label: String, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.width(6.dp))
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

package com.proxpanel.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxMuted
import com.proxpanel.app.ui.theme.ProxWarn
import com.proxpanel.app.util.guestVisualStatus
import com.proxpanel.app.util.statusLabel

private enum class StatusTone { Good, Warn, Accent, Muted }

private fun toneFor(status: String): StatusTone {
    val s = status.lowercase()
    return when {
        s == "running" || s == "online" -> StatusTone.Good
        s == "shutting down" || s == "stopping" || s == "rebooting" ||
            s == "paused" || s == "suspended" -> StatusTone.Warn
        s == "starting" || s == "creating" || s == "migrating" -> StatusTone.Accent
        else -> StatusTone.Muted
    }
}

@Composable
fun StatusBadge(
    status: String?,
    qmpstatus: String? = null,
    lock: String? = null,
    pending: String? = null,
    modifier: Modifier = Modifier,
) {
    val visual = guestVisualStatus(status, qmpstatus, lock, pending)
    val tone = toneFor(visual)
    val bg: Color
    val fg: Color
    val dot: Color
    when (tone) {
        StatusTone.Good -> {
            bg = ProxGood.copy(alpha = 0.15f); fg = ProxGood; dot = ProxGood
        }
        StatusTone.Warn -> {
            bg = ProxWarn.copy(alpha = 0.15f); fg = ProxWarn; dot = ProxWarn
        }
        StatusTone.Accent -> {
            bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            fg = MaterialTheme.colorScheme.primary
            dot = MaterialTheme.colorScheme.primary
        }
        StatusTone.Muted -> {
            bg = ProxMuted.copy(alpha = 0.15f); fg = ProxMuted; dot = ProxMuted
        }
    }
    val pulsing = tone != StatusTone.Muted
    val pulse = rememberInfiniteTransition(label = "status-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-s",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-a",
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                if (pulsing) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                            .clip(CircleShape)
                            .background(dot.copy(alpha = 0.45f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = statusLabel(visual),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

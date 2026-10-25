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
    val bg = when (tone) {
        StatusTone.Good -> ProxGood.copy(alpha = 0.15f)
        StatusTone.Warn -> ProxWarn.copy(alpha = 0.15f)
        StatusTone.Accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        StatusTone.Muted -> ProxMuted.copy(alpha = 0.15f)
    }
    val fg = when (tone) {
        StatusTone.Good -> ProxGood
        StatusTone.Warn -> ProxWarn
        StatusTone.Accent -> MaterialTheme.colorScheme.primary
        StatusTone.Muted -> ProxMuted
    }
    val wantPulse = tone == StatusTone.Warn || tone == StatusTone.Accent
    // Only subscribe to the scroll gate when a pulse would run — otherwise every
    // running badge would recompose on fling start and hitch the list.
    val scrolling = if (wantPulse) {
        com.proxpanel.app.ui.util.LocalListScrollSession.current.scrolling
    } else {
        false
    }
    val pulsing = wantPulse && !scrolling

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
            if (pulsing) {
                PulsingStatusDot(color = fg)
            } else {
                StaticStatusDot(color = fg)
            }
            Spacer(Modifier.width(6.dp))
            Text(text = statusLabel(visual), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StaticStatusDot(color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun PulsingStatusDot(color: Color) {
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
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.45f)),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

package com.proxpanel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.proxpanel.app.jobs.JobCelebration
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxSpace
import kotlinx.coroutines.delay

@Composable
fun SuccessBurstOverlay(
    celebration: JobCelebration?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = celebration != null,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.86f, animationSpec = tween(280, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.92f),
        modifier = modifier.fillMaxSize(),
    ) {
        val c = celebration ?: return@AnimatedVisibility
        LaunchedEffect(c.id) {
            delay(2_400)
            onDismiss()
        }
        val glow by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(500),
            label = "glow",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(ProxSpace.xxl)
                    .graphicsLayer { alpha = glow },
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(ProxGood.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ProxGood),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.height(ProxSpace.lg))
                Text(
                    text = c.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = ProxGood,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(ProxSpace.sm))
                Text(
                    text = c.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

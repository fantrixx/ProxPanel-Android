package com.proxpanel.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val nums = values.filter { it.isFinite() }
    if (nums.size < 2) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val min = nums.minOrNull() ?: 0f
        val max = nums.maxOrNull() ?: 1f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        nums.forEachIndexed { i, v ->
            val x = (i.toFloat() / (nums.lastIndex)) * size.width
            val y = size.height - ((v - min) / span) * (size.height - 4f) - 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // endpoint dot
        val lastX = size.width
        val lastY = size.height - ((nums.last() - min) / span) * (size.height - 4f) - 2f
        drawCircle(color = color, radius = 3f, center = Offset(lastX, lastY))
    }
}

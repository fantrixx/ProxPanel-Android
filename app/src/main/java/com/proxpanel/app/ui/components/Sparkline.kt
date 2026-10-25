package com.proxpanel.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxpanel.app.R
import com.proxpanel.app.ui.theme.ProxBad
import com.proxpanel.app.ui.theme.ProxGood
import com.proxpanel.app.ui.theme.ProxWarn

/**
 * CPU history with fixed 0–100% scale so the curve height reflects real load.
 */
@Composable
fun CpuSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val nums = remember(values) { values.filter { it.isFinite() } }
    if (nums.size < 2) return

    val last = nums.last()
    val dataMin = nums.minOrNull() ?: 0f
    val dataMax = nums.maxOrNull() ?: 0f
    val color = when {
        last >= 85f -> ProxBad
        last >= 60f -> ProxWarn
        else -> ProxGood
    }
    val guide = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Snapshot for Canvas — avoids capturing unstable list into draw lambda churn.
    val points = remember(nums) { nums.toFloatArray() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(28.dp)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("100%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = muted)
                Text("50%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = muted)
                Text("0%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = muted)
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    fun yOf(v: Float): Float {
                        val clamped = v.coerceIn(0f, 100f)
                        return size.height - (clamped / 100f) * (size.height - 4f) - 2f
                    }
                    val lastIndex = (points.size - 1).coerceAtLeast(1)
                    listOf(25f, 50f, 75f).forEach { t ->
                        val gy = yOf(t)
                        drawLine(guide, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                    }
                    val path = Path()
                    for (i in points.indices) {
                        val x = (i.toFloat() / lastIndex) * size.width
                        val y = yOf(points[i])
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    drawCircle(color = color, radius = 3.5f, center = Offset(size.width, yOf(last)))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${last.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.cpu_spark_range, dataMin.toInt(), dataMax.toInt()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = muted,
        )
    }
}

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Compact history chart. When [fixedMin]/[fixedMax] are set (e.g. 0–100 for CPU),
 * the Y axis stays absolute so the curve’s height reflects real load — not just local jitter.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    showScale: Boolean = false,
) {
    val nums = values.filter { it.isFinite() }
    if (nums.size < 2) return

    val dataMin = nums.minOrNull() ?: 0f
    val dataMax = nums.maxOrNull() ?: 1f
    val yMin = fixedMin ?: dataMin
    val yMax = fixedMax ?: dataMax
    val span = (yMax - yMin).takeIf { it > 1e-4f } ?: 1f

    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showScale) 40.dp else 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showScale) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(28.dp)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = formatAxis(yMax),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = muted,
                    )
                    Text(
                        text = formatAxis((yMin + yMax) / 2f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = muted,
                    )
                    Text(
                        text = formatAxis(yMin),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = muted,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    fun yOf(v: Float): Float {
                        val clamped = v.coerceIn(yMin, yMax)
                        return size.height - ((clamped - yMin) / span) * (size.height - 4f) - 2f
                    }

                    // Absolute reference lines (25 / 50 / 75 % of fixed scale)
                    if (fixedMin != null && fixedMax != null) {
                        listOf(0.25f, 0.5f, 0.75f).forEach { t ->
                            val gy = yOf(yMin + span * t)
                            drawLine(
                                color = guideColor,
                                start = Offset(0f, gy),
                                end = Offset(size.width, gy),
                                strokeWidth = 1f,
                            )
                        }
                    }

                    val line = Path()
                    val fill = Path()
                    nums.forEachIndexed { i, v ->
                        val x = (i.toFloat() / nums.lastIndex) * size.width
                        val y = yOf(v)
                        if (i == 0) {
                            line.moveTo(x, y)
                            fill.moveTo(x, size.height)
                            fill.lineTo(x, y)
                        } else {
                            line.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                    }
                    fill.lineTo(size.width, size.height)
                    fill.close()

                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.02f)),
                        ),
                    )
                    drawPath(
                        path = line,
                        color = color,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    drawCircle(
                        color = color,
                        radius = 3.5f,
                        center = Offset(size.width, yOf(nums.last())),
                    )
                }
            }

            if (showScale) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${nums.last().toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
        }

        if (showScale) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.cpu_spark_range,
                    dataMin.toInt(),
                    dataMax.toInt(),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = muted,
            )
        }
    }
}

@Composable
fun CpuSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val last = values.lastOrNull() ?: 0f
    val color = when {
        last >= 85f -> ProxBad
        last >= 60f -> ProxWarn
        else -> ProxGood
    }
    Sparkline(
        values = values,
        modifier = modifier,
        color = color,
        fixedMin = 0f,
        fixedMax = 100f,
        showScale = true,
    )
}

private fun formatAxis(v: Float): String = "${v.toInt()}%"

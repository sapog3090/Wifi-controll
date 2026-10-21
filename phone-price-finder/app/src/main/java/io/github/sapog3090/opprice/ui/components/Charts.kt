package io.github.sapog3090.opprice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.sapog3090.opprice.ui.date
import io.github.sapog3090.opprice.ui.groupDigits
import io.github.sapog3090.opprice.ui.theme.LocalExtraColors

data class ChartPoint(val time: Long, val value: Long)

/** Мини-график для строки списка: только форма, без осей. */
@Composable
fun Sparkline(points: List<Long>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val last = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val lo = points.min().toFloat()
        val hi = points.max().toFloat()
        val span = (hi - lo).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        fun at(i: Int) = Offset(i * stepX, size.height - (points[i] - lo) / span * size.height)
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(last, 2.5.dp.toPx(), at(points.lastIndex))
    }
}

/**
 * История минимальной цены: ступенчатая линия (цена держится до следующей проверки),
 * пунктиром — медиана, точкой — исторический минимум.
 */
@Composable
fun PriceChart(min: List<ChartPoint>, median: List<ChartPoint>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = LocalExtraColors.current.hairline
    val accent = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted, fontFeatureSettings = "tnum")

    Canvas(modifier) {
        if (min.isEmpty()) return@Canvas
        val all = min.map { it.value } + median.map { it.value }
        val rawLo = all.min()
        val rawHi = all.max()
        val pad = ((rawHi - rawLo) * 0.08).toLong().coerceAtLeast(500)
        val lo = rawLo - pad
        val hi = rawHi + pad
        val t0 = min.first().time
        val t1 = maxOf(min.last().time, t0 + 1)

        val labelWidth = measurer.measure(groupDigits(hi), labelStyle).size.width + 8.dp.toPx()
        val bottomLabel = measurer.measure("0", labelStyle).size.height + 6.dp.toPx()
        val left = labelWidth
        val w = size.width - left
        val h = size.height - bottomLabel

        fun x(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * w
        fun y(v: Long) = h - (v - lo).toFloat() / (hi - lo) * h

        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(size.width, yy), 1.dp.toPx())
            val label = measurer.measure(groupDigits(v), labelStyle)
            drawText(label, topLeft = Offset(0f, yy - label.size.height / 2f))
        }

        fun stepPath(points: List<ChartPoint>) = Path().apply {
            moveTo(x(points.first().time), y(points.first().value))
            for (i in 1 until points.size) {
                lineTo(x(points[i].time), y(points[i - 1].value))
                lineTo(x(points[i].time), y(points[i].value))
            }
            lineTo(x(t1), y(points.last().value))
        }

        if (median.size >= 2) {
            drawPath(
                stepPath(median),
                muted,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
            )
        }
        drawPath(stepPath(min), ink, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))

        val lowest = min.minBy { it.value }
        drawCircle(accent, 4.dp.toPx(), Offset(x(lowest.time), y(lowest.value)))

        val first = measurer.measure(date(t0), labelStyle)
        drawText(first, topLeft = Offset(left, h + 4.dp.toPx()))
        val last = measurer.measure(date(t1), labelStyle)
        if (last.size.width + first.size.width < w) {
            drawText(last, topLeft = Offset(size.width - last.size.width, h + 4.dp.toPx()))
        }
    }
}

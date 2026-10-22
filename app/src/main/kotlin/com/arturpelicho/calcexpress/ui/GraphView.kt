package com.arturpelicho.calcexpress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.arturpelicho.calcexpress.core.Format
import com.arturpelicho.calcexpress.core.GraphSpec
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private fun safe(f: (Double) -> Double, x: Double): Double = try { f(x) } catch (e: Exception) { Double.NaN }

private data class Viewport(val x0: Double, val x1: Double, val y0: Double, val y1: Double)

private fun autoY(xs: List<Double>, fs: List<(Double) -> Double>): Pair<Double, Double> {
    val ys = xs.flatMap { x -> fs.map { safe(it, x) } }.filter { it.isFinite() }.sorted()
    if (ys.isEmpty()) return -10.0 to 10.0
    var lo = ys[(ys.size * 0.03).toInt()]
    var hi = ys[((ys.size - 1) * 0.97).toInt()]
    lo = minOf(lo, 0.0); hi = maxOf(hi, 0.0)
    if (hi - lo < 1e-9) { lo -= 1; hi += 1 }
    val pad = (hi - lo) * 0.12
    return (lo - pad) to (hi + pad)
}

private fun initialViewport(spec: GraphSpec): Viewport {
    val (x0, x1) = when (spec) {
        is GraphSpec.Plot -> if (spec.marks.isNotEmpty()) {
            val lo = spec.marks.min(); val hi = spec.marks.max()
            val w = maxOf(hi - lo, 4.0)
            (lo - w * 0.5) to (hi + w * 0.5)
        } else -10.0 to 10.0
        is GraphSpec.Area -> { val w = maxOf(abs(spec.b - spec.a), 1e-3); (minOf(spec.a, spec.b) - w * 0.3) to (maxOf(spec.a, spec.b) + w * 0.3) }
        is GraphSpec.Region -> { val w = maxOf(abs(spec.b - spec.a), 1e-3); (minOf(spec.a, spec.b) - w * 0.2) to (maxOf(spec.a, spec.b) + w * 0.2) }
        // Drawn by the Mohr's circle view, never by this plotter.
        is GraphSpec.MohrState -> -1.0 to 1.0
    }
    val xs = (0..200).map { x0 + (x1 - x0) * it / 200 }
    val fs = when (spec) {
        is GraphSpec.Plot -> listOf(spec.f) + spec.extra
        is GraphSpec.Area -> listOf(spec.f)
        is GraphSpec.Region -> listOf(spec.lo, spec.hi)
        is GraphSpec.MohrState -> emptyList()
    }
    val (y0, y1) = autoY(xs, fs)
    return Viewport(x0, x1, y0, y1)
}

private fun niceStep(range: Double): Double {
    val raw = range / 6
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    return mag * when { n < 1.5 -> 1.0; n < 3.5 -> 2.0; n < 7.5 -> 5.0; else -> 10.0 }
}

@Composable
fun GraphView(spec: GraphSpec, modifier: Modifier = Modifier) {
    val c = LocalCalcColors.current
    var vp by remember(spec) { mutableStateOf(initialViewport(spec)) }
    // Tapping the graph shows the values of every curve at that x.
    var traceX by remember(spec) { mutableStateOf<Double?>(null) }
    val palette = listOf(c.graphLine, c.highlight, c.accent, c.error)
    Canvas(
        // Clip so curves that leave the viewport never draw over the rest of the screen.
        modifier.clipToBounds().pointerInput(spec) {
            detectTapGestures { pos ->
                val v = vp
                val x = v.x0 + pos.x / size.width * (v.x1 - v.x0)
                traceX = if (traceX == null) x else null
            }
        }.pointerInput(spec) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val v = vp
                val w = size.width.toDouble(); val h = size.height.toDouble()
                val sx = (v.x1 - v.x0) / w; val sy = (v.y1 - v.y0) / h
                val cx = v.x0 + centroid.x * sx
                val cy = v.y1 - centroid.y * sy
                val z = 1.0 / zoom
                vp = Viewport(
                    cx + (v.x0 - cx) * z - pan.x * sx, cx + (v.x1 - cx) * z - pan.x * sx,
                    cy + (v.y0 - cy) * z + pan.y * sy, cy + (v.y1 - cy) * z + pan.y * sy,
                )
            }
        },
    ) {
        val v = vp
        fun px(x: Double) = ((x - v.x0) / (v.x1 - v.x0) * size.width).toFloat()
        fun py(y: Double) = ((v.y1 - y) / (v.y1 - v.y0) * size.height).toFloat()
        drawGrid(v, c, ::px, ::py)

        when (spec) {
            is GraphSpec.Plot -> {
                val fs = listOf(spec.f) + spec.extra
                fs.forEachIndexed { i, f -> drawCurve(f, v, palette[i % palette.size], ::px, ::py) }
                for (m in spec.marks) drawCircle(c.highlight, 5.dp.toPx(), Offset(px(m), py(0.0)))
                traceX?.let { tx ->
                    drawLine(c.muted, Offset(px(tx), 0f), Offset(px(tx), size.height), 1.dp.toPx())
                    val labelSize = 13.dp.toPx()
                    var y = labelSize + 6.dp.toPx()
                    drawText("${spec.variable} = ${Format.number(tx, 6)}", 8.dp.toPx(), y, c.text.toArgb(), labelSize)
                    fs.forEachIndexed { i, f ->
                        val fy = safe(f, tx)
                        val col = palette[i % palette.size]
                        if (fy.isFinite()) drawCircle(col, 4.5.dp.toPx(), Offset(px(tx), py(fy)))
                        y += labelSize + 4.dp.toPx()
                        val name = if (fs.size > 1) "y${"₀₁₂₃₄₅₆₇₈₉"[(i + 1) % 10]}" else "y"
                        drawText("$name = ${if (fy.isFinite()) Format.number(fy, 6) else "—"}", 8.dp.toPx(), y, col.toArgb(), labelSize)
                    }
                }
            }
            is GraphSpec.Area -> {
                val a = minOf(spec.a, spec.b); val b = maxOf(spec.a, spec.b)
                val p = Path()
                val n = 200
                p.moveTo(px(a), py(0.0))
                for (i in 0..n) {
                    val x = a + (b - a) * i / n
                    val y = safe(spec.f, x).let { if (it.isFinite()) it else 0.0 }
                    p.lineTo(px(x), py(y))
                }
                p.lineTo(px(b), py(0.0)); p.close()
                drawPath(p, c.graphFill)
                drawCurve(spec.f, v, c.graphLine, ::px, ::py)
            }
            is GraphSpec.Region -> {
                val a = minOf(spec.a, spec.b); val b = maxOf(spec.a, spec.b)
                val n = 200
                val p = Path()
                for (i in 0..n) {
                    val x = a + (b - a) * i / n
                    val y = safe(spec.lo, x).let { if (it.isFinite()) it else 0.0 }
                    if (i == 0) p.moveTo(px(x), py(y)) else p.lineTo(px(x), py(y))
                }
                for (i in n downTo 0) {
                    val x = a + (b - a) * i / n
                    val y = safe(spec.hi, x).let { if (it.isFinite()) it else 0.0 }
                    p.lineTo(px(x), py(y))
                }
                p.close()
                drawPath(p, c.graphFill)
                drawPath(p, c.graphLine, style = Stroke(2.dp.toPx()))
            }
            is GraphSpec.MohrState -> {}
        }
        val label = when (spec) {
            is GraphSpec.Region -> "${spec.outer} → , ${spec.inner} ↑"
            is GraphSpec.Plot -> spec.variable
            is GraphSpec.Area -> spec.variable
            is GraphSpec.MohrState -> ""
        }
        drawText(label, size.width - 8.dp.toPx(), size.height - 8.dp.toPx(), c.muted.toArgb(), 13.dp.toPx(), alignRight = true)
    }
}

private fun DrawScope.drawText(text: String, x: Float, y: Float, color: Int, sizePx: Float, alignRight: Boolean = false) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color
        textSize = sizePx
        textAlign = if (alignRight) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun DrawScope.drawGrid(v: Viewport, c: CalcColors, px: (Double) -> Float, py: (Double) -> Float) {
    val grid = c.muted.copy(alpha = 0.18f)
    val axis = c.muted.copy(alpha = 0.8f)
    val sx = niceStep(v.x1 - v.x0)
    val sy = niceStep(v.y1 - v.y0)
    var x = floor(v.x0 / sx) * sx
    val labelSize = 10.dp.toPx()
    while (x <= v.x1) {
        drawLine(grid, Offset(px(x), 0f), Offset(px(x), size.height))
        if (abs(x) > sx / 2) drawText(Format.number(x, 4), px(x) + 2, (py(0.0) + labelSize + 2).coerceIn(labelSize, size.height - 2), c.muted.toArgb(), labelSize)
        x += sx
    }
    var y = floor(v.y0 / sy) * sy
    while (y <= v.y1) {
        drawLine(grid, Offset(0f, py(y)), Offset(size.width, py(y)))
        if (abs(y) > sy / 2) drawText(Format.number(y, 4), (px(0.0) + 3).coerceIn(2f, size.width - 40), py(y) - 2, c.muted.toArgb(), labelSize)
        y += sy
    }
    drawLine(axis, Offset(0f, py(0.0)), Offset(size.width, py(0.0)), 1.5.dp.toPx())
    drawLine(axis, Offset(px(0.0), 0f), Offset(px(0.0), size.height), 1.5.dp.toPx())
}

private fun DrawScope.drawCurve(
    f: (Double) -> Double, v: Viewport, color: androidx.compose.ui.graphics.Color,
    px: (Double) -> Float, py: (Double) -> Float,
) {
    val n = (size.width / 2).toInt().coerceAtLeast(100)
    val p = Path()
    var drawing = false
    var prevY = 0.0
    val span = v.y1 - v.y0
    for (i in 0..n) {
        val x = v.x0 + (v.x1 - v.x0) * i / n
        val y = safe(f, x)
        if (!y.isFinite() || (drawing && abs(y - prevY) > span * 4)) {
            drawing = false
            if (!y.isFinite()) continue
        }
        val yy = y.coerceIn(v.y0 - span * 2, v.y1 + span * 2)
        if (drawing) p.lineTo(px(x), py(yy)) else { p.moveTo(px(x), py(yy)); drawing = true }
        prevY = y
    }
    drawPath(p, color, style = Stroke(2.5.dp.toPx()))
}

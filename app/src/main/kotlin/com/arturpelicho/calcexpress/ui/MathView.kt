package com.arturpelicho.calcexpress.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturpelicho.calcexpress.core.Derivative
import com.arturpelicho.calcexpress.core.Fn
import com.arturpelicho.calcexpress.core.MatrixNode
import com.arturpelicho.calcexpress.core.Editor
import com.arturpelicho.calcexpress.core.Fraction
import com.arturpelicho.calcexpress.core.FuncCall
import com.arturpelicho.calcexpress.core.Group
import com.arturpelicho.calcexpress.core.Integral
import com.arturpelicho.calcexpress.core.MathRow
import com.arturpelicho.calcexpress.core.Node
import com.arturpelicho.calcexpress.core.Power
import com.arturpelicho.calcexpress.core.Root
import com.arturpelicho.calcexpress.core.Slot
import com.arturpelicho.calcexpress.core.Sym
import com.arturpelicho.calcexpress.core.allRows
import com.arturpelicho.calcexpress.core.SymKind
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything the renderer needs; a new instance per editor change forces recomposition. */
data class MathCtx(
    val editor: Editor?,
    val version: Long,
    val colors: CalcColors,
    val font: FontFamily,
    val caretAlpha: Float,
)

@Composable
fun rememberCaretAlpha(): Float {
    val t = rememberInfiniteTransition(label = "caret")
    val a = t.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "caretAlpha",
    )
    return if (a.value > 0.5f) 1f else 0f
}

/** Bracket nesting depth below a row: 1 for ( ), 2 for [ ], 3 for { }, ... */
fun bracketDepth(row: MathRow): Int = row.items.maxOfOrNull { n ->
    when (n) {
        is Group -> 1 + bracketDepth(n.content)
        is FuncCall -> 1 + bracketDepth(n.argument)
        is Derivative -> max(bracketDepth(n.variable), 1 + bracketDepth(n.body))
        else -> n.rows.maxOfOrNull { bracketDepth(it) } ?: 0
    }
} ?: 0

private fun Placeable.baseline(): Int {
    val b = this[FirstBaseline]
    return if (b == androidx.compose.ui.layout.AlignmentLine.Unspecified) height else b
}

/** Lays children out horizontally, aligned on their baselines. */
@Composable
private fun BaselineRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        val ps = measurables.map { it.measure(loose) }
        val ascent = ps.maxOfOrNull { it.baseline() } ?: 0
        val descent = ps.maxOfOrNull { it.height - it.baseline() } ?: 0
        val w = ps.sumOf { it.width }
        layout(w, ascent + descent, mapOf(FirstBaseline to ascent)) {
            var x = 0
            for (p in ps) {
                p.placeRelative(x, ascent - p.baseline())
                x += p.width
            }
        }
    }
}

@Composable
fun MathRowView(row: MathRow, ctx: MathCtx, size: Float, highlight: Boolean = false, modifier: Modifier = Modifier) {
    val editor = ctx.editor
    val caretHere = editor != null && editor.caret.row === row
    if (row.isEmpty) {
        CaseBox(null, size, ctx, active = caretHere || highlight, onTap = { editor?.setCaret(row, 0) }, modifier = modifier)
        return
    }
    val border = if (highlight) Modifier.border(1.5.dp, ctx.colors.highlight, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp) else Modifier
    BaselineRow(modifier.then(border)) {
        row.items.forEachIndexed { i, node ->
            if (caretHere && editor!!.caret.index == i) Caret(size, ctx)
            NodeView(node, row, i, ctx, size)
        }
        if (caretHere && editor!!.caret.index == row.items.size) Caret(size, ctx)
    }
}

@Composable
private fun Caret(size: Float, ctx: MathCtx) {
    val density = LocalDensity.current
    val h = with(density) { (size * 1.15f).sp.toPx() }
    val base = with(density) { (size * 0.9f).sp.toPx() }
    Layout(
        content = {
            Box(Modifier.alpha(ctx.caretAlpha).background(ctx.colors.accent))
        },
    ) { ms, _ ->
        val w = 2.dp.roundToPx()
        val p = ms[0].measure(Constraints.fixed(w, h.roundToInt()))
        layout(w, h.roundToInt(), mapOf(FirstBaseline to base.roundToInt())) { p.place(0, 0) }
    }
}

@Composable
private fun CaseBox(label: String?, size: Float, ctx: MathCtx, active: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val color = if (active) ctx.colors.highlight else ctx.colors.caseBorder
    val minW = with(density) { (size * 0.75f).sp.toPx() }
    val boxH = with(density) { (size * 1.05f).sp.toPx() }
    val baseline = with(density) { (size * 0.85f).sp.toPx() }
    Layout(
        modifier = modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onTap),
        content = {
            Canvas(Modifier) {
                val stroke = Stroke(
                    width = if (active) 2.dp.toPx() else 1.2.dp.toPx(),
                    pathEffect = if (active) null else PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
                drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
            }
            if (label != null) {
                Text(label, style = mathStyle(ctx.font, size * 0.7f, ctx.colors.muted, italic = true))
            }
        },
    ) { ms, _ ->
        val labelP = ms.getOrNull(1)?.measure(Constraints())
        val pad = 4.dp.roundToPx()
        val w = max(minW.roundToInt(), (labelP?.width ?: 0) + 2 * pad)
        val h = boxH.roundToInt()
        val boxP = ms[0].measure(Constraints.fixed(w, h))
        val gap = 1.dp.roundToPx()
        layout(w + 2 * gap, h, mapOf(FirstBaseline to baseline.roundToInt())) {
            boxP.place(gap, 0)
            labelP?.place(gap + (w - labelP.width) / 2, (h - labelP.height) / 2)
        }
    }
}

@Composable
private fun NodeView(node: Node, row: MathRow, index: Int, ctx: MathCtx, size: Float) {
    val tap = Modifier.clickable(remember { MutableInteractionSource() }, null) { ctx.editor?.setCaret(row, index + 1) }
    when (node) {
        is Sym -> SymView(node, ctx, size, tap)
        is Group -> Bracketed(bracketDepth(node.content) + 1, ctx, size) { MathRowView(node.content, ctx, size) }
        is Power -> PowerView(node, ctx, size)
        is Fraction -> FractionLayout(ctx, size, { MathRowView(node.numerator, ctx, size * 0.92f) }, { MathRowView(node.denominator, ctx, size * 0.92f) })
        is Root -> RootView(node, ctx, size)
        is FuncCall -> BaselineRow {
            // A lone matrix argument already has brackets: det[…] instead of det([…]).
            val loneMatrix = node.argument.items.singleOrNull() is MatrixNode
            val arg = @Composable {
                if (loneMatrix) MathRowView(node.argument, ctx, size)
                else Bracketed(bracketDepth(node.argument) + 1, ctx, size) { MathRowView(node.argument, ctx, size) }
            }
            if (node.fn == Fn.TRANSPOSE) {
                arg()
                RaisedText("T", ctx, size, tap)
            } else if (node.fn == Fn.ABS) {
                Bracketed(1, ctx, size, bars = true) { MathRowView(node.argument, ctx, size) }
            } else {
                Text(node.fn.label, style = mathStyle(ctx.font, size, ctx.colors.text), modifier = tap.padding(start = 1.dp, end = 2.dp))
                arg()
            }
        }
        is MatrixNode -> Bracketed(2, ctx, size) { MatrixGrid(node, ctx, size) }
        is Integral -> IntegralView(node, ctx, size)
        is Derivative -> BaselineRow {
            FractionLayout(
                ctx, size,
                { Text("d", style = mathStyle(ctx.font, size * 0.92f, ctx.colors.text)) },
                {
                    BaselineRow {
                        Text("d", style = mathStyle(ctx.font, size * 0.92f, ctx.colors.text))
                        MathRowView(node.variable, ctx, size * 0.92f)
                    }
                },
            )
            Bracketed(bracketDepth(node.body) + 1, ctx, size) { MathRowView(node.body, ctx, size) }
        }
        is Slot -> {
            val editor = ctx.editor
            if (node.content.isEmpty) {
                val active = editor != null && editor.caret.row === node.content
                CaseBox(node.label, size, ctx, active, onTap = { editor?.setCaret(node.content, 0) })
            } else {
                MathRowView(
                    node.content, ctx, size,
                    modifier = Modifier
                        .border(0.8.dp, ctx.colors.caseBorder.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SymView(s: Sym, ctx: MathCtx, size: Float, modifier: Modifier) {
    val (text, italic, padH) = when (s.kind) {
        SymKind.VARIABLE -> Triple(s.text, true, 0.5f)
        SymKind.OPERATOR -> Triple(s.text, false, if (s.text == Sym.DOT) 1.5f else 4f)
        SymKind.EQUALS -> Triple("=", false, 6f)
        else -> Triple(s.text, false, 0f)
    }
    Text(
        text,
        style = mathStyle(ctx.font, size, ctx.colors.text, italic),
        modifier = modifier.padding(horizontal = padH.dp),
    )
}

@Composable
private fun PowerView(p: Power, ctx: MathCtx, size: Float) {
    val editor = ctx.editor
    val inThis = editor != null && p.exponent.allRows().any { it === editor.caret.row }
    val density = LocalDensity.current
    val raise = with(density) { (size * 0.42f).sp.toPx() }
    Layout(
        content = { MathRowView(p.exponent, ctx, max(size * 0.68f, 11f), highlight = inThis) },
    ) { ms, _ ->
        val e = ms[0].measure(Constraints())
        val b = e.baseline() + raise.roundToInt()
        val h = max(e.height, b + (size * 0.1f).sp.roundToPx())
        layout(e.width + 1.dp.roundToPx(), h, mapOf(FirstBaseline to b)) { e.place(1.dp.roundToPx(), 0) }
    }
}

@Composable
private fun FractionLayout(ctx: MathCtx, size: Float, num: @Composable () -> Unit, den: @Composable () -> Unit) {
    val color = ctx.colors.text
    Layout(
        content = {
            Box { num() }
            Box { den() }
            Canvas(Modifier) {
                drawLine(color, Offset(0f, this.size.height / 2), Offset(this.size.width, this.size.height / 2), strokeWidth = this.size.height)
            }
        },
    ) { ms, _ ->
        val n = ms[0].measure(Constraints())
        val d = ms[1].measure(Constraints())
        val pad = 3.dp.roundToPx()
        val w = max(n.width, d.width) + 2 * pad
        val thick = max(1, 1.2.dp.roundToPx())
        val line = ms[2].measure(Constraints.fixed(w, thick))
        val gap = 2.dp.roundToPx()
        val lineY = n.height + gap
        val axis = (size * 0.3f).sp.roundToPx()
        val baseline = lineY + axis
        val h = lineY + thick + gap + d.height
        layout(w, h, mapOf(FirstBaseline to baseline)) {
            n.place((w - n.width) / 2, 0)
            line.place(0, lineY)
            d.place((w - d.width) / 2, lineY + thick + gap)
        }
    }
}

/** Small text raised like an exponent (used for the transpose mark). */
@Composable
private fun RaisedText(text: String, ctx: MathCtx, size: Float, modifier: Modifier) {
    val raise = with(LocalDensity.current) { (size * 0.42f).sp.toPx() }
    Layout(content = { Text(text, style = mathStyle(ctx.font, size * 0.68f, ctx.colors.text), modifier = modifier) }) { ms, _ ->
        val t = ms[0].measure(Constraints())
        val b = t.baseline() + raise.roundToInt()
        layout(t.width + 1.dp.roundToPx(), max(t.height, b + (size * 0.1f).sp.roundToPx()), mapOf(FirstBaseline to b)) { t.place(1.dp.roundToPx(), 0) }
    }
}

@Composable
private fun MatrixGrid(m: MatrixNode, ctx: MathCtx, size: Float) {
    Layout(content = { m.cells.forEach { cell -> Box { MathRowView(cell, ctx, size * 0.92f) } } }) { ms, _ ->
        val ps = ms.map { it.measure(Constraints()) }
        val gapX = (size * 0.6f).sp.roundToPx()
        val gapY = (size * 0.25f).sp.roundToPx()
        val colW = IntArray(m.colCount) { c -> (0 until m.rowCount).maxOf { r -> ps[r * m.colCount + c].width } }
        val asc = IntArray(m.rowCount) { r -> (0 until m.colCount).maxOf { c -> ps[r * m.colCount + c].baseline() } }
        val desc = IntArray(m.rowCount) { r -> (0 until m.colCount).maxOf { c -> ps[r * m.colCount + c].let { it.height - it.baseline() } } }
        val w = colW.sum() + gapX * (m.colCount - 1) + 4.dp.roundToPx()
        val h = (0 until m.rowCount).sumOf { asc[it] + desc[it] } + gapY * (m.rowCount - 1)
        val baseline = h / 2 + (size * 0.3f).sp.roundToPx()
        layout(w, h, mapOf(FirstBaseline to baseline)) {
            var y = 0
            for (r in 0 until m.rowCount) {
                var x = 2.dp.roundToPx()
                for (c in 0 until m.colCount) {
                    val p = ps[r * m.colCount + c]
                    p.place(x + (colW[c] - p.width) / 2, y + asc[r] - p.baseline())
                    x += colW[c] + gapX
                }
                y += asc[r] + desc[r] + gapY
            }
        }
    }
}

/** Brackets sized to their content; ( ) [ ] { } by depth, growing beyond the third level. */
@Composable
fun Bracketed(depth: Int, ctx: MathCtx, size: Float, bars: Boolean = false, content: @Composable () -> Unit) {
    val color = ctx.colors.text
    val kind = if (bars) BARS else (depth - 1) % 3
    val grow = (depth - 1) / 3
    Layout(
        content = {
            Canvas(Modifier) { drawBracket(kind, left = true, color) }
            Box { content() }
            Canvas(Modifier) { drawBracket(kind, left = false, color) }
        },
    ) { ms, _ ->
        val c = ms[1].measure(Constraints())
        val minH = (size * 1.1f).sp.roundToPx()
        val extra = ((size * 0.12f) * (depth - 1 + grow)).sp.roundToPx()
        val h = max(c.height, minH) + extra
        val bw = (size * (0.32f + 0.05f * grow)).sp.roundToPx()
        val l = ms[0].measure(Constraints.fixed(bw, h))
        val r = ms[2].measure(Constraints.fixed(bw, h))
        val top = (h - c.height) / 2
        val baseline = top + c.baseline()
        layout(bw * 2 + c.width, h, mapOf(FirstBaseline to baseline)) {
            l.place(0, 0)
            c.place(bw, top)
            r.place(bw + c.width, 0)
        }
    }
}

private const val BARS = 3

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBracket(kind: Int, left: Boolean, color: Color) {
    val w = size.width
    val h = size.height
    val sw = (w * 0.12f).coerceIn(1.5f, 4f)
    val inset = w * 0.25f
    val outer = if (left) inset else w - inset
    val inner = if (left) w - inset else inset
    val p = Path()
    when (kind) {
        0 -> { // ( )
            p.moveTo(inner, h * 0.04f)
            p.quadraticTo(outer - (inner - outer) * 0.3f, h / 2, inner, h * 0.96f)
        }
        BARS -> { // | |
            p.moveTo((outer + inner) / 2, h * 0.04f); p.lineTo((outer + inner) / 2, h * 0.96f)
        }
        1 -> { // [ ]
            p.moveTo(inner, h * 0.04f); p.lineTo(outer, h * 0.04f); p.lineTo(outer, h * 0.96f); p.lineTo(inner, h * 0.96f)
        }
        else -> { // { }
            val mid = outer - (inner - outer) * 0.35f
            val c = (outer + inner) / 2
            p.moveTo(inner, h * 0.04f)
            p.quadraticTo(c, h * 0.04f, c, h * 0.2f)
            p.lineTo(c, h * 0.4f)
            p.quadraticTo(c, h * 0.5f, mid, h * 0.5f)
            p.quadraticTo(c, h * 0.5f, c, h * 0.6f)
            p.lineTo(c, h * 0.8f)
            p.quadraticTo(c, h * 0.96f, inner, h * 0.96f)
        }
    }
    drawPath(p, color, style = Stroke(width = sw, cap = StrokeCap.Round))
}

@Composable
private fun RootView(r: Root, ctx: MathCtx, size: Float) {
    val color = ctx.colors.text
    Layout(
        content = {
            Box { MathRowView(r.radicand, ctx, size) }
            Canvas(Modifier) {
                val w = this.size.width
                val h = this.size.height
                val sign = h * 0.55f
                val p = Path().apply {
                    moveTo(0f, h * 0.62f)
                    lineTo(sign * 0.25f, h * 0.55f)
                    lineTo(sign * 0.55f, h * 0.97f)
                    lineTo(sign, 1.5f)
                    lineTo(w, 1.5f)
                }
                drawPath(p, color, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
            }
            if (r.index != null) Box { MathRowView(r.index!!, ctx, size * 0.6f) }
        },
    ) { ms, _ ->
        val rad = ms[0].measure(Constraints())
        val top = 3.dp.roundToPx()
        val h = max(rad.height, (size * 1.1f).sp.roundToPx()) + top
        val signW = (h * 0.55f).roundToInt()
        val idx = ms.getOrNull(2)?.measure(Constraints())
        val shift = max(0, (idx?.width ?: 0) - signW / 2)
        val w = shift + signW + rad.width + 2.dp.roundToPx()
        val sign = ms[1].measure(Constraints.fixed(w - shift, h))
        val baseline = top + rad.baseline() + (h - top - rad.height) / 2
        layout(w, h, mapOf(FirstBaseline to baseline)) {
            sign.place(shift, 0)
            rad.place(shift + signW, top + (h - top - rad.height) / 2)
            idx?.place(0, (h * 0.35f).roundToInt() - idx.height)
        }
    }
}

@Composable
private fun IntegralView(n: Integral, ctx: MathCtx, size: Float) {
    val color = ctx.colors.text
    val small = max(size * 0.62f, 11f)
    Layout(
        content = {
            Canvas(Modifier) {
                val w = this.size.width
                val h = this.size.height
                val p = Path().apply {
                    moveTo(w * 0.95f, h * 0.06f)
                    cubicTo(w * 0.75f, -h * 0.02f, w * 0.55f, h * 0.05f, w * 0.52f, h * 0.25f)
                    lineTo(w * 0.48f, h * 0.75f)
                    cubicTo(w * 0.45f, h * 0.95f, w * 0.25f, h * 1.02f, w * 0.05f, h * 0.94f)
                }
                drawPath(p, color, style = Stroke(width = (w * 0.11f).coerceIn(1.5f, 4.5f), cap = StrokeCap.Round))
            }
            Box { MathRowView(n.upper, ctx, small) }
            Box { MathRowView(n.lower, ctx, small) }
            Box(Modifier.padding(horizontal = 3.dp)) { MathRowView(n.body, ctx, size) }
            Text("d", style = mathStyle(ctx.font, size, color), modifier = Modifier.padding(start = 2.dp))
            Box(Modifier.padding(end = 2.dp)) { MathRowView(n.variable, ctx, size) }
        },
    ) { ms, _ ->
        val upper = ms[1].measure(Constraints())
        val lower = ms[2].measure(Constraints())
        val body = ms[3].measure(Constraints())
        val d = ms[4].measure(Constraints())
        val v = ms[5].measure(Constraints())
        val signH = max((size * 2.0f).sp.roundToPx(), body.height + (size * 0.4f).sp.roundToPx())
        val signW = (signH * 0.32f).roundToInt()
        val sign = ms[0].measure(Constraints.fixed(signW, signH))
        val signTop = upper.height / 2
        val axisY = signTop + signH / 2
        val baseline = axisY + (size * 0.3f).sp.roundToPx()
        val limitsX = (signW * 0.8f).roundToInt()
        val limitsW = max(upper.width, lower.width)
        val lowerY = signTop + signH - lower.height / 2
        val bodyX = limitsX + limitsW
        val bodyY = baseline - body.baseline()
        val dX = bodyX + body.width
        val h = maxOf(lowerY + lower.height, bodyY + body.height, baseline + (d.height - d.baseline()))
        val w = dX + d.width + v.width
        layout(w, h, mapOf(FirstBaseline to baseline)) {
            sign.place(0, signTop)
            upper.place(limitsX, 0)
            lower.place(limitsX, lowerY)
            body.place(bodyX, bodyY)
            d.place(dX, baseline - d.baseline())
            v.place(dX + d.width, baseline - v.baseline())
        }
    }
}

/** Read-only rendering of formula previews and history. */
@Composable
fun StaticMath(rows: List<MathRow>, size: Float, modifier: Modifier = Modifier) {
    val ctx = MathCtx(null, 0, LocalCalcColors.current, LocalMathFont.current, 0f)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        androidx.compose.foundation.layout.Column {
            rows.forEach { MathRowView(it, ctx, size) }
        }
    }
}

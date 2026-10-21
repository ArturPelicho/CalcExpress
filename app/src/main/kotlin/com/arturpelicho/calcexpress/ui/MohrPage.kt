package com.arturpelicho.calcexpress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arturpelicho.calcexpress.AppSettings
import com.arturpelicho.calcexpress.R
import com.arturpelicho.calcexpress.core.AngleUnit
import com.arturpelicho.calcexpress.core.Format
import com.arturpelicho.calcexpress.core.Mohr
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Maps stress coordinates (σ to the right, τ up) to canvas pixels with one scale for both axes. */
private class MohrFrame(val ox: Float, val oy: Float, val k: Float) {
    fun x(s: Double) = ox + (s * k).toFloat()
    fun y(up: Double) = oy - (up * k).toFloat()
}

private fun mohrFrame(size: Size, lo: Double, hi: Double, radius: Double): MohrFrame {
    val half = max(max((hi - lo) / 2, radius), max(abs(lo), abs(hi)) * 0.05 + 1e-12)
    val k = min(size.width, size.height) / (2 * half * 1.3)
    return MohrFrame((size.width / 2 - (lo + hi) / 2 * k).toFloat(), size.height / 2, k.toFloat())
}

private fun parseNum(s: String) = s.trim().replace(',', '.').replace('−', '-').toDoubleOrNull()

private fun angleText(rad: Double, unit: AngleUnit) =
    Format.number(unit.fromRadians(rad), 4) + when (unit) { AngleUnit.DEG -> "°"; AngleUnit.RAD -> " rad"; AngleUnit.GRAD -> " gon" }

private fun editText(v: Double) = Format.plain(Math.round(v * 100) / 100.0)

@Composable
fun MohrPage(settings: AppSettings, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    val unit = settings.angle
    var strain by rememberSaveable { mutableStateOf(false) }
    var threeD by rememberSaveable { mutableStateOf(false) }
    var byNormal by rememberSaveable { mutableStateOf(false) }
    // Tensor components xx, yy, zz, xy, yz, xz (for strain the shear fields hold the engineering γ).
    val xx = rememberSaveable { mutableStateOf("80") }
    val yy = rememberSaveable { mutableStateOf("-40") }
    val zz = rememberSaveable { mutableStateOf("0") }
    val xy = rememberSaveable { mutableStateOf("25") }
    val yz = rememberSaveable { mutableStateOf("0") }
    val xz = rememberSaveable { mutableStateOf("0") }
    val theta = rememberSaveable { mutableStateOf(editText(unit.fromRadians(Math.PI / 6))) }
    val nx = rememberSaveable { mutableStateOf("1") }
    val ny = rememberSaveable { mutableStateOf("1") }
    val nz = rememberSaveable { mutableStateOf("1") }

    val s = if (strain) "ε" else "σ"
    val t = if (strain) "γ" else "τ"
    val shearFactor = if (strain) 0.5 else 1.0

    PageScaffold(stringResource(R.string.mohr_title), onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(!strain, { strain = false }, { Text(stringResource(R.string.mohr_stress)) })
                FilterChip(strain, { strain = true }, { Text(stringResource(R.string.mohr_strain)) })
                Spacer(Modifier.padding(horizontal = 4.dp))
                FilterChip(!threeD, { threeD = false }, { Text("2D") })
                FilterChip(threeD, { threeD = true }, { Text("3D") })
            }

            val vxx = parseNum(xx.value); val vyy = parseNum(yy.value); val vzz = parseNum(zz.value)
            val vxy = parseNum(xy.value)?.times(shearFactor)
            val vyz = parseNum(yz.value)?.times(shearFactor)
            val vxz = parseNum(xz.value)?.times(shearFactor)

            if (!threeD) {
                val th = if (byNormal) {
                    val a = parseNum(nx.value); val b = parseNum(ny.value)
                    if (a != null && b != null && (a != 0.0 || b != 0.0)) atan2(b, a) else null
                } else parseNum(theta.value)?.let { unit.toRadians(it) }
                if (vxx != null && vyy != null && vxy != null) {
                    MohrCanvas2D(vxx, vyy, vxy, th ?: 0.0, s, t, strain) { newTheta ->
                        theta.value = editText(unit.fromRadians(newTheta))
                        nx.value = editText(cos(newTheta)); ny.value = editText(sin(newTheta))
                    }
                } else BlankCanvas()
                Text(stringResource(R.string.mohr_hint_2d), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                FieldRow(listOf(xx to "${s}_x", yy to "${s}_y", xy to "${t}_xy"))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FilterChip(!byNormal, { byNormal = false }, { Text(stringResource(R.string.mohr_by_angle)) })
                    FilterChip(byNormal, { byNormal = true }, { Text(stringResource(R.string.mohr_by_normal)) })
                }
                if (byNormal) FieldRow(listOf(nx to "n_x", ny to "n_y"))
                else FieldRow(listOf(theta to "θ (${unit.label})"))
                if (strain) Note(stringResource(R.string.mohr_strain_note))
                if (vxx == null || vyy == null || vxy == null || th == null) Note(stringResource(R.string.mohr_fill))
                else Results2D(vxx, vyy, vxy, th, s, t, strain, unit)
            } else {
                val n = listOf(nx, ny, nz).map { parseNum(it.value) }
                val ten = if (listOf(vxx, vyy, vzz, vxy, vyz, vxz).all { it != null })
                    Mohr.Tensor3(vxx!!, vyy!!, vzz!!, vxy!!, vyz!!, vxz!!) else null
                val normal = if (n.all { it != null }) DoubleArray(3) { n[it]!! } else null
                val p = ten?.let { Mohr.principal3D(it) }
                val onPlane = if (ten != null && normal != null) Mohr.onPlane3D(ten, normal) else null
                if (p != null) MohrCanvas3D(p, onPlane, s, strain) else BlankCanvas()
                Text(stringResource(R.string.mohr_hint_3d), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                FieldRow(listOf(xx to "${s}_x", yy to "${s}_y", zz to "${s}_z"))
                FieldRow(listOf(xy to "${t}_xy", yz to "${t}_yz", xz to "${t}_xz"))
                Text(stringResource(R.string.mohr_by_normal), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FieldRow(listOf(nx to "n_x", ny to "n_y", nz to "n_z"))
                if (strain) Note(stringResource(R.string.mohr_strain_note))
                if (p != null && normal != null) Results3D(p, onPlane, s, t, strain)
                else Note(stringResource(R.string.mohr_fill))
                if (normal != null && onPlane == null) Note(stringResource(R.string.mohr_zero_normal))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FieldRow(fields: List<Pair<MutableState<String>, String>>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { (state, label) ->
            OutlinedTextField(
                value = state.value,
                onValueChange = { state.value = it },
                label = { Text(mathLabel(label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = mathStyle(LocalMathFont.current, 17f, LocalCalcColors.current.text),
                modifier = Modifier.weight(1f),
            )
        }
        // Keep fields the same width whatever their number.
        repeat(3 - fields.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = LocalCalcColors.current.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ResultTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun ResultLine(text: String) {
    Text(mathLabel(text), style = mathStyle(LocalMathFont.current, 18f, LocalCalcColors.current.accent))
}

private fun n(v: Double) = Format.number(v, 6)

@Composable
private fun Results2D(sx: Double, sy: Double, txy: Double, th: Double, s: String, t: String, strain: Boolean, unit: AngleUnit) {
    val m = Mohr.plane2D(sx, sy, txy)
    val p = Mohr.rotate2D(sx, sy, txy, th)
    val k = if (strain) 2.0 else 1.0
    ResultTitle(stringResource(R.string.mohr_principal))
    ResultLine("${s}_1 = ${n(m.p1)}    ${s}_2 = ${n(m.p2)}")
    ResultLine("${t}_max = ${n(k * m.maxShear)}")
    ResultLine("θ_p = ${angleText(m.thetaP, unit)}    θ_s = ${angleText(Mohr.normaliseHalfTurn(m.thetaS), unit)}")
    ResultLine("${stringResource(R.string.mohr_centre)} = ${n(m.centre)}    ${stringResource(R.string.mohr_radius)} = ${n(m.radius)}")
    ResultTitle(stringResource(R.string.mohr_on_plane) + " θ = " + angleText(th, unit))
    ResultLine("${s}_θ = ${n(p.normal)}    ${t}_θ = ${n(k * p.shear)}")
    // The perpendicular face completes the rotated element.
    val q = Mohr.rotate2D(sx, sy, txy, th + Math.PI / 2)
    ResultLine("${s}_θ+90° = ${n(q.normal)}")
}

@Composable
private fun Results3D(p: Mohr.Principal3D, on: Mohr.Point?, s: String, t: String, strain: Boolean) {
    val k = if (strain) 2.0 else 1.0
    ResultTitle(stringResource(R.string.mohr_principal))
    ResultLine("${s}_1 = ${n(p.values[0])}    ${s}_2 = ${n(p.values[1])}    ${s}_3 = ${n(p.values[2])}")
    ResultLine("${t}_max = ${n(k * p.maxShear)}")
    ResultTitle(stringResource(R.string.mohr_directions))
    p.directions.forEachIndexed { i, d ->
        // Show each direction with a non-negative leading component, for readability.
        val sign = if ((d.firstOrNull { abs(it) > 1e-12 } ?: 1.0) < 0) -1.0 else 1.0
        ResultLine("n_${i + 1} = (${d.joinToString(", ") { Format.number(sign * it + 0.0, 4) }})")
    }
    if (on != null) {
        ResultTitle(stringResource(R.string.mohr_on_plane))
        ResultLine("${s}_n = ${n(on.normal)}    ${t}_n = ${n(k * on.shear)}")
    }
}

@Composable
private fun BlankCanvas() {
    Spacer(Modifier.fillMaxWidth().aspectRatio(1.15f).padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(LocalCalcColors.current.display))
}

@Composable
private fun MohrCanvas2D(sx: Double, sy: Double, txy: Double, theta: Double, s: String, t: String, strain: Boolean, onTheta: (Double) -> Unit) {
    val c = LocalCalcColors.current
    val m = Mohr.plane2D(sx, sy, txy)
    val lo = m.centre - m.radius; val hi = m.centre + m.radius
    fun pick(size: Size, pos: Offset) {
        val f = mohrFrame(size, lo, hi, m.radius)
        val phi = atan2((f.oy - pos.y).toDouble(), (pos.x - f.x(m.centre)).toDouble())
        onTheta(Mohr.thetaAt(sx, sy, txy, phi))
    }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1.15f).padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(c.display).clipToBounds()
            .pointerInput(sx, sy, txy) {
                detectTapGestures { pos -> pick(Size(size.width.toFloat(), size.height.toFloat()), pos) }
            }
            .pointerInput(sx, sy, txy) {
                detectDragGestures(onDragStart = { pos -> pick(Size(size.width.toFloat(), size.height.toFloat()), pos) }) { change, _ ->
                    change.consume()
                    pick(Size(size.width.toFloat(), size.height.toFloat()), change.position)
                }
            },
    ) {
        val f = mohrFrame(size, lo, hi, m.radius)
        val r = (m.radius * f.k).toFloat()
        val centre = Offset(f.x(m.centre), f.oy)
        drawAxes(f, c, s, if (strain) "γ/2" else t, shearDown = true)
        drawCircle(c.graphFill, r, centre)
        drawCircle(c.graphLine, r, centre, style = Stroke(2.dp.toPx()))
        // Faces x (θ = 0) and y (θ = 90°): shear drawn positive downwards.
        val px = Offset(f.x(sx), f.y(-txy)); val py = Offset(f.x(sy), f.y(txy))
        drawLine(c.muted, px, py, 1.2.dp.toPx())
        drawCircle(c.muted, 4.dp.toPx(), px); drawCircle(c.muted, 4.dp.toPx(), py)
        label("x", px + Offset(6.dp.toPx(), -6.dp.toPx()), c.muted)
        label("y", py + Offset(6.dp.toPx(), -6.dp.toPx()), c.muted)
        // Principal values on the axis.
        val p1 = Offset(f.x(m.p1), f.oy); val p2 = Offset(f.x(m.p2), f.oy)
        drawCircle(c.accent, 4.dp.toPx(), p1); drawCircle(c.accent, 4.dp.toPx(), p2)
        label("${s}₁", p1 + Offset(4.dp.toPx(), 16.dp.toPx()), c.accent)
        label("${s}₂", p2 + Offset(-18.dp.toPx(), 16.dp.toPx()), c.accent)
        // The chosen plane and its 2θ arc from face x.
        val q = Mohr.rotate2D(sx, sy, txy, theta)
        val pq = Offset(f.x(q.normal), f.y(-q.shear))
        val opp = Offset(2 * centre.x - pq.x, 2 * centre.y - pq.y)
        drawLine(c.highlight, opp, pq, 2.dp.toPx())
        if (r > 8.dp.toPx()) {
            val alpha = Math.toDegrees(atan2(txy, (sx - sy) / 2)).toFloat()
            val ar = r * 0.3f
            drawArc(
                c.highlight, startAngle = alpha, sweepAngle = -Math.toDegrees(2 * theta).toFloat(), useCenter = false,
                topLeft = Offset(centre.x - ar, centre.y - ar), size = Size(2 * ar, 2 * ar), style = Stroke(1.5.dp.toPx()),
            )
        }
        drawCircle(c.highlight, 6.dp.toPx(), pq)
        label("θ", pq + Offset(8.dp.toPx(), -8.dp.toPx()), c.highlight)
        drawElement(theta, c)
    }
}

@Composable
private fun MohrCanvas3D(p: Mohr.Principal3D, on: Mohr.Point?, s: String, strain: Boolean) {
    val c = LocalCalcColors.current
    val (big, bigR) = p.circles[2]
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f).padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(c.display).clipToBounds()) {
        val f = mohrFrame(size, p.values[2], p.values[0], bigR)
        drawAxes(f, c, s, if (strain) "γ/2" else "τ", shearDown = false)
        // Shade the admissible region: inside the big circle, outside the two small ones.
        drawCircle(c.graphFill, (bigR * f.k).toFloat(), Offset(f.x(big), f.oy))
        for ((cc, rr) in p.circles.take(2)) drawCircle(c.display, (rr * f.k).toFloat(), Offset(f.x(cc), f.oy))
        drawAxesLine(f, c)
        for ((cc, rr) in p.circles) drawCircle(c.graphLine, (rr * f.k).toFloat(), Offset(f.x(cc), f.oy), style = Stroke(2.dp.toPx()))
        p.values.forEachIndexed { i, v ->
            val o = Offset(f.x(v), f.oy)
            drawCircle(c.accent, 4.dp.toPx(), o)
            label("$s${"₁₂₃"[i]}", o + Offset(4.dp.toPx(), 16.dp.toPx()), c.accent)
        }
        if (on != null) {
            val o = Offset(f.x(on.normal), f.y(on.shear))
            drawCircle(c.highlight, 6.dp.toPx(), o)
            label("n", o + Offset(8.dp.toPx(), -8.dp.toPx()), c.highlight)
        }
    }
}

private fun DrawScope.drawAxesLine(f: MohrFrame, c: CalcColors) {
    drawLine(c.muted.copy(alpha = 0.7f), Offset(0f, f.oy), Offset(size.width, f.oy), 1.dp.toPx())
}

private fun DrawScope.drawAxes(f: MohrFrame, c: CalcColors, s: String, t: String, shearDown: Boolean) {
    drawAxesLine(f, c)
    label(s, Offset(size.width - 18.dp.toPx(), f.oy - 6.dp.toPx()), c.muted)
    val x0 = f.x(0.0)
    if (x0 in 0f..size.width) {
        drawLine(c.muted.copy(alpha = 0.7f), Offset(x0, 0f), Offset(x0, size.height), 1.dp.toPx())
        // Arrow on the positive shear direction.
        val tip = if (shearDown) size.height - 4.dp.toPx() else 4.dp.toPx()
        val back = if (shearDown) -8.dp.toPx() else 8.dp.toPx()
        drawLine(c.muted, Offset(x0, tip), Offset(x0 - 5.dp.toPx(), tip + back), 1.dp.toPx())
        drawLine(c.muted, Offset(x0, tip), Offset(x0 + 5.dp.toPx(), tip + back), 1.dp.toPx())
        label(t, Offset(x0 + 8.dp.toPx(), if (shearDown) size.height - 8.dp.toPx() else 18.dp.toPx()), c.muted)
    }
}

/** Small rotated element in the corner, with the plane's normal. */
private fun DrawScope.drawElement(theta: Double, c: CalcColors) {
    val centre = Offset(40.dp.toPx(), 40.dp.toPx())
    val h = 13.dp.toPx()
    drawLine(c.muted.copy(alpha = 0.5f), centre, centre + Offset(30.dp.toPx(), 0f), 1.dp.toPx())
    rotate(degrees = -Math.toDegrees(theta).toFloat(), pivot = centre) {
        val sq = Path().apply {
            moveTo(centre.x - h, centre.y - h); lineTo(centre.x + h, centre.y - h)
            lineTo(centre.x + h, centre.y + h); lineTo(centre.x - h, centre.y + h); close()
        }
        drawPath(sq, c.graphLine, style = Stroke(1.5.dp.toPx()))
        val tip = centre + Offset(h + 14.dp.toPx(), 0f)
        drawLine(c.highlight, centre + Offset(h, 0f), tip, 2.dp.toPx())
        drawLine(c.highlight, tip, tip + Offset(-5.dp.toPx(), -4.dp.toPx()), 2.dp.toPx())
        drawLine(c.highlight, tip, tip + Offset(-5.dp.toPx(), 4.dp.toPx()), 2.dp.toPx())
    }
}

private fun DrawScope.label(text: String, at: Offset, color: Color) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = 14.dp.toPx()
    }
    drawContext.canvas.nativeCanvas.drawText(text, at.x, at.y, paint)
}

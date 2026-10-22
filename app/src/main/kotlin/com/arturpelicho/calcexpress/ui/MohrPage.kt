package com.arturpelicho.calcexpress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.arturpelicho.calcexpress.core.TextExpr
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

private fun editText3(v: Double) = Format.plain(Math.round(v * 1000) / 1000.0)

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

    var shearDown by rememberSaveable { mutableStateOf(false) }
    // 3D plane: by normal components or by spherical angles (θ from x in the xy-plane, φ from z).
    var byAngles3 by rememberSaveable { mutableStateOf(false) }
    val az = rememberSaveable { mutableStateOf(editText(unit.fromRadians(Math.PI / 4))) }
    val polar = rememberSaveable { mutableStateOf(editText(unit.fromRadians(Math.acos(1 / Math.sqrt(3.0))))) }
    // Point where a stress/strain field (entries in x, y, z) is evaluated.
    val px = rememberSaveable { mutableStateOf("0") }
    val py = rememberSaveable { mutableStateOf("0") }
    val pz = rememberSaveable { mutableStateOf("0") }

    val s = if (strain) "ε" else "σ"
    // 2D strain takes the engineering γ; the 3D matrix holds tensor components ε_ij = γ_ij/2.
    val t = if (strain) (if (threeD) "ε" else "γ") else "τ"
    val shearFactor = if (strain && !threeD) 0.5 else 1.0

    val tensorFields = if (threeD) listOf(xx, yy, zz, xy, yz, xz) else listOf(xx, yy, xy)
    val fieldVars = tensorFields.flatMap { TextExpr.variables(it.value) }.toSet()
    val point = mapOf("x" to parseNum(px.value), "y" to parseNum(py.value), "z" to parseNum(pz.value))
        .filterValues { it != null }.mapValues { it.value!! }
    fun ev(f: MutableState<String>) = TextExpr.eval(f.value, point, unit)

    PageScaffold(stringResource(R.string.mohr_title), onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(!strain, { strain = false }, { Text(stringResource(R.string.mohr_stress)) })
                FilterChip(strain, { strain = true }, { Text(stringResource(R.string.mohr_strain)) })
                Spacer(Modifier.padding(horizontal = 4.dp))
                FilterChip(!threeD, { threeD = false }, { Text("2D") })
                FilterChip(threeD, { threeD = true }, { Text("3D") })
            }

            val vxx = ev(xx); val vyy = ev(yy); val vzz = ev(zz)
            val vxy = ev(xy)?.times(shearFactor)
            val vyz = ev(yz)?.times(shearFactor)
            val vxz = ev(xz)?.times(shearFactor)

            if (!threeD) {
                val th = if (byNormal) {
                    val a = parseNum(nx.value); val b = parseNum(ny.value)
                    if (a != null && b != null && (a != 0.0 || b != 0.0)) atan2(b, a) else null
                } else parseNum(theta.value)?.let { unit.toRadians(it) }
                val faces = "x" to "y"
                if (vxx != null && vyy != null && vxy != null) {
                    MohrCanvas2D(vxx, vyy, vxy, th ?: 0.0, s, t, strain, faces, shearDown = shearDown) { newTheta ->
                        theta.value = editText(unit.fromRadians(newTheta))
                        nx.value = editText3(cos(newTheta)); ny.value = editText3(sin(newTheta))
                    }
                } else BlankCanvas()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    val shearName = if (strain) "γ/2" else "τ"
                    FilterChip(!shearDown, { shearDown = false }, { Text("$shearName ↑") })
                    FilterChip(shearDown, { shearDown = true }, { Text("$shearName ↓") })
                }
                if (vxx != null && vyy != null && vxy != null) MohrElements(vxx, vyy, vxy, th ?: 0.0, strain, s, t, unit)
                Text(stringResource(R.string.mohr_hint_2d), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                FieldRow(listOf(xx to "${s}_x", yy to "${s}_y", xy to "${t}_xy"), text = true)
                Note(stringResource(R.string.mohr_points_note))
                PointRow(fieldVars, threeD = false, px, py, pz)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FilterChip(!byNormal, { byNormal = false }, { Text(stringResource(R.string.mohr_by_angle)) })
                    FilterChip(byNormal, { byNormal = true }, { Text(stringResource(R.string.mohr_by_normal)) })
                }
                if (byNormal) FieldRow(listOf(nx to "n_x", ny to "n_y"))
                else FieldRow(listOf(theta to "θ (${unit.label})"))
                if (strain) Note(stringResource(R.string.mohr_strain_note))
                Note(stringResource(R.string.mohr_field_hint))
                if (vxx == null || vyy == null || vxy == null || th == null) Note(stringResource(R.string.mohr_fill))
                else Results2D(vxx, vyy, vxy, th, s, t, strain, unit)
            } else {
                val n = if (byAngles3) {
                    val a = parseNum(az.value)?.let { unit.toRadians(it) }
                    val b = parseNum(polar.value)?.let { unit.toRadians(it) }
                    if (a != null && b != null) listOf(sin(b) * cos(a), sin(b) * sin(a), cos(b)) else listOf(null, null, null)
                } else listOf(nx, ny, nz).map { parseNum(it.value) }
                val ten = if (listOf(vxx, vyy, vzz, vxy, vyz, vxz).all { it != null })
                    Mohr.Tensor3(vxx!!, vyy!!, vzz!!, vxy!!, vyz!!, vxz!!) else null
                val normal = if (n.all { it != null }) DoubleArray(3) { n[it]!! } else null
                val p = ten?.let { Mohr.principal3D(it) }
                val onPlane = if (ten != null && normal != null) Mohr.onPlane3D(ten, normal) else null
                if (p != null) MohrCanvas3D(p, onPlane, s, strain) { sn, tn ->
                    // Dragging moves the plane's point: find a normal that lands there.
                    val u = Mohr.normalFor(p, sn, tn)
                    nx.value = editText3(u[0]); ny.value = editText3(u[1]); nz.value = editText3(u[2])
                    az.value = editText(unit.fromRadians(atan2(u[1], u[0])))
                    polar.value = editText(unit.fromRadians(Math.acos(u[2].coerceIn(-1.0, 1.0))))
                } else BlankCanvas()
                Text(stringResource(R.string.mohr_hint_3d), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                // The symmetric matrix: mirrored cells share their state, so editing either keeps it symmetric.
                Text(stringResource(R.string.mohr_matrix), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                FieldRow(listOf(xx to "${s}_x", xy to "${t}_xy", xz to "${t}_xz"), text = true)
                FieldRow(listOf(xy to "${t}_yx", yy to "${s}_y", yz to "${t}_yz"), text = true)
                FieldRow(listOf(xz to "${t}_zx", yz to "${t}_zy", zz to "${s}_z"), text = true)
                PointRow(fieldVars, threeD = true, px, py, pz)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(!byAngles3, { byAngles3 = false }, { Text(stringResource(R.string.mohr_by_normal)) })
                    FilterChip(byAngles3, { byAngles3 = true }, { Text(stringResource(R.string.mohr_by_angles)) })
                }
                if (byAngles3) {
                    FieldRow(listOf(az to "θ (${unit.label})", polar to "φ (${unit.label})"))
                    Note(stringResource(R.string.mohr_angles_note))
                } else FieldRow(listOf(nx to "n_x", ny to "n_y", nz to "n_z"))
                if (strain) Note(stringResource(R.string.mohr_matrix_note))
                Note(stringResource(R.string.mohr_field_hint))
                if (p != null && ten != null && normal != null) Results3D(p, ten, onPlane, s, t, strain)
                else Note(stringResource(R.string.mohr_fill))
                if (normal != null && onPlane == null) Note(stringResource(R.string.mohr_zero_normal))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Coordinates of the point, shown once an entry uses x, y or z. */
@Composable
private fun PointRow(vars: Set<String>, threeD: Boolean, px: MutableState<String>, py: MutableState<String>, pz: MutableState<String>) {
    if (vars.none { it in setOf("x", "y", "z") }) return
    val used = listOf("x" to px, "y" to py, "z" to pz).take(if (threeD || "z" in vars) 3 else 2)
    Text(stringResource(R.string.mohr_point), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FieldRow(used.map { (name, st) -> st to name })
}

@Composable
private fun FieldRow(fields: List<Pair<MutableState<String>, String>>, text: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { (state, label) ->
            OutlinedTextField(
                value = state.value,
                onValueChange = { state.value = it },
                label = { Text(mathLabel(label)) },
                singleLine = true,
                // Entries of a field may hold expressions in x, y, z.
                keyboardOptions = KeyboardOptions(keyboardType = if (text) KeyboardType.Ascii else KeyboardType.Decimal),
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
    val (i1, i2) = Mohr.invariants2D(sx, sy, txy)
    ResultTitle(stringResource(R.string.mohr_invariants))
    ResultLine("I_1 = ${n(i1)}    I_2 = ${n(i2)}")
    ResultTitle(stringResource(R.string.mohr_on_plane) + " θ = " + angleText(th, unit))
    ResultLine("${s}_θ = ${n(p.normal)}    ${t}_θ = ${n(k * p.shear)}")
    // The perpendicular face completes the rotated element.
    val q = Mohr.rotate2D(sx, sy, txy, th + Math.PI / 2)
    ResultLine("${s}_θ+90° = ${n(q.normal)}")
}

@Composable
private fun Results3D(p: Mohr.Principal3D, ten: Mohr.Tensor3, on: Mohr.Point?, s: String, t: String, strain: Boolean) {
    // Strain shear is reported as the engineering γ = 2·ε_ij.
    val k = if (strain) 2.0 else 1.0
    val g = if (strain) "γ" else t
    ResultTitle(stringResource(R.string.mohr_principal))
    ResultLine("${s}_1 = ${n(p.values[0])}    ${s}_2 = ${n(p.values[1])}    ${s}_3 = ${n(p.values[2])}")
    ResultLine("${g}_max = ${n(k * p.maxShear)}")
    val (i1, i2, i3) = ten.invariants()
    ResultTitle(stringResource(R.string.mohr_invariants))
    ResultLine("I_1 = ${n(i1)}    I_2 = ${n(i2)}    I_3 = ${n(i3)}")
    ResultTitle(stringResource(R.string.mohr_directions))
    p.directions.forEachIndexed { i, d ->
        // Show each direction with a non-negative leading component, for readability.
        val sign = if ((d.firstOrNull { abs(it) > 1e-12 } ?: 1.0) < 0) -1.0 else 1.0
        ResultLine("n_${i + 1} = (${d.joinToString(", ") { Format.number(sign * it + 0.0, 4) }})")
    }
    if (on != null) {
        ResultTitle(stringResource(R.string.mohr_on_plane))
        ResultLine("${s}_n = ${n(on.normal)}    ${g}_n = ${n(k * on.shear)}")
    }
}

@Composable
private fun BlankCanvas() {
    Spacer(Modifier.fillMaxWidth().aspectRatio(1.15f).padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(LocalCalcColors.current.display))
}

private val CANVAS = Modifier.fillMaxWidth().aspectRatio(1.15f).padding(top = 8.dp)

/** Short number for labels on the diagram. */
private fun short(v: Double) = Format.number(if (abs(v) < 1e-12) 0.0 else v, 4)

@Composable
internal fun MohrCanvas2D(
    sx: Double, sy: Double, txy: Double, theta: Double, s: String, t: String, strain: Boolean, faces: Pair<String, String>,
    modifier: Modifier = CANVAS, shearDown: Boolean = false, onTheta: (Double) -> Unit,
) {
    val c = LocalCalcColors.current
    val m = Mohr.plane2D(sx, sy, txy)
    val lo = m.centre - m.radius; val hi = m.centre + m.radius
    // Positive shear is drawn up (sg = 1) or down (sg = −1).
    val sg = if (shearDown) -1.0 else 1.0
    fun pick(size: Size, pos: Offset) {
        val f = mohrFrame(size, lo, hi, m.radius)
        val phiUp = atan2((f.oy - pos.y).toDouble(), (pos.x - f.x(m.centre)).toDouble())
        // thetaAt works in the "shear down" picture; mirror the angle when shear is drawn up.
        onTheta(Mohr.thetaAt(sx, sy, txy, if (shearDown) phiUp else -phiUp))
    }
    Canvas(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.display).clipToBounds()
            .pointerInput(sx, sy, txy, shearDown) {
                detectTapGestures { pos -> pick(Size(size.width.toFloat(), size.height.toFloat()), pos) }
            }
            .pointerInput(sx, sy, txy, shearDown) {
                detectDragGestures(onDragStart = { pos -> pick(Size(size.width.toFloat(), size.height.toFloat()), pos) }) { change, _ ->
                    change.consume()
                    pick(Size(size.width.toFloat(), size.height.toFloat()), change.position)
                }
            },
    ) {
        val f = mohrFrame(size, lo, hi, m.radius)
        val r = (m.radius * f.k).toFloat()
        val centre = Offset(f.x(m.centre), f.oy)
        fun at(normal: Double, shear: Double) = Offset(f.x(normal), f.y(sg * shear))
        drawScale(f, c, sg)
        drawCircle(c.graphFill, r, centre)
        drawAxes(f, c, s, if (strain) "γ/2" else t, shearDown)
        drawCircle(c.graphLine, r, centre, style = Stroke(2.dp.toPx()))
        // Faces x (θ = 0) and y (θ = 90°).
        val px = at(sx, txy); val py = at(sy, -txy)
        drawLine(c.muted, px, py, 1.2.dp.toPx())
        drawCircle(c.muted, 4.dp.toPx(), px); drawCircle(c.muted, 4.dp.toPx(), py)
        tag("${faces.first} (${short(sx)}, ${short(txy)})", px, centre, c, c.muted)
        tag("${faces.second} (${short(sy)}, ${short(-txy)})", py, centre, c, c.muted)
        // Principal values on the axis.
        val p1 = Offset(f.x(m.p1), f.oy); val p2 = Offset(f.x(m.p2), f.oy)
        drawCircle(c.accent, 4.dp.toPx(), p1); drawCircle(c.accent, 4.dp.toPx(), p2)
        tag("${s}₁ = ${short(m.p1)}", p1, centre, c, c.accent, below = true)
        tag("${s}₂ = ${short(m.p2)}", p2, centre, c, c.accent, below = true)
        // The chosen plane, its opposite face and the 2θ arc from face x.
        val q = Mohr.rotate2D(sx, sy, txy, theta)
        val pq = at(q.normal, q.shear)
        val opp = Offset(2 * centre.x - pq.x, 2 * centre.y - pq.y)
        drawLine(c.highlight, opp, pq, 2.dp.toPx())
        if (r > 8.dp.toPx()) {
            val start = -Math.toDegrees(atan2(sg * txy, (sx - sy) / 2)).toFloat()
            val ar = r * 0.3f
            drawArc(
                c.highlight, startAngle = start, sweepAngle = (sg * Math.toDegrees(2 * theta)).toFloat(), useCenter = false,
                topLeft = Offset(centre.x - ar, centre.y - ar), size = Size(2 * ar, 2 * ar), style = Stroke(1.5.dp.toPx()),
            )
        }
        drawCircle(c.highlight, 6.dp.toPx(), pq)
        drawCircle(c.display, 2.5.dp.toPx(), pq)
        tag("θ (${short(q.normal)}, ${short(q.shear)})", pq, centre, c, c.highlight, strong = true)
    }
}

@Composable
internal fun MohrCanvas3D(
    p: Mohr.Principal3D, on: Mohr.Point?, s: String, strain: Boolean, modifier: Modifier = CANVAS,
    onPick: ((Double, Double) -> Unit)? = null,
) {
    val c = LocalCalcColors.current
    val (big, bigR) = p.circles[2]
    // A plane's shear magnitude has no sign in 3D: the diagram is symmetric, and the point
    // stays on the side (τ > 0 or τ < 0) where the finger put it.
    var below by remember { mutableStateOf(false) }
    val pickNow by rememberUpdatedState { size: Size, pos: Offset ->
        val f = mohrFrame(size, p.values[2], p.values[0], bigR)
        below = pos.y > f.oy
        onPick?.invoke((pos.x - f.ox) / f.k.toDouble(), abs((f.oy - pos.y) / f.k.toDouble()))
    }
    fun pick(size: Size, pos: Offset) = pickNow(size, pos)
    // Keyed on the values, not on p: a new (equal) Principal3D each frame would restart the drag.
    val gestures = if (onPick == null) Modifier else Modifier
        .pointerInput(p.values) { detectTapGestures { pos -> pick(Size(size.width.toFloat(), size.height.toFloat()), pos) } }
        .pointerInput(p.values) {
            detectDragGestures(onDragStart = { pos -> pick(Size(size.width.toFloat(), size.height.toFloat()), pos) }) { change, _ ->
                change.consume()
                pick(Size(size.width.toFloat(), size.height.toFloat()), change.position)
            }
        }
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(c.display).clipToBounds().then(gestures)) {
        val f = mohrFrame(size, p.values[2], p.values[0], bigR)
        val centre = Offset(f.x(big), f.oy)
        drawScale(f, c, 1.0)
        // Shade the admissible region: inside the big circle, outside the two small ones.
        drawCircle(c.graphFill, (bigR * f.k).toFloat(), centre)
        for ((cc, rr) in p.circles.take(2)) drawCircle(c.display, (rr * f.k).toFloat(), Offset(f.x(cc), f.oy))
        // Axes after the fills, so the circles never hide them.
        drawAxes(f, c, s, if (strain) "γ/2" else "τ", shearDown = false)
        for ((cc, rr) in p.circles) drawCircle(c.graphLine, (rr * f.k).toFloat(), Offset(f.x(cc), f.oy), style = Stroke(2.dp.toPx()))
        p.values.forEachIndexed { i, v ->
            val o = Offset(f.x(v), f.oy)
            drawCircle(c.accent, 4.dp.toPx(), o)
            tag("$s${"₁₂₃"[i]} = ${short(v)}", o, centre, c, c.accent, below = true, stagger = i == 1)
        }
        if (on != null) {
            val o = Offset(f.x(on.normal), f.y(if (below) -on.shear else on.shear))
            drawCircle(c.highlight, 6.dp.toPx(), o)
            drawCircle(c.display, 2.5.dp.toPx(), o)
            tag("n (${short(on.normal)}, ${short(if (below) -on.shear else on.shear)})", o, centre, c, c.highlight, strong = true)
        }
    }
}

private fun niceStep(range: Double): Double {
    if (range <= 0 || !range.isFinite()) return 1.0
    val raw = range / 6
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val n = raw / mag
    return mag * when { n < 1.5 -> 1.0; n < 3.5 -> 2.0; n < 7.5 -> 5.0; else -> 10.0 }
}

/** Light grid with numbered ticks on both axes; shear labels follow the drawing direction [sg]. */
private fun DrawScope.drawScale(f: MohrFrame, c: CalcColors, sg: Double) {
    val grid = c.muted.copy(alpha = 0.14f)
    val x0 = -f.ox / f.k.toDouble(); val x1 = (size.width - f.ox) / f.k.toDouble()
    val step = niceStep(x1 - x0)
    val axisY = f.oy
    val zeroX = f.x(0.0).coerceIn(0f, size.width)
    var v = Math.floor(x0 / step) * step
    while (v <= x1) {
        val x = f.x(v)
        drawLine(grid, Offset(x, 0f), Offset(x, size.height))
        if (abs(v) > step / 2) small(short(v), Offset(x, axisY + 13.dp.toPx()), c.muted, center = true)
        v += step
    }
    val y0 = (f.oy - size.height) / f.k.toDouble(); val y1 = f.oy / f.k.toDouble()
    var u = Math.floor(y0 / step) * step
    while (u <= y1) {
        val y = f.y(u)
        drawLine(grid, Offset(0f, y), Offset(size.width, y))
        if (abs(u) > step / 2) small(short(sg * u), Offset(zeroX + 4.dp.toPx(), y - 2.dp.toPx()), c.muted)
        u += step
    }
}

private fun DrawScope.drawAxes(f: MohrFrame, c: CalcColors, s: String, t: String, shearDown: Boolean) {
    val axis = c.muted.copy(alpha = 0.85f)
    drawLine(axis, Offset(0f, f.oy), Offset(size.width, f.oy), 1.2.dp.toPx())
    // σ arrow at the right end.
    val end = size.width - 3.dp.toPx()
    drawLine(axis, Offset(end, f.oy), Offset(end - 7.dp.toPx(), f.oy - 4.dp.toPx()), 1.2.dp.toPx())
    drawLine(axis, Offset(end, f.oy), Offset(end - 7.dp.toPx(), f.oy + 4.dp.toPx()), 1.2.dp.toPx())
    label(s, Offset(size.width - 16.dp.toPx(), f.oy - 8.dp.toPx()), c.muted)
    val x0 = f.x(0.0)
    if (x0 in 0f..size.width) {
        drawLine(axis, Offset(x0, 0f), Offset(x0, size.height), 1.2.dp.toPx())
        // Arrow on the positive shear direction.
        val tip = if (shearDown) size.height - 3.dp.toPx() else 3.dp.toPx()
        val back = if (shearDown) -7.dp.toPx() else 7.dp.toPx()
        drawLine(axis, Offset(x0, tip), Offset(x0 - 4.dp.toPx(), tip + back), 1.2.dp.toPx())
        drawLine(axis, Offset(x0, tip), Offset(x0 + 4.dp.toPx(), tip + back), 1.2.dp.toPx())
        label(t, Offset(x0 - 8.dp.toPx() - textWidth(t, 14.dp.toPx()), if (shearDown) size.height - 8.dp.toPx() else 18.dp.toPx()), c.muted)
    }
}

/**
 * The infinitesimal element on the chosen plane, on the principal planes and on the planes of
 * maximum shear. Stress: arrows on every face, sized by value. Strain: the deformed element over
 * the original one (dashed). [txy] is τ_xy, or γ_xy/2 for strain.
 */
@Composable
internal fun MohrElements(sx: Double, sy: Double, txy: Double, theta: Double, strain: Boolean, s: String, t: String, unit: AngleUnit) {
    val c = LocalCalcColors.current
    val m = Mohr.plane2D(sx, sy, txy)
    val k = if (strain) 2.0 else 1.0
    val sh = if (strain) "γ" else t
    data class El(val title: String, val angle: Double, val a: Double, val b: Double, val tau: Double, val color: Color)
    val q = Mohr.rotate2D(sx, sy, txy, theta)
    val els = listOf(
        El(stringResource(R.string.mohr_el_plane), theta, q.normal, Mohr.rotate2D(sx, sy, txy, theta + Math.PI / 2).normal, q.shear, c.highlight),
        El(stringResource(R.string.mohr_el_principal), m.thetaP, m.p1, m.p2, 0.0, c.accent),
        El(stringResource(R.string.mohr_el_shear), Mohr.normaliseHalfTurn(m.thetaS), m.centre, m.centre, m.radius, c.accent),
    )
    // One scale for all three, so sizes compare across elements.
    val scale = listOf(abs(m.p1), abs(m.p2), m.radius, abs(m.centre)).max().takeIf { it > 1e-300 } ?: 1.0
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        els.forEach { e ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.display).padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(e.title, color = e.color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    if (strain) drawStrainElement(e.angle, e.a, e.b, e.tau, scale, c, e.color)
                    else drawStressElement(e.angle, e.a, e.b, e.tau, scale, c, e.color)
                }
                Text(angleText(e.angle, unit), color = c.muted, style = MaterialTheme.typography.labelSmall)
                val font = LocalMathFont.current
                val style = mathStyle(font, 13f, c.text)
                Text(mathLabel("${s}_x′ = ${short(e.a)}"), style = style)
                Text(mathLabel("${s}_y′ = ${short(e.b)}"), style = style)
                Text(mathLabel("${sh}_x′y′ = ${short(k * e.tau)}"), style = style)
            }
        }
    }
    Note(stringResource(if (strain) R.string.mohr_el_note_strain else R.string.mohr_el_note_stress))
}

/** Maps element coordinates (u along x′, v along y′, v up) to the canvas. */
private class ElementFrame(val cx: Float, val cy: Float, theta: Double) {
    private val co = cos(theta).toFloat(); private val si = sin(theta).toFloat()
    fun at(u: Float, v: Float) = Offset(cx + u * co - v * si, cy - (u * si + v * co))
}

private fun DrawScope.square(f: ElementFrame, h: Float) = Path().apply {
    val pts = listOf(f.at(-h, -h), f.at(h, -h), f.at(h, h), f.at(-h, h))
    moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
}

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color, width: Float) {
    val d = to - from
    val len = d.getDistance()
    if (len < 1f) return
    val ux = d.x / len; val uy = d.y / len
    val head = min(7.dp.toPx(), len * 0.55f)
    drawLine(color, from, to - Offset(ux, uy) * (head * 0.6f), width, cap = StrokeCap.Round)
    val back = to - Offset(ux, uy) * head
    val side = Offset(-uy, ux) * (head * 0.5f)
    drawPath(Path().apply { moveTo(to.x, to.y); lineTo(back.x + side.x, back.y + side.y); lineTo(back.x - side.x, back.y - side.y); close() }, color)
}

private fun DrawScope.axesHint(f: ElementFrame, h: Float, c: CalcColors) {
    // Faint x′ direction through the element, so the rotation reads at a glance.
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
    drawLine(c.muted.copy(alpha = 0.35f), Offset(f.cx, f.cy), Offset(f.cx + size.width * 0.48f, f.cy), 1.dp.toPx(), pathEffect = dash)
    drawLine(c.muted.copy(alpha = 0.6f), f.at(0f, 0f), f.at(h * 0.8f, 0f), 1.dp.toPx())
}

private fun DrawScope.drawStressElement(theta: Double, a: Double, b: Double, tau: Double, scale: Double, c: CalcColors, color: Color) {
    val h = size.minDimension * 0.19f
    val f = ElementFrame(size.width / 2, size.height / 2, theta)
    val maxLen = size.minDimension * 0.26f
    val w = 2.dp.toPx()
    val gap = 2.dp.toPx()
    fun len(v: Double) = (abs(v) / scale * maxLen).toFloat()
    axesHint(f, h, c)
    val sq = square(f, h)
    drawPath(sq, color.copy(alpha = 0.16f))
    drawPath(sq, color, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
    // Normal stresses: outwards when positive (tension), into the face when negative.
    for ((value, dir) in listOf(a to 0, b to 1)) {
        val l = len(value)
        if (l < 1.5f) continue
        for (side in listOf(1f, -1f)) {
            fun p(r: Float) = if (dir == 0) f.at(side * r, 0f) else f.at(0f, side * r)
            if (value > 0) arrow(p(h + gap), p(h + gap + l), c.accent, w)
            else arrow(p(h + gap + l), p(h + gap), c.accent, w)
        }
    }
    // Shear: up on face +x′, right on face +y′, reversed on the opposite faces.
    val l = min(len(tau), 1.7f * h)
    if (l >= 1.5f) {
        val sgn = if (tau > 0) 1f else -1f
        val d = h + 4.dp.toPx()
        arrow(f.at(d, -sgn * l / 2), f.at(d, sgn * l / 2), c.highlight, w)
        arrow(f.at(-d, sgn * l / 2), f.at(-d, -sgn * l / 2), c.highlight, w)
        arrow(f.at(-sgn * l / 2, d), f.at(sgn * l / 2, d), c.highlight, w)
        arrow(f.at(sgn * l / 2, -d), f.at(-sgn * l / 2, -d), c.highlight, w)
    }
}

private fun DrawScope.drawStrainElement(theta: Double, ea: Double, eb: Double, half: Double, scale: Double, c: CalcColors, color: Color) {
    val h = size.minDimension * 0.24f
    val f = ElementFrame(size.width / 2, size.height / 2, theta)
    // Exaggerate so the largest strain moves a corner by about a quarter of the side.
    val g = (0.28 / scale).toFloat()
    axesHint(f, h, c)
    val original = square(f, h)
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    drawPath(original, c.muted.copy(alpha = 0.8f), style = Stroke(1.2.dp.toPx(), pathEffect = dash))
    // Corner (u, v) moves by g·E·(u, v) with E = [[ε_x′, γ/2], [γ/2, ε_y′]]: positive γ closes the right angle.
    fun moved(u: Float, v: Float) = f.at(
        u + g * (ea.toFloat() * u + half.toFloat() * v),
        v + g * (half.toFloat() * u + eb.toFloat() * v),
    )
    val pts = listOf(moved(-h, -h), moved(h, -h), moved(h, h), moved(-h, h))
    val shape = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    drawPath(shape, color.copy(alpha = 0.18f))
    drawPath(shape, color, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
}

private fun paint(color: Color, sizePx: Float, bold: Boolean = false) = android.graphics.Paint().apply {
    isAntiAlias = true
    this.color = color.toArgb()
    textSize = sizePx
    if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
}

private fun textWidth(text: String, sizePx: Float) = paint(Color.Black, sizePx).measureText(text)

private fun DrawScope.label(text: String, at: Offset, color: Color) {
    drawContext.canvas.nativeCanvas.drawText(text, at.x, at.y, paint(color, 14.dp.toPx()))
}

private fun DrawScope.small(text: String, at: Offset, color: Color, center: Boolean = false) {
    val pt = paint(color.copy(alpha = 0.9f), 9.5.dp.toPx())
    val x = if (center) at.x - pt.measureText(text) / 2 else at.x
    drawContext.canvas.nativeCanvas.drawText(text, x, at.y, pt)
}

/**
 * A point's label in a small rounded pill, placed on the side away from the circle's centre
 * so it does not cover the circle, and kept inside the canvas.
 */
private fun DrawScope.tag(
    text: String, at: Offset, centre: Offset, c: CalcColors, color: Color,
    below: Boolean = false, strong: Boolean = false, stagger: Boolean = false,
) {
    val pt = paint(color, 11.dp.toPx(), bold = strong)
    val w = pt.measureText(text)
    val padH = 5.dp.toPx(); val h = 16.dp.toPx(); val gap = 7.dp.toPx()
    val right = at.x >= centre.x
    var left = if (right) at.x + gap else at.x - gap - w - 2 * padH
    var top = when {
        below -> at.y + gap + if (stagger) h + 2.dp.toPx() else 0f
        at.y <= centre.y -> at.y - gap - h
        else -> at.y + gap
    }
    left = left.coerceIn(2.dp.toPx(), size.width - w - 2 * padH - 2.dp.toPx())
    top = top.coerceIn(2.dp.toPx(), size.height - h - 2.dp.toPx())
    drawRoundRect(
        c.display.copy(alpha = 0.88f), topLeft = Offset(left, top), size = Size(w + 2 * padH, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
    )
    drawRoundRect(
        color.copy(alpha = 0.35f), topLeft = Offset(left, top), size = Size(w + 2 * padH, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2), style = Stroke(0.8.dp.toPx()),
    )
    drawContext.canvas.nativeCanvas.drawText(text, left + padH, top + h * 0.72f, pt)
}

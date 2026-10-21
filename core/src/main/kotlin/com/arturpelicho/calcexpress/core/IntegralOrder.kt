package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Changes the order of a double integral ∫_a^b ∫_{g1(x)}^{g2(x)} f dy dx with variable inner limits
 * into a sum of ∫_{c}^{d} ∫_{h1(y)}^{h2(y)} f dx dy, inverting g1 and g2 symbolically.
 */
object IntegralOrder {

    private enum class Shape { CONST, UP, DOWN }

    private class Limit(val s: S, val shape: Shape, val atA: Double, val atB: Double, val inverse: S?, val sAtA: S, val sAtB: S)

    private class Piece(val y0: S, val y1: S, val lo: S, val hi: S)

    /** The new line content, or null when the region is not handled. */
    fun reorder(outer: Integral, inner: Integral, angle: AngleUnit): List<Node>? = try {
        build(outer, inner, angle)
    } catch (e: CalcException) {
        null
    } catch (e: Unsupported) {
        null
    }

    private fun build(outer: Integral, inner: Integral, angle: AngleUnit): List<Node>? {
        val x = (outer.variable.items.singleOrNull() as? Sym)?.text ?: return null
        val y = (inner.variable.items.singleOrNull() as? Sym)?.text ?: return null
        if (x == y) return null
        val ctx = EvalContext(angle)
        val aE = Parser.parse(outer.lower); val bE = Parser.parse(outer.upper)
        if (aE.freeVariables().isNotEmpty() || bE.freeVariables().isNotEmpty()) return null
        val a = aE.eval(ctx); val b = bE.eval(ctx)
        if (!(a < b)) return null
        val aS = Cas.fromExpr(aE, angle); val bS = Cas.fromExpr(bE, angle)
        val calc = Calculus(angle)

        fun limit(row: MathRow): Limit? {
            val e = Parser.parse(row)
            if (!e.freeVariables().all { it == x }) return null
            val s = Cas.fromExpr(e, angle)
            val f = { t: Double -> e.eval(ctx.with(x, t)) }
            val n = 200
            val vs = (0..n).map { f(a + (b - a) * it / n) }
            if (vs.any { !it.isFinite() }) return null
            val span = max(1.0, vs.maxOf { abs(it) })
            val diffs = vs.zipWithNext { p, q -> q - p }
            val shape = when {
                diffs.all { abs(it) < 1e-12 * span } -> Shape.CONST
                diffs.all { it > 0 } -> Shape.UP
                diffs.all { it < 0 } -> Shape.DOWN
                else -> return null
            }
            var inv: S? = null
            if (shape != Shape.CONST) {
                val sols = calc.solveFor(Cas.sub(s, S.V(y)), x) ?: return null
                inv = sols.firstOrNull { sol ->
                    val ie = Cas.toExpr(sol)
                    (1 until 10).all { k ->
                        val t = a + (b - a) * k / 10
                        val back = try { ie.eval(ctx.with(y, f(t))) } catch (ex: CalcException) { Double.NaN }
                        abs(back - t) < 1e-7 * max(1.0, abs(t))
                    }
                } ?: return null
            }
            return Limit(s, shape, vs.first(), vs.last(), inv, Cas.substitute(s, x, aS), Cas.substitute(s, x, bS))
        }

        val g1 = limit(inner.lower) ?: return null
        val g2 = limit(inner.upper) ?: return null
        // The region must have g1 ≤ g2 on [a, b].
        val e1 = Parser.parse(inner.lower); val e2 = Parser.parse(inner.upper)
        for (k in 0..50) {
            val t = a + (b - a) * k / 50
            if (e1.eval(ctx.with(x, t)) > e2.eval(ctx.with(x, t)) + 1e-12) return null
        }

        // Breakpoints in y, keeping a symbolic form for display.
        val points = listOf(g1.atA to g1.sAtA, g1.atB to g1.sAtB, g2.atA to g2.sAtA, g2.atB to g2.sAtB)
            .sortedBy { it.first }
        val distinct = mutableListOf<Pair<Double, S>>()
        for (p in points) if (distinct.isEmpty() || p.first - distinct.last().first > 1e-12 * max(1.0, abs(p.first))) distinct += p
        val yMin = minOf(g1.atA, g1.atB); val yMax = maxOf(g2.atA, g2.atB)
        val pts = distinct.filter { it.first >= yMin - 1e-12 && it.first <= yMax + 1e-12 }

        val xa = aS; val xb = bS
        val pieces = mutableListOf<Piece>()
        for ((p, q) in pts.zipWithNext()) {
            val ym = (p.first + q.first) / 2
            var lo: S = xa; var hi: S = xb
            var empty = false
            // y ≤ g2(x)
            when (g2.shape) {
                Shape.CONST -> if (ym > g2.atA) empty = true
                Shape.UP -> if (ym > g2.atB) empty = true else if (ym > g2.atA) lo = g2.inverse!!
                Shape.DOWN -> if (ym > g2.atA) empty = true else if (ym > g2.atB) hi = g2.inverse!!
            }
            // g1(x) ≤ y
            when (g1.shape) {
                Shape.CONST -> if (ym < g1.atA) empty = true
                Shape.UP -> if (ym < g1.atA) empty = true else if (ym < g1.atB) {
                    if (hi != xb) return null
                    hi = g1.inverse!!
                }
                Shape.DOWN -> if (ym < g1.atB) empty = true else if (ym < g1.atA) {
                    if (lo != xa) return null
                    lo = g1.inverse!!
                }
            }
            if (empty) continue
            val last = pieces.lastOrNull()
            if (last != null && last.lo == lo && last.hi == hi && Cas.sub(last.y1, p.second) == Cas.ZERO) {
                pieces[pieces.size - 1] = Piece(last.y0, q.second, lo, hi)
            } else pieces += Piece(p.second, q.second, lo, hi)
        }
        if (pieces.isEmpty()) return null

        val out = mutableListOf<Node>()
        for ((i, piece) in pieces.withIndex()) {
            if (i > 0) out += Sym.op(Sym.PLUS)
            val innerNew = Integral(Cas.toRow(piece.lo), Cas.toRow(piece.hi), inner.body.deepCopy(), MathRow(mutableListOf(Sym.variable(x))))
            out += Integral(Cas.toRow(piece.y0), Cas.toRow(piece.y1), MathRow(mutableListOf(innerNew)), MathRow(mutableListOf(Sym.variable(y))))
        }
        return out
    }
}

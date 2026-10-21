package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.math.max

/** constant + Σ coefficient·parameter, e.g. x = 4 − 2y. */
data class Affine(val constant: Double, val terms: List<Pair<String, Double>>)

/** Exact solving of linear systems, including ones with fewer equations than unknowns. */
object Linear {

    /** Order in which unknowns are solved for; the rest become parameters. */
    private val PRIORITY = listOf("x", "y", "z", "α", "β", "γ", "σ", "τ")

    fun byPriority(names: Collection<String>): List<String> =
        names.sortedWith(compareBy<String> { PRIORITY.indexOf(it).let { i -> if (i < 0) PRIORITY.size else i } }.thenBy { it })

    /**
     * Writes each residual as g·v + c, or returns null when a residual is not linear in the unknowns
     * (or cannot be evaluated at the probe points).
     */
    fun coefficients(residuals: List<Expr>, unknowns: List<String>, ctx: EvalContext): Pair<Array<DoubleArray>, DoubleArray>? {
        val m = unknowns.size
        fun at(p: DoubleArray, r: Expr): Double =
            r.eval(ctx.with(unknowns.indices.associate { unknowns[it] to p[it] }))
        val base = DoubleArray(m) { 0.31 + 0.17 * it }
        val probes = listOf(DoubleArray(m) { 1.7 - 0.43 * it }, DoubleArray(m) { -2.3 + 0.61 * it * it })
        val a = Array(residuals.size) { DoubleArray(m) }
        val c = DoubleArray(residuals.size)
        try {
            residuals.forEachIndexed { i, r ->
                val r0 = at(base, r)
                for (j in 0 until m) {
                    val p = base.copyOf(); p[j] += 1.0
                    a[i][j] = at(p, r) - r0
                }
                c[i] = r0 - (0 until m).sumOf { a[i][it] * base[it] }
                for (p in probes) {
                    val predicted = c[i] + (0 until m).sumOf { a[i][it] * p[it] }
                    val actual = at(p, r)
                    if (!actual.isFinite() || abs(actual - predicted) > 1e-8 * max(1.0, abs(actual))) return null
                }
            }
        } catch (e: CalcException) {
            return null
        }
        if (a.any { row -> row.any { !it.isFinite() } } || c.any { !it.isFinite() }) return null
        return a to c
    }

    /**
     * Solves A·v + c = 0. Unknowns are tried as pivots in [byPriority] order; unknowns without a pivot
     * become free parameters. Throws NO_SOLUTION for an inconsistent system.
     */
    fun solve(a: Array<DoubleArray>, c: DoubleArray, unknowns: List<String>): List<Pair<String, Affine>> {
        val rows = a.size
        val m = unknowns.size
        val order = byPriority(unknowns).map { unknowns.indexOf(it) }
        val mat = Array(rows) { i -> DoubleArray(m + 1) { j -> if (j < m) a[i][j] else -c[i] } }
        val scale = max(1.0, mat.maxOf { row -> row.maxOf { abs(it) } })
        val eps = 1e-10 * scale
        val pivotRowOf = mutableMapOf<Int, Int>()
        var next = 0
        for (col in order) {
            if (next == rows) break
            val p = (next until rows).maxByOrNull { abs(mat[it][col]) } ?: break
            if (abs(mat[p][col]) <= eps) continue
            val t = mat[p]; mat[p] = mat[next]; mat[next] = t
            val pv = mat[next][col]
            for (j in 0..m) mat[next][j] /= pv
            for (i in 0 until rows) if (i != next) {
                val f = mat[i][col]
                if (f != 0.0) for (j in 0..m) mat[i][j] -= f * mat[next][j]
            }
            pivotRowOf[col] = next
            next++
        }
        for (i in next until rows) if (abs(mat[i][m]) > 1e-8 * scale) throw CalcException(CalcException.Reason.NO_SOLUTION)
        val params = order.filter { it !in pivotRowOf }
        return order.filter { it in pivotRowOf }.map { col ->
            val row = mat[pivotRowOf.getValue(col)]
            unknowns[col] to Affine(
                clean(row[m]),
                params.mapNotNull { pj -> clean(-row[pj]).takeIf { abs(it) > 1e-12 }?.let { unknowns[pj] to it } },
            )
        }
    }

    private fun clean(v: Double): Double {
        val r = Math.rint(v * 1e9) / 1e9
        return if (abs(r - v) < 1e-9 * max(1.0, abs(v))) r + 0.0 else v
    }
}

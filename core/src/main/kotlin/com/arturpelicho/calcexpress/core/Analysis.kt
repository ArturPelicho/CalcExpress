package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.math.max

/** What the calculation area currently holds, and what "=" produced. */
sealed class Outcome {
    /** A number; [exact] is "= closed form" (π/3, √2/2, 1/7…) when one was found. */
    data class Value(val value: Double, val exact: MathRow? = null) : Outcome()
    data class MatrixValue(val matrix: com.arturpelicho.calcexpress.core.Value.Mat) : Outcome()
    /** One entry per unknown; several values when an equation has several roots. */
    data class Solutions(val values: List<Pair<String, List<Double>>>) : Outcome()
    /** Fewer equations than unknowns: the first unknowns (by priority) in terms of the rest. */
    data class Parametric(val values: List<Pair<String, Affine>>) : Outcome()
    /** Symbolic results, one row each, already starting with "=" or "x =" (e.g. antiderivatives). */
    data class Symbolic(val rows: List<MathRow>) : Outcome()
    /** A differential equation solved only numerically: see the graph of [y]([x]). */
    data class NumericOde(val y: String, val x: String) : Outcome()
    data class Truth(val holds: Boolean) : Outcome()
    /** An expression with one free variable: a function that can be plotted. */
    data class Function(val variable: String) : Outcome()
    data class Error(val reason: CalcException.Reason, val detail: String = "") : Outcome()
}

/** What can be drawn for the current content. */
sealed class GraphSpec {
    data class Plot(val variable: String, val f: (Double) -> Double, val marks: List<Double> = emptyList()) : GraphSpec()
    data class Area(val variable: String, val f: (Double) -> Double, val a: Double, val b: Double) : GraphSpec()
    /** Region of a double integral: outer ∈ [a,b], inner between lo(outer) and hi(outer). */
    data class Region(
        val outer: String, val inner: String, val a: Double, val b: Double,
        val lo: (Double) -> Double, val hi: (Double) -> Double,
    ) : GraphSpec()
}

object Analysis {

    fun evaluate(lines: List<MathRow>, angle: AngleUnit): Outcome = try {
        evaluateOrThrow(lines.filter { !it.isEmpty }, angle)
    } catch (e: CalcException) {
        Outcome.Error(e.reason, e.detail)
    } catch (e: StackOverflowError) {
        Outcome.Error(CalcException.Reason.SYNTAX)
    }

    private fun isEquation(line: MathRow) = line.items.any { it is Sym && it.kind == SymKind.EQUALS }

    private fun evaluateOrThrow(lines: List<MathRow>, angle: AngleUnit): Outcome {
        if (lines.isEmpty()) throw CalcException(CalcException.Reason.EMPTY_CASE)
        val ctx = EvalContext(angle)
        val filled = Parser.filledCases(lines)

        if (lines.none(::isEquation)) {
            if (lines.size > 1) throw CalcException(CalcException.Reason.NOT_EQUATION)
            val e = Parser.parse(lines[0], filled)
            if (Expr.hasIndefinite(e)) return Outcome.Symbolic(listOf(antiderivativeRow(symbolic(e, angle))))
            val free = e.freeVariables()
            // A derivative of a function (not at a point): show it written out.
            if (free.isNotEmpty() && Expr.hasDerivative(e)) {
                val d = try { Cas.fromExpr(e, angle) } catch (x: Unsupported) { null }
                if (d != null) return Outcome.Symbolic(listOf(MathRow(mutableListOf<Node>(Sym.equals())).also { it.items += Cas.toRow(d).items }))
            }
            return when (free.size) {
                0 -> when (val v = e.evalValue(ctx)) {
                    is Value.Scalar -> Outcome.Value(checked(v.v), exactRow(e, lines[0], v.v, angle))
                    is Value.Mat -> Outcome.MatrixValue(v)
                }
                1 -> Outcome.Function(free.first())
                else -> throw CalcException(CalcException.Reason.UNKNOWN_VARIABLE, free.sorted().joinToString(", "))
            }
        }

        val parsed = lines.map { Parser.parseLine(it, filled) }
        Ode.detect(parsed, angle)?.let { p ->
            val sol = Ode.solve(p, angle)
            return if (sol.rows.isEmpty()) Outcome.NumericOde(p.y, p.x) else Outcome.Symbolic(sol.rows)
        }

        // Equations: each "a = b = c" contributes a−b and b−c.
        val residuals = mutableListOf<Expr>()
        for (line in lines) {
            val sides = Parser.parseLine(line, filled)
            if (sides.size < 2) throw CalcException(CalcException.Reason.NOT_EQUATION)
            for (k in 0 until sides.size - 1) residuals += Expr.Bin('-', sides[k], sides[k + 1])
        }
        val unknowns = residuals.flatMap { it.freeVariables() }.toSortedSet().toList()

        if (unknowns.isEmpty()) {
            val holds = residuals.all { r -> kotlin.math.abs(r.eval(ctx)) < 1e-9 }
            return Outcome.Truth(holds)
        }
        // Linear systems are solved exactly; with too few equations the priority unknowns
        // (x, y, z, α, …) are written in terms of the others.
        Linear.coefficients(residuals, unknowns, ctx)?.let { (a, c) ->
            val sol = Linear.solve(a, c, unknowns)
            return if (sol.isEmpty()) Outcome.Truth(true) else if (sol.all { it.second.terms.isEmpty() } && sol.size == unknowns.size) {
                Outcome.Solutions(Linear.byPriority(unknowns).map { u -> u to listOf(sol.first { it.first == u }.second.constant) })
            } else Outcome.Parametric(sol)
        }
        if (unknowns.size > residuals.size) {
            eliminate(residuals, unknowns, angle)?.let { return it }
            throw CalcException(CalcException.Reason.UNDERDETERMINED, Linear.byPriority(unknowns).joinToString(", "))
        }

        if (unknowns.size == 1 && residuals.size == 1) {
            val v = unknowns[0]
            val r = residuals[0]
            val roots = Numerics.findRoots({ x -> r.eval(ctx.with(v, x)) })
            if (roots.isEmpty()) throw CalcException(CalcException.Reason.NO_SOLUTION)
            return Outcome.Solutions(listOf(v to roots))
        }

        // Square (or over-determined, using the first n) system.
        val n = unknowns.size
        val used = residuals.take(n)
        val sol = Numerics.solveSystem(n) { x ->
            val c = ctx.with(unknowns.indices.associate { unknowns[it] to x[it] })
            DoubleArray(n) { used[it].eval(c) }
        } ?: throw CalcException(CalcException.Reason.NO_SOLUTION)
        val c = ctx.with(unknowns.indices.associate { unknowns[it] to sol[it] })
        if (residuals.drop(n).any { kotlin.math.abs(it.eval(c)) > 1e-7 }) throw CalcException(CalcException.Reason.NO_SOLUTION)
        return Outcome.Solutions(unknowns.indices.map { unknowns[it] to listOf(clean(sol[it])) })
    }

    /**
     * Too few (non-linear) equations: isolates the unknowns in priority order (x, y, z, α, …),
     * substituting each into the remaining equations. One row per unknown and branch.
     */
    private fun eliminate(residuals: List<Expr>, unknowns: List<String>, angle: AngleUnit): Outcome? {
        val eqs = try { residuals.map { Cas.fromExpr(it, angle) } } catch (e: Unsupported) { return null }
        val calc = Calculus(angle)
        val order = Linear.byPriority(unknowns)
        val branches = solveSymbolic(eqs, order, calc, 0) ?: return null
        if (branches.isEmpty()) return null
        val rows = mutableListOf<MathRow>()
        branches.take(MAX_BRANCHES).forEachIndexed { i, m ->
            for (v in order) {
                val sol = m[v] ?: continue
                val name = if (branches.size > 1) v + subscript(i + 1) else v
                rows += MathRow(mutableListOf<Node>(Sym.variable(name), Sym.equals())).also { it.items += Cas.toRow(sol).items }
            }
        }
        return Outcome.Symbolic(rows)
    }

    private const val MAX_BRANCHES = 4

    private fun subscript(n: Int) = n.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")

    /**
     * All solution branches as maps unknown → expression in the remaining (free) unknowns.
     * Among the possible eliminations, keeps the one that solves the highest-priority unknowns.
     */
    private fun solveSymbolic(eqs: List<S>, order: List<String>, calc: Calculus, depth: Int): List<Map<String, S>>? {
        if (eqs.isEmpty()) return listOf(emptyMap())
        if (depth > 4) return null
        var best: List<Map<String, S>>? = null
        var bestScore: List<Int>? = null
        for (v in order) {
            for (eq in eqs) {
                if (Cas.freeOf(eq, v)) continue
                val sols = calc.solveFor(eq, v) ?: continue
                val results = mutableListOf<Map<String, S>>()
                for (sol in sols) {
                    val rest = (eqs - eq).map { Cas.substitute(it, v, sol) }.filter { it != Cas.ZERO }
                    if (rest.any { it is S.N }) continue // inconsistent branch
                    val sub = solveSymbolic(rest, order - v, calc, depth + 1) ?: continue
                    for (m in sub) {
                        var full = sol
                        for ((name, value) in m) full = Cas.substitute(full, name, value)
                        results += m + (v to full)
                    }
                }
                if (results.isEmpty()) continue
                val score = results.first().keys.map { order.indexOf(it) }.sorted()
                if (bestScore == null || compareScores(score, bestScore) < 0) {
                    best = results.take(MAX_BRANCHES); bestScore = score
                }
            }
            // Solving for the first unknown that works is already optimal for it.
            if (best != null && bestScore!!.first() == order.indexOf(v)) return best
        }
        return best
    }

    private fun compareScores(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) if (a[i] != b[i]) return a[i] - b[i]
        return b.size - a.size
    }

    /** "= closed form" when it is short, matches [v] and says more than the input or the decimal. */
    private fun exactRow(e: Expr, input: MathRow, v: Double, angle: AngleUnit): MathRow? {
        val s = try { Cas.fromExpr(e, angle) } catch (x: Unsupported) { return null } catch (x: CalcException) { return null }
        if (s is S.N) {
            val r = Cas.rational(s.v) ?: return null
            if (r.second == 1L || Format.number(v).length <= 8) return null
        } else {
            val sv = Cas.evalOrNull(s, angle) ?: return null
            if (abs(sv - v) > 1e-9 * max(1.0, abs(v))) return null
        }
        val row = Cas.toRow(s)
        if (row.toString() == input.toString() || row.allRows().sumOf { it.items.size } > 30) return null
        return MathRow(mutableListOf<Node>(Sym.equals())).also { it.items += row.items }
    }

    private fun symbolic(e: Expr, angle: AngleUnit): S =
        try { Cas.fromExpr(e, angle) } catch (x: Unsupported) { throw CalcException(CalcException.Reason.NO_ANTIDERIVATIVE) }

    /** "= F + C" */
    private fun antiderivativeRow(f: S): MathRow {
        val row = Cas.toRow(f)
        row.items.add(0, Sym.equals())
        row.items += Sym.op(Sym.PLUS)
        row.items += Sym.variable("C")
        return row
    }

    private fun clean(v: Double): Double {
        val r = Math.rint(v * 1e9) / 1e9
        return if (kotlin.math.abs(r - v) < 1e-9 * kotlin.math.max(1.0, kotlin.math.abs(v))) r else v
    }

    private fun checked(v: Double): Double {
        if (v.isNaN()) throw CalcException(CalcException.Reason.DOMAIN)
        return v
    }

    /** True when no limit of the chain depends on another integration variable. */
    fun limitsAreConstant(chain: List<Integral>): Boolean {
        val vars = chain.mapNotNull { (it.variable.items.singleOrNull() as? Sym)?.text }.toSet()
        return chain.all { integral ->
            listOf(integral.lower, integral.upper).all { row ->
                row.allRows().none { r -> r.items.any { it is Sym && it.kind == SymKind.VARIABLE && it.text in vars } }
            }
        }
    }

    /** Picks the most useful graph for the active content, or null. */
    fun graphFor(lines: List<MathRow>, angle: AngleUnit): GraphSpec? = try {
        graphOrThrow(lines.filter { !it.isEmpty }, angle)
    } catch (e: CalcException) { null }

    private fun graphOrThrow(lines: List<MathRow>, angle: AngleUnit): GraphSpec? {
        if (lines.isNotEmpty() && lines.all(::isEquation)) {
            val parsed = lines.map { Parser.parseLine(it, Parser.filledCases(lines)) }
            Ode.detect(parsed, angle)?.let { p ->
                val f = Ode.solve(p, angle).f ?: return null
                return GraphSpec.Plot(p.x, f)
            }
        }
        if (lines.size != 1) return null
        val line = lines[0]
        val ctx = EvalContext(angle)

        // Integrals: shaded area (single) or integration region (double).
        val integral = line.items.singleOrNull() as? Integral
        if (integral != null) {
            val chain = Editor.integralChain(integral)
            val e = Parser.parse(line) as? Expr.IntegralE ?: return null
            if (chain.size == 1) {
                val a = Parser.parse(integral.lower).eval(ctx)
                val b = Parser.parse(integral.upper).eval(ctx)
                val body = e.body
                return GraphSpec.Area(e.variable, { x -> body.eval(ctx.with(e.variable, x)) }, a, b)
            }
            if (chain.size == 2) {
                val outer = e
                val inner = e.body as? Expr.IntegralE ?: return null
                val a = outer.lower.eval(ctx)
                val b = outer.upper.eval(ctx)
                return GraphSpec.Region(
                    outer.variable, inner.variable, a, b,
                    { x -> inner.lower.eval(ctx.with(outer.variable, x)) },
                    { x -> inner.upper.eval(ctx.with(outer.variable, x)) },
                )
            }
            return null
        }

        if (isEquation(line)) {
            val sides = Parser.parseLine(line)
            if (sides.size != 2) return null
            val r = Expr.Bin('-', sides[0], sides[1])
            val free = r.freeVariables()
            if (free.size != 1) return null
            val v = free.first()
            val f = { x: Double -> r.eval(ctx.with(v, x)) }
            return GraphSpec.Plot(v, f, Numerics.findRoots(f))
        }

        var e = Parser.parse(line)
        if (Expr.hasIndefinite(e)) e = Cas.toExpr(symbolic(e, angle))
        val free = e.freeVariables()
        if (free.size != 1) return null
        val v = free.first()
        return GraphSpec.Plot(v, { x -> e.eval(ctx.with(v, x)) })
    }
}

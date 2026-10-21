package com.arturpelicho.calcexpress.core

/** What the calculation area currently holds, and what "=" produced. */
sealed class Outcome {
    data class Value(val value: Double) : Outcome()
    /** One entry per unknown; several values when an equation has several roots. */
    data class Solutions(val values: List<Pair<String, List<Double>>>) : Outcome()
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
            val free = e.freeVariables()
            return when (free.size) {
                0 -> Outcome.Value(checked(e.eval(ctx)))
                1 -> Outcome.Function(free.first())
                else -> throw CalcException(CalcException.Reason.UNKNOWN_VARIABLE, free.sorted().joinToString(", "))
            }
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
        if (unknowns.size > residuals.size) {
            throw CalcException(CalcException.Reason.UNDERDETERMINED, unknowns.joinToString(", "))
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

        val e = Parser.parse(line)
        val free = e.freeVariables()
        if (free.size != 1) return null
        val v = free.first()
        return GraphSpec.Plot(v, { x -> e.eval(ctx.with(v, x)) })
    }
}

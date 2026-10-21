package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Ordinary differential equations written with the d/dx key: d/dx(y) = f(x, y), or
 * d/dx(d/dx(y)) = f(x, y, y′). Extra lines "x = x₀", "y = y₀" and "d/dx(y) = y₁" give the
 * initial conditions. Symbolic solutions are tried first (direct integration, linear,
 * separable, constant-coefficient second order); otherwise RK4 gives a numerical solution.
 */
object Ode {

    private const val Y1 = "\u0001y′"
    private const val Y2 = "\u0001y″"
    const val CONSTANT = "C"

    class Problem(
        val x: String, val y: String, val order: Int,
        /** Highest derivative as an explicit function of x, y (and y′). */
        val rhs: S,
        val x0: Double?, val y0: Double?, val y1: Double?,
    )

    class Solution(
        /** Rows to show: "y = …". Empty when only a numerical solution exists. */
        val rows: List<MathRow>,
        /** y(x) for plotting, when the solution is fully determined. */
        val f: ((Double) -> Double)?,
    )

    // ------------------------------------------------------------------ detection

    private data class D(val x: String, val y: String, val order: Int)

    private fun derivatives(e: Expr, out: MutableSet<D>) {
        when (e) {
            is Expr.DerivativeE -> {
                val (y, order) = chain(e)
                if (y != null) { out += D(e.variable, y, order); return }
                derivatives(e.body, out)
            }
            is Expr.Neg -> derivatives(e.e, out)
            is Expr.Bin -> { derivatives(e.a, out); derivatives(e.b, out) }
            is Expr.Call -> derivatives(e.arg, out)
            is Expr.RootE -> { derivatives(e.radicand, out); e.index?.let { derivatives(it, out) } }
            is Expr.Factorial -> derivatives(e.e, out)
            else -> {}
        }
    }

    /** d/dx(y) → (y, 1); d/dx(d/dx(y)) → (y, 2); anything else → (null, 0). */
    private fun chain(e: Expr.DerivativeE): Pair<String?, Int> {
        val b = e.body
        if (b is Expr.Var && b.name != e.variable) return b.name to 1
        if (b is Expr.DerivativeE && b.variable == e.variable) {
            val inner = b.body
            if (inner is Expr.Var && inner.name != e.variable) return inner.name to 2
        }
        return null to 0
    }

    private fun lift(e: Expr): Expr = when (e) {
        is Expr.DerivativeE -> when (chain(e).second) {
            1 -> Expr.Var(Y1)
            2 -> Expr.Var(Y2)
            else -> Expr.DerivativeE(e.variable, lift(e.body))
        }
        is Expr.Neg -> Expr.Neg(lift(e.e))
        is Expr.Bin -> Expr.Bin(e.op, lift(e.a), lift(e.b))
        is Expr.Call -> Expr.Call(e.fn, lift(e.arg))
        is Expr.RootE -> Expr.RootE(lift(e.radicand), e.index?.let(::lift))
        is Expr.Factorial -> Expr.Factorial(lift(e.e))
        else -> e
    }

    /** Returns the problem when [lines] (parsed sides per line) hold a differential equation. */
    fun detect(lines: List<List<Expr>>, angle: AngleUnit): Problem? {
        val withDerivatives = lines.indices.filter { i ->
            mutableSetOf<D>().also { found -> lines[i].forEach { derivatives(it, found) } }.isNotEmpty()
        }
        if (withDerivatives.isEmpty()) return null
        // With several such lines, "d/dx(y) = number" lines are initial conditions.
        val candidates = if (withDerivatives.size == 1) withDerivatives else withDerivatives.filterNot { i ->
            val s = lines[i]
            s.size == 2 && s[0] is Expr.DerivativeE && s[1].freeVariables().isEmpty()
        }
        if (candidates.size != 1) return null
        val odeIndex = candidates[0]
        val found = mutableSetOf<D>().also { f -> lines[odeIndex].forEach { derivatives(it, f) } }
        if (found.map { it.x to it.y }.toSet().size != 1) return null
        val d = found.maxBy { it.order }
        val sides = lines[odeIndex]
        if (sides.size != 2) throw CalcException(CalcException.Reason.NOT_EQUATION)
        val ctx = EvalContext(angle)
        var x0: Double? = null; var y0: Double? = null; var y1: Double? = null
        for ((i, s) in lines.withIndex()) {
            if (i == odeIndex) continue
            if (s.size != 2 || s[1].freeVariables().isNotEmpty()) throw CalcException(CalcException.Reason.NOT_EQUATION)
            val v = s[1].eval(ctx)
            val l = s[0]
            when {
                l is Expr.Var && l.name == d.x -> x0 = v
                l is Expr.Var && l.name == d.y -> y0 = v
                l is Expr.DerivativeE && chain(l) == (d.y to 1) -> y1 = v
                else -> throw CalcException(CalcException.Reason.NOT_EQUATION)
            }
        }
        val r = Cas.fromExpr(Expr.Bin('-', lift(sides[0]), lift(sides[1])), angle)
        val top = if (d.order == 2) Y2 else Y1
        val rhs = Calculus(angle).solveFor(r, top)?.firstOrNull() ?: throw CalcException(CalcException.Reason.NO_SOLUTION)
        if (d.order == 1 && !Cas.freeOf(rhs, Y2)) throw CalcException(CalcException.Reason.NO_SOLUTION)
        return Problem(d.x, d.y, d.order, rhs, x0, y0, y1)
    }

    // ------------------------------------------------------------------ solving

    fun solve(p: Problem, angle: AngleUnit): Solution {
        val calc = Calculus(angle)
        val general = try {
            if (p.order == 1) firstOrder(p, calc) else secondOrder(p, angle)
        } catch (e: Unsupported) { null }
        val hasIc = p.y0 != null && (p.order == 1 || p.y1 != null)
        if (general != null) {
            val particular = if (hasIc) applyConditions(general, p, calc, angle) else null
            val shown = particular ?: general.second
            val row = MathRow(mutableListOf<Node>(Sym.variable(p.y), Sym.equals())).also { it.items += Cas.toRow(shown).items }
            val f = particular?.let { s -> val e = Cas.toExpr(s); { t: Double -> e.eval(EvalContext(angle, mapOf(p.x to t))) } }
            if (!hasIc || particular != null) return Solution(listOf(row), f)
        }
        if (!hasIc) throw CalcException(CalcException.Reason.NO_SOLUTION)
        return Solution(emptyList(), numeric(p, angle))
    }

    /** (constants used, general solution). */
    private fun firstOrder(p: Problem, calc: Calculus): Pair<List<String>, S>? = with(Cas) {
        val x = p.x; val y = p.y
        val f = p.rhs
        val c = S.V(CONSTANT)
        // y′ = f(x)
        if (freeOf(f, y)) {
            val big = calc.antiderivative(f, x) ?: return null
            return listOf(CONSTANT) to add(big, c)
        }
        // y′ = a(x)·y + b(x)
        polynomial(f, y)?.let { poly ->
            if (poly.keys.all { it <= 1 }) {
                val a = poly[1] ?: ZERO
                val b = poly[0] ?: ZERO
                val ia = calc.antiderivative(a, x) ?: return@let
                val mu = pow(EULER, neg(ia))
                val ib = if (b == ZERO) ZERO else calc.antiderivative(expand(mul(b, mu)), x) ?: return@let
                return listOf(CONSTANT) to expand(mul(add(ib, c), pow(EULER, ia)))
            }
        }
        // y′ = g(x)·h(y)
        val factors = if (f is S.Mul) f.factors else listOf(f)
        val (gx, hy) = factors.partition { freeOf(it, y) }
        if (hy.any { !freeOf(it, x) }) return null
        val h = mul(hy)
        val hInt = calc.antiderivative(pow(h, MINUS_ONE), y) ?: return null
        val gInt = calc.antiderivative(mul(gx), x) ?: return null
        val sols = calc.solveFor(sub(hInt, add(gInt, c)), y) ?: return null
        return listOf(CONSTANT) to sols.first()
    }

    /** y″ = −p·y′ − q·y with constant p, q. */
    private fun secondOrder(pr: Problem, angle: AngleUnit): Pair<List<String>, S>? = with(Cas) {
        val f = pr.rhs
        val poly1 = polynomial(f, Y1) ?: return null
        if (poly1.keys.any { it > 1 }) return null
        val a1 = poly1[1] ?: ZERO
        val rest = poly1[0] ?: ZERO
        val poly0 = polynomial(rest, pr.y) ?: return null
        if (poly0.keys.any { it > 1 } || poly0[0] != null) return null
        val a0 = poly0[1] ?: ZERO
        if (a1 !is S.N || a0 !is S.N) return null
        // λ² − a1·λ − a0 = 0
        val p = -a1.v; val q = -a0.v
        val disc = p * p - 4 * q
        val x = S.V(pr.x)
        val c1 = S.V(CONSTANT + "₁"); val c2 = S.V(CONSTANT + "₂")
        val k = angle.toRadians(1.0)
        val sol = when {
            disc > 1e-12 -> {
                val l1 = (-p + sqrt(disc)) / 2; val l2 = (-p - sqrt(disc)) / 2
                add(mul(c1, pow(EULER, mul(num(l1), x))), mul(c2, pow(EULER, mul(num(l2), x))))
            }
            disc < -1e-12 -> {
                val al = -p / 2; val be = sqrt(-disc) / 2
                // cos and sin follow the angle unit: cos_unit(βx/k) = cos(βx) in radians.
                val arg = mul(num(be / k), x)
                mul(pow(EULER, mul(num(al), x)), add(mul(c1, fn(Fn.COS, arg)), mul(c2, fn(Fn.SIN, arg))))
            }
            else -> mul(add(c1, mul(c2, x)), pow(EULER, mul(num(-p / 2), x)))
        }
        return listOf(c1.name, c2.name) to sol
    }

    /** Fixes the constants from the initial conditions (linear in them). */
    private fun applyConditions(general: Pair<List<String>, S>, p: Problem, calc: Calculus, angle: AngleUnit): S? = with(Cas) {
        val (consts, sol) = general
        val x0 = p.x0 ?: 0.0
        val atX0 = substitute(sol, p.x, num(x0))
        if (consts.size == 1) {
            val cs = calc.solveFor(sub(atX0, num(p.y0!!)), consts[0]) ?: return null
            return substitute(sol, consts[0], cs.first())
        }
        val d = calc.derivative(sol, p.x)
        val dAtX0 = substitute(d, p.x, num(x0))
        fun coef(e: S, c: String): Double? {
            val poly = polynomial(e, c) ?: return null
            if (poly.keys.any { it > 1 }) return null
            return (poly[1] ?: ZERO).let { evalNum(substituteAll(it, consts, 0.0), angle) }
        }
        fun const0(e: S) = evalNum(substituteAll(e, consts, 0.0), angle)
        val a11 = coef(atX0, consts[0]) ?: return null
        val a12 = coef(atX0, consts[1]) ?: return null
        val a21 = coef(dAtX0, consts[0]) ?: return null
        val a22 = coef(dAtX0, consts[1]) ?: return null
        val b1 = p.y0!! - (const0(atX0) ?: return null)
        val b2 = p.y1!! - (const0(dAtX0) ?: return null)
        val det = a11 * a22 - a12 * a21
        if (abs(det) < 1e-14) return null
        val v1 = (b1 * a22 - a12 * b2) / det
        val v2 = (a11 * b2 - a21 * b1) / det
        return substitute(substitute(sol, consts[0], num(v1)), consts[1], num(v2))
    }

    private fun substituteAll(e: S, names: List<String>, v: Double): S =
        names.fold(e) { acc, n -> Cas.substitute(acc, n, Cas.num(v)) }

    private fun evalNum(e: S, angle: AngleUnit): Double? =
        try { Cas.toExpr(e).eval(EvalContext(angle)).takeIf { it.isFinite() } } catch (x: CalcException) { null }

    /** RK4 from (x₀, y₀[, y₁]) in both directions, tabulated and interpolated. */
    private fun numeric(p: Problem, angle: AngleUnit): (Double) -> Double {
        val rhs = Cas.toExpr(p.rhs)
        val x0 = p.x0 ?: 0.0
        fun deriv(x: Double, s: DoubleArray): DoubleArray {
            val ctx = EvalContext(angle, mapOf(p.x to x, p.y to s[0], Y1 to (if (p.order == 2) s[1] else 0.0)))
            val v = try { rhs.eval(ctx) } catch (e: CalcException) { Double.NaN }
            return if (p.order == 1) doubleArrayOf(v) else doubleArrayOf(s[1], v)
        }
        val start = if (p.order == 1) doubleArrayOf(p.y0!!) else doubleArrayOf(p.y0!!, p.y1!!)
        val h = 0.01
        val forward = mutableListOf(start)
        val backward = mutableListOf(start)
        fun extend(list: MutableList<DoubleArray>, dir: Double, upTo: Int) {
            while (list.size <= upTo) {
                val s = list.last()
                if (s.any { !it.isFinite() }) { list += s; continue }
                val x = x0 + dir * h * (list.size - 1)
                val hh = dir * h
                val k1 = deriv(x, s)
                val k2 = deriv(x + hh / 2, DoubleArray(s.size) { s[it] + hh / 2 * k1[it] })
                val k3 = deriv(x + hh / 2, DoubleArray(s.size) { s[it] + hh / 2 * k2[it] })
                val k4 = deriv(x + hh, DoubleArray(s.size) { s[it] + hh * k3[it] })
                val n = DoubleArray(s.size) { s[it] + hh / 6 * (k1[it] + 2 * k2[it] + 2 * k3[it] + k4[it]) }
                list += if (n.all { abs(it) < 1e12 }) n else DoubleArray(s.size) { Double.NaN }
            }
        }
        return { x ->
            val t = (x - x0) / h
            val list = if (t >= 0) forward else backward
            val at = abs(t)
            val i = at.toInt()
            if (i > 40_000) Double.NaN else {
                extend(list, if (t >= 0) 1.0 else -1.0, i + 1)
                val f = at - i
                list[i][0] * (1 - f) + list[i + 1][0] * f
            }
        }
    }
}

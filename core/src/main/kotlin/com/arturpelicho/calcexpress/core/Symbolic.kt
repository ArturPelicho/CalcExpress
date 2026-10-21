package com.arturpelicho.calcexpress.core

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

import kotlin.math.sqrt

/**
 * Small computer-algebra layer: canonical expressions, simplification, symbolic derivatives,
 * antiderivatives and isolation of a variable. Trigonometric functions follow the calculator's
 * angle unit, so derivatives carry the factor k = (radians per unit).
 */
sealed class S {
    data class N(val v: Double) : S()
    data class C(val name: String, val v: Double) : S()
    data class V(val name: String) : S()
    data class Add(val terms: List<S>) : S()
    data class Mul(val factors: List<S>) : S()
    data class Pow(val base: S, val exp: S) : S()
    data class F(val fn: Fn, val arg: S) : S()
}

/** Thrown when an expression has no symbolic treatment here (matrices, factorials, ...). */
class Unsupported : Exception()

object Cas {
    val ZERO = S.N(0.0)
    val ONE = S.N(1.0)
    val MINUS_ONE = S.N(-1.0)
    val HALF = S.N(0.5)
    val EULER = S.C(Sym.E, E)
    val PI_C = S.C(Sym.PI, PI)

    fun num(v: Double): S.N {
        val r = Math.rint(v)
        return S.N(if (abs(v - r) < 1e-10 * max(1.0, abs(v))) r + 0.0 else v)
    }

    private fun isInt(v: Double) = v == floor(v) && abs(v) < 1e9

    // ------------------------------------------------------------------ builders (canonical forms)

    fun add(vararg terms: S) = add(terms.toList())

    fun add(terms: List<S>): S {
        val flat = terms.flatMap { if (it is S.Add) it.terms else listOf(it) }
        var c = 0.0
        val groups = LinkedHashMap<S, Double>()
        for (t in flat) {
            if (t is S.N) { c += t.v; continue }
            val (k, rest) = splitCoef(t)
            groups[rest] = (groups[rest] ?: 0.0) + k
        }
        val out = groups.filter { abs(it.value) > 1e-13 }.map { (r, k) -> scaled(k, r) }.sortedWith(termOrder).toMutableList()
        val cn = num(c)
        if (cn.v != 0.0) out += cn
        return when (out.size) { 0 -> ZERO; 1 -> out[0]; else -> S.Add(out) }
    }

    private fun scaled(k: Double, r: S): S = if (num(k).v == 1.0) r else mul(num(k), r)

    /** Numeric coefficient and the rest of a term: 3x² → (3, x²). */
    fun splitCoef(t: S): Pair<Double, S> {
        if (t is S.N) return t.v to ONE
        if (t is S.Mul && t.factors.first() is S.N) {
            val rest = t.factors.drop(1)
            return (t.factors.first() as S.N).v to (if (rest.size == 1) rest[0] else S.Mul(rest))
        }
        return 1.0 to t
    }

    fun mul(vararg factors: S) = mul(factors.toList())

    fun mul(factors: List<S>): S {
        val flat = factors.flatMap { if (it is S.Mul) it.factors else listOf(it) }
        var c = 1.0
        val powers = LinkedHashMap<S, S>()
        for (f in flat) {
            if (f is S.N) { c *= f.v; continue }
            val (b, e) = if (f is S.Pow) f.base to f.exp else f to ONE
            powers[b] = powers[b]?.let { add(it, e) } ?: e
        }
        if (c == 0.0) return ZERO
        val rest = mutableListOf<S>()
        var again = false
        for ((b, e) in powers) {
            when (val p = pow(b, e)) {
                is S.N -> c *= p.v
                is S.Mul -> { rest += p.factors; again = true }
                else -> if (p != ONE) rest += p
            }
        }
        if (again) return mul(listOf(num(c)) + rest)
        rest.sortWith(factorOrder)
        val cn = num(c)
        return when {
            rest.isEmpty() -> cn
            cn.v == 1.0 && rest.size == 1 -> rest[0]
            cn.v == 1.0 -> S.Mul(rest)
            else -> S.Mul(listOf(cn) + rest)
        }
    }

    fun neg(a: S) = mul(MINUS_ONE, a)
    fun sub(a: S, b: S) = add(a, neg(b))
    fun div(a: S, b: S) = mul(a, pow(b, MINUS_ONE))

    fun pow(b: S, e: S): S {
        if (e is S.N && e.v == 0.0) return ONE
        if (e is S.N && e.v == 1.0) return b
        if (b is S.N && b.v == 1.0) return ONE
        if (b is S.N && b.v == 0.0 && e is S.N && e.v > 0) return ZERO
        if (b is S.N && e is S.N) {
            if (isInt(e.v) && abs(e.v) <= 64) {
                val r = b.v.pow(e.v)
                if (r.isFinite() && (e.v > 0 || isInt(r) || isInt(1 / r) || abs(b.v) < 1e6)) return num(r)
            }
            if (b.v > 0) {
                val r = b.v.pow(e.v)
                if (isInt(r) && abs(r) < 1e9) return num(r)
            }
            return S.Pow(b, e)
        }
        if (b is S.Pow && e is S.N && isInt(e.v)) return pow(b.base, mul(b.exp, e))
        if (b is S.Pow && e is S.N && b.exp is S.N && isInt(b.exp.v) && abs(b.exp.v % 2.0) == 1.0) return pow(b.base, mul(b.exp, e))
        if (b is S.Mul && e is S.N && isInt(e.v)) return mul(b.factors.map { pow(it, e) })
        if (b == EULER && e is S.F && e.fn == Fn.LN) return e.arg
        return S.Pow(b, e)
    }

    fun fn(f: Fn, a: S): S {
        if (f == Fn.EXP) return pow(EULER, a)
        if (a is S.N) {
            val v = a.v
            when (f) {
                Fn.SIN, Fn.TAN, Fn.ASIN, Fn.ATAN, Fn.SINH, Fn.TANH -> if (v == 0.0) return ZERO
                Fn.COS, Fn.COSH -> if (v == 0.0) return ONE
                Fn.LN, Fn.LOG -> if (v == 1.0) return ZERO
                Fn.ABS -> return num(abs(v))
                else -> {}
            }
            if (f == Fn.LOG && v > 0 && isInt(kotlin.math.log10(v))) return num(kotlin.math.log10(v))
        }
        if (f == Fn.LN && a == EULER) return ONE
        if (f == Fn.LN && a is S.Pow && a.base == EULER) return a.exp
        if (f == Fn.ABS && (a is S.F && a.fn == Fn.ABS || isPositive(a))) return a
        return S.F(f, a)
    }

    /** Conservative: true only when [s] is positive for every real value of its variables. */
    fun isPositive(s: S): Boolean = when (s) {
        is S.N -> s.v > 0
        is S.C -> s.v > 0
        is S.Pow -> s.base == EULER || (s.base is S.N && s.base.v > 0) ||
            (isPositive(s.base))
        is S.F -> s.fn == Fn.COSH || (s.fn == Fn.EXP)
        is S.Mul -> s.factors.all { isPositive(it) }
        is S.Add -> s.terms.all { isPositive(it) || isEvenPower(it) } && s.terms.any { isPositive(it) }
        else -> false
    }

    private fun isEvenPower(s: S): Boolean {
        val (k, r) = splitCoef(s)
        return k > 0 && r is S.Pow && r.exp is S.N && (r.exp as S.N).v > 0 && (r.exp as S.N).v % 2.0 == 0.0
    }

    private fun rank(s: S): Int = when (s) {
        is S.N -> 0; is S.C -> 1; is S.V -> 2; is S.Pow -> 3; is S.F -> 4; is S.Mul -> 5; is S.Add -> 6
    }

    /** Order of factors: numbers, constants, powers of variables, then exponentials and functions. */
    private fun factorRank(s: S): Int = when {
        s is S.Pow && s.base is S.V -> 2
        s is S.Pow && s.base is S.C && s.exp !is S.N -> 4
        s is S.Pow -> rank(s.base)
        else -> rank(s)
    }

    private fun key(s: S): String = when (s) {
        is S.N -> "0"
        is S.C -> "1" + s.name
        is S.V -> "2" + s.name
        is S.Pow -> key(s.base)
        is S.F -> "4" + s.fn.name + key(s.arg)
        is S.Mul -> s.factors.joinToString("") { key(it) }
        is S.Add -> "6" + s.terms.joinToString("") { key(it) }
    }

    private val factorOrder = Comparator<S> { a, b ->
        val ra = factorRank(a)
        val rb = factorRank(b)
        if (ra != rb) ra - rb else key(a).compareTo(key(b))
    }

    /** Terms by decreasing degree in their main variable, then by name. */
    private val termOrder = Comparator<S> { a, b ->
        val da = degreeHint(a); val db = degreeHint(b)
        if (da != db) db.compareTo(da) else key(splitCoef(a).second).compareTo(key(splitCoef(b).second))
    }

    private fun degreeHint(s: S): Double = when (s) {
        is S.N -> 0.0
        is S.V -> 1.0
        is S.Pow -> (s.exp as? S.N)?.v?.let { it * max(1.0, degreeHint(s.base)) } ?: 1.5
        is S.Mul -> s.factors.sumOf { degreeHint(it) }
        else -> 1.0
    }

    // ------------------------------------------------------------------ conversion

    fun fromExpr(e: Expr, angle: AngleUnit): S = when (e) {
        is Expr.Num -> num(e.value)
        is Expr.Const -> if (e.name == Sym.PI) PI_C else EULER
        is Expr.Var -> S.V(e.name)
        is Expr.Neg -> neg(fromExpr(e.e, angle))
        is Expr.Bin -> {
            val a = fromExpr(e.a, angle); val b = fromExpr(e.b, angle)
            when (e.op) {
                '+' -> add(a, b); '-' -> sub(a, b); '*' -> mul(a, b); '/' -> div(a, b); '^' -> pow(a, b)
                else -> throw Unsupported()
            }
        }
        is Expr.Call -> if (e.fn.isMatrixFn) throw Unsupported() else fn(e.fn, fromExpr(e.arg, angle))
        is Expr.RootE -> pow(fromExpr(e.radicand, angle), e.index?.let { div(ONE, fromExpr(it, angle)) } ?: HALF)
        is Expr.Factorial -> if (e.freeVariables().isEmpty()) num(e.eval(EvalContext(angle))) else throw Unsupported()
        is Expr.IntegralE -> if (e.freeVariables().isEmpty()) num(e.eval(EvalContext(angle))) else throw Unsupported()
        is Expr.IndefiniteE -> Calculus(angle).antiderivative(fromExpr(e.body, angle), e.variable) ?: throw Unsupported()
        is Expr.DerivativeE -> Calculus(angle).derivative(fromExpr(e.body, angle), e.variable)
        is Expr.MatrixE -> throw Unsupported()
    }

    fun toExpr(s: S): Expr = when (s) {
        is S.N -> Expr.Num(s.v)
        is S.C -> Expr.Const(s.name, s.v)
        is S.V -> Expr.Var(s.name)
        is S.Add -> s.terms.map(::toExpr).reduce { a, b -> Expr.Bin('+', a, b) }
        is S.Mul -> s.factors.map(::toExpr).reduce { a, b -> Expr.Bin('*', a, b) }
        is S.Pow -> when {
            s.exp == HALF -> Expr.RootE(toExpr(s.base), null)
            s.exp is S.N && s.exp.v != 0.0 && isInt(1 / s.exp.v) && s.exp.v > 0 -> Expr.RootE(toExpr(s.base), Expr.Num(Math.rint(1 / s.exp.v)))
            else -> Expr.Bin('^', toExpr(s.base), toExpr(s.exp))
        }
        is S.F -> Expr.Call(s.fn, toExpr(s.arg))
    }

    fun freeOf(s: S, v: String): Boolean = when (s) {
        is S.N, is S.C -> true
        is S.V -> s.name != v
        is S.Add -> s.terms.all { freeOf(it, v) }
        is S.Mul -> s.factors.all { freeOf(it, v) }
        is S.Pow -> freeOf(s.base, v) && freeOf(s.exp, v)
        is S.F -> freeOf(s.arg, v)
    }

    fun variables(s: S): Set<String> = when (s) {
        is S.N, is S.C -> emptySet()
        is S.V -> setOf(s.name)
        is S.Add -> s.terms.flatMap { variables(it) }.toSet()
        is S.Mul -> s.factors.flatMap { variables(it) }.toSet()
        is S.Pow -> variables(s.base) + variables(s.exp)
        is S.F -> variables(s.arg)
    }

    /** Structural replacement, rebuilt through the canonical builders. */
    fun replace(s: S, target: S, with: S): S {
        if (s == target) return with
        return when (s) {
            is S.N, is S.C, is S.V -> s
            is S.Add -> add(s.terms.map { replace(it, target, with) })
            is S.Mul -> mul(s.factors.map { replace(it, target, with) })
            is S.Pow -> pow(replace(s.base, target, with), replace(s.exp, target, with))
            is S.F -> fn(s.fn, replace(s.arg, target, with))
        }
    }

    fun substitute(s: S, name: String, with: S) = replace(s, S.V(name), with)

    /** Distributes products over sums and expands small integer powers of sums. */
    fun expand(s: S): S = when (s) {
        is S.Add -> add(s.terms.map(::expand))
        is S.Mul -> s.factors.map(::expand).fold(ONE as S) { acc, f -> distribute(acc, f) }
        is S.Pow -> {
            val b = expand(s.base)
            val e = s.exp
            if (b is S.Add && e is S.N && isInt(e.v) && e.v in 2.0..8.0) {
                var r: S = ONE
                repeat(e.v.toInt()) { r = distribute(r, b) }
                r
            } else pow(b, e)
        }
        else -> s
    }

    private fun distribute(a: S, b: S): S {
        val ta = if (a is S.Add) a.terms else listOf(a)
        val tb = if (b is S.Add) b.terms else listOf(b)
        return add(ta.flatMap { x -> tb.map { y -> mul(x, y) } })
    }

    /** Coefficients by degree when [s] is a polynomial in [v] (coefficients free of v), else null. */
    fun polynomial(s: S, v: String): Map<Int, S>? {
        val out = mutableMapOf<Int, MutableList<S>>()
        val terms = expand(s).let { if (it is S.Add) it.terms else listOf(it) }
        for (t in terms) {
            val factors = if (t is S.Mul) t.factors else listOf(t)
            var deg = 0
            val coef = mutableListOf<S>()
            for (f in factors) {
                when {
                    freeOf(f, v) -> coef += f
                    f == S.V(v) -> deg += 1
                    f is S.Pow && f.base == S.V(v) && f.exp is S.N && isInt(f.exp.v) && f.exp.v > 0 -> deg += f.exp.v.toInt()
                    else -> return null
                }
            }
            out.getOrPut(deg) { mutableListOf() } += mul(coef)
        }
        return out.mapValues { add(it.value) }.filterValues { it != ZERO }
    }

    fun numericPolynomial(s: S, v: String): DoubleArray? {
        val p = polynomial(s, v) ?: return null
        if (p.values.any { it !is S.N }) return null
        val deg = p.keys.maxOrNull() ?: return DoubleArray(1)
        return DoubleArray(deg + 1) { (p[it] as? S.N)?.v ?: 0.0 }
    }

    fun fromPolynomial(c: DoubleArray, v: String): S =
        add(c.indices.map { mul(num(c[it]), pow(S.V(v), num(it.toDouble()))) })

    // ------------------------------------------------------------------ display

    /** Builds an editor row for [s] (used for results; also re-parseable). */
    fun toRow(s: S): MathRow = MathRow().also { emit(s, it.items) }

    private fun emit(s: S, out: MutableList<Node>) {
        when (s) {
            is S.Add -> s.terms.forEachIndexed { i, t ->
                val (k, _) = splitCoef(t)
                val negative = k < 0
                if (i > 0) out += Sym.op(if (negative) Sym.MINUS else Sym.PLUS) else if (negative) out += Sym.op(Sym.MINUS)
                emitTerm(if (negative) neg(t) else t, out)
            }
            else -> {
                val (k, _) = splitCoef(s)
                if (k < 0) { out += Sym.op(Sym.MINUS); emitTerm(neg(s), out) } else emitTerm(s, out)
            }
        }
    }

    /** A term with a non-negative coefficient. */
    private fun emitTerm(t: S, out: MutableList<Node>) {
        val (k, rest) = splitCoef(t)
        val factors = if (rest == ONE) emptyList() else if (rest is S.Mul) rest.factors else listOf(rest)
        val numer = mutableListOf<S>()
        val denom = mutableListOf<S>()
        for (f in factors) {
            if (f is S.Pow && f.exp is S.N && f.exp.v < 0) denom += pow(f.base, num(-f.exp.v)) else numer += f
        }
        val frac = rational(k)
        val (p, q) = frac ?: (null to 1L)
        if (denom.isNotEmpty() || (q != 1L && factors.isNotEmpty())) {
            val nr = MathRow()
            if (p != null) {
                if (p != 1L || numer.isEmpty()) emitNumber(p.toDouble(), nr.items)
            } else if (k != 1.0 || numer.isEmpty()) emitNumber(k, nr.items)
            if (nr.items.isEmpty() && numer.size == 1 && numer[0] is S.Add) emit(numer[0], nr.items)
            else emitFactors(numer, nr.items, nr.items.isNotEmpty())
            val dr = MathRow()
            if (p != null && q != 1L) emitNumber(q.toDouble(), dr.items)
            if (dr.items.isEmpty() && denom.size == 1 && denom[0] is S.Add) emit(denom[0], dr.items)
            else emitFactors(denom, dr.items, dr.items.isNotEmpty())
            out += Fraction(nr, dr)
            return
        }
        val showK = k != 1.0 || factors.isEmpty()
        if (showK) emitNumber(k, out)
        emitFactors(factors, out, showK)
    }

    private fun emitFactors(fs: List<S>, out: MutableList<Node>, afterNumber: Boolean) {
        var prevNumberLike = afterNumber
        for (f in fs) {
            val startsWithNumber = f is S.N || (f is S.Pow && f.base is S.N)
            if (prevNumberLike && startsWithNumber) out += Sym.op(Sym.DOT)
            emitFactor(f, out)
            prevNumberLike = f is S.N
        }
    }

    private fun emitFactor(f: S, out: MutableList<Node>) {
        when (f) {
            is S.N -> emitNumber(f.v, out)
            is S.C -> out += Sym.constant(f.name)
            is S.V -> out += Sym.variable(f.name)
            is S.Add -> out += Group(toRow(f))
            is S.Mul -> out += Group(toRow(f))
            is S.F -> out += FuncCall(f.fn, toRow(f.arg))
            is S.Pow -> {
                val e = f.exp
                when {
                    e == HALF -> out += Root(toRow(f.base))
                    e is S.N && e.v > 0 && e.v < 1 && isInt(1 / e.v) -> out += Root(toRow(f.base), toRow(num(Math.rint(1 / e.v))))
                    e is S.N && e.v < 0 -> out += Fraction(MathRow(mutableListOf(Sym.digit('1'))), toRow(pow(f.base, num(-e.v))))
                    else -> {
                        val b = f.base
                        if (b is S.V || b is S.C || b is S.F || (b is S.N && b.v >= 0 && rational(b.v)?.second == 1L)) emitFactor(b, out)
                        else out += Group(toRow(b))
                        out += Power(toRow(e))
                    }
                }
            }
        }
    }

    private fun emitNumber(v: Double, out: MutableList<Node>) {
        if (v < 0) out += Sym.op(Sym.MINUS)
        val a = abs(v)
        val r = rational(a)
        if (r != null && r.second != 1L) {
            out += Fraction(MathRow(mutableListOf(Sym(SymKind.DIGIT, r.first.toString()))), MathRow(mutableListOf(Sym(SymKind.DIGIT, r.second.toString()))))
        } else out += Sym(SymKind.DIGIT, Format.number(a))
    }

    /** p/q with a small denominator when [v] is (numerically) such a fraction. */
    fun rational(v: Double): Pair<Long, Long>? {
        if (!v.isFinite()) return null
        for (q in 1L..360L) {
            val p = Math.rint(v * q)
            if (abs(p) > 1e9) return null
            if (abs(v * q - p) < 1e-9 * max(1.0, abs(v * q))) return p.toLong() to q
        }
        return null
    }
}

/** Derivatives, antiderivatives and isolation in a given angle unit. */
class Calculus(private val angle: AngleUnit) {
    /** Radians per angle unit: d/dx sin(x) = k·cos(x). */
    private val k: S = when (angle) {
        AngleUnit.RAD -> Cas.ONE
        AngleUnit.DEG -> Cas.mul(Cas.num(1.0 / 180.0), Cas.PI_C)
        AngleUnit.GRAD -> Cas.mul(Cas.num(1.0 / 200.0), Cas.PI_C)
    }

    fun derivative(s: S, v: String): S = with(Cas) {
        when (s) {
            is S.N, is S.C -> ZERO
            is S.V -> if (s.name == v) ONE else ZERO
            is S.Add -> add(s.terms.map { derivative(it, v) })
            is S.Mul -> add(s.factors.indices.map { i ->
                mul(s.factors.mapIndexed { j, f -> if (i == j) derivative(f, v) else f })
            })
            is S.Pow -> {
                val b = s.base; val e = s.exp
                when {
                    freeOf(e, v) -> mul(e, pow(b, sub(e, ONE)), derivative(b, v))
                    freeOf(b, v) -> mul(s, if (b == EULER) ONE else fn(Fn.LN, b), derivative(e, v))
                    else -> mul(s, add(mul(derivative(e, v), fn(Fn.LN, b)), mul(e, derivative(b, v), pow(b, MINUS_ONE))))
                }
            }
            is S.F -> {
                val a = s.arg
                val da = derivative(a, v)
                if (da == ZERO) return ZERO
                val outer: S = when (s.fn) {
                    Fn.SIN -> mul(k, fn(Fn.COS, a))
                    Fn.COS -> mul(neg(k), fn(Fn.SIN, a))
                    Fn.TAN -> mul(k, pow(fn(Fn.COS, a), num(-2.0)))
                    Fn.ASIN -> div(ONE, mul(k, pow(sub(ONE, pow(a, num(2.0))), HALF)))
                    Fn.ACOS -> div(MINUS_ONE, mul(k, pow(sub(ONE, pow(a, num(2.0))), HALF)))
                    Fn.ATAN -> div(ONE, mul(k, add(ONE, pow(a, num(2.0)))))
                    Fn.SINH -> fn(Fn.COSH, a)
                    Fn.COSH -> fn(Fn.SINH, a)
                    Fn.TANH -> sub(ONE, pow(fn(Fn.TANH, a), num(2.0)))
                    Fn.LN -> pow(a, MINUS_ONE)
                    Fn.LOG -> div(ONE, mul(a, fn(Fn.LN, num(10.0))))
                    Fn.ABS -> div(a, fn(Fn.ABS, a))
                    Fn.EXP -> pow(EULER, a)
                    Fn.DET, Fn.TRANSPOSE, Fn.TRACE -> throw Unsupported()
                }
                mul(outer, da)
            }
        }
    }

    /** d/dv of [g] when it is free of v and non-zero (g = a·v + b), else null. */
    private fun linear(g: S, v: String): S? {
        val d = derivative(g, v)
        return if (d != Cas.ZERO && Cas.freeOf(d, v)) d else null
    }

    /** An antiderivative of [f] with respect to [v], checked numerically; null when none was found. */
    fun antiderivative(f: S, v: String): S? {
        val r = try { integrate(f, v, 0) } catch (e: Unsupported) { null } ?: return null
        return if (verify(f, r, v)) dropConstant(r, v) else null
    }

    private fun dropConstant(r: S, v: String): S =
        if (r is S.Add) Cas.add(r.terms.filterNot { it is S.N }) else r

    private fun verify(f: S, big: S, v: String): Boolean {
        val fe = Cas.toExpr(f)
        val be = Cas.toExpr(big)
        val others = (Cas.variables(f) + Cas.variables(big) - v).toList()
        var good = 0
        val pts = listOf(0.37, 1.21, 2.63, -0.83, -1.77, 0.11, 3.9, -2.9, 0.71, 1.63)
        for ((i, x) in pts.withIndex()) {
            val values = others.mapIndexed { j, n -> n to 0.6 + 0.37 * ((i + j) % 5) }.toMap() + (v to x)
            val ctx = EvalContext(angle, values)
            val fv = try { fe.eval(ctx) } catch (e: CalcException) { Double.NaN }
            if (!fv.isFinite()) continue
            val dv = try {
                Numerics.derivative(x) { t -> be.eval(ctx.with(v, t)) }
            } catch (e: CalcException) { Double.NaN }
            if (!dv.isFinite()) continue
            if (abs(dv - fv) > 1e-5 * max(1.0, abs(fv))) return false
            good++
        }
        return good >= 3
    }

    private fun integrate(f: S, v: String, depth: Int): S? = with(Cas) {
        if (depth > 6) return null
        if (freeOf(f, v)) return mul(f, S.V(v))
        when (f) {
            is S.Add -> {
                val parts = f.terms.map { integrate(it, v, depth + 1) ?: return integrateWhole(f, v, depth) }
                return add(parts)
            }
            is S.Mul -> {
                val (consts, vars) = f.factors.partition { freeOf(it, v) }
                if (consts.isNotEmpty()) return integrate(mul(vars), v, depth)?.let { mul(consts + it) }
                return integrateWhole(f, v, depth)
            }
            else -> return table(f, v) ?: integrateWhole(f, v, depth)
        }
    }

    /** Strategies for a term that is not a plain table entry. */
    private fun integrateWhole(f: S, v: String, depth: Int): S? = with(Cas) {
        val ex = expand(f)
        if (ex != f && ex is S.Add) integrate(ex, v, depth + 1)?.let { return it }
        rational(f, v)?.let { return it }
        substitution(f, v, depth)?.let { return it }
        expTrig(f, v)?.let { return it }
        parts(f, v, depth)?.let { return it }
        null
    }

    private fun table(f: S, v: String): S? = with(Cas) {
        when (f) {
            is S.V -> mul(HALF, pow(f, num(2.0)))
            is S.Pow -> {
                val b = f.base; val e = f.exp
                if (freeOf(e, v)) {
                    val a = linear(b, v)
                    if (a != null) {
                        return if (e == MINUS_ONE) div(fn(Fn.LN, fn(Fn.ABS, b)), a)
                        else div(pow(b, add(e, ONE)), mul(add(e, ONE), a))
                    }
                    trigPower(b, e, v)?.let { return it }
                    quadraticRoot(b, e, v)?.let { return it }
                    return null
                }
                if (freeOf(b, v)) {
                    val a = linear(e, v) ?: return null
                    return div(f, mul(a, if (b == EULER) ONE else fn(Fn.LN, b)))
                }
                null
            }
            is S.F -> {
                val g = f.arg
                val a = linear(g, v) ?: return null
                val ka = mul(k, a)
                when (f.fn) {
                    Fn.SIN -> div(neg(fn(Fn.COS, g)), ka)
                    Fn.COS -> div(fn(Fn.SIN, g), ka)
                    Fn.TAN -> div(neg(fn(Fn.LN, fn(Fn.ABS, fn(Fn.COS, g)))), ka)
                    Fn.SINH -> div(fn(Fn.COSH, g), a)
                    Fn.COSH -> div(fn(Fn.SINH, g), a)
                    Fn.TANH -> div(fn(Fn.LN, fn(Fn.COSH, g)), a)
                    Fn.LN -> div(sub(mul(g, fn(Fn.LN, g)), g), a)
                    Fn.LOG -> div(sub(mul(g, fn(Fn.LN, g)), g), mul(a, fn(Fn.LN, num(10.0))))
                    Fn.ABS -> div(mul(g, fn(Fn.ABS, g)), mul(num(2.0), a))
                    Fn.ATAN -> div(sub(mul(g, fn(Fn.ATAN, g)), div(fn(Fn.LN, add(ONE, pow(g, num(2.0)))), mul(num(2.0), k))), a)
                    Fn.ASIN -> div(add(mul(g, fn(Fn.ASIN, g)), div(pow(sub(ONE, pow(g, num(2.0))), HALF), k)), a)
                    Fn.ACOS -> div(sub(mul(g, fn(Fn.ACOS, g)), div(pow(sub(ONE, pow(g, num(2.0))), HALF), k)), a)
                    else -> null
                }
            }
            else -> null
        }
    }

    /** sin², cos², tan², 1/cos², 1/sin² of a linear argument. */
    private fun trigPower(b: S, e: S, v: String): S? = with(Cas) {
        if (b !is S.F || e !is S.N) return null
        val g = b.arg
        val a = linear(g, v) ?: return null
        val ka = mul(k, a)
        val two = num(2.0)
        when {
            b.fn == Fn.SIN && e.v == 2.0 -> sub(mul(HALF, S.V(v)), div(fn(Fn.SIN, mul(two, g)), mul(num(4.0), ka)))
            b.fn == Fn.COS && e.v == 2.0 -> add(mul(HALF, S.V(v)), div(fn(Fn.SIN, mul(two, g)), mul(num(4.0), ka)))
            b.fn == Fn.TAN && e.v == 2.0 -> sub(div(fn(Fn.TAN, g), ka), S.V(v))
            b.fn == Fn.COS && e.v == -2.0 -> div(fn(Fn.TAN, g), ka)
            b.fn == Fn.SIN && e.v == -2.0 -> div(neg(div(fn(Fn.COS, g), fn(Fn.SIN, g))), ka)
            else -> null
        }
    }

    /** (A v² + B v + C)^(±1/2) with numeric coefficients, by completing the square. */
    private fun quadraticRoot(b: S, e: S, v: String): S? = with(Cas) {
        if (e != HALF && e != num(-0.5)) return null
        val p = numericPolynomial(b, v) ?: return null
        if (p.size != 3 || p[2] == 0.0) return null
        val (cc, bb, aa) = Triple(p[0], p[1], p[2])
        val u = add(S.V(v), num(bb / (2 * aa)))
        val d = cc / aa - bb * bb / (4 * aa * aa)
        val root = pow(b, HALF)
        if (aa > 0) {
            if (abs(d) < 1e-12) return null
            val lnPart = fn(Fn.LN, fn(Fn.ABS, add(u, pow(add(pow(u, num(2.0)), num(d)), HALF))))
            return if (e == HALF) add(mul(HALF, u, root), mul(num(sqrt(aa) * d / 2), lnPart))
            else mul(num(1 / sqrt(aa)), lnPart)
        }
        if (d >= 0) return null
        val r = sqrt(-d)
        val asinPart = mul(k, fn(Fn.ASIN, div(u, num(r))))
        return if (e == HALF) add(mul(HALF, u, root), mul(num(sqrt(-aa) * r * r / 2), asinPart))
        else mul(num(1 / sqrt(-aa)), asinPart)
    }

    /** Polynomial ÷ polynomial with numeric coefficients (denominator degree ≤ 2, or real simple roots). */
    private fun rational(f: S, v: String): S? = with(Cas) {
        val factors = if (f is S.Mul) f.factors else listOf(f)
        val numer = mutableListOf<S>()
        val denom = mutableListOf<S>()
        for (x in factors) {
            if (x is S.Pow && x.exp is S.N && (x.exp as S.N).v < 0 && floor((x.exp as S.N).v) == (x.exp as S.N).v) {
                denom += pow(x.base, num(-(x.exp as S.N).v))
            } else numer += x
        }
        if (denom.isEmpty()) return null
        val n = numericPolynomial(mul(numer), v) ?: return null
        val d = numericPolynomial(mul(denom), v) ?: return null
        if (d.size < 2) return null
        val (q, r) = polyDivide(n, d)
        val polyPart = integratePolynomial(q, v)
        val rest = properFraction(r, d, v) ?: return null
        add(polyPart, rest)
    }

    private fun integratePolynomial(c: DoubleArray, v: String): S = with(Cas) {
        add(c.indices.map { mul(num(c[it] / (it + 1)), pow(S.V(v), num(it + 1.0))) })
    }

    private fun polyDivide(n: DoubleArray, d: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val dn = d.size - 1
        val r = n.copyOf()
        if (n.size - 1 < dn) return DoubleArray(1) to r
        val q = DoubleArray(n.size - dn)
        for (i in n.size - 1 downTo dn) {
            val c = r[i] / d[dn]
            q[i - dn] = c
            for (j in 0..dn) r[i - dn + j] -= c * d[j]
        }
        return q to r.copyOf(dn).let { if (it.isEmpty()) DoubleArray(1) else it }
    }

    /** ∫ r/d with deg r < deg d. */
    private fun properFraction(r: DoubleArray, d: DoubleArray, v: String): S? = with(Cas) {
        val x = S.V(v)
        if (r.all { abs(it) < 1e-14 }) return ZERO
        val deg = d.size - 1
        if (deg == 1) return mul(num(r[0] / d[1]), fn(Fn.LN, fn(Fn.ABS, fromPolynomial(d, v))))
        if (deg == 2) {
            val (c0, b0, a0) = Triple(d[0], d[1], d[2])
            val r1 = r.getOrElse(1) { 0.0 }
            val r0 = r[0]
            val den = fromPolynomial(d, v)
            val logPart = mul(num(r1 / (2 * a0)), fn(Fn.LN, fn(Fn.ABS, den)))
            val m = r0 - r1 * b0 / (2 * a0)
            if (abs(m) < 1e-14) return logPart
            val disc = b0 * b0 - 4 * a0 * c0
            val inv: S = when {
                disc > 1e-12 -> {
                    val s = sqrt(disc)
                    val p1 = (-b0 + s) / (2 * a0); val p2 = (-b0 - s) / (2 * a0)
                    mul(num(1 / (a0 * (p1 - p2))), sub(fn(Fn.LN, fn(Fn.ABS, sub(x, num(p1)))), fn(Fn.LN, fn(Fn.ABS, sub(x, num(p2))))))
                }
                disc < -1e-12 -> {
                    val s = sqrt(-disc)
                    mul(num(2 / s), k, fn(Fn.ATAN, div(add(mul(num(2 * a0), x), num(b0)), num(s))))
                }
                else -> div(num(-1 / a0), sub(x, num(-b0 / (2 * a0))))
            }
            return add(logPart, mul(num(m), inv))
        }
        // Higher degree: only distinct real roots.
        val roots = Numerics.findRoots({ t -> d.indices.sumOf { d[it] * t.pow(it) } })
        if (roots.size != deg) return null
        val dd = DoubleArray(deg) { (it + 1) * d[it + 1] }
        add(roots.map { p ->
            val res = r.indices.sumOf { r[it] * p.pow(it) } / dd.indices.sumOf { dd[it] * p.pow(it) }
            mul(num(res), fn(Fn.LN, fn(Fn.ABS, sub(x, num(p)))))
        })
    }

    /** ∫ f(g(v))·g'(v) dv = F(g(v)). */
    private fun substitution(f: S, v: String, depth: Int): S? = with(Cas) {
        val u = "\u0001u"
        val candidates = mutableSetOf<S>()
        fun collect(s: S) {
            when (s) {
                is S.Add -> { s.terms.forEach(::collect) }
                is S.Mul -> s.factors.forEach(::collect)
                is S.Pow -> {
                    if (!freeOf(s.base, v) && s.base !is S.V) candidates += s.base
                    if (!freeOf(s.exp, v) && s.exp !is S.V) candidates += s.exp
                    collect(s.base); collect(s.exp)
                }
                is S.F -> {
                    if (!freeOf(s.arg, v)) candidates += s
                    if (!freeOf(s.arg, v) && s.arg !is S.V) candidates += s.arg
                    collect(s.arg)
                }
                else -> {}
            }
        }
        collect(f)
        for (g in candidates.sortedByDescending { it.toString().length }) {
            if (g == f) continue
            val dg = derivative(g, v)
            if (dg == ZERO) continue
            val q = div(f, dg)
            val r = replace(q, g, S.V(u))
            if (!freeOf(r, v)) continue
            val big = integrate(r, u, depth + 1) ?: continue
            return substitute(big, u, g)
        }
        null
    }

    /** e^(αv)·sin(βv+c) and e^(αv)·cos(βv+c). */
    private fun expTrig(f: S, v: String): S? = with(Cas) {
        if (f !is S.Mul || f.factors.size != 2) return null
        val ex = f.factors.firstOrNull { it is S.Pow && it.base == EULER } as? S.Pow ?: return null
        val tr = f.factors.firstOrNull { it is S.F && (it.fn == Fn.SIN || it.fn == Fn.COS) } as? S.F ?: return null
        val alpha = linear(ex.exp, v) ?: return null
        val b = linear(tr.arg, v) ?: return null
        val beta = mul(k, b)
        val den = add(pow(alpha, num(2.0)), pow(beta, num(2.0)))
        val s = fn(Fn.SIN, tr.arg); val c = fn(Fn.COS, tr.arg)
        val inner = if (tr.fn == Fn.SIN) sub(mul(alpha, s), mul(beta, c)) else add(mul(alpha, c), mul(beta, s))
        mul(ex, inner, pow(den, MINUS_ONE))
    }

    private fun isPolynomialFactor(s: S, v: String) = Cas.polynomial(s, v) != null && !Cas.freeOf(s, v)

    /** Integration by parts: polynomial × (exp, sin, cos, sinh, cosh) or × (ln, inverse trig). */
    private fun parts(f: S, v: String, depth: Int): S? = with(Cas) {
        val factors = if (f is S.Mul) f.factors else listOf(f)
        val (polyF, rest) = factors.partition { isPolynomialFactor(it, v) }
        if (rest.size != 1) return null
        val t = rest[0]
        val p = mul(polyF)
        val logLike = (t is S.F && (t.fn == Fn.LN || t.fn == Fn.LOG || t.fn.isInverseTrig)) ||
            (t is S.Pow && t.exp is S.N && (t.exp as S.N).v > 1 && t.base is S.F && ((t.base as S.F).fn == Fn.LN))
        if (logLike) {
            val bigP = integrate(p, v, depth + 1) ?: return null
            val restInt = integrate(expand(mul(bigP, derivative(t, v))), v, depth + 1) ?: return null
            return sub(mul(bigP, t), restInt)
        }
        if (polyF.isEmpty()) return null
        // Tabular: ∫p·t = p·T₁ − p′·T₂ + p″·T₃ − …
        val terms = mutableListOf<S>()
        var dp = p
        var ti: S = t
        var sign = 1.0
        repeat(12) {
            if (dp == ZERO) return add(terms)
            ti = integrate(ti, v, depth + 1) ?: return null
            terms += mul(num(sign), dp, ti)
            dp = derivative(dp, v)
            sign = -sign
        }
        null
    }

    // ------------------------------------------------------------------ isolation

    /**
     * Solutions of [r] = 0 for [v] in terms of the other variables, or null when [v]
     * cannot be isolated. Each solution is checked numerically.
     */
    fun solveFor(r: S, v: String): List<S>? {
        val raw = try { isolate(r, v) } catch (e: Unsupported) { null } ?: return null
        val ok = raw.filter { checkSolution(r, v, it) }
        return ok.ifEmpty { null }
    }

    private fun isolate(r0: S, v: String): List<S>? = with(Cas) {
        // Clear negative powers of v: z/y + y − 3 = 0 → z + y² − 3y = 0.
        val r = negativePower(r0, v)?.let { expand(mul(r0, pow(S.V(v), num(it)))) } ?: r0
        polynomial(r, v)?.let { p ->
            val deg = p.keys.maxOrNull() ?: return null
            val a = p[2] ?: ZERO; val b = p[1] ?: ZERO; val c = p[0] ?: ZERO
            if (deg == 1) return listOf(div(neg(c), b))
            if (deg == 2) {
                if (b == ZERO) {
                    val s = pow(div(neg(c), a), HALF)
                    return listOf(s, neg(s))
                }
                val disc = pow(sub(pow(b, num(2.0)), mul(num(4.0), a, c)), HALF)
                return listOf(div(add(neg(b), disc), mul(num(2.0), a)), div(sub(neg(b), disc), mul(num(2.0), a)))
            }
        }
        // One occurrence of v: peel the outer operations off.
        var lhs = r
        var rhs: List<S> = listOf(ZERO)
        repeat(20) {
            if (lhs == S.V(v)) return rhs
            when (val l = lhs) {
                is S.Add -> {
                    val (free, dep) = l.terms.partition { freeOf(it, v) }
                    if (dep.size != 1) return null
                    lhs = dep[0]; rhs = rhs.map { sub(it, add(free)) }
                }
                is S.Mul -> {
                    val (free, dep) = l.factors.partition { freeOf(it, v) }
                    if (dep.size != 1) return null
                    lhs = dep[0]; rhs = rhs.map { div(it, mul(free)) }
                }
                is S.Pow -> when {
                    freeOf(l.exp, v) -> {
                        val e = l.exp
                        lhs = l.base
                        val even = e is S.N && floor(e.v) == e.v && e.v % 2.0 == 0.0
                        rhs = rhs.flatMap { x -> val s = pow(x, div(ONE, e)); if (even) listOf(s, neg(s)) else listOf(s) }
                    }
                    freeOf(l.base, v) -> {
                        lhs = l.exp
                        rhs = rhs.map { x -> if (l.base == EULER) fn(Fn.LN, x) else div(fn(Fn.LN, x), fn(Fn.LN, l.base)) }
                    }
                    else -> return null
                }
                is S.F -> {
                    lhs = l.arg
                    rhs = rhs.map { x ->
                        when (l.fn) {
                            Fn.LN -> pow(EULER, x)
                            Fn.LOG -> pow(num(10.0), x)
                            Fn.SIN -> fn(Fn.ASIN, x)
                            Fn.COS -> fn(Fn.ACOS, x)
                            Fn.TAN -> fn(Fn.ATAN, x)
                            Fn.ASIN -> fn(Fn.SIN, x)
                            Fn.ACOS -> fn(Fn.COS, x)
                            Fn.ATAN -> fn(Fn.TAN, x)
                            else -> throw Unsupported()
                        }
                    }
                }
                else -> return null
            }
        }
        null
    }

    /** The largest k with a v^(−k) factor among the terms of [r], or null. */
    private fun negativePower(r: S, v: String): Double? {
        val terms = if (r is S.Add) r.terms else listOf(r)
        var k = 0.0
        for (t in terms) {
            val fs = if (t is S.Mul) t.factors else listOf(t)
            for (f in fs) if (f is S.Pow && f.base == S.V(v) && f.exp is S.N && (f.exp as S.N).v < 0) k = max(k, -(f.exp as S.N).v)
        }
        return if (k > 0 && k == floor(k)) k else null
    }

    private fun checkSolution(r: S, v: String, sol: S): Boolean {
        val re = Cas.toExpr(r)
        val se = Cas.toExpr(sol)
        val others = (Cas.variables(r) + Cas.variables(sol) - v).toList()
        var good = 0
        for (i in 0 until 8) {
            val values = others.mapIndexed { j, n -> n to 0.4 + 0.53 * ((i * 3 + j) % 7) }.toMap()
            val ctx = EvalContext(angle, values)
            val x = try { se.eval(ctx) } catch (e: CalcException) { Double.NaN }
            if (!x.isFinite()) continue
            val res = try { re.eval(ctx.with(v, x)) } catch (e: CalcException) { Double.NaN }
            if (!res.isFinite()) continue
            if (abs(res) > 1e-7 * max(1.0, abs(x))) return false
            good++
        }
        return good >= 2 || (others.isEmpty() && good >= 1)
    }
}

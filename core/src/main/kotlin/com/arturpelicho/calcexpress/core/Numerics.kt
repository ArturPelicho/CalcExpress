package com.arturpelicho.calcexpress.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Numerics {

    // 10-point Gauss–Legendre nodes/weights on [-1, 1].
    private val GL_X = doubleArrayOf(
        -0.9739065285171717, -0.8650633666889845, -0.6794095682990244, -0.4333953941292472, -0.1488743389816312,
        0.1488743389816312, 0.4333953941292472, 0.6794095682990244, 0.8650633666889845, 0.9739065285171717,
    )
    private val GL_W = doubleArrayOf(
        0.0666713443086881, 0.1494513491505806, 0.2190863625159820, 0.2692667193099963, 0.2955242247147529,
        0.2955242247147529, 0.2692667193099963, 0.2190863625159820, 0.1494513491505806, 0.0666713443086881,
    )

    private fun gaussComposite(a: Double, b: Double, pieces: Int, f: (Double) -> Double): Double {
        val h = (b - a) / pieces
        var sum = 0.0
        for (p in 0 until pieces) {
            val mid = a + (p + 0.5) * h
            val half = h / 2
            for (k in GL_X.indices) sum += GL_W[k] * f(mid + half * GL_X[k])
        }
        return sum * h / 2
    }

    /**
     * Definite integral. The outermost integral refines until two successive
     * estimates agree; inner integrals of a multiple integral use a fixed rule
     * to keep the cost bounded.
     */
    fun integrate(a: Double, b: Double, nested: Boolean, f: (Double) -> Double): Double {
        if (a == b) return 0.0
        if (!a.isFinite() || !b.isFinite()) throw CalcException(CalcException.Reason.DOMAIN, "∞")
        if (nested) return gaussComposite(a, b, 4, f)
        var pieces = 2
        var prev = gaussComposite(a, b, pieces, f)
        while (pieces < 256) {
            pieces *= 2
            val cur = gaussComposite(a, b, pieces, f)
            if (abs(cur - prev) <= 1e-11 * max(1.0, abs(cur))) return cur
            prev = cur
        }
        return prev
    }

    /** Central difference with Richardson extrapolation. */
    fun derivative(x: Double, f: (Double) -> Double): Double {
        val h = 1e-3 * max(1.0, abs(x))
        fun d(h: Double) = (f(x + h) - f(x - h)) / (2 * h)
        val d1 = d(h)
        val d2 = d(h / 2)
        val d3 = d(h / 4)
        val r1 = (4 * d2 - d1) / 3
        val r2 = (4 * d3 - d2) / 3
        return (16 * r2 - r1) / 15
    }

    /** Lanczos approximation of Γ(z). */
    fun gamma(z: Double): Double {
        if (z < 0.5) return PI / (sin(PI * z) * gamma(1 - z))
        val g = 7.0
        val c = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
            1.5056327351493116e-7,
        )
        val x = z - 1
        var a = c[0]
        val t = x + g + 0.5
        for (i in 1 until 9) a += c[i] / (x + i)
        return sqrt(2 * PI) * t.pow(x + 0.5) * exp(-t) * a
    }

    private fun safe(f: (Double) -> Double, x: Double): Double =
        try { f(x) } catch (e: CalcException) { Double.NaN } catch (e: ArithmeticException) { Double.NaN }

    /**
     * Finds the real roots of f in a wide range: scans for sign changes (refined
     * with bisection) and also polishes Newton iterations from a few seeds
     * (catches double roots like x² = 0).
     */
    fun findRoots(f: (Double) -> Double, maxRoots: Int = 8): List<Double> {
        val roots = mutableListOf<Double>()
        fun addRoot(r: Double) {
            if (!r.isFinite()) return
            val fr = safe(f, r)
            if (!fr.isFinite() || abs(fr) > 1e-7 * max(1.0, abs(r))) return
            val tol = 1e-7 * max(1.0, abs(r))
            if (roots.none { abs(it - r) < tol }) roots += r
        }

        // Grid: dense near 0, sparser further away (symmetric), up to ±1e6.
        val grid = mutableListOf<Double>()
        var x = 0.0
        var step = 0.01
        while (x < 1e6) {
            grid += x
            x += step
            if (x > 400 * step) step *= 2
        }
        val pts = (grid.drop(1).map { -it }.reversed() + grid).sorted()
        var px = pts[0]
        var pf = safe(f, px)
        for (k in 1 until pts.size) {
            val cx = pts[k]
            val cf = safe(f, cx)
            if (cf == 0.0) addRoot(cx)
            else if (pf.isFinite() && cf.isFinite() && pf * cf < 0) {
                bisect(f, px, cx)?.let { addRoot(it) }
            }
            px = cx; pf = cf
            if (roots.size >= maxRoots) break
        }
        for (seed in listOf(0.0, 1.0, -1.0, 0.5, 2.0, -2.0, 10.0)) newton1(f, seed)?.let { addRoot(it) }
        return roots.sorted().map { polish(it) }
    }

    private fun polish(r: Double): Double {
        val rounded = Math.rint(r * 1e9) / 1e9
        return if (abs(rounded - r) < 1e-10 * max(1.0, abs(r))) rounded else r
    }

    private fun bisect(f: (Double) -> Double, a0: Double, b0: Double): Double? {
        var a = a0
        var b = b0
        var fa = safe(f, a)
        repeat(200) {
            val m = (a + b) / 2
            val fm = safe(f, m)
            if (!fm.isFinite()) return null
            if (fm == 0.0 || (b - a) / 2 < 1e-15 * max(1.0, abs(m))) return m
            if (fa * fm < 0) b = m else { a = m; fa = fm }
        }
        return (a + b) / 2
    }

    private fun newton1(f: (Double) -> Double, seed: Double): Double? {
        var x = seed
        repeat(100) {
            val fx = safe(f, x)
            if (!fx.isFinite()) return null
            if (abs(fx) < 1e-14) return x
            val d = safe({ t -> derivative(t, f) }, x)
            if (!d.isFinite() || d == 0.0) return null
            val nx = x - fx / d
            if (abs(nx - x) < 1e-13 * max(1.0, abs(x))) return nx
            x = nx
        }
        return x
    }

    /**
     * Newton–Raphson for a square system F(x) = 0 with a numeric Jacobian and
     * step damping; tries several seeds and returns the first convergent solution.
     */
    fun solveSystem(n: Int, fns: (DoubleArray) -> DoubleArray): DoubleArray? {
        val seeds = listOf(1.0, 0.5, -1.0, 2.0, 0.0, 3.0, -2.0, 10.0)
        for (s in seeds) {
            val x = DoubleArray(n) { s + 0.1 * it }
            if (newtonN(x, fns)) return x
        }
        return null
    }

    private fun norm(v: DoubleArray) = sqrt(v.sumOf { it * it })

    private fun newtonN(x: DoubleArray, fns: (DoubleArray) -> DoubleArray): Boolean {
        val n = x.size
        fun eval(p: DoubleArray): DoubleArray? = try {
            fns(p).takeIf { r -> r.all { it.isFinite() } }
        } catch (e: CalcException) { null }

        var fx = eval(x) ?: return false
        repeat(100) {
            if (norm(fx) < 1e-12) return true
            val jac = Array(n) { DoubleArray(n) }
            for (j in 0 until n) {
                val h = 1e-7 * max(1.0, abs(x[j]))
                val xp = x.copyOf(); xp[j] += h
                val xm = x.copyOf(); xm[j] -= h
                val fp = eval(xp) ?: return false
                val fm = eval(xm) ?: return false
                for (i in 0 until n) jac[i][j] = (fp[i] - fm[i]) / (2 * h)
            }
            val step = solveLinear(jac, fx.map { -it }.toDoubleArray()) ?: return false
            var lambda = 1.0
            var accepted = false
            while (lambda > 1e-4) {
                val trial = DoubleArray(n) { x[it] + lambda * step[it] }
                val ft = eval(trial)
                if (ft != null && norm(ft) < norm(fx)) {
                    trial.copyInto(x); fx = ft; accepted = true; break
                }
                lambda /= 2
            }
            if (!accepted) return norm(fx) < 1e-9
        }
        return norm(fx) < 1e-9
    }

    /** Gaussian elimination with partial pivoting. */
    fun solveLinear(a0: Array<DoubleArray>, b0: DoubleArray): DoubleArray? {
        val n = b0.size
        val a = Array(n) { a0[it].copyOf() }
        val b = b0.copyOf()
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            if (abs(a[piv][c]) < 1e-14) return null
            if (piv != c) {
                val t = a[c]; a[c] = a[piv]; a[piv] = t
                val tb = b[c]; b[c] = b[piv]; b[piv] = tb
            }
            for (r in c + 1 until n) {
                val f = a[r][c] / a[c][c]
                for (k in c until n) a[r][k] -= f * a[c][k]
                b[r] -= f * b[c]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = b[r]
            for (k in r + 1 until n) s -= a[r][k] * x[k]
            x[r] = s / a[r][r]
        }
        return x
    }
}

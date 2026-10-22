package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mohr's circle for a symmetric stress (or strain) tensor.
 *
 * Shear components are tensor components: for strain pass γ/2, not the engineering γ.
 * Angles are in radians, measured counter-clockwise from x.
 */
object Mohr {
    /** Normal and shear component on one plane. */
    data class Point(val normal: Double, val shear: Double)

    data class Plane2D(
        val centre: Double,
        val radius: Double,
        val p1: Double,
        val p2: Double,
        /** Angle from x to the direction of p1. */
        val thetaP: Double,
    ) {
        val maxShear get() = radius
        /** Angle of the plane with the largest positive shear. */
        val thetaS get() = thetaP - Math.PI / 4
    }

    fun plane2D(sx: Double, sy: Double, txy: Double): Plane2D {
        val c = (sx + sy) / 2
        val r = hypot((sx - sy) / 2, txy)
        return Plane2D(c, r, c + r, c - r, 0.5 * atan2(2 * txy, sx - sy))
    }

    /** Components on the plane whose normal makes [theta] with x. */
    fun rotate2D(sx: Double, sy: Double, txy: Double, theta: Double): Point {
        val a = (sx - sy) / 2
        val c = cos(2 * theta); val s = sin(2 * theta)
        return Point((sx + sy) / 2 + a * c + txy * s, -a * s + txy * c)
    }

    /** Inverse of the circle drawing: the plane angle whose point sits at polar angle [phi] around the centre. */
    fun thetaAt(sx: Double, sy: Double, txy: Double, phi: Double): Double {
        // The point for θ is drawn at polar angle 2θ − α, with α the angle of (a, τ).
        val alpha = atan2(txy, (sx - sy) / 2)
        return normaliseHalfTurn((phi + alpha) / 2)
    }

    /** Folds an angle into [−π/2, π/2): planes repeat every half turn. */
    fun normaliseHalfTurn(t: Double): Double {
        var x = t
        while (x >= Math.PI / 2) x -= Math.PI
        while (x < -Math.PI / 2) x += Math.PI
        return x
    }

    data class Tensor3(val xx: Double, val yy: Double, val zz: Double, val xy: Double, val yz: Double, val xz: Double) {
        fun matrix() = arrayOf(doubleArrayOf(xx, xy, xz), doubleArrayOf(xy, yy, yz), doubleArrayOf(xz, yz, zz))

        /** I₁ (trace), I₂ (sum of principal minors) and I₃ (determinant). */
        fun invariants() = Triple(
            xx + yy + zz,
            xx * yy + yy * zz + zz * xx - xy * xy - yz * yz - xz * xz,
            xx * yy * zz + 2 * xy * yz * xz - xx * yz * yz - yy * xz * xz - zz * xy * xy,
        )
    }

    /** I₁ and I₂ of a plane state. */
    fun invariants2D(sx: Double, sy: Double, txy: Double) = (sx + sy) to (sx * sy - txy * txy)

    data class Principal3D(
        /** Principal values, largest first. */
        val values: List<Double>,
        /** Unit directions matching [values]. */
        val directions: List<DoubleArray>,
    ) {
        val maxShear get() = (values[0] - values[2]) / 2
        /** The three circles as (centre, radius): 1-2, 2-3, 1-3. */
        val circles get() = listOf(0 to 1, 1 to 2, 0 to 2).map { (i, j) -> (values[i] + values[j]) / 2 to abs(values[i] - values[j]) / 2 }
    }

    fun principal3D(t: Tensor3): Principal3D {
        val (vals, vecs) = jacobi(t.matrix())
        val order = vals.indices.sortedByDescending { vals[it] }
        return Principal3D(order.map { vals[it] }, order.map { k -> DoubleArray(3) { vecs[it][k] } })
    }

    /** Normal and shear magnitude on the plane with normal [n] (any length, not zero). */
    fun onPlane3D(t: Tensor3, n: DoubleArray): Point? {
        val len = sqrt(n.sumOf { it * it })
        if (len < 1e-300) return null
        val u = DoubleArray(3) { n[it] / len }
        val m = t.matrix()
        val tr = DoubleArray(3) { i -> (0 until 3).sumOf { j -> m[i][j] * u[j] } }
        val sn = (0 until 3).sumOf { tr[it] * u[it] }
        val tt = (0 until 3).sumOf { tr[it] * tr[it] }
        return Point(sn, sqrt(max(0.0, tt - sn * sn)))
    }

    /** Eigenvalues and eigenvectors (as columns) of a symmetric 3×3 matrix. */
    private fun jacobi(input: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val a = Array(3) { input[it].copyOf() }
        val v = Array(3) { i -> DoubleArray(3) { j -> if (i == j) 1.0 else 0.0 } }
        repeat(60) {
            var p = 0; var q = 1
            for (i in 0 until 3) for (j in i + 1 until 3) if (abs(a[i][j]) > abs(a[p][q])) { p = i; q = j }
            val scale = abs(a[0][0]) + abs(a[1][1]) + abs(a[2][2]) + 1e-300
            if (abs(a[p][q]) <= 1e-15 * scale) return DoubleArray(3) { a[it][it] } to v
            val phi = 0.5 * atan2(2 * a[p][q], a[q][q] - a[p][p])
            val c = cos(phi); val s = sin(phi)
            for (k in 0 until 3) {
                val akp = a[k][p]; val akq = a[k][q]
                a[k][p] = c * akp - s * akq; a[k][q] = s * akp + c * akq
            }
            for (k in 0 until 3) {
                val apk = a[p][k]; val aqk = a[q][k]
                a[p][k] = c * apk - s * aqk; a[q][k] = s * apk + c * aqk
            }
            for (k in 0 until 3) {
                val vkp = v[k][p]; val vkq = v[k][q]
                v[k][p] = c * vkp - s * vkq; v[k][q] = s * vkp + c * vkq
            }
        }
        return DoubleArray(3) { a[it][it] } to v
    }
}

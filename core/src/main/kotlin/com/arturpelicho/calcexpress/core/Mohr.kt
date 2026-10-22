package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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

    /** A plane state read from two faces A and B: its components with A as the x face, and the angle A→B. */
    data class FromPoints(val sx: Double, val sy: Double, val txy: Double, val thetaAB: Double)

    /**
     * The circle through A = (σA, τA) and B = (σB, τB), centred on the σ axis. With [tb] null, B is
     * taken at 180° on the circle (the face at 90° to A), so τB = −τA. Null when no such circle exists.
     */
    fun fromPoints(sa: Double, ta: Double, sb: Double, tb: Double?): FromPoints? {
        val tB = tb ?: -ta
        val scale = abs(sa) + abs(sb) + abs(ta) + abs(tB) + 1e-300
        val c = if (abs(sa - sb) <= 1e-12 * scale) {
            if (abs(ta + tB) <= 1e-12 * scale && abs(ta) > 1e-12 * scale) sa else return null
        } else (sa * sa + ta * ta - sb * sb - tB * tB) / (2 * (sa - sb))
        val theta = normaliseHalfTurn((atan2(ta, sa - c) - atan2(tB, sb - c)) / 2)
        // A turn of exactly 180° on the circle is the face at +90°, not −90°.
        val t = if (abs(abs(theta) - Math.PI / 2) < 1e-12) Math.PI / 2 else theta
        return FromPoints(sa, 2 * c - sa, ta, t)
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

    /**
     * A unit normal (global axes) whose point on the 3D diagram is (sn, tn), or the nearest
     * admissible one when (sn, tn) lies outside the region between the circles.
     */
    fun normalFor(p: Principal3D, sn: Double, tn: Double): DoubleArray {
        val (s1, s2raw, s3raw) = p.values
        // Keep the principal values apart so the closed form below stays defined.
        val gap = 1e-9 * (abs(s1) + abs(s2raw) + abs(s3raw) + 1e-300)
        val s2 = min(s2raw, s1 - gap)
        val s3 = min(s3raw, s2 - gap)
        val t2 = tn * tn
        val sq = doubleArrayOf(
            (t2 + (sn - s2) * (sn - s3)) / ((s1 - s2) * (s1 - s3)),
            (t2 + (sn - s3) * (sn - s1)) / ((s2 - s3) * (s2 - s1)),
            (t2 + (sn - s1) * (sn - s2)) / ((s3 - s1) * (s3 - s2)),
        ).map { max(0.0, it) }
        val sum = sq.sum().takeIf { it > 0 } ?: 1.0
        val c = sq.map { sqrt(it / sum) }
        return DoubleArray(3) { i -> (0 until 3).sumOf { k -> c[k] * p.directions[k][i] } }
    }

    /** Right-handed principal axes (1, 2, 3), each with a non-negative leading component where possible. */
    fun principalFrame(p: Principal3D): List<DoubleArray> {
        val a = tidy(p.directions[0]); val b = tidy(p.directions[1])
        return listOf(a, b, cross(a, b))
    }

    /**
     * Axes of the element of maximum shear: the bisectors of axes 1 and 3, with axis 2 kept.
     * Its faces carry τ_max = (σ1 − σ3)/2 and normal stress (σ1 + σ3)/2.
     */
    fun maxShearFrame(p: Principal3D): List<DoubleArray> {
        val (n1, n2, n3) = principalFrame(p)
        val k = 1 / sqrt(2.0)
        val a = DoubleArray(3) { k * (n1[it] + n3[it]) }
        val c = DoubleArray(3) { k * (n3[it] - n1[it]) }
        return listOf(a, n2, c)
    }

    /** Components T′_ij = e_i · T e_j of [t] in the orthonormal frame [e]. */
    fun inFrame(t: Tensor3, e: List<DoubleArray>): Tensor3 {
        val m = t.matrix()
        fun c(i: Int, j: Int) = (0 until 3).sumOf { r -> (0 until 3).sumOf { s -> e[i][r] * m[r][s] * e[j][s] } }
        return Tensor3(c(0, 0), c(1, 1), c(2, 2), c(0, 1), c(1, 2), c(0, 2))
    }

    /** Angles (radians) between the unit vector [n] and the x, y and z axes. */
    fun axisAngles(n: DoubleArray) = DoubleArray(3) { Math.acos(n[it].coerceIn(-1.0, 1.0)) }

    private fun tidy(v: DoubleArray): DoubleArray {
        val lead = v.firstOrNull { abs(it) > 1e-12 } ?: 1.0
        return if (lead < 0) DoubleArray(3) { -v[it] + 0.0 } else v.copyOf()
    }

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
    )

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

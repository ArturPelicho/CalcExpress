package com.arturpelicho.calcexpress.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MohrTest {
    private val eps = 1e-9

    @Test fun plane2DPrincipals() {
        // Classic example: σx = 80, σy = −40, τxy = 25 → C = 20, R = 65.
        val m = Mohr.plane2D(80.0, -40.0, 25.0)
        assertEquals(20.0, m.centre, eps)
        assertEquals(65.0, m.radius, eps)
        assertEquals(85.0, m.p1, eps)
        assertEquals(-45.0, m.p2, eps)
        val p = Mohr.rotate2D(80.0, -40.0, 25.0, m.thetaP)
        assertEquals(85.0, p.normal, 1e-9)
        assertEquals(0.0, p.shear, 1e-9)
        val s = Mohr.rotate2D(80.0, -40.0, 25.0, m.thetaS)
        assertEquals(65.0, abs(s.shear), 1e-9)
    }

    @Test fun pointsLieOnTheCircleAndDragInverts() {
        val (sx, sy, t) = Triple(30.0, 10.0, -12.0)
        val m = Mohr.plane2D(sx, sy, t)
        for (k in 0 until 12) {
            val th = Mohr.normaliseHalfTurn(k * PI / 12 - 0.3)
            val p = Mohr.rotate2D(sx, sy, t, th)
            assertEquals(m.radius, Math.hypot(p.normal - m.centre, p.shear), 1e-9)
            // Drawn at (σ, −τ): its polar angle around the centre maps back to θ.
            val phi = atan2(-p.shear, p.normal - m.centre)
            assertEquals(th, Mohr.thetaAt(sx, sy, t, phi), 1e-9)
        }
    }

    @Test fun principal3D() {
        val t = Mohr.Tensor3(50.0, -20.0, 30.0, 40.0, 0.0, 0.0)
        val p = Mohr.principal3D(t)
        // xy block: C = 15, R = √(35² + 40²); z is already principal.
        val r = sqrt(35.0 * 35 + 40.0 * 40)
        assertEquals(15 + r, p.values[0], 1e-9)
        assertEquals(30.0, p.values[1], 1e-9)
        assertEquals(15 - r, p.values[2], 1e-9)
        for ((i, d) in p.directions.withIndex()) {
            val on = Mohr.onPlane3D(t, d)!!
            assertEquals(p.values[i], on.normal, 1e-9)
            assertEquals(0.0, on.shear, 1e-6)
        }
        assertEquals(r, p.maxShear, 1e-9)
    }

    @Test fun planePointInsideTheBigCircle() {
        val t = Mohr.Tensor3(10.0, 4.0, -6.0, 3.0, -2.0, 5.0)
        val p = Mohr.principal3D(t)
        val (c13, r13) = p.circles[2]
        for (n in listOf(doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(1.0, -2.0, 0.5), doubleArrayOf(0.0, 0.0, 3.0))) {
            val on = Mohr.onPlane3D(t, n)!!
            assertTrue(Math.hypot(on.normal - c13, on.shear) <= r13 + 1e-9)
        }
        // A 2D state seen in 3D gives the same normal stress at the same angle.
        val th = 0.4
        val flat = Mohr.Tensor3(80.0, -40.0, 0.0, 25.0, 0.0, 0.0)
        val a = Mohr.onPlane3D(flat, doubleArrayOf(cos(th), sin(th), 0.0))!!
        val b = Mohr.rotate2D(80.0, -40.0, 25.0, th)
        assertEquals(b.normal, a.normal, 1e-9)
        assertEquals(abs(b.shear), a.shear, 1e-9)
    }
}

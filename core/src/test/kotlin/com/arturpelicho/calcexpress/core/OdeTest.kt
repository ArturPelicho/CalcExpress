package com.arturpelicho.calcexpress.core

import kotlin.math.E
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OdeTest {

    private fun Editor.type(text: String) {
        for (c in text) when (c) {
            in '0'..'9' -> digit(c)
            '.' -> point()
            '+' -> operator(Sym.PLUS)
            '-' -> operator(Sym.MINUS)
            '*' -> operator(Sym.TIMES)
            '=' -> equalsSign()
            '>' -> moveRight()
            '²' -> square()
            'D' -> derivative()
            'S' -> function(Fn.SIN)
            '\n' -> addEquationLine()
            else -> variable(c.toString())
        }
    }

    /** New line without the system template. */
    private fun Editor.addEquationLine() {
        addEquation()
        // addEquation inserts "[] = []": clear it so the line can be typed freely.
        activeLine.items.clear()
        setCaret(activeLine, 0)
    }

    private fun near(expected: Double, actual: Double, tol: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= tol * maxOf(1.0, abs(expected)), "expected $expected got $actual")

    private fun solved(text: String): Pair<Outcome.Symbolic, Editor> {
        val e = Editor(); e.type(text)
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD)
        assertIs<Outcome.Symbolic>(o, "$text → $o (${e.lines})")
        return o to e
    }

    private fun valueAt(o: Outcome.Symbolic, x: Double): Double {
        val sides = Parser.parseLine(o.rows.single())
        return sides[1].eval(EvalContext(AngleUnit.RAD, mapOf("x" to x)))
    }

    @Test fun directIntegration() {
        val (o, _) = solved("Dy>=2x\ny=1")
        near(1.0 + 1.0, valueAt(o, 1.0))
        near(1.0 + 4.0, valueAt(o, 2.0))
    }

    @Test fun generalSolutionKeepsTheConstant() {
        val (o, _) = solved("Dy>=3")
        assertEquals("y=3x+C", o.rows.single().toString())
    }

    @Test fun exponentialGrowth() {
        val (o, e) = solved("Dy>=y\ny=1")
        near(E, valueAt(o, 1.0))
        val g = Analysis.graphFor(e.lines, AngleUnit.RAD)
        assertIs<GraphSpec.Plot>(g)
        near(E, g.f(1.0))
    }

    @Test fun linearFirstOrder() {
        val (o, _) = solved("Dy>=xy\ny=2")
        near(2 * exp(0.5), valueAt(o, 1.0))
    }

    @Test fun separable() {
        val (o, _) = solved("Dy>=y²\ny=1")
        near(2.0, valueAt(o, 0.5))
    }

    @Test fun secondOrderOscillator() {
        val (o, _) = solved("DDy>>=-y\ny=0\nDy>=1")
        near(sin(1.0), valueAt(o, 1.0))
        near(sin(2.5), valueAt(o, 2.5))
    }

    @Test fun numericalFallback() {
        // y′ = sin(y) + x, y(0) = 0.5: no symbolic method applies.
        val e = Editor(); e.type("Dy>=Sy>+x\ny=0.5")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD)
        assertIs<Outcome.NumericOde>(o, "got $o")
        val g = Analysis.graphFor(e.lines, AngleUnit.RAD)
        assertNotNull(g)
        // Reference: RK4 with a much smaller step.
        var y = 0.5; var x = 0.0; val h = 1e-4
        fun f(x: Double, y: Double) = sin(y) + x
        while (x < 1.0 - 1e-12) {
            val k1 = f(x, y); val k2 = f(x + h / 2, y + h / 2 * k1); val k3 = f(x + h / 2, y + h / 2 * k2); val k4 = f(x + h, y + h * k3)
            y += h / 6 * (k1 + 2 * k2 + 2 * k3 + k4); x += h
        }
        near(y, (g as GraphSpec.Plot).f(1.0), 1e-6)
    }
}

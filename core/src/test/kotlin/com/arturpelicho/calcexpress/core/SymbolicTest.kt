package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SymbolicTest {

    /** Types into [e]: S( C( T( L( A( = sin cos tan ln atan, E = e, R = √, ^ toggles power mode, > moves right. */
    private fun Editor.type(text: String) {
        for (c in text) when (c) {
            in '0'..'9' -> digit(c)
            '.' -> point()
            '+' -> operator(Sym.PLUS)
            '-' -> operator(Sym.MINUS)
            '*' -> operator(Sym.TIMES)
            '/' -> operator(Sym.DIVIDE)
            '(' -> openBracket()
            ')' -> closeBracket()
            '>' -> moveRight()
            '^' -> togglePower()
            'S' -> function(Fn.SIN)
            'C' -> function(Fn.COS)
            'T' -> function(Fn.TAN)
            'L' -> function(Fn.LN)
            'A' -> function(Fn.ATAN)
            'E' -> constant(Sym.E)
            'R' -> sqrt()
            'F' -> fraction()
            '²' -> square()
            else -> variable(c.toString())
        }
    }

    private fun bodyRow(text: String): MathRow = Editor().apply { type(text) }.lines[0]

    private fun antiderivative(body: String, angle: AngleUnit = AngleUnit.RAD): Pair<MathRow, Expr> {
        val line = MathRow(mutableListOf(Integral(body = bodyRow(body))))
        val o = Analysis.evaluate(listOf(line), angle)
        assertIs<Outcome.Symbolic>(o, "∫ $body: got $o")
        val row = o.rows.single()
        // Strip "=" and "+ C" to get F back as an expression.
        val inner = MathRow(row.items.drop(1).dropLast(2).toMutableList())
        return row to Parser.parse(inner)
    }

    private fun check(body: String, angle: AngleUnit = AngleUnit.RAD, xs: List<Double> = listOf(0.3, 0.9, 1.7, 2.4)): MathRow {
        val (row, big) = antiderivative(body, angle)
        val f = Parser.parse(bodyRow(body))
        for (x in xs) {
            val ctx = EvalContext(angle, mapOf("x" to x, "a" to 1.3))
            val d = Numerics.derivative(x) { t -> big.eval(ctx.with("x", t)) }
            val fv = f.eval(ctx)
            assertTrue(abs(d - fv) < 1e-6 * maxOf(1.0, abs(fv)), "∫ $body = $row: F'($x)=$d, f=$fv")
        }
        return row
    }

    @Test fun polynomials() {
        assertEquals("=frac(x^(3),3)+C", check("x²").toString())
        check("3x²+2x-5")
        check("(x+1)^3")
        check("a*x")
    }

    @Test fun table() {
        check("Sx")
        check("C2x+1")
        check("E^3x")
        check("1/x", xs = listOf(0.5, 1.5, -0.7))
        check("Lx")
        check("Ax")
        check("Rx")
        check("Tx")
    }

    @Test fun substitutionAndParts() {
        check("xE^x²")
        check("xSx")
        check("x²E^x")
        check("xLx")
        check("Sx>^3^Cx")
        check("Fx>x²+1")
        check("ExSx", xs = listOf(0.2, 1.1))
    }

    @Test fun rationalFunctions() {
        check("F1>x²+1")
        check("F1>x²-1", xs = listOf(1.5, 2.5, -3.0))
        check("Fx^3^>x+1", xs = listOf(0.5, 1.5, 2.5))
    }

    @Test fun degreesCarryTheFactor() {
        check("Sx", AngleUnit.DEG, listOf(10.0, 45.0, 80.0))
    }

    @Test fun noElementaryAntiderivative() {
        val line = MathRow(mutableListOf(Integral(body = bodyRow("E^x²"))))
        val o = Analysis.evaluate(listOf(line), AngleUnit.RAD)
        assertIs<Outcome.Error>(o)
        assertEquals(CalcException.Reason.NO_ANTIDERIVATIVE, o.reason)
    }

    @Test fun isolation() {
        val c = Calculus(AngleUnit.RAD)
        val x = S.V("x"); val y = S.V("y")
        // x·y − 4 = 0 → x = 4/y
        val sol = c.solveFor(Cas.sub(Cas.mul(x, y), Cas.num(4.0)), "x")!!
        assertEquals(1, sol.size)
        // x² + y − 1 = 0 → x = ±√(1 − y)
        assertEquals(2, c.solveFor(Cas.add(Cas.pow(x, Cas.num(2.0)), y, Cas.num(-1.0)), "x")!!.size)
        // e^x = y → x = ln y
        assertEquals(1, c.solveFor(Cas.sub(Cas.pow(Cas.EULER, x), y), "x")!!.size)
    }
}

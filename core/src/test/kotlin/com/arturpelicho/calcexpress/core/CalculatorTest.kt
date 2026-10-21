package com.arturpelicho.calcexpress.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CalculatorTest {

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
            '=' -> equalsSign()
            '>' -> moveRight()
            '!' -> postfix(Sym.FACTORIAL)
            else -> variable(c.toString())
        }
    }

    private fun value(e: Editor, angle: AngleUnit = AngleUnit.RAD): Double {
        val o = Analysis.evaluate(e.lines, angle)
        assertIs<Outcome.Value>(o, "got $o")
        return o.value
    }

    private fun near(expected: Double, actual: Double, tol: Double = 1e-9) =
        assertTrue(abs(expected - actual) <= tol * maxOf(1.0, abs(expected)), "expected $expected got $actual")

    @Test fun precedenceAndImplicitMultiplication() {
        val e = Editor(); e.type("2+3*4")
        near(14.0, value(e))
        val f = Editor(); f.type("2(3+4)")
        near(14.0, value(f))
        val g = Editor(); g.type("-3*-2")
        near(6.0, value(g))
    }

    @Test fun closingBracketWithoutOpenWrapsPreviousInput() {
        val e = Editor(); e.type("1+2)*3")
        near(9.0, value(e))
    }

    @Test fun squareAndPowerMode() {
        val e = Editor(); e.type("3"); e.square()
        near(9.0, value(e))
        val p = Editor(); p.type("2"); p.togglePower(); assertTrue(p.inPower); p.type("10"); p.togglePower()
        assertTrue(!p.inPower); p.type("+1")
        near(1025.0, value(p))
    }

    @Test fun powerBindsTighterThanUnaryMinus() {
        val e = Editor(); e.type("-2"); e.square()
        near(-4.0, value(e))
    }

    @Test fun fractionTakesPreviousOperand() {
        val e = Editor(); e.type("1+6"); e.fraction(); e.type("3")
        near(3.0, value(e))
    }

    @Test fun trigUsesAngleUnit() {
        val e = Editor(); e.function(Fn.SIN); e.type("90")
        near(1.0, value(e, AngleUnit.DEG))
        val g = Editor(); g.function(Fn.COS); g.type("200")
        near(-1.0, value(g, AngleUnit.GRAD))
        val z = Editor(); z.function(Fn.SIN); z.type("180")
        assertEquals(0.0, value(z, AngleUnit.DEG))
    }

    @Test fun linearEquation() {
        val e = Editor(); e.type("2x+3=11")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD)
        assertIs<Outcome.Solutions>(o)
        assertEquals("x", o.values[0].first)
        near(4.0, o.values[0].second.single())
    }

    @Test fun quadraticHasTwoRoots() {
        val e = Editor(); e.type("x"); e.square(); e.type("=4")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD) as Outcome.Solutions
        val roots = o.values[0].second
        assertEquals(2, roots.size, "$roots")
        near(-2.0, roots[0]); near(2.0, roots[1])
    }

    @Test fun doubleRoot() {
        val e = Editor(); e.type("(x-3)"); e.square(); e.type("=0")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD) as Outcome.Solutions
        near(3.0, o.values[0].second.single(), 1e-6)
    }

    @Test fun systemOfTwoEquations() {
        val e = Editor(); e.type("x+y=3")
        e.addEquation()
        e.type("x"); e.moveRight(); e.moveRight(); e.moveRight(); e.type("y+1")
        // second line is [x] = [y+1]
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD) as Outcome.Solutions
        val m = o.values.toMap()
        near(2.0, m.getValue("x").single()); near(1.0, m.getValue("y").single())
    }

    @Test fun underdetermined() {
        val e = Editor(); e.type("x*x*y=3")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD)
        assertIs<Outcome.Error>(o)
        assertEquals(CalcException.Reason.UNDERDETERMINED, o.reason)
    }

    @Test fun linearUnderdeterminedSolvesForXInTermsOfY() {
        val e = Editor(); e.type("2y+x=3")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD)
        assertIs<Outcome.Parametric>(o, "got $o")
        val (name, form) = o.values.single()
        assertEquals("x", name)
        near(3.0, form.constant)
        assertEquals("y", form.terms.single().first)
        near(-2.0, form.terms.single().second)
    }

    @Test fun twoEquationsThreeUnknownsSolveXAndY() {
        val e = Editor(); e.type("x+y+z=6")
        e.addEquation()
        e.type("x"); e.moveRight(); e.moveRight(); e.moveRight(); e.type("y+z")
        // x + y + z = 6, x = y + z  →  x = 3, y = 3 − z
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD)
        assertIs<Outcome.Parametric>(o, "got $o")
        val m = o.values.toMap()
        near(3.0, m.getValue("x").constant)
        assertTrue(m.getValue("x").terms.isEmpty())
        near(3.0, m.getValue("y").constant)
        near(-1.0, m.getValue("y").terms.single().second)
    }

    @Test fun dependentLinearSystemIsParametric() {
        val e = Editor(); e.type("x+y=2")
        e.addEquation()
        e.type("2x+2y"); e.moveRight(); e.moveRight(); e.moveRight(); e.type("4")
        assertIs<Outcome.Parametric>(Analysis.evaluate(e.lines, AngleUnit.RAD))
    }

    @Test fun functionOfOneVariable() {
        val e = Editor(); e.type("2x")
        assertIs<Outcome.Function>(Analysis.evaluate(e.lines, AngleUnit.RAD))
        assertIs<GraphSpec.Plot>(Analysis.graphFor(e.lines, AngleUnit.RAD))
    }

    @Test fun definiteIntegral() {
        val e = Editor(); e.integral()
        e.type("0>1>x"); e.square()
        near(1.0 / 3.0, value(e))
        assertIs<GraphSpec.Area>(Analysis.graphFor(e.lines, AngleUnit.RAD))
    }

    @Test fun doubleIntegralAndReorder() {
        // ∫0^2 ∫0^1 x·y dy dx = 1
        val e = Editor(); e.integral(); e.type("0>2>")
        e.integral(); e.type("0>1>xy")
        (e.lines[0].items[0] as Integral).let { outer ->
            (outer.body.items[0] as Integral).variable.items[0] = Sym.variable("y")
        }
        near(1.0, value(e), 1e-9)
        assertTrue(e.reorderIntegration())
        val outer = e.lines[0].items[0] as Integral
        assertEquals("y", outer.variable.toString())
        assertEquals("1", outer.upper.toString())
        near(1.0, value(e), 1e-9)
    }

    @Test fun regionGraphForVariableLimits() {
        // ∫0^1 ∫0^x 1 dy dx = 1/2
        val e = Editor(); e.integral(); e.type("0>1>")
        e.integral(); e.type("0>x>1")
        (e.lines[0].items[0] as Integral).let { (it.body.items[0] as Integral).variable.items[0] = Sym.variable("y") }
        near(0.5, value(e), 1e-9)
        assertTrue(!e.reorderIntegration())
        assertIs<GraphSpec.Region>(Analysis.graphFor(e.lines, AngleUnit.RAD))
    }

    @Test fun derivativeInEquation() {
        // d/dx (x²) = 6  →  x = 3
        val e = Editor(); e.derivative(); e.type("x"); e.square(); e.moveRight(); e.type("=6")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD) as Outcome.Solutions
        near(3.0, o.values[0].second.single(), 1e-6)
    }

    @Test fun formulaSolvesForEmptyCase() {
        val ohm = Formulas.all.first { it.id == "ohm" }
        val e = Editor(); e.load(ohm.build(UnitSystem.METRIC))
        // caret is in V; fill I = 2 and R = 5 instead, leave V empty
        e.nextEmptyCase(); e.type("2"); e.nextEmptyCase(); e.type("5")
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD) as Outcome.Solutions
        assertEquals("V", o.values[0].first)
        near(10.0, o.values[0].second.single())
    }

    @Test fun allFormulasParse() {
        for (f in Formulas.all) for (sys in UnitSystem.entries) {
            val o = Analysis.evaluate(f.build(sys), AngleUnit.RAD)
            if (o is Outcome.Error) {
                assertTrue(
                    o.reason == CalcException.Reason.UNDERDETERMINED || o.reason == CalcException.Reason.EMPTY_CASE ||
                        o.reason == CalcException.Reason.UNKNOWN_VARIABLE,
                    "${f.id}: $o",
                )
            }
        }
        val gauss = Analysis.evaluate(Formulas.all.first { it.id == "gaussian" }.build(UnitSystem.METRIC), AngleUnit.RAD)
        near(kotlin.math.sqrt(PI), (gauss as Outcome.Value).value, 1e-8)
    }

    @Test fun factorialAndConstants() {
        val e = Editor(); e.type("5!")
        near(120.0, value(e))
        val p = Editor(); p.constant(Sym.PI)
        near(PI, value(p))
    }

    @Test fun backspaceUnwrapsBrackets() {
        val e = Editor(); e.type("(12")
        e.moveLeft(); e.moveLeft(); e.backspace() // caret at start of group → unwrap
        assertEquals("12", e.lines[0].toString())
    }

    @Test fun conversions() {
        val len = Units.all.getValue(UnitCategory.LENGTH)
        near(30.48, Units.convert(1.0, len.first { it.symbol == "ft" }, len.first { it.symbol == "cm" }))
        val t = Units.all.getValue(UnitCategory.TEMPERATURE)
        near(212.0, Units.convert(100.0, t.first { it.symbol == "°C" }, t.first { it.symbol == "°F" }))
    }

    @Test fun formatting() {
        assertEquals("0.333333333333", Format.number(1.0 / 3))
        assertEquals("−2.5", Format.number(-2.5))
        assertEquals("1.5×10^20", Format.number(1.5e20))
        assertEquals("1.5E20", Format.plain(1.5e20))
    }

    @Test fun truthOfClosedEquation() {
        val e = Editor(); e.type("2+2=4")
        assertEquals(Outcome.Truth(true), Analysis.evaluate(e.lines, AngleUnit.RAD))
    }
}

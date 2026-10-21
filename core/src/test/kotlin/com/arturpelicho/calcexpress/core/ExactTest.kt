package com.arturpelicho.calcexpress.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExactTest {
    private fun eval(e: Editor, angle: AngleUnit = AngleUnit.DEG) = Analysis.evaluate(e.lines, angle)

    @Test fun trigAtSpecialAngles() {
        val e = Editor(); e.function(Fn.SIN); e.digit('4'); e.digit('5')
        val o = eval(e) as Outcome.Value
        assertEquals("=frac(sqrt(2),2)", o.exact.toString())
        val a = Editor(); a.function(Fn.ASIN); a.digit('1'); a.operator(Sym.DIVIDE); a.digit('2')
        val r = eval(a, AngleUnit.RAD) as Outcome.Value
        assertEquals("=frac(π,6)", r.exact.toString())
    }

    @Test fun repeatingFractions() {
        val e = Editor(); e.digit('1'); e.operator(Sym.DIVIDE); e.digit('7')
        assertEquals("=frac(1,7)", (eval(e) as Outcome.Value).exact.toString())
        val d = Editor(); d.digit('0'); d.point(); d.digit('1'); d.operator(Sym.PLUS); d.digit('0'); d.point(); d.digit('2')
        assertNull((eval(d) as Outcome.Value).exact)
    }

    @Test fun definiteIntegralExactValue() {
        // ∫0^π sin x dx = 2 and ∫0^1 x² dx = 1/3
        val e = Editor(); e.integral(); e.digit('0'); e.moveRight(); e.constant(Sym.PI); e.moveRight(); e.function(Fn.SIN); e.variable("x")
        val o = eval(e, AngleUnit.RAD)
        assertIs<Outcome.Value>(o)
        val q = Editor(); q.integral(); q.digit('0'); q.moveRight(); q.digit('1'); q.moveRight(); q.variable("x"); q.square()
        assertEquals("=frac(1,3)", (eval(q, AngleUnit.RAD) as Outcome.Value).exact.toString())
    }

    @Test fun symbolicDerivative() {
        // d/dx (x·sin x) = x·cos x + sin x
        val e = Editor(); e.derivative(); e.variable("x"); e.function(Fn.SIN); e.variable("x")
        val o = eval(e, AngleUnit.RAD)
        assertIs<Outcome.Symbolic>(o, "got $o")
        assertEquals("=xcos(x)+sin(x)", o.rows.single().toString())
    }
}

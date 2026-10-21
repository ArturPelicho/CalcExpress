package com.arturpelicho.calcexpress.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MathCodecTest {
    @Test fun roundTripEveryNode() {
        val e = Editor()
        e.integral(); e.digit('0'); e.moveRight(); e.constant(Sym.PI); e.moveRight()
        e.function(Fn.SIN); e.variable("x"); e.moveRight(); e.square(); e.fraction(); e.digit('1'); e.moveRight(); e.variable("α")
        e.addEquation(); e.matrix(2, 2); e.digit('7')
        e.lines += MathRow(mutableListOf(Slot("ε"), Sym.equals(), Root(MathRow(mutableListOf(Sym.digit('8'))), MathRow(mutableListOf(Sym.digit('3')))),
            Derivative(body = MathRow(mutableListOf(Sym.variable("y")))), Group(MathRow(mutableListOf(Sym.op(Sym.MINUS), Power(MathRow()))))))
        val text = MathCodec.encode(e.lines)
        val back = MathCodec.decode(text)!!
        assertEquals(e.lines.map { it.toString() }, back.map { it.toString() })
        assertEquals(text, MathCodec.encode(back))
    }

    @Test fun invalidInputIsRejected() {
        assertNull(MathCodec.decode("[s9,1:x]"))
        assertNull(MathCodec.decode("[q]"))
    }
}

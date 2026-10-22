package com.arturpelicho.calcexpress.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitBarTest {
    private fun slots(lines: List<MathRow>) = lines.flatMap { it.allRows() }.flatMap { it.items }.filterIsInstance<Slot>()

    private fun fill(s: Slot, text: String) {
        for (c in text) s.content.items += if (c == '.') Sym.point() else Sym.digit(c)
    }

    @Test fun formulaBoxesKnowTheirUnits() {
        val f = Formulas.all.first { it.id == "kinetic" }
        val metric = slots(f.rows(UnitSystem.METRIC)).associate { it.label to it.baseUnit }
        assertEquals<Map<String?, String?>>(mapOf("E" to "J", "m" to "kg", "v" to "m/s"), metric)
        val imperial = slots(f.rows(UnitSystem.IMPERIAL)).associate { it.label to it.baseUnit }
        assertEquals("ft·lbf", imperial["E"])
        // Subscripted labels fall back to their base symbol.
        val vm = Formulas.all.first { it.id == "von_mises" }
        assertTrue(slots(vm.rows(UnitSystem.METRIC)).all { it.baseUnit == "Pa" })
    }

    @Test fun typedUnitsScaleAndUnknownIsGivenInItsUnit() {
        val f = Formulas.all.first { it.id == "kinetic" }
        val lines = f.rows(UnitSystem.METRIC)
        val (e, m, v) = slots(lines)
        val ed = Editor().apply { this.lines.clear(); this.lines += lines }
        fill(m, "2"); ed.setSlotUnit(m, UnitOptions.forUnit("kg").first { it.symbol == "t" })   // 2 t = 2000 kg
        fill(v, "100")                                                                       // 100 m/s
        ed.setSlotUnit(e, UnitOptions.forUnit("J").first { it.symbol == "MJ" })
        val o = Analysis.evaluate(lines, AngleUnit.RAD) as Outcome.Solutions
        // ½·2000·100² = 10⁷ J = 10 MJ
        assertEquals(10.0, o.values.single().second.single(), 1e-9)
        assertEquals("MJ", o.units["E"])
        // Choosing the formula's own unit again clears the conversion.
        ed.setSlotUnit(e, UnitOptions.forUnit("J").first { it.symbol == "J" })
        assertNull(e.unit)
        val o2 = Analysis.evaluate(lines, AngleUnit.RAD) as Outcome.Solutions
        assertEquals(1e7, o2.values.single().second.single(), 1e-3)
        assertEquals("J", o2.units["E"])
    }

    @Test fun optionsAndHints() {
        assertTrue(UnitOptions.forUnit("Pa").any { it.symbol == "GPa" && it.factor == 1e9 })
        assertTrue(UnitOptions.forUnit("V").any { it.symbol == "kV" })
        assertTrue(UnitOptions.forUnit("K").isEmpty())
        assertEquals("×10⁹", UnitOptions.powerHint(1e9))
        assertEquals("×10⁻³", UnitOptions.powerHint(1e-3))
        assertNull(UnitOptions.powerHint(0.3048))
    }

    @Test fun unitsSurviveTheHistoryCodec() {
        val s = Slot("E", MathRow(), "J", UnitOption("GJ", 1e9))
        val back = MathCodec.decode(MathCodec.encode(listOf(MathRow(mutableListOf(s)))))!!
        val b = back[0].items[0] as Slot
        assertEquals("J", b.baseUnit); assertEquals("GJ", b.unit?.symbol); assertEquals(1e9, b.unit?.factor)
    }
}

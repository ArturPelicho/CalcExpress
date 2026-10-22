package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

/** A unit a formula box can be typed in: value in the formula's own unit = typed value × [factor]. */
data class UnitOption(val symbol: String, val factor: Double, val system: UnitSystem? = null)

object UnitOptions {
    /** Electrical and similar SI units that are not in the converter but take prefixes. */
    private val PREFIXABLE = setOf("V", "A", "Ω", "H", "F", "Hz", "C", "S", "T", "Wb")
    private val PREFIXES = listOf("p" to 1e-12, "n" to 1e-9, "µ" to 1e-6, "m" to 1e-3, "" to 1.0, "k" to 1e3, "M" to 1e6, "G" to 1e9)

    /** The units a quantity given in [base] can be typed in, [base] itself included (factor 1). */
    fun forUnit(base: String): List<UnitOption> {
        for ((cat, units) in Units.all) {
            // Temperatures have offsets: a box multiplies, so they cannot be offered.
            if (cat == UnitCategory.TEMPERATURE || cat == UnitCategory.ANGLE) continue
            val b = units.firstOrNull { it.symbol == base } ?: continue
            return units.filter { it.offset == 0.0 }.map { UnitOption(it.symbol, it.factor / b.factor, it.system) }
        }
        if (base in PREFIXABLE) return PREFIXES.map { (p, f) -> UnitOption(p + base, f) }
        return emptyList()
    }

    /** "×10⁹" for a pure power of ten other than 1, else null. */
    fun powerHint(factor: Double): String? {
        if (factor <= 0) return null
        val e = log10(factor)
        val n = e.roundToInt()
        if (n == 0 || abs(e - n) > 1e-9) return null
        val sup = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it.digitToIntOrNull() ?: return@map '⁻'] }.joinToString("")
        return "×10$sup"
    }
}

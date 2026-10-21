package com.arturpelicho.calcexpress.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

object Format {
    /** Up to [digits] significant digits, scientific notation for very large/small numbers. */
    fun number(v: Double, digits: Int = 12): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "∞" else "−∞"
        if (v == 0.0) return "0"
        val bd = BigDecimal(v).round(MathContext(digits, RoundingMode.HALF_EVEN))
        val a = abs(bd.toDouble())
        val text = if (a >= 1e12 || a < 1e-6) {
            val s = String.format(java.util.Locale.ROOT, "%.${digits - 1}E", bd)
            val (mant, exp) = s.split("E")
            val m = mant.trimEnd('0').trimEnd('.')
            "$m×10^${exp.toInt()}"
        } else {
            bd.stripTrailingZeros().toPlainString()
        }
        return text.replace("-", "−")
    }

    /** Machine-readable form for re-inserting a result in the editor (uses 'E'). */
    fun plain(v: Double, digits: Int = 12): String {
        if (v == 0.0) return "0"
        val bd = BigDecimal(v).round(MathContext(digits, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val a = abs(v)
        return if (a >= 1e12 || a < 1e-6) {
            val s = String.format(java.util.Locale.ROOT, "%.${digits - 1}E", bd)
            val (mant, exp) = s.split("E")
            mant.trimEnd('0').trimEnd('.') + "E" + exp.toInt()
        } else bd.toPlainString()
    }
}

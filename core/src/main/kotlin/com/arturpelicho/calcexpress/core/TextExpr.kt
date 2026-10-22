package com.arturpelicho.calcexpress.core

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.pow

/**
 * Small evaluator for typed expressions such as "3x^2 - 2*x*y + sin(z)", used by input fields
 * (e.g. a stress field). Letters that do not form a function name are single-letter variables,
 * so "xy" means x·y; juxtaposition multiplies.
 */
object TextExpr {
    private val FUNCTIONS = listOf("sqrt", "sin", "cos", "tan", "exp", "ln", "log", "abs")

    /** The value, or null when the text is not a valid expression or uses an unknown variable. */
    fun eval(text: String, vars: Map<String, Double> = emptyMap(), angle: AngleUnit = AngleUnit.RAD): Double? =
        try {
            val p = P(normalise(text), vars, angle)
            val v = p.sum()
            if (p.i != p.s.length) null else v
        } catch (e: IllegalArgumentException) {
            null
        }

    /** Single-letter variables used by the text. */
    fun variables(text: String): Set<String> {
        val s = normalise(text)
        val out = mutableSetOf<String>()
        var i = 0
        while (i < s.length) {
            val f = FUNCTIONS.firstOrNull { s.startsWith(it, i) }
            when {
                f != null -> i += f.length
                s.startsWith("pi", i) -> i += 2
                s[i] == 'π' -> i++
                s[i].isLetter() && s[i] != 'e' -> { out += s[i].toString(); i++ }
                else -> i++
            }
        }
        return out
    }

    private fun normalise(text: String) = text.lowercase().replace(" ", "").replace(',', '.')
        .replace('−', '-').replace('×', '*').replace('·', '*').replace('÷', '/')
        .replace("²", "^2").replace("³", "^3")

    private class P(val s: String, val vars: Map<String, Double>, val angle: AngleUnit) {
        var i = 0

        fun fail(): Nothing = throw IllegalArgumentException("bad expression")

        fun sum(): Double {
            var v = product()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = product()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun product(): Double {
            var v = unary()
            while (i < s.length) {
                val c = s[i]
                v = when {
                    c == '*' -> { i++; v * unary() }
                    c == '/' -> { i++; v / unary() }
                    // Implicit multiplication: 2x, 3(…), x y, x sin(y).
                    c == '(' || c.isLetterOrDigit() || c == '.' || c == 'π' -> v * power()
                    else -> return v
                }
            }
            return v
        }

        fun unary(): Double = when {
            i < s.length && s[i] == '-' -> { i++; -unary() }
            i < s.length && s[i] == '+' -> { i++; unary() }
            else -> power()
        }

        fun power(): Double {
            val base = atom()
            if (i < s.length && s[i] == '^') {
                i++
                return base.pow(unary())
            }
            return base
        }

        fun atom(): Double {
            if (i >= s.length) fail()
            val c = s[i]
            if (c.isDigit() || c == '.') {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                // Scientific notation: 2e-3 (only when digits follow).
                if (i + 1 < s.length && s[i] == 'e' && (s[i + 1].isDigit() || (s[i + 1] in "+-" && i + 2 < s.length && s[i + 2].isDigit()))) {
                    i += 2
                    while (i < s.length && s[i].isDigit()) i++
                }
                return s.substring(start, i).toDoubleOrNull() ?: fail()
            }
            if (c == '(') {
                i++
                val v = sum()
                if (i >= s.length || s[i] != ')') fail()
                i++
                return v
            }
            if (c == 'π') { i++; return PI }
            if (s.startsWith("pi", i)) { i += 2; return PI }
            val f = FUNCTIONS.firstOrNull { s.startsWith(it, i) }
            if (f != null) {
                i += f.length
                val a = power()
                return when (f) {
                    "sqrt" -> kotlin.math.sqrt(a)
                    "sin" -> kotlin.math.sin(angle.toRadians(a))
                    "cos" -> kotlin.math.cos(angle.toRadians(a))
                    "tan" -> kotlin.math.tan(angle.toRadians(a))
                    "exp" -> kotlin.math.exp(a)
                    "ln" -> kotlin.math.ln(a)
                    "log" -> kotlin.math.log10(a)
                    else -> kotlin.math.abs(a)
                }
            }
            if (c.isLetter()) {
                i++
                if (c == 'e' && !vars.containsKey("e")) return E
                return vars[c.toString()] ?: fail()
            }
            fail()
        }
    }
}

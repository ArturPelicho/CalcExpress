package com.arturpelicho.calcexpress.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

enum class AngleUnit(val label: String) {
    DEG("DEG"), RAD("RAD"), GRAD("GRAD");

    fun toRadians(v: Double) = when (this) {
        DEG -> v * PI / 180.0
        RAD -> v
        GRAD -> v * PI / 200.0
    }

    fun fromRadians(v: Double) = when (this) {
        DEG -> v * 180.0 / PI
        RAD -> v
        GRAD -> v * 200.0 / PI
    }

    fun next() = entries[(ordinal + 1) % entries.size]
}

class CalcException(val reason: Reason, val detail: String = "") : Exception("$reason $detail") {
    enum class Reason { SYNTAX, EMPTY_CASE, DOMAIN, UNKNOWN_VARIABLE, NO_SOLUTION, UNDERDETERMINED, NOT_EQUATION, INDEFINITE_INTEGRAL }
}

class EvalContext(val angle: AngleUnit, val values: Map<String, Double> = emptyMap()) {
    fun with(name: String, value: Double) = EvalContext(angle, values + (name to value))
    fun with(more: Map<String, Double>) = EvalContext(angle, values + more)
}

/** Evaluable expression tree produced by [Parser]. */
sealed class Expr {
    abstract fun eval(ctx: EvalContext): Double

    /** Variables that must come from outside (bound integration/derivative variables excluded). */
    abstract fun freeVariables(): Set<String>

    data class Num(val value: Double) : Expr() {
        override fun eval(ctx: EvalContext) = value
        override fun freeVariables() = emptySet<String>()
    }

    data class Var(val name: String) : Expr() {
        override fun eval(ctx: EvalContext) =
            ctx.values[name] ?: throw CalcException(CalcException.Reason.UNKNOWN_VARIABLE, name)
        override fun freeVariables() = setOf(name)
    }

    data class Neg(val e: Expr) : Expr() {
        override fun eval(ctx: EvalContext) = -e.eval(ctx)
        override fun freeVariables() = e.freeVariables()
    }

    data class Bin(val op: Char, val a: Expr, val b: Expr) : Expr() {
        override fun eval(ctx: EvalContext): Double {
            val x = a.eval(ctx)
            val y = b.eval(ctx)
            return when (op) {
                '+' -> x + y
                '-' -> x - y
                '*' -> x * y
                '/' -> if (y == 0.0) throw CalcException(CalcException.Reason.DOMAIN, "÷0") else x / y
                '^' -> power(x, y)
                else -> error("op $op")
            }
        }
        override fun freeVariables() = a.freeVariables() + b.freeVariables()
    }

    data class Call(val fn: Fn, val arg: Expr) : Expr() {
        override fun eval(ctx: EvalContext): Double {
            val v = arg.eval(ctx)
            val r = when (fn) {
                Fn.SIN -> sin(ctx.angle.toRadians(v))
                Fn.COS -> cos(ctx.angle.toRadians(v))
                Fn.TAN -> {
                    val c = cos(ctx.angle.toRadians(v))
                    if (abs(c) < 1e-15) throw CalcException(CalcException.Reason.DOMAIN, "tan")
                    tan(ctx.angle.toRadians(v))
                }
                Fn.ASIN -> ctx.angle.fromRadians(asin(v))
                Fn.ACOS -> ctx.angle.fromRadians(acos(v))
                Fn.ATAN -> ctx.angle.fromRadians(atan(v))
                Fn.SINH -> sinh(v)
                Fn.COSH -> cosh(v)
                Fn.TANH -> tanh(v)
                Fn.LN -> ln(v)
                Fn.LOG -> log10(v)
                Fn.EXP -> exp(v)
                Fn.ABS -> abs(v)
            }
            return cleanTrig(fn, r)
        }
        override fun freeVariables() = arg.freeVariables()
    }

    data class RootE(val radicand: Expr, val index: Expr?) : Expr() {
        override fun eval(ctx: EvalContext): Double {
            val x = radicand.eval(ctx)
            if (index == null) {
                if (x < 0) throw CalcException(CalcException.Reason.DOMAIN, "√")
                return sqrt(x)
            }
            val n = index.eval(ctx)
            return if (x < 0 && isOddInteger(n)) -(-x).pow(1.0 / n) else x.pow(1.0 / n)
        }
        override fun freeVariables() = radicand.freeVariables() + (index?.freeVariables() ?: emptySet())
    }

    data class Factorial(val e: Expr) : Expr() {
        override fun eval(ctx: EvalContext) = factorial(e.eval(ctx))
        override fun freeVariables() = e.freeVariables()
    }

    data class IntegralE(val lower: Expr, val upper: Expr, val body: Expr, val variable: String, val depth: Int) : Expr() {
        override fun eval(ctx: EvalContext): Double {
            val a = lower.eval(ctx)
            val b = upper.eval(ctx)
            return Numerics.integrate(a, b, nested = depth > 0) { t -> body.eval(ctx.with(variable, t)) }
        }
        override fun freeVariables() = lower.freeVariables() + upper.freeVariables() + (body.freeVariables() - variable)
    }

    data class DerivativeE(val variable: String, val body: Expr) : Expr() {
        override fun eval(ctx: EvalContext): Double {
            val x0 = ctx.values[variable] ?: throw CalcException(CalcException.Reason.UNKNOWN_VARIABLE, variable)
            return Numerics.derivative(x0) { t -> body.eval(ctx.with(variable, t)) }
        }
        override fun freeVariables() = body.freeVariables() + variable
    }

    companion object {
        fun power(x: Double, y: Double): Double {
            if (x < 0 && y != floor(y)) {
                // Real odd roots of negative numbers, e.g. (-8)^(1/3).
                val inv = 1.0 / y
                if (isOddInteger(Math.rint(inv)) && abs(inv - Math.rint(inv)) < 1e-9) return -(-x).pow(y)
                throw CalcException(CalcException.Reason.DOMAIN, "^")
            }
            return x.pow(y)
        }

        private fun isOddInteger(n: Double) = n == floor(n) && abs(n % 2.0) == 1.0

        /** Removes floating noise such as sin(180°) = 1.2E-16. */
        private fun cleanTrig(fn: Fn, r: Double): Double =
            if ((fn.isTrig) && abs(r) < 1e-14) 0.0 else r

        fun factorial(n: Double): Double {
            if (n < 0 && n == floor(n)) throw CalcException(CalcException.Reason.DOMAIN, "!")
            if (n == floor(n) && n <= 170) {
                var r = 1.0
                var i = 2.0
                while (i <= n) { r *= i; i++ }
                return r
            }
            return Numerics.gamma(n + 1)
        }
    }
}

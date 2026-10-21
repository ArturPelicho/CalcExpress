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
    enum class Reason {
        SYNTAX, EMPTY_CASE, DOMAIN, UNKNOWN_VARIABLE, NO_SOLUTION, UNDERDETERMINED, NOT_EQUATION, INDEFINITE_INTEGRAL,
        MATRIX_DIMENSIONS, MATRIX_SINGULAR, MATRIX_NOT_ALLOWED, NO_ANTIDERIVATIVE,
    }
}

class EvalContext(val angle: AngleUnit, val values: Map<String, Double> = emptyMap()) {
    fun with(name: String, value: Double) = EvalContext(angle, values + (name to value))
    fun with(more: Map<String, Double>) = EvalContext(angle, values + more)
}

/** Evaluable expression tree produced by [Parser]. */
sealed class Expr {
    abstract fun eval(ctx: EvalContext): Double

    /** Evaluation that also allows matrices; scalar-only nodes just wrap [eval]. */
    open fun evalValue(ctx: EvalContext): Value = Value.Scalar(eval(ctx))

    /** Variables that must come from outside (bound integration/derivative variables excluded). */
    abstract fun freeVariables(): Set<String>

    data class Num(val value: Double) : Expr() {
        override fun eval(ctx: EvalContext) = value
        override fun freeVariables() = emptySet<String>()
    }

    /** π or e: evaluates like a number but keeps its name for symbolic results. */
    data class Const(val name: String, val value: Double) : Expr() {
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
        override fun evalValue(ctx: EvalContext): Value = when (val v = e.evalValue(ctx)) {
            is Value.Scalar -> Value.Scalar(-v.v)
            is Value.Mat -> MatrixOps.scale(v, -1.0)
        }
        override fun freeVariables() = e.freeVariables()
    }

    data class Bin(val op: Char, val a: Expr, val b: Expr) : Expr() {
        override fun evalValue(ctx: EvalContext): Value {
            val x = a.evalValue(ctx)
            val y = b.evalValue(ctx)
            if (x is Value.Scalar && y is Value.Scalar) return Value.Scalar(scalarOp(x.v, y.v))
            return when (op) {
                '+', '-' -> {
                    if (x !is Value.Mat || y !is Value.Mat) throw CalcException(CalcException.Reason.MATRIX_DIMENSIONS)
                    MatrixOps.add(x, y, if (op == '+') 1.0 else -1.0)
                }
                '*' -> when {
                    x is Value.Mat && y is Value.Mat -> MatrixOps.multiply(x, y)
                    x is Value.Mat -> MatrixOps.scale(x, y.scalar())
                    else -> MatrixOps.scale(y as Value.Mat, x.scalar())
                }
                '/' -> when {
                    x is Value.Mat && y is Value.Scalar -> MatrixOps.scale(x, 1.0 / nonZero(y.v))
                    x is Value.Mat && y is Value.Mat -> MatrixOps.multiply(x, MatrixOps.inverse(y))
                    else -> MatrixOps.scale(MatrixOps.inverse(y as Value.Mat), x.scalar())
                }
                '^' -> {
                    if (x !is Value.Mat) throw CalcException(CalcException.Reason.MATRIX_NOT_ALLOWED)
                    MatrixOps.power(x, y.scalar())
                }
                else -> error("op $op")
            }
        }

        private fun nonZero(v: Double) = if (v == 0.0) throw CalcException(CalcException.Reason.DOMAIN, "÷0") else v

        override fun eval(ctx: EvalContext): Double = scalarOp(a.eval(ctx), b.eval(ctx))

        private fun scalarOp(x: Double, y: Double): Double {
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
        override fun evalValue(ctx: EvalContext): Value =
            if (fn == Fn.TRANSPOSE) {
                when (val m = arg.evalValue(ctx)) {
                    is Value.Mat -> MatrixOps.transpose(m)
                    is Value.Scalar -> m
                }
            } else Value.Scalar(eval(ctx))

        override fun eval(ctx: EvalContext): Double {
            if (fn.isMatrixFn) {
                val m = arg.evalValue(ctx)
                return when (fn) {
                    Fn.DET -> if (m is Value.Mat) MatrixOps.determinant(m) else m.scalar()
                    Fn.TRACE -> if (m is Value.Mat) MatrixOps.trace(m) else m.scalar()
                    else -> m.scalar() // transpose of a matrix is not a number
                }
            }
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
                Fn.DET, Fn.TRANSPOSE, Fn.TRACE -> v
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

    data class MatrixE(val rows: Int, val cols: Int, val cells: List<Expr>) : Expr() {
        override fun eval(ctx: EvalContext): Double =
            if (rows == 1 && cols == 1) cells[0].eval(ctx) else throw CalcException(CalcException.Reason.MATRIX_NOT_ALLOWED)
        override fun evalValue(ctx: EvalContext): Value =
            Value.Mat(rows, cols, DoubleArray(cells.size) { cells[it].evalValue(ctx).scalar() })
        override fun freeVariables() = cells.flatMap { it.freeVariables() }.toSet()
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

    /** ∫ body d(variable) without limits: only meaningful symbolically (see [Cas]). */
    data class IndefiniteE(val body: Expr, val variable: String) : Expr() {
        override fun eval(ctx: EvalContext): Double = throw CalcException(CalcException.Reason.INDEFINITE_INTEGRAL)
        override fun freeVariables() = body.freeVariables() + variable
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

        /** True when [e] contains an indefinite integral anywhere. */
        fun hasIndefinite(e: Expr): Boolean = when (e) {
            is IndefiniteE -> true
            is Num, is Const, is Var -> false
            is Neg -> hasIndefinite(e.e)
            is Bin -> hasIndefinite(e.a) || hasIndefinite(e.b)
            is Call -> hasIndefinite(e.arg)
            is RootE -> hasIndefinite(e.radicand) || (e.index?.let(::hasIndefinite) ?: false)
            is MatrixE -> e.cells.any(::hasIndefinite)
            is Factorial -> hasIndefinite(e.e)
            is IntegralE -> hasIndefinite(e.body) || hasIndefinite(e.lower) || hasIndefinite(e.upper)
            is DerivativeE -> hasIndefinite(e.body)
        }

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

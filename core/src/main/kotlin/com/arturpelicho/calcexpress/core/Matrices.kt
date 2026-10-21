package com.arturpelicho.calcexpress.core

import kotlin.math.abs

/** Result of evaluating an expression: a number or a matrix. */
sealed class Value {
    data class Scalar(val v: Double) : Value()

    /** Row-major matrix. */
    class Mat(val rows: Int, val cols: Int, val data: DoubleArray) : Value() {
        operator fun get(r: Int, c: Int) = data[r * cols + c]
        override fun equals(other: Any?) =
            other is Mat && other.rows == rows && other.cols == cols && other.data.contentEquals(data)
        override fun hashCode() = 31 * (31 * rows + cols) + data.contentHashCode()
        override fun toString() = (0 until rows).joinToString(";", "[", "]") { r ->
            (0 until cols).joinToString(",") { c -> this[r, c].toString() }
        }
    }

    fun scalar(): Double = when (this) {
        is Scalar -> v
        is Mat -> throw CalcException(CalcException.Reason.MATRIX_NOT_ALLOWED)
    }
}

object MatrixOps {
    private fun dims(): Nothing = throw CalcException(CalcException.Reason.MATRIX_DIMENSIONS)

    fun add(a: Value.Mat, b: Value.Mat, sign: Double): Value.Mat {
        if (a.rows != b.rows || a.cols != b.cols) dims()
        return Value.Mat(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] + sign * b.data[it] })
    }

    fun scale(a: Value.Mat, k: Double) = Value.Mat(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] * k })

    fun multiply(a: Value.Mat, b: Value.Mat): Value.Mat {
        if (a.cols != b.rows) dims()
        val out = DoubleArray(a.rows * b.cols)
        for (i in 0 until a.rows) for (j in 0 until b.cols) {
            var s = 0.0
            for (k in 0 until a.cols) s += a[i, k] * b[k, j]
            out[i * b.cols + j] = s
        }
        return Value.Mat(a.rows, b.cols, out)
    }

    fun transpose(a: Value.Mat) = Value.Mat(a.cols, a.rows, DoubleArray(a.data.size) { i -> a[i % a.rows, i / a.rows] })

    fun trace(a: Value.Mat): Double {
        if (a.rows != a.cols) dims()
        return (0 until a.rows).sumOf { a[it, it] }
    }

    fun identity(n: Int) = Value.Mat(n, n, DoubleArray(n * n) { if (it / n == it % n) 1.0 else 0.0 })

    /** Determinant by Gaussian elimination with partial pivoting. */
    fun determinant(a: Value.Mat): Double {
        if (a.rows != a.cols) dims()
        val n = a.rows
        val m = Array(n) { r -> DoubleArray(n) { c -> a[r, c] } }
        var det = 1.0
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[piv][c])) piv = r
            if (m[piv][c] == 0.0) return 0.0
            if (piv != c) { val t = m[c]; m[c] = m[piv]; m[piv] = t; det = -det }
            det *= m[c][c]
            for (r in c + 1 until n) {
                val f = m[r][c] / m[c][c]
                for (k in c until n) m[r][k] -= f * m[c][k]
            }
        }
        return det
    }

    /** Gauss–Jordan inverse. */
    fun inverse(a: Value.Mat): Value.Mat {
        if (a.rows != a.cols) dims()
        val n = a.rows
        val m = Array(n) { r -> DoubleArray(2 * n) { c -> if (c < n) a[r, c] else if (c - n == r) 1.0 else 0.0 } }
        val scale = a.data.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1.0
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[piv][c])) piv = r
            if (abs(m[piv][c]) < 1e-12 * scale) throw CalcException(CalcException.Reason.MATRIX_SINGULAR)
            if (piv != c) { val t = m[c]; m[c] = m[piv]; m[piv] = t }
            val p = m[c][c]
            for (k in 0 until 2 * n) m[c][k] /= p
            for (r in 0 until n) if (r != c) {
                val f = m[r][c]
                if (f != 0.0) for (k in 0 until 2 * n) m[r][k] -= f * m[c][k]
            }
        }
        return Value.Mat(n, n, DoubleArray(n * n) { i -> m[i / n][n + i % n] })
    }

    fun power(a: Value.Mat, e: Double): Value.Mat {
        if (a.rows != a.cols) dims()
        if (e != Math.rint(e)) throw CalcException(CalcException.Reason.DOMAIN, "^")
        var n = e.toLong()
        var base = if (n < 0) inverse(a) else a
        if (n < 0) n = -n
        var result = identity(a.rows)
        while (n > 0) {
            if (n and 1L == 1L) result = multiply(result, base)
            base = multiply(base, base)
            n = n shr 1
        }
        return result
    }
}

package com.arturpelicho.calcexpress.core

import kotlin.math.E
import kotlin.math.PI

/** Turns the editor's [MathRow] trees into [Expr]s, with implicit multiplication. */
class Parser private constructor(
    private val integralDepth: Int,
    /** Filled formula cases by label: an empty case with the same label reuses the value. */
    private val filled: Map<String, MathRow>,
) {

    private sealed class Tok {
        data class Atom(val expr: Expr) : Tok()
        data class Op(val op: Char) : Tok()
        data class Pow(val exponent: Expr) : Tok()
        data class Post(val p: String) : Tok()
    }

    private fun tokenize(row: MathRow): List<Tok> {
        val out = mutableListOf<Tok>()
        val items = row.items
        var i = 0
        while (i < items.size) {
            when (val n = items[i]) {
                is Sym -> when (n.kind) {
                    SymKind.DIGIT, SymKind.POINT -> {
                        val sb = StringBuilder()
                        while (i < items.size) {
                            val s = items[i] as? Sym ?: break
                            if (s.kind != SymKind.DIGIT && s.kind != SymKind.POINT) break
                            sb.append(s.text)
                            i++
                        }
                        val v = sb.toString().let { if (it.startsWith(".")) "0$it" else it }.toDoubleOrNull()
                            ?: throw CalcException(CalcException.Reason.SYNTAX, sb.toString())
                        out += Tok.Atom(Expr.Num(v))
                        continue
                    }
                    SymKind.VARIABLE -> out += Tok.Atom(Expr.Var(n.text))
                    SymKind.CONSTANT -> out += Tok.Atom(
                        Expr.Num(
                            when (n.text) {
                                Sym.PI -> PI
                                Sym.E -> E
                                else -> throw CalcException(CalcException.Reason.SYNTAX, n.text)
                            }
                        )
                    )
                    SymKind.OPERATOR -> out += Tok.Op(
                        when (n.text) {
                            Sym.PLUS, "+" -> '+'
                            Sym.MINUS, "-" -> '-'
                            Sym.TIMES, Sym.DOT, "*" -> '*'
                            Sym.DIVIDE, "/" -> '/'
                            else -> throw CalcException(CalcException.Reason.SYNTAX, n.text)
                        }
                    )
                    SymKind.POSTFIX -> out += Tok.Post(n.text)
                    SymKind.EQUALS -> throw CalcException(CalcException.Reason.SYNTAX, "=")
                }
                is Group -> out += Tok.Atom(parseRow(n.content))
                is Slot -> out += Tok.Atom(
                    if (n.content.isEmpty) {
                        val label = n.label ?: throw CalcException(CalcException.Reason.EMPTY_CASE)
                        filled[label]?.let { parseRow(it) } ?: Expr.Var(label)
                    } else parseRow(n.content)
                )
                is Power -> out += Tok.Pow(parseRow(n.exponent))
                is Fraction -> out += Tok.Atom(Expr.Bin('/', parseRow(n.numerator), parseRow(n.denominator)))
                is Root -> out += Tok.Atom(Expr.RootE(parseRow(n.radicand), n.index?.let { parseRow(it) }))
                is FuncCall -> out += Tok.Atom(Expr.Call(n.fn, parseRow(n.argument)))
                is Integral -> {
                    if (n.lower.isEmpty || n.upper.isEmpty) throw CalcException(CalcException.Reason.INDEFINITE_INTEGRAL)
                    val v = variableName(n.variable)
                    val inner = Parser(integralDepth + 1, filled)
                    out += Tok.Atom(Expr.IntegralE(parseRow(n.lower), parseRow(n.upper), inner.parseRow(n.body), v, integralDepth))
                }
                is MatrixNode -> out += Tok.Atom(Expr.MatrixE(n.rowCount, n.colCount, n.cells.map { parseRow(it) }))
                is Derivative -> out += Tok.Atom(Expr.DerivativeE(variableName(n.variable), parseRow(n.body)))
            }
            i++
        }
        return out
    }

    private fun variableName(row: MathRow): String {
        val s = row.items.singleOrNull() as? Sym
        if (s == null || s.kind != SymKind.VARIABLE) throw CalcException(CalcException.Reason.EMPTY_CASE)
        return s.text
    }

    fun parseRow(row: MathRow): Expr {
        if (row.isEmpty) throw CalcException(CalcException.Reason.EMPTY_CASE)
        val toks = tokenize(row)
        val p = TokParser(toks)
        val e = p.expression()
        if (!p.atEnd()) throw CalcException(CalcException.Reason.SYNTAX)
        return e
    }

    private class TokParser(val toks: List<Tok>) {
        var pos = 0
        fun atEnd() = pos >= toks.size
        fun peek() = toks.getOrNull(pos)

        fun expression(): Expr {
            var left = term()
            while (true) {
                val t = peek() as? Tok.Op ?: break
                if (t.op != '+' && t.op != '-') break
                pos++
                left = Expr.Bin(t.op, left, term())
            }
            return left
        }

        fun term(): Expr {
            var left = unary()
            while (true) {
                val t = peek() ?: break
                left = when {
                    t is Tok.Op && (t.op == '*' || t.op == '/') -> { pos++; Expr.Bin(t.op, left, unary()) }
                    t is Tok.Atom -> Expr.Bin('*', left, unary()) // implicit multiplication: 2x, 3(…)
                    else -> break
                }
            }
            return left
        }

        fun unary(): Expr {
            val t = peek()
            if (t is Tok.Op && (t.op == '-' || t.op == '+')) {
                pos++
                val inner = unary()
                return if (t.op == '-') Expr.Neg(inner) else inner
            }
            return postfix()
        }

        fun postfix(): Expr {
            val t = peek() as? Tok.Atom ?: throw CalcException(CalcException.Reason.SYNTAX)
            pos++
            var e = t.expr
            while (true) {
                e = when (val n = peek()) {
                    is Tok.Pow -> { pos++; Expr.Bin('^', e, n.exponent) }
                    is Tok.Post -> {
                        pos++
                        when (n.p) {
                            Sym.FACTORIAL -> Expr.Factorial(e)
                            Sym.PERCENT -> Expr.Bin('/', e, Expr.Num(100.0))
                            else -> throw CalcException(CalcException.Reason.SYNTAX, n.p)
                        }
                    }
                    else -> break
                }
            }
            return e
        }
    }

    companion object {
        /** Splits a line at top-level '=' and parses each side. */
        fun parseLine(line: MathRow, filled: Map<String, MathRow> = filledCases(listOf(line))): List<Expr> {
            val sides = mutableListOf(MathRow())
            for (n in line.items) {
                if (n is Sym && n.kind == SymKind.EQUALS) sides += MathRow() else sides.last().items += n
            }
            return sides.map { Parser(0, filled).parseRow(it) }
        }

        fun parse(row: MathRow, filled: Map<String, MathRow> = filledCases(listOf(row))): Expr = Parser(0, filled).parseRow(row)

        fun filledCases(lines: List<MathRow>): Map<String, MathRow> {
            val out = mutableMapOf<String, MathRow>()
            for (line in lines) for (r in line.allRows()) for (n in r.items) {
                if (n is Slot && n.label != null && !n.content.isEmpty) out.putIfAbsent(n.label, n.content)
            }
            return out
        }
    }
}

package com.arturpelicho.calcexpress.core

/**
 * Compact text form of editor rows, used to keep the history between sessions.
 * Every text is length-prefixed, so any character can appear in it.
 */
object MathCodec {

    fun encode(lines: List<MathRow>): String = StringBuilder().apply { lines.forEach { row(it, this) } }.toString()

    /** Null when [text] is not a valid encoding (e.g. written by a future version). */
    fun decode(text: String): List<MathRow>? = try {
        val r = Reader(text)
        val out = mutableListOf<MathRow>()
        while (!r.atEnd()) out += r.row()
        out
    } catch (e: RuntimeException) {
        null
    }

    private fun row(r: MathRow, sb: StringBuilder) {
        sb.append('[')
        r.items.forEach { node(it, sb) }
        sb.append(']')
    }

    private fun text(t: String, sb: StringBuilder) { sb.append(t.length).append(':').append(t) }

    private fun node(n: Node, sb: StringBuilder) {
        when (n) {
            is Sym -> { sb.append('s').append(n.kind.ordinal).append(','); text(n.text, sb) }
            is Group -> { sb.append('g'); row(n.content, sb) }
            is Power -> { sb.append('p'); row(n.exponent, sb) }
            is Fraction -> { sb.append('f'); row(n.numerator, sb); row(n.denominator, sb) }
            is Root -> if (n.index == null) { sb.append('r'); row(n.radicand, sb) } else { sb.append('R'); row(n.index, sb); row(n.radicand, sb) }
            is FuncCall -> { sb.append('c'); text(n.fn.name, sb); row(n.argument, sb) }
            is Integral -> { sb.append('i'); row(n.lower, sb); row(n.upper, sb); row(n.body, sb); row(n.variable, sb) }
            is Derivative -> { sb.append('d'); row(n.variable, sb); row(n.body, sb) }
            is Slot -> if (n.baseUnit == null && n.unit == null) {
                sb.append('l'); text(n.label ?: "", sb); sb.append(if (n.label == null) '0' else '1'); row(n.content, sb)
            } else {
                // A box with units: label, formula unit, typed unit and its factor.
                sb.append('u'); text(n.label ?: "", sb); text(n.baseUnit ?: "", sb)
                text(n.unit?.symbol ?: "", sb); text(n.unit?.factor?.toString() ?: "", sb); row(n.content, sb)
            }
            is MatrixNode -> {
                sb.append('m').append(n.rowCount).append(',').append(n.colCount).append(',')
                n.cells.forEach { row(it, sb) }
            }
        }
    }

    private class Reader(val s: String) {
        var i = 0
        fun atEnd() = i >= s.length
        fun expect(c: Char) { require(s[i] == c) { "expected $c at $i" }; i++ }

        fun int(): Int {
            val start = i
            while (s[i].isDigit()) i++
            return s.substring(start, i).toInt()
        }

        fun text(): String {
            val n = int(); expect(':')
            val t = s.substring(i, i + n); i += n
            return t
        }

        fun row(): MathRow {
            expect('[')
            val items = mutableListOf<Node>()
            while (s[i] != ']') items += node()
            i++
            return MathRow(items)
        }

        fun node(): Node = when (val c = s[i++]) {
            's' -> { val k = SymKind.entries[int()]; expect(','); Sym(k, text()) }
            'g' -> Group(row())
            'p' -> Power(row())
            'f' -> Fraction(row(), row())
            'r' -> Root(row())
            'R' -> { val index = row(); Root(row(), index) }
            'c' -> FuncCall(Fn.valueOf(text()), row())
            'i' -> Integral(row(), row(), row(), row())
            'd' -> Derivative(row(), row())
            'l' -> { val label = text(); val has = s[i++] == '1'; Slot(if (has) label else null, row()) }
            'u' -> {
                val label = text().ifEmpty { null }
                val base = text().ifEmpty { null }
                val symbol = text(); val factor = text().toDoubleOrNull()
                val unit = if (symbol.isNotEmpty() && factor != null) UnitOption(symbol, factor) else null
                Slot(label, row(), base, unit)
            }
            'm' -> {
                val rows = int(); expect(','); val cols = int(); expect(',')
                MatrixNode(rows, cols, MutableList(rows * cols) { row() })
            }
            else -> error("node $c")
        }
    }
}

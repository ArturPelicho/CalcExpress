package com.arturpelicho.calcexpress.core

/** Position of the caret: before item [index] of [row]. */
data class Caret(val row: MathRow, val index: Int)

/**
 * Editing state of the calculation area: one or more lines (a system of equations)
 * and a caret. All operations mutate in place; the UI observes [version].
 */
class Editor {
    val lines: MutableList<MathRow> = mutableListOf(MathRow())
    var caret: Caret = Caret(lines[0], 0)
        private set

    /** Incremented on every change so a UI can recompose. */
    var version: Long = 0
        private set

    val activeLine: MathRow get() = lineOf(caret.row) ?: lines.last()

    val isEmpty: Boolean get() = lines.all { it.isEmpty }

    /** True while the caret sits inside a matrix cell. */
    val inMatrix: Boolean get() = enclosingMatrix() != null

    private fun enclosingMatrix(): Pair<MatrixNode, MathRow>? {
        var r = caret.row
        while (true) {
            val p = parentOf(r) ?: return null
            if (p.node is MatrixNode) return p.node to r
            r = p.owner
        }
    }

    /** True while the caret sits inside an exponent ("power mode"). */
    val inPower: Boolean get() = enclosingNodes(caret.row).any { it.first is Power }

    private fun changed() { version++ }

    // ---------------------------------------------------------------- tree helpers

    private data class Parent(val node: Node, val owner: MathRow)

    private fun parentOf(row: MathRow): Parent? {
        for (line in lines) findParent(line, row)?.let { return it }
        return null
    }

    private fun findParent(scope: MathRow, target: MathRow): Parent? {
        for (node in scope.items) {
            for (child in node.rows) {
                if (child === target) return Parent(node, scope)
                findParent(child, target)?.let { return it }
            }
        }
        return null
    }

    private fun lineOf(row: MathRow): MathRow? {
        var r = row
        while (true) {
            if (lines.any { it === r }) return r
            r = parentOf(r)?.owner ?: return null
        }
    }

    /** Ancestors of [row], innermost first, as (node, row-owning-node). */
    private fun enclosingNodes(row: MathRow): List<Pair<Node, MathRow>> {
        val out = mutableListOf<Pair<Node, MathRow>>()
        var r = row
        while (true) {
            val p = parentOf(r) ?: break
            out += p.node to p.owner
            r = p.owner
        }
        return out
    }

    private fun indexIn(owner: MathRow, node: Node) = owner.items.indexOfFirst { it === node }

    // ---------------------------------------------------------------- caret movement

    fun setCaret(row: MathRow, index: Int) {
        caret = Caret(row, index.coerceIn(0, row.items.size))
        changed()
    }

    fun moveLeft() {
        val (row, i) = caret
        if (i > 0) {
            val prev = row.items[i - 1]
            caret = if (prev.rows.isNotEmpty()) {
                val last = prev.rows.last()
                Caret(last, last.items.size)
            } else Caret(row, i - 1)
        } else {
            val p = parentOf(row)
            if (p != null) {
                val idx = p.node.rows.indexOfFirst { it === row }
                caret = if (idx > 0) {
                    val r = p.node.rows[idx - 1]
                    Caret(r, r.items.size)
                } else Caret(p.owner, indexIn(p.owner, p.node))
            } else {
                val li = lines.indexOfFirst { it === row }
                if (li > 0) caret = Caret(lines[li - 1], lines[li - 1].items.size)
            }
        }
        changed()
    }

    fun moveRight() {
        val (row, i) = caret
        if (i < row.items.size) {
            val next = row.items[i]
            caret = if (next.rows.isNotEmpty()) Caret(next.rows.first(), 0) else Caret(row, i + 1)
        } else {
            exitForward()
        }
        changed()
    }

    /** Moves to the next case of the enclosing node, or after the node. */
    private fun exitForward() {
        val row = caret.row
        val p = parentOf(row)
        if (p != null) {
            val idx = p.node.rows.indexOfFirst { it === row }
            caret = if (idx < p.node.rows.size - 1) Caret(p.node.rows[idx + 1], 0)
            else Caret(p.owner, indexIn(p.owner, p.node) + 1)
        } else {
            val li = lines.indexOfFirst { it === row }
            if (li in 0 until lines.size - 1) caret = Caret(lines[li + 1], 0)
        }
    }

    /** Jumps to the next empty case anywhere after the caret (wrapping around). */
    fun nextEmptyCase(): Boolean {
        val rows = lines.flatMap { it.allRows().toList() }
        val here = rows.indexOfFirst { it === caret.row }
        val ordered = rows.drop(here + 1) + rows.take(here + 1)
        val target = ordered.firstOrNull { it.isEmpty && it !== caret.row } ?: return false
        caret = Caret(target, 0)
        changed()
        return true
    }

    // ---------------------------------------------------------------- insertion

    private fun insertNode(node: Node) {
        val (row, i) = caret
        row.items.add(i, node)
        caret = Caret(row, i + 1)
    }

    private fun insertAndEnter(node: Node, enter: MathRow) {
        val (row, i) = caret
        row.items.add(i, node)
        caret = Caret(enter, 0)
    }

    fun digit(d: Char) { insertNode(Sym.digit(d)); changed() }
    fun point() { insertNode(Sym.point()); changed() }
    fun operator(op: String) { insertNode(Sym.op(op)); changed() }
    fun variable(name: String) { insertNode(Sym.variable(name)); changed() }
    fun constant(c: String) { insertNode(Sym.constant(c)); changed() }
    fun postfix(p: String) { insertNode(Sym(SymKind.POSTFIX, p)); changed() }

    /** The "=[]" key: writes an equality, turning the line into an equation. */
    fun equalsSign() { insertNode(Sym.equals()); changed() }

    fun openBracket() {
        val g = Group()
        insertAndEnter(g, g.content)
        changed()
    }

    /**
     * Closing bracket: leaves the innermost open bracket; with none open, wraps
     * everything typed before the caret in brackets.
     */
    fun closeBracket() {
        val enclosing = enclosingNodes(caret.row).firstOrNull { it.first is Group }
        if (enclosing != null) {
            val (node, owner) = enclosing
            caret = Caret(owner, indexIn(owner, node) + 1)
        } else {
            val (row, i) = caret
            if (i == 0) return
            val moved = row.items.subList(0, i).toMutableList()
            repeat(i) { row.items.removeAt(0) }
            row.items.add(0, Group(MathRow(moved)))
            caret = Caret(row, 1)
        }
        changed()
    }

    private fun ensureBase() {
        if (caret.index == 0) insertNode(Slot())
    }

    /** ^ short press: square. */
    fun square() {
        ensureBase()
        insertNode(Power(MathRow(mutableListOf(Sym.digit('2')))))
        changed()
    }

    /**
     * ^ long press: enters power mode (caret in an empty exponent); when already
     * inside an exponent, leaves it back to the normal level.
     */
    fun togglePower() {
        val enclosing = enclosingNodes(caret.row).firstOrNull { it.first is Power }
        if (enclosing != null) {
            val (node, owner) = enclosing
            caret = Caret(owner, indexIn(owner, node) + 1)
        } else {
            ensureBase()
            val p = Power()
            insertAndEnter(p, p.exponent)
        }
        changed()
    }

    /** Fraction: takes the operand just before the caret as numerator. */
    fun fraction() {
        val (row, i) = caret
        val start = operandStart(row, i)
        if (start < i) {
            val num = row.items.subList(start, i).toMutableList()
            repeat(i - start) { row.items.removeAt(start) }
            val f = Fraction(MathRow(num))
            row.items.add(start, f)
            caret = Caret(f.denominator, 0)
        } else {
            val f = Fraction()
            insertAndEnter(f, f.numerator)
        }
        changed()
    }

    /** Index where the operand ending at [end] starts (digits, letters, brackets, powers). */
    private fun operandStart(row: MathRow, end: Int): Int {
        var s = end
        while (s > 0) {
            val n = row.items[s - 1]
            val operandPart = when (n) {
                is Sym -> n.kind == SymKind.DIGIT || n.kind == SymKind.POINT || n.kind == SymKind.VARIABLE ||
                    n.kind == SymKind.CONSTANT || n.kind == SymKind.POSTFIX
                is Group, is Power, is Slot, is FuncCall, is Root -> true
                else -> false
            }
            if (!operandPart) break
            s--
        }
        return s
    }

    fun sqrt() { val r = Root(); insertAndEnter(r, r.radicand); changed() }

    fun nthRoot() { val r = Root(index = MathRow()); insertAndEnter(r, r.index!!); changed() }

    fun function(fn: Fn) { val f = FuncCall(fn); insertAndEnter(f, f.argument); changed() }

    fun integral() {
        val n = Integral()
        insertAndEnter(n, n.lower)
        changed()
    }

    fun derivative() {
        val n = Derivative()
        insertAndEnter(n, n.body)
        changed()
    }

    fun matrix(rows: Int, cols: Int) {
        val m = MatrixNode.empty(rows, cols)
        insertAndEnter(m, m.cells[0])
        changed()
    }

    /** Adds a row below the caret's row of the enclosing matrix. */
    fun addMatrixRow() {
        val (m, cell) = enclosingMatrix() ?: return
        val r = m.cells.indexOfFirst { it === cell } / m.colCount
        val at = (r + 1) * m.colCount
        repeat(m.colCount) { m.cells.add(at, MathRow()) }
        m.rowCount++
        caret = Caret(m.cells[at], 0)
        changed()
    }

    /** Adds a column right of the caret's column of the enclosing matrix. */
    fun addMatrixColumn() {
        val (m, cell) = enclosingMatrix() ?: return
        val c = m.cells.indexOfFirst { it === cell } % m.colCount
        val newCols = m.colCount + 1
        val rebuilt = MutableList(m.rowCount * newCols) { i ->
            val rr = i / newCols
            val cc = i % newCols
            when {
                cc <= c -> m.cells[rr * m.colCount + cc]
                cc == c + 1 -> MathRow()
                else -> m.cells[rr * m.colCount + cc - 1]
            }
        }
        m.cells.clear(); m.cells.addAll(rebuilt)
        m.colCount = newCols
        caret = Caret(m.cells[c + 1], 0)
        changed()
    }

    fun removeMatrixRow() {
        val (m, cell) = enclosingMatrix() ?: return
        if (m.rowCount <= 1) return
        val r = m.cells.indexOfFirst { it === cell } / m.colCount
        repeat(m.colCount) { m.cells.removeAt(r * m.colCount) }
        m.rowCount--
        val target = m.cells[(minOf(r, m.rowCount - 1)) * m.colCount]
        caret = Caret(target, target.items.size)
        changed()
    }

    fun removeMatrixColumn() {
        val (m, cell) = enclosingMatrix() ?: return
        if (m.colCount <= 1) return
        val c = m.cells.indexOfFirst { it === cell } % m.colCount
        for (rr in m.rowCount - 1 downTo 0) m.cells.removeAt(rr * m.colCount + c)
        m.colCount--
        val target = m.cells[minOf(c, m.colCount - 1)]
        caret = Caret(target, target.items.size)
        changed()
    }

    fun emptyCase() { val s = Slot(); insertAndEnter(s, s.content); changed() }

    // ---------------------------------------------------------------- deletion

    fun backspace() {
        val (row, i) = caret
        if (i > 0) {
            val prev = row.items[i - 1]
            if (prev.rows.isEmpty() || prev.rows.all { it.isEmpty }) {
                row.items.removeAt(i - 1)
                caret = Caret(row, i - 1)
            } else if (prev is Group || prev is Slot) {
                // Unwrap brackets/cases, keeping their content.
                val content = prev.rows.first().items
                row.items.removeAt(i - 1)
                row.items.addAll(i - 1, content)
                caret = Caret(row, i - 1 + content.size)
            } else {
                val last = prev.rows.last()
                caret = Caret(last, last.items.size)
            }
        } else {
            val p = parentOf(row)
            if (p != null) {
                val idx = p.node.rows.indexOfFirst { it === row }
                val nodeIndex = indexIn(p.owner, p.node)
                if (p.node.rows.all { it.isEmpty }) {
                    p.owner.items.removeAt(nodeIndex)
                    caret = Caret(p.owner, nodeIndex)
                } else if (idx > 0) {
                    val r = p.node.rows[idx - 1]
                    caret = Caret(r, r.items.size)
                } else if (p.node is Group || p.node is Slot) {
                    val content = p.node.rows.first().items.toList()
                    p.owner.items.removeAt(nodeIndex)
                    p.owner.items.addAll(nodeIndex, content)
                    caret = Caret(p.owner, nodeIndex)
                } else {
                    caret = Caret(p.owner, nodeIndex)
                }
            } else {
                val li = lines.indexOfFirst { it === row }
                if (li > 0 && row.isEmpty) {
                    lines.removeAt(li)
                    caret = Caret(lines[li - 1], lines[li - 1].items.size)
                }
            }
        }
        changed()
    }

    fun clear() {
        lines.clear()
        lines += MathRow()
        caret = Caret(lines[0], 0)
        changed()
    }

    // ---------------------------------------------------------------- lines / equations

    /**
     * "Add another equation": the current equation moves up and a new line
     * with the template [] = [] is started.
     */
    fun addEquation() {
        val left = Slot()
        val line = MathRow(mutableListOf(left, Sym.equals(), Slot()))
        lines += line
        caret = Caret(left.content, 0)
        changed()
    }

    /** Adds an empty line (e.g. another function to plot on the same graph). */
    fun addLine() {
        val line = MathRow()
        lines += line
        caret = Caret(line, 0)
        changed()
    }

    /** Replaces everything with [newLines] (formula insertion, history...). */
    fun load(newLines: List<MathRow>) {
        lines.clear()
        lines.addAll(newLines.ifEmpty { listOf(MathRow()) })
        val firstEmpty = lines.flatMap { it.allRows().filter { r -> r.isEmpty }.toList() }.firstOrNull()
        caret = if (firstEmpty != null) Caret(firstEmpty, 0) else Caret(lines.last(), lines.last().items.size)
        changed()
    }

    /** Inserts plain text produced by e.g. a previous result ("-1.5E-3"). */
    fun insertNumber(text: String) {
        for (c in text) when {
            c.isDigit() -> insertNode(Sym.digit(c))
            c == '.' -> insertNode(Sym.point())
            c == '-' || c == '−' -> insertNode(Sym.op(Sym.MINUS))
            c == 'E' -> {
                insertNode(Sym.op(Sym.TIMES)); insertNode(Sym.digit('1')); insertNode(Sym.digit('0'))
                val p = Power(); insertAndEnter(p, p.exponent)
            }
        }
        if (text.contains('E')) exitForward()
        changed()
    }

    /** Integrals nested directly as the only content of the body form a multiple integral. */
    fun outermostIntegral(): Integral? {
        val line = activeLine
        return line.items.singleOrNull() as? Integral
            ?: line.items.filterIsInstance<Integral>().singleOrNull()
    }

    /**
     * Rotates the order of integration of the multiple integral on the active line
     * (any order for constant limits; a double integral with variable inner limits is split into
     * pieces when needed; returns false when the region is not handled).
     */
    fun reorderIntegration(angle: AngleUnit = AngleUnit.RAD): Boolean {
        val outer = outermostIntegral() ?: return false
        val chain = integralChain(outer)
        if (chain.size == 2 && !Analysis.limitsAreConstant(chain)) {
            val nodes = IntegralOrder.reorder(chain[0], chain[1], angle) ?: return false
            val line = activeLine
            val at = line.items.indexOf(outer)
            if (at < 0) return false
            line.items.removeAt(at)
            line.items.addAll(at, nodes)
            caret = Caret(line, line.items.size)
            changed()
            return true
        }
        if (chain.size < 2 || !Analysis.limitsAreConstant(chain)) return false
        val limits = chain.map { Triple(it.lower.deepCopy(), it.upper.deepCopy(), it.variable.deepCopy()) }
        val rotated = limits.drop(1) + limits.first()
        chain.forEachIndexed { k, integral ->
            val (lo, hi, v) = rotated[k]
            integral.lower.items.apply { clear(); addAll(lo.items) }
            integral.upper.items.apply { clear(); addAll(hi.items) }
            integral.variable.items.apply { clear(); addAll(v.items) }
        }
        caret = Caret(activeLine, activeLine.items.size)
        changed()
        return true
    }

    companion object {
        fun integralChain(outer: Integral): List<Integral> {
            val out = mutableListOf(outer)
            var cur = outer
            while (true) {
                val inner = cur.body.items.singleOrNull() as? Integral ?: break
                out += inner
                cur = inner
            }
            return out
        }
    }
}

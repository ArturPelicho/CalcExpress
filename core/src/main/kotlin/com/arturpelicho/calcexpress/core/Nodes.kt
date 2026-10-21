package com.arturpelicho.calcexpress.core

/**
 * Structured expression model used by the calculator's editor.
 *
 * The display is a tree: a [MathRow] holds a sequence of [Node]s, and structural
 * nodes (fractions, powers, integrals...) own child rows. Every empty child row
 * is rendered as a "case" (a box to fill in), which is what makes templates such
 * as fractions, integrals or formulas fast to complete.
 */
class MathRow(val items: MutableList<Node> = mutableListOf()) {
    val isEmpty: Boolean get() = items.isEmpty()

    fun deepCopy(): MathRow = MathRow(items.mapTo(mutableListOf()) { it.deepCopy() })

    override fun toString(): String = items.joinToString("") { it.toString() }
}

sealed class Node {
    /** Child rows in editing order (used for cursor navigation). */
    open val rows: List<MathRow> get() = emptyList()

    abstract fun deepCopy(): Node
}

enum class SymKind { DIGIT, POINT, OPERATOR, VARIABLE, CONSTANT, EQUALS, POSTFIX }

/** A single character: digit, decimal point, operator, variable letter, constant, '='... */
class Sym(val kind: SymKind, val text: String) : Node() {
    override fun deepCopy() = Sym(kind, text)
    override fun toString() = text

    companion object {
        const val PLUS = "+"
        const val MINUS = "−"
        const val TIMES = "×"
        const val DIVIDE = "÷"
        const val DOT = "·"
        const val PI = "π"
        const val E = "e"
        const val FACTORIAL = "!"
        const val PERCENT = "%"

        fun digit(d: Char) = Sym(SymKind.DIGIT, d.toString())
        fun op(o: String) = Sym(SymKind.OPERATOR, o)
        fun variable(v: String) = Sym(SymKind.VARIABLE, v)
        fun constant(c: String) = Sym(SymKind.CONSTANT, c)
        fun equals() = Sym(SymKind.EQUALS, "=")
        fun point() = Sym(SymKind.POINT, ".")
    }
}

/** Brackets. The bracket style ( [ { is derived from the nesting depth when rendering. */
class Group(val content: MathRow = MathRow()) : Node() {
    override val rows get() = listOf(content)
    override fun deepCopy() = Group(content.deepCopy())
    override fun toString() = "($content)"
}

/** Superscript applied to the node immediately before it. */
class Power(val exponent: MathRow = MathRow()) : Node() {
    override val rows get() = listOf(exponent)
    override fun deepCopy() = Power(exponent.deepCopy())
    override fun toString() = "^($exponent)"
}

class Fraction(val numerator: MathRow = MathRow(), val denominator: MathRow = MathRow()) : Node() {
    override val rows get() = listOf(numerator, denominator)
    override fun deepCopy() = Fraction(numerator.deepCopy(), denominator.deepCopy())
    override fun toString() = "frac($numerator,$denominator)"
}

/** Square root, or n-th root when [index] is present. */
class Root(val radicand: MathRow = MathRow(), val index: MathRow? = null) : Node() {
    override val rows get() = if (index != null) listOf(index, radicand) else listOf(radicand)
    override fun deepCopy() = Root(radicand.deepCopy(), index?.deepCopy())
    override fun toString() = if (index != null) "root($index,$radicand)" else "sqrt($radicand)"
}

enum class Fn(val label: String) {
    SIN("sin"), COS("cos"), TAN("tan"),
    ASIN("sin⁻¹"), ACOS("cos⁻¹"), ATAN("tan⁻¹"),
    SINH("sinh"), COSH("cosh"), TANH("tanh"),
    LN("ln"), LOG("log"), EXP("exp"), ABS("abs");

    val isTrig get() = this == SIN || this == COS || this == TAN
    val isInverseTrig get() = this == ASIN || this == ACOS || this == ATAN
}

class FuncCall(val fn: Fn, val argument: MathRow = MathRow()) : Node() {
    override val rows get() = listOf(argument)
    override fun deepCopy() = FuncCall(fn, argument.deepCopy())
    override fun toString() = "${fn.name.lowercase()}($argument)"
}

/** Definite integral: ∫_lower^upper body d(variable). */
class Integral(
    val lower: MathRow = MathRow(),
    val upper: MathRow = MathRow(),
    val body: MathRow = MathRow(),
    val variable: MathRow = MathRow(mutableListOf(Sym.variable("x"))),
) : Node() {
    override val rows get() = listOf(lower, upper, body, variable)
    override fun deepCopy() = Integral(lower.deepCopy(), upper.deepCopy(), body.deepCopy(), variable.deepCopy())
    override fun toString() = "int($lower,$upper,$body,d$variable)"
}

/** Derivative d/d(variable) of body, evaluated at the current value of the variable. */
class Derivative(
    val variable: MathRow = MathRow(mutableListOf(Sym.variable("x"))),
    val body: MathRow = MathRow(),
) : Node() {
    override val rows get() = listOf(variable, body)
    override fun deepCopy() = Derivative(variable.deepCopy(), body.deepCopy())
    override fun toString() = "d/d$variable($body)"
}

/**
 * A "case": a box to fill in. When [label] is set (formulas) an empty slot acts as the
 * unknown named [label]; a filled slot evaluates like brackets around its content.
 */
class Slot(val label: String? = null, val content: MathRow = MathRow()) : Node() {
    override val rows get() = listOf(content)
    override fun deepCopy() = Slot(label, content.deepCopy())
    override fun toString() = if (content.isEmpty) "[${label ?: ""}]" else "[$content]"
}

/** Walks every row in the tree (depth first, including [root]). */
fun MathRow.allRows(): Sequence<MathRow> = sequence {
    yield(this@allRows)
    for (n in items) for (r in n.rows) yieldAll(r.allRows())
}

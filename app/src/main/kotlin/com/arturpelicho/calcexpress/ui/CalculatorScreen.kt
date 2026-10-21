package com.arturpelicho.calcexpress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arturpelicho.calcexpress.AppSettings
import com.arturpelicho.calcexpress.R
import com.arturpelicho.calcexpress.core.Affine
import com.arturpelicho.calcexpress.core.Analysis
import com.arturpelicho.calcexpress.core.AngleUnit
import com.arturpelicho.calcexpress.core.CalcException
import com.arturpelicho.calcexpress.core.Editor
import com.arturpelicho.calcexpress.core.Fn
import com.arturpelicho.calcexpress.core.Format
import com.arturpelicho.calcexpress.core.GraphSpec
import com.arturpelicho.calcexpress.core.Integral
import com.arturpelicho.calcexpress.core.MathRow
import com.arturpelicho.calcexpress.core.MatrixNode
import com.arturpelicho.calcexpress.core.Outcome
import com.arturpelicho.calcexpress.core.Sym
import com.arturpelicho.calcexpress.core.SymKind
import com.arturpelicho.calcexpress.core.Value
import com.arturpelicho.calcexpress.core.allRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryEntry(val lines: List<MathRow>, val outcome: Outcome)

/** State of the calculator screen; survives navigation between areas. */
class CalcState : KeyboardActions {
    val editor = Editor()
    var version by mutableLongStateOf(0L)
        private set
    var outcome by mutableStateOf<Outcome?>(null)
        private set
    /** Live preview shown while typing simple expressions. */
    var preview by mutableStateOf<Outcome?>(null)
        private set
    var computing by mutableStateOf(false)
        private set
    var showGraph by mutableStateOf(false)
    var graph by mutableStateOf<GraphSpec?>(null)
        private set
    var answer by mutableStateOf<Double?>(null)
        private set
    var message by mutableStateOf<Int?>(null)
    val history = mutableStateListOf<HistoryEntry>()

    var angle: AngleUnit = AngleUnit.DEG
    var scope: CoroutineScope? = null
    private var job: Job? = null

    private fun changed(clearOutcome: Boolean = true) {
        version = editor.version
        if (clearOutcome) outcome = null
        message = null
        updatePreview()
    }

    private fun hasStructure(lines: List<MathRow>, pred: (Any) -> Boolean) =
        lines.any { l -> l.allRows().any { r -> r.items.any(pred) } }

    /** True when the content has an equation (its graph needs root finding, done on demand). */
    var hasEquation by mutableStateOf(false)
        private set

    private fun updatePreview() {
        val lines = editor.lines
        hasEquation = hasStructure(lines) { it is Sym && it.kind == SymKind.EQUALS }
        val heavy = hasStructure(lines) { it is Integral }
        preview = if (hasEquation || heavy || editor.isEmpty) null
        else Analysis.evaluate(lines, angle).takeIf { it is Outcome.Value }
        graph = if (hasEquation) null else Analysis.graphFor(lines, angle)
        if (showGraph && hasEquation) computeGraph()
    }

    fun refreshAngle(a: AngleUnit) {
        angle = a
        changed()
        outcome = null
    }

    fun load(lines: List<MathRow>) {
        editor.load(lines.map { it.deepCopy() })
        showGraph = false
        changed()
    }

    private fun edit(block: Editor.() -> Unit) {
        editor.block()
        changed()
    }

    override fun digit(d: Char) = edit { digit(d) }
    override fun point() = edit { point() }
    override fun op(o: String) = edit { operator(o) }
    override fun letter(l: String) = edit { variable(l) }
    override fun constant(c: String) = edit { constant(c) }
    override fun postfix(p: String) = edit { postfix(p) }
    override fun open() = edit { openBracket() }
    override fun close() = edit { closeBracket() }
    override fun square() = edit { square() }
    override fun togglePower() = edit { togglePower() }
    override fun fraction() = edit { fraction() }
    override fun sqrt() = edit { sqrt() }
    override fun nthRoot() = edit { nthRoot() }
    override fun function(fn: Fn) = edit { function(fn) }
    override fun matrix(rows: Int, cols: Int) = edit { matrix(rows, cols) }
    fun addMatrixRow() = edit { this.addMatrixRow() }
    fun addMatrixColumn() = edit { this.addMatrixColumn() }
    fun removeMatrixRow() = edit { this.removeMatrixRow() }
    fun removeMatrixColumn() = edit { this.removeMatrixColumn() }
    override fun integral() = edit { integral() }
    override fun derivative() = edit { derivative() }
    override fun backspace() = edit { backspace() }
    override fun clear() { edit { clear() }; showGraph = false }
    override fun equation() = edit { equalsSign() }
    override fun addEquation() = edit { addEquation() }
    override fun answer() { answer?.let { a -> edit { insertNumber(Format.plain(a)) } } }

    fun left() = edit { moveLeft() }
    fun right() = edit { moveRight() }
    fun nextCase() = edit { nextEmptyCase() }

    fun reorder() {
        if (editor.reorderIntegration(angle)) changed() else message = R.string.reorder_not_possible
    }

    val canReorder: Boolean
        get() = editor.outermostIntegral()?.let { Editor.integralChain(it).size >= 2 } ?: false

    fun toggleGraph() {
        showGraph = !showGraph
        if (showGraph && graph == null) computeGraph()
    }

    private fun computeGraph() {
        val lines = editor.lines.map { it.deepCopy() }
        val a = angle
        scope?.launch {
            graph = withContext(Dispatchers.Default) { Analysis.graphFor(lines, a) }
        }
    }

    override fun evaluate() {
        if (editor.isEmpty) return
        val lines = editor.lines.map { it.deepCopy() }
        val a = angle
        job?.cancel()
        computing = true
        job = scope?.launch {
            val (o, g) = withContext(Dispatchers.Default) {
                Analysis.evaluate(lines, a) to Analysis.graphFor(lines, a)
            }
            computing = false
            outcome = o
            graph = g
            when (o) {
                is Outcome.Value -> answer = o.value
                is Outcome.Solutions -> o.values.lastOrNull()?.second?.firstOrNull()?.let { answer = it }
                else -> {}
            }
            if (o !is Outcome.Error) {
                history.add(0, HistoryEntry(lines, o))
                if (history.size > 50) history.removeAt(history.size - 1)
            }
            if ((o is Outcome.Function || o is Outcome.NumericOde) && g != null) showGraph = true
        }
    }
}

/** Result as text: "= v" for a plain value, one "name = v" line per unknown otherwise. */
@Composable
fun outcomeText(o: Outcome): String = when (o) {
    is Outcome.Value -> "= " + Format.number(o.value)
    is Outcome.MatrixValue -> "= " + (0 until o.matrix.rows).joinToString("; ", "[", "]") { r ->
        (0 until o.matrix.cols).joinToString("  ") { col -> Format.number(o.matrix.data[r * o.matrix.cols + col]) }
    }
    is Outcome.Symbolic -> o.rows.joinToString("\n") { it.toString() }
    is Outcome.NumericOde -> stringResource(R.string.result_numeric_ode, o.y, o.x)
    is Outcome.Truth -> stringResource(if (o.holds) R.string.result_true else R.string.result_false)
    is Outcome.Function -> stringResource(R.string.result_function, o.variable)
    is Outcome.Solutions -> o.values.joinToString("\n") { (name, vals) ->
        if (vals.size == 1) "$name = ${Format.number(vals[0])}"
        else {
            // Equal roots share one line: x₁ = x₂ = 3.
            val indexed = vals.mapIndexed { i, v -> i + 1 to Format.number(v) }
            indexed.groupBy({ it.second }, { it.first }).entries.joinToString("\n") { (text, idx) ->
                idx.joinToString(" = ") { "$name${subscript(it)}" } + " = " + text
            }
        }
    }
    is Outcome.Parametric -> o.values.joinToString("\n") { (name, form) -> "$name = ${affineText(form)}" }
    is Outcome.Error -> when (o.reason) {
        CalcException.Reason.SYNTAX -> stringResource(R.string.err_syntax)
        CalcException.Reason.EMPTY_CASE -> stringResource(R.string.err_empty_case)
        CalcException.Reason.DOMAIN -> stringResource(R.string.err_domain)
        CalcException.Reason.UNKNOWN_VARIABLE -> stringResource(R.string.err_unknown, o.detail)
        CalcException.Reason.NO_SOLUTION -> stringResource(R.string.err_no_solution)
        CalcException.Reason.UNDERDETERMINED -> stringResource(R.string.err_underdetermined, o.detail)
        CalcException.Reason.NOT_EQUATION -> stringResource(R.string.err_not_equation)
        CalcException.Reason.INDEFINITE_INTEGRAL -> stringResource(R.string.err_indefinite)
        CalcException.Reason.MATRIX_DIMENSIONS -> stringResource(R.string.err_matrix_dims)
        CalcException.Reason.MATRIX_SINGULAR -> stringResource(R.string.err_matrix_singular)
        CalcException.Reason.MATRIX_NOT_ALLOWED -> stringResource(R.string.err_matrix_not_allowed)
        CalcException.Reason.NO_ANTIDERIVATIVE -> stringResource(R.string.err_no_antiderivative)
    }
}

/** 4 − 2y + 0.5z */
private fun affineText(f: Affine): String {
    val sb = StringBuilder()
    if (f.constant != 0.0 || f.terms.isEmpty()) sb.append(Format.number(f.constant))
    for ((name, k) in f.terms) {
        val mag = kotlin.math.abs(k)
        val coef = if (mag == 1.0) "" else Format.number(mag)
        if (sb.isEmpty()) sb.append(if (k < 0) "−" else "") else sb.append(if (k < 0) " − " else " + ")
        sb.append(coef).append(name)
    }
    return sb.toString()
}

/** A matrix result as a row "= [grid]" so it renders like the input. */
fun matrixRow(m: Value.Mat): MathRow = MathRow(
    mutableListOf(
        Sym(SymKind.EQUALS, "="),
        MatrixNode(m.rows, m.cols, MutableList(m.rows * m.cols) { MathRow(mutableListOf(Sym(SymKind.DIGIT, Format.number(m.data[it])))) }),
    ),
)

private fun subscript(n: Int) = n.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")

@Composable
fun CalculatorScreen(state: CalcState, settings: AppSettings, modifier: Modifier = Modifier) {
    val c = LocalCalcColors.current
    val scope = rememberCoroutineScope()
    state.scope = scope
    LaunchedEffect(settings.angle) { state.refreshAngle(settings.angle) }

    @Suppress("UNUSED_VARIABLE") val v = state.version // observe editor changes
    val caretAlpha = rememberCaretAlpha()
    val ctx = MathCtx(state.editor, state.version, c, LocalMathFont.current, caretAlpha)
    val graph = state.graph

    Column(modifier.fillMaxSize().background(c.background)) {
        // Calculation area: at least a third of the screen.
        Column(
            Modifier
                .weight(0.36f)
                .fillMaxWidth()
                .background(c.display)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (state.showGraph && graph != null) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    GraphView(graph, Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                    Column { state.editor.lines.forEach { MathRowView(it, ctx, 20f) } }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        state.editor.lines.forEach { line ->
                            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 3.dp)) {
                                MathRowView(line, ctx, 30f)
                            }
                        }
                    }
                }
            }
            ResultLine(state)
        }
        MainKeyboard(
            actions = state,
            greek = settings.greek,
            inPower = state.editor.inPower,
            hasAnswer = state.answer != null,
            toolbar = listOfNotNull(
                KeyDef(settings.angle.label, KeyKind.TOOL) { settings.updateAngle(settings.angle.next()) },
                KeyDef(if (settings.greek) "αβγ" else "abc", KeyKind.TOOL, italic = true) { settings.updateGreek(!settings.greek) },
                KeyDef("◀", KeyKind.TOOL) { state.left() },
                KeyDef("▶", KeyKind.TOOL) { state.right() },
                KeyDef("⇥□", KeyKind.TOOL) { state.nextCase() },
                KeyDef("📈", KeyKind.TOOL, enabled = graph != null || state.hasEquation, highlighted = state.showGraph, description = stringResource(R.string.graph)) { state.toggleGraph() },
                if (state.editor.inMatrix) KeyDef("+↓", KeyKind.TOOL, description = stringResource(R.string.matrix_row), onLongClick = { state.removeMatrixRow() }) { state.addMatrixRow() } else null,
                if (state.editor.inMatrix) KeyDef("+→", KeyKind.TOOL, description = stringResource(R.string.matrix_col), onLongClick = { state.removeMatrixColumn() }) { state.addMatrixColumn() } else null,
                if (state.canReorder) KeyDef("⇅∫", KeyKind.TOOL, description = stringResource(R.string.reorder)) { state.reorder() } else null,
            ),
            modifier = Modifier.weight(0.64f),
        )
    }
}

@Composable
private fun ResultLine(state: CalcState) {
    val c = LocalCalcColors.current
    val font = LocalMathFont.current
    Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.CenterEnd) {
        val o = state.outcome
        val msg = state.message
        when {
            state.computing -> CircularProgressIndicator(Modifier.padding(4.dp))
            msg != null -> Text(stringResource(msg), style = mathStyle(font, 16f, c.muted), textAlign = TextAlign.End)
            o is Outcome.MatrixValue -> StaticMath(listOf(matrixRow(o.matrix)), 24f, Modifier.horizontalScroll(rememberScrollState()))
            o is Outcome.Symbolic -> StaticMath(o.rows, 24f, Modifier.horizontalScroll(rememberScrollState()))
            o != null -> Text(
                outcomeText(o),
                style = mathStyle(font, if (o is Outcome.Error) 17f else 30f, if (o is Outcome.Error) c.error else c.accent),
                textAlign = TextAlign.End,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
            state.preview != null -> Text(
                outcomeText(state.preview!!),
                style = mathStyle(font, 20f, c.muted),
                textAlign = TextAlign.End,
            )
            else -> Text(" ", style = mathStyle(font, 20f, c.muted))
        }
    }
}

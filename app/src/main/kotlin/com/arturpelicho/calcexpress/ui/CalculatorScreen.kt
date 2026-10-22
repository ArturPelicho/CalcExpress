package com.arturpelicho.calcexpress.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
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
import com.arturpelicho.calcexpress.core.Mohr
import com.arturpelicho.calcexpress.core.Integral
import com.arturpelicho.calcexpress.core.MathCodec
import com.arturpelicho.calcexpress.core.MathRow
import com.arturpelicho.calcexpress.core.MatrixNode
import com.arturpelicho.calcexpress.core.Outcome
import com.arturpelicho.calcexpress.core.Sym
import com.arturpelicho.calcexpress.core.SymKind
import com.arturpelicho.calcexpress.core.Slot
import com.arturpelicho.calcexpress.core.UnitOption
import com.arturpelicho.calcexpress.core.UnitOptions
import com.arturpelicho.calcexpress.core.UnitSystem
import com.arturpelicho.calcexpress.core.Value
import com.arturpelicho.calcexpress.core.allRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

data class HistoryEntry(val lines: List<MathRow>, val outcome: Outcome, val angle: AngleUnit)

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

    /** History file I/O runs on one background thread, in order. */
    private val io = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private var historyFile: File? = null

    /** Loads the saved history (one "ANGLE<tab>encoded lines" per line) and re-evaluates it. */
    fun restoreHistory(file: File) {
        if (historyFile != null) return
        historyFile = file
        io.launch {
            val saved = try { if (file.exists()) file.readLines() else emptyList() } catch (e: IOException) { emptyList() }
            val entries = saved.take(MAX_HISTORY).mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab < 0) return@mapNotNull null
                val a = AngleUnit.entries.firstOrNull { it.name == line.substring(0, tab) } ?: return@mapNotNull null
                val lines = MathCodec.decode(line.substring(tab + 1)) ?: return@mapNotNull null
                val o = Analysis.evaluate(lines, a)
                if (o is Outcome.Error) null else HistoryEntry(lines, o, a)
            }
            withContext(Dispatchers.Main) {
                history.addAll(entries)
                while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
            }
        }
    }

    private fun saveHistory() {
        val file = historyFile ?: return
        val text = history.joinToString("\n") { "${it.angle.name}\t${MathCodec.encode(it.lines)}" }
        io.launch { try { file.writeText(text) } catch (e: IOException) { } }
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

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

    /** Units shown in the units bar; starts from the app's preference. */
    var unitSystem by mutableStateOf<UnitSystem?>(null)

    /** Types the box being filled (and its twins) in [u]. */
    fun setUnit(u: UnitOption) = edit { activeSlot?.let { setSlotUnit(it, u) } }

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
    override fun addLine() = edit { addLine() }
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
                history.add(0, HistoryEntry(lines, o, a))
                if (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
                saveHistory()
            }
            if ((o is Outcome.Function || o is Outcome.NumericOde) && g != null) showGraph = true
        }
    }
}

private const val MAX_HISTORY = 50
private val BAR_HEIGHT = 38.dp

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
    is Outcome.Solutions -> o.values.joinToString("\n") { (name, real) ->
        val vals = real.map { Format.number(it) } + if (o.values.size == 1) o.complex.map(::complexText) else emptyList()
        val unit = o.units[name]?.let { " $it" } ?: ""
        if (vals.size == 1) "$name = ${vals[0]}$unit"
        else {
            // Equal roots share one line: x₁ = x₂ = 3.
            val indexed = vals.mapIndexed { i, v -> i + 1 to v }
            indexed.groupBy({ it.second }, { it.first }).entries.joinToString("\n") { (text, idx) ->
                idx.joinToString(" = ") { "$name${subscript(it)}" } + " = " + text + unit
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

/** a + bi, a − bi or bi. */
private fun complexText(z: Pair<Double, Double>): String {
    val (re, im) = z
    val mag = kotlin.math.abs(im)
    val imPart = (if (mag == 1.0) "" else Format.number(mag)) + "i"
    return when {
        re == 0.0 -> (if (im < 0) "−" else "") + imPart
        else -> Format.number(re) + (if (im < 0) " − " else " + ") + imPart
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
                    if (graph is GraphSpec.MohrState) MohrOfMatrix(graph)
                    else GraphView(graph, Modifier.fillMaxSize())
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
        UnitBar(state, settings)
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
                if (state.editor.inMatrix) KeyDef("−↓", KeyKind.TOOL, description = stringResource(R.string.matrix_row_remove)) { state.removeMatrixRow() } else null,
                if (state.editor.inMatrix) KeyDef("−→", KeyKind.TOOL, description = stringResource(R.string.matrix_col_remove)) { state.removeMatrixColumn() } else null,
                if (state.canReorder) KeyDef("⇅∫", KeyKind.TOOL, description = stringResource(R.string.reorder)) { state.reorder() } else null,
            ),
            modifier = Modifier.weight(0.64f),
        )
    }
}

/** A symmetric matrix read as a stress state: its Mohr's circle (2D: drag to rotate the plane). */
@Composable
private fun MohrOfMatrix(g: GraphSpec.MohrState) {
    val t = g.tensor
    if (g.twoD) {
        var theta by remember(g) { mutableStateOf(0.0) }
        MohrCanvas2D(t.xx, t.yy, t.xy, theta, "σ", "τ", false, "x" to "y", Modifier.fillMaxSize()) { theta = it }
    } else {
        MohrCanvas3D(Mohr.principal3D(t), null, "σ", false, Modifier.fillMaxSize())
    }
}

/**
 * Units of the formula box being filled: tap one to type the value in it. The formula's own unit
 * is outlined when nothing else is chosen; one chip switches between metric and imperial units.
 */
@Composable
private fun UnitBar(state: CalcState, settings: AppSettings) {
    @Suppress("UNUSED_VARIABLE") val v = state.version
    val c = LocalCalcColors.current
    // While a formula with units is open the bar keeps its place, so the keyboard does not jump.
    val hasUnits = state.editor.lines.any { l ->
        l.allRows().any { r -> r.items.any { it is Slot && it.baseUnit?.let { b -> UnitOptions.forUnit(b).size > 1 } == true } }
    }
    if (!hasUnits) return
    val slot = state.editor.activeSlot
    val base = slot?.baseUnit
    val options = remember(base) { base?.let { UnitOptions.forUnit(it) } ?: emptyList() }
    if (slot == null || options.size < 2) {
        Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).background(c.display))
        return
    }
    val system = state.unitSystem ?: settings.units
    val chosen = slot.unit?.symbol ?: base
    val shown = options.filter { it.system == null || it.system == system || it.symbol == chosen || it.symbol == base }
    val listState = rememberLazyListState()
    LaunchedEffect(base, system) {
        val i = shown.indexOfFirst { it.symbol == chosen }
        if (i > 0) listState.scrollToItem(maxOf(0, i - 2))
    }
    Row(
        Modifier.fillMaxWidth().height(BAR_HEIGHT).background(c.display).padding(start = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val imperial = system == UnitSystem.IMPERIAL
        UnitChip(
            stringResource(if (imperial) R.string.units_imperial else R.string.units_metric),
            null, selected = false, strong = true,
        ) { state.unitSystem = if (imperial) UnitSystem.METRIC else UnitSystem.IMPERIAL }
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(c.muted.copy(alpha = 0.35f)))
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(shown, key = { it.symbol }) { u ->
                UnitChip(u.symbol, UnitOptions.powerHint(u.factor), selected = u.symbol == chosen) { state.setUnit(u) }
            }
        }
    }
}

@Composable
private fun UnitChip(text: String, hint: String?, selected: Boolean, strong: Boolean = false, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    val font = LocalMathFont.current
    val shape = RoundedCornerShape(50)
    val label = buildAnnotatedString {
        append(mathLabel(text))
        if (hint != null) withStyle(SpanStyle(fontSize = 0.6.em, baselineShift = BaselineShift(0.6f), color = c.muted)) { append(mathLabel(hint)) }
    }
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.14f) else if (strong) c.muted.copy(alpha = 0.12f) else c.background)
            .border(if (selected) 1.6.dp else 0.8.dp, if (selected) c.accent else c.muted.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = mathStyle(font, if (strong) 13f else 16f, if (selected) c.accent else c.text))
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
            o is Outcome.Value && o.exact != null -> Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                StaticMath(listOfNotNull(o.exact), 26f)
                Text("  ≈ " + Format.number(o.value), style = mathStyle(font, 22f, c.accent))
            }
            o != null -> Text(
                mathLabel(outcomeText(o)),
                style = mathStyle(font, if (o is Outcome.Error) 17f else 30f, if (o is Outcome.Error) c.error else c.accent),
                textAlign = TextAlign.End,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
            state.preview != null -> Text(
                mathLabel(outcomeText(state.preview!!)),
                style = mathStyle(font, 20f, c.muted),
                textAlign = TextAlign.End,
            )
            else -> Text(" ", style = mathStyle(font, 20f, c.muted))
        }
    }
}

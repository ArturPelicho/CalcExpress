package com.arturpelicho.calcexpress.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arturpelicho.calcexpress.core.Fn
import com.arturpelicho.calcexpress.core.Sym

enum class KeyKind { NUMBER, OPERATOR, FUNCTION, ACTION, EQUALS, LETTER, TOOL }

class KeyDef(
    val label: String,
    val kind: KeyKind,
    val hint: String? = null,
    val italic: Boolean = false,
    val enabled: Boolean = true,
    val highlighted: Boolean = false,
    val description: String? = null,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.Key(key: KeyDef, textSize: Float, weight: Float = 1f) {
    val c = LocalCalcColors.current
    val haptic = LocalHapticFeedback.current
    val bg = when (key.kind) {
        KeyKind.NUMBER -> c.keyNumber
        KeyKind.OPERATOR -> c.keyOperator
        KeyKind.FUNCTION, KeyKind.LETTER -> c.keyFunction
        KeyKind.ACTION, KeyKind.TOOL -> c.keyAction
        KeyKind.EQUALS -> c.keyEquals
    }
    val fg = if (key.kind == KeyKind.EQUALS) c.onKeyEquals else c.text
    val shape = RoundedCornerShape(if (key.kind == KeyKind.LETTER) 6.dp else 10.dp)
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(if (key.kind == KeyKind.LETTER) 1.5.dp else 2.5.dp)
            .clip(shape)
            .background(bg)
            .then(if (key.highlighted) Modifier.border(2.dp, c.highlight, shape) else Modifier)
            .alpha(if (key.enabled) 1f else 0.35f)
            .combinedClickable(
                enabled = key.enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    key.onClick()
                },
                onLongClick = key.onLongClick?.let { lc ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lc()
                    }
                },
            )
            .semantics { key.description?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key.label,
            style = mathStyle(LocalMathFont.current, textSize, fg, key.italic),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (key.hint != null) {
            Text(
                key.hint,
                style = mathStyle(LocalMathFont.current, textSize * 0.45f, if (key.kind == KeyKind.EQUALS) fg else c.muted),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}

@Composable
fun KeyRow(keys: List<KeyDef>, textSize: Float, modifier: Modifier = Modifier, weights: List<Float>? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        keys.forEachIndexed { i, k -> Key(k, textSize, weights?.getOrNull(i) ?: 1f) }
    }
}

val LATIN = "abcdefghijklmnopqrstuvwxyz"
val GREEK = "αβγδεζηθικλμνξοπρστυφχψω"

/** Actions the keyboard triggers; implemented by the calculator state. */
interface KeyboardActions {
    fun digit(d: Char)
    fun point()
    fun op(o: String)
    fun letter(l: String)
    fun constant(c: String)
    fun postfix(p: String)
    fun open()
    fun close()
    fun square()
    fun togglePower()
    fun fraction()
    fun sqrt()
    fun nthRoot()
    fun function(fn: Fn)
    fun matrix(rows: Int, cols: Int)
    fun integral()
    fun derivative()
    fun backspace()
    fun clear()
    fun equation()
    fun addEquation()
    fun addLine()
    fun evaluate()
    fun answer()
}

@Composable
fun MainKeyboard(
    actions: KeyboardActions,
    greek: Boolean,
    inPower: Boolean,
    hasAnswer: Boolean,
    toolbar: List<KeyDef>,
    modifier: Modifier = Modifier,
) {
    val letters = if (greek) GREEK else LATIN
    val half = (letters.length + 1) / 2
    val letterKey = { ch: Char ->
        val s = ch.toString()
        KeyDef(
            s, KeyKind.LETTER, italic = s != Sym.PI,
            onLongClick = { actions.letter(s.uppercase()) },
        ) { if (s == Sym.PI) actions.constant(Sym.PI) else actions.letter(s) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)) {
        KeyRow(toolbar, 13f, Modifier.weight(0.8f))
        KeyRow(letters.substring(0, half).map(letterKey), 17f, Modifier.weight(0.82f))
        KeyRow(letters.substring(half).map(letterKey), 17f, Modifier.weight(0.82f))
        KeyRow(
            listOf(
                KeyDef("sin", KeyKind.FUNCTION, hint = "sin⁻¹", onLongClick = { actions.function(Fn.ASIN) }) { actions.function(Fn.SIN) },
                KeyDef("cos", KeyKind.FUNCTION, hint = "cos⁻¹", onLongClick = { actions.function(Fn.ACOS) }) { actions.function(Fn.COS) },
                KeyDef("tan", KeyKind.FUNCTION, hint = "tan⁻¹", onLongClick = { actions.function(Fn.ATAN) }) { actions.function(Fn.TAN) },
                KeyDef("ln", KeyKind.FUNCTION, hint = "eˣ", onLongClick = { actions.function(Fn.EXP) }) { actions.function(Fn.LN) },
                KeyDef("log", KeyKind.FUNCTION, hint = "|x|", onLongClick = { actions.function(Fn.ABS) }) { actions.function(Fn.LOG) },
                KeyDef("√", KeyKind.FUNCTION, hint = "ⁿ√", onLongClick = { actions.nthRoot() }) { actions.sqrt() },
                KeyDef("[⋮⋮]", KeyKind.FUNCTION, hint = "3×3", onLongClick = { actions.matrix(3, 3) }) { actions.matrix(2, 2) },
            ),
            19f, Modifier.weight(1f),
        )
        KeyRow(
            listOf(
                KeyDef("d/dx", KeyKind.FUNCTION) { actions.derivative() },
                KeyDef("∫", KeyKind.FUNCTION) { actions.integral() },
                KeyDef("π", KeyKind.FUNCTION) { actions.constant(Sym.PI) },
                KeyDef("e", KeyKind.FUNCTION) { actions.constant(Sym.E) },
                KeyDef("□⁄□", KeyKind.FUNCTION) { actions.fraction() },
                KeyDef(
                    if (inPower) "x^□" else "x²", KeyKind.FUNCTION, hint = "^□", highlighted = inPower,
                    onLongClick = { actions.togglePower() },
                ) { actions.square() },
                KeyDef("det", KeyKind.FUNCTION, hint = "Aᵀ", onLongClick = { actions.function(Fn.TRANSPOSE) }) { actions.function(Fn.DET) },
            ),
            19f, Modifier.weight(1f),
        )
        KeyRow(
            listOf(
                KeyDef("7", KeyKind.NUMBER) { actions.digit('7') },
                KeyDef("8", KeyKind.NUMBER) { actions.digit('8') },
                KeyDef("9", KeyKind.NUMBER) { actions.digit('9') },
                KeyDef("(", KeyKind.OPERATOR) { actions.open() },
                KeyDef(")", KeyKind.OPERATOR) { actions.close() },
                KeyDef("⌫", KeyKind.ACTION, onLongClick = { actions.clear() }) { actions.backspace() },
            ),
            24f, Modifier.weight(1.15f),
        )
        KeyRow(
            listOf(
                KeyDef("4", KeyKind.NUMBER) { actions.digit('4') },
                KeyDef("5", KeyKind.NUMBER) { actions.digit('5') },
                KeyDef("6", KeyKind.NUMBER) { actions.digit('6') },
                KeyDef("×", KeyKind.OPERATOR) { actions.op(Sym.TIMES) },
                KeyDef("÷", KeyKind.OPERATOR) { actions.op(Sym.DIVIDE) },
                KeyDef("AC", KeyKind.ACTION) { actions.clear() },
            ),
            24f, Modifier.weight(1.15f),
        )
        KeyRow(
            listOf(
                KeyDef("1", KeyKind.NUMBER) { actions.digit('1') },
                KeyDef("2", KeyKind.NUMBER) { actions.digit('2') },
                KeyDef("3", KeyKind.NUMBER) { actions.digit('3') },
                KeyDef("+", KeyKind.OPERATOR) { actions.op(Sym.PLUS) },
                KeyDef("−", KeyKind.OPERATOR) { actions.op(Sym.MINUS) },
                KeyDef("=□", KeyKind.OPERATOR) { actions.equation() },
            ),
            24f, Modifier.weight(1.15f),
        )
        KeyRow(
            listOf(
                KeyDef("0", KeyKind.NUMBER) { actions.digit('0') },
                KeyDef(".", KeyKind.NUMBER) { actions.point() },
                KeyDef("!", KeyKind.OPERATOR, hint = "%", onLongClick = { actions.postfix(Sym.PERCENT) }) { actions.postfix(Sym.FACTORIAL) },
                KeyDef("Ans", KeyKind.ACTION, enabled = hasAnswer) { actions.answer() },
                KeyDef("⊕=", KeyKind.ACTION, hint = "⊕", onLongClick = { actions.addLine() }) { actions.addEquation() },
                KeyDef("=", KeyKind.EQUALS) { actions.evaluate() },
            ),
            24f, Modifier.weight(1.15f),
        )
    }
}

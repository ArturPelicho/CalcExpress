package com.arturpelicho.calcexpress.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arturpelicho.calcexpress.AppLanguage
import com.arturpelicho.calcexpress.AppSettings
import com.arturpelicho.calcexpress.BuildConfig
import com.arturpelicho.calcexpress.FontChoice
import com.arturpelicho.calcexpress.R
import com.arturpelicho.calcexpress.ThemeChoice
import com.arturpelicho.calcexpress.core.Format
import com.arturpelicho.calcexpress.core.Formula
import com.arturpelicho.calcexpress.core.FormulaArea
import com.arturpelicho.calcexpress.core.FormulaCategory
import com.arturpelicho.calcexpress.core.Formulas
import com.arturpelicho.calcexpress.core.MeasureUnit
import com.arturpelicho.calcexpress.core.Outcome
import com.arturpelicho.calcexpress.core.UnitCategory
import com.arturpelicho.calcexpress.core.UnitNames
import com.arturpelicho.calcexpress.core.UnitSystem
import com.arturpelicho.calcexpress.core.Units
import com.arturpelicho.calcexpress.update.UpdateStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalCalcColors.current
    Column(Modifier.fillMaxSize().background(c.background)) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
        )
        content()
    }
}

/** Left third: list of areas. Right two thirds: the sections of the selected area. */
@Composable
fun <T> SplitPage(areas: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, content: @Composable (T) -> Unit) {
    val c = LocalCalcColors.current
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            areas.forEach { (key, label) ->
                val active = key == selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) c.keyOperator else c.keyFunction)
                        .then(if (active) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable { onSelect(key) }
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.caseBorder.copy(alpha = 0.3f)))
        Box(Modifier.weight(2f).fillMaxHeight()) { content(selected) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun <T> RadioList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    options.forEach { (v, label) ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelect(v) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = v == selected, onClick = { onSelect(v) })
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ------------------------------------------------------------------------------------------ menu

enum class MenuArea { CALCULATOR, CONVERSIONS, FORMULAS }

@Composable
fun MenuPage(
    area: MenuArea,
    onArea: (MenuArea) -> Unit,
    calc: CalcState,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenCalculator: () -> Unit,
    onFormula: (Formula) -> Unit,
    onConversion: (UnitCategory) -> Unit,
    onMohr: () -> Unit,
) {
    PageScaffold(stringResource(R.string.menu), onBack) {
        SplitPage(
            listOf(
                MenuArea.CALCULATOR to stringResource(R.string.area_calculator),
                MenuArea.CONVERSIONS to stringResource(R.string.area_conversions),
                MenuArea.FORMULAS to stringResource(R.string.area_formulas),
            ),
            area, onArea,
        ) { a ->
            when (a) {
                MenuArea.CALCULATOR -> CalculatorSections(calc, onOpenCalculator)
                MenuArea.CONVERSIONS -> ConversionSections(onConversion)
                MenuArea.FORMULAS -> FormulaSections(settings, onFormula, onMohr)
            }
        }
    }
}

@Composable
private fun CalculatorSections(calc: CalcState, onOpen: () -> Unit) {
    val c = LocalCalcColors.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Button(onClick = onOpen, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.calc_open)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.calc_history)) }
                if (calc.history.isNotEmpty()) TextButton(onClick = { calc.clearHistory() }) { Text(stringResource(R.string.calc_history_clear)) }
            }
            if (calc.history.isEmpty()) Text(stringResource(R.string.calc_history_empty), color = c.muted)
        }
        items(calc.history) { h ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { calc.load(h.lines); onOpen() }.padding(vertical = 6.dp),
            ) {
                Box(Modifier.horizontalScroll(rememberScrollState())) { StaticMath(h.lines, 18f) }
                val o = h.outcome
                if (o is Outcome.MatrixValue) StaticMath(listOf(matrixRow(o.matrix)), 16f)
                else if (o is Outcome.Symbolic) Box(Modifier.horizontalScroll(rememberScrollState())) { StaticMath(o.rows, 16f) }
                else if (o is Outcome.Value && o.exact != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    StaticMath(listOfNotNull(o.exact), 16f)
                    Text("  ≈ " + Format.number(o.value), color = c.accent, style = MaterialTheme.typography.bodyLarge)
                }
                else Text(mathLabel(outcomeText(o)), color = c.accent, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = c.caseBorder.copy(alpha = 0.25f))
        }
        item {
            SectionTitle(stringResource(R.string.calc_tips_title))
            Text(stringResource(R.string.calc_tips), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
fun categoryName(cat: UnitCategory): String = stringResource(
    when (cat) {
        UnitCategory.LENGTH -> R.string.cat_length
        UnitCategory.AREA -> R.string.cat_area
        UnitCategory.VOLUME -> R.string.cat_volume
        UnitCategory.MASS -> R.string.cat_mass
        UnitCategory.TEMPERATURE -> R.string.cat_temperature
        UnitCategory.SPEED -> R.string.cat_speed
        UnitCategory.TIME -> R.string.cat_time
        UnitCategory.PRESSURE -> R.string.cat_pressure
        UnitCategory.ENERGY -> R.string.cat_energy
        UnitCategory.POWER -> R.string.cat_power
        UnitCategory.FORCE -> R.string.cat_force
        UnitCategory.ANGLE -> R.string.cat_angle
        UnitCategory.DATA -> R.string.cat_data
    },
)

@Composable
private fun ConversionSections(onConversion: (UnitCategory) -> Unit) {
    val c = LocalCalcColors.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(UnitCategory.entries) { cat ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.keyNumber).clickable { onConversion(cat) }.padding(12.dp),
            ) {
                Text(categoryName(cat), style = MaterialTheme.typography.titleMedium)
                Text(Units.all.getValue(cat).take(6).joinToString("  ") { it.symbol }, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FormulaSections(settings: AppSettings, onFormula: (Formula) -> Unit, onMohr: () -> Unit) {
    // Areas first; tapping one opens just its formulas, so the list stays short.
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openName?.let { n -> FormulaCategory.entries.firstOrNull { it.name == n } }
    if (open == null) FormulaAreas(settings.language.code, onMohr) { openName = it.name }
    else {
        BackHandler { openName = null }
        FormulaList(open, settings, onBack = { openName = null }, onFormula = onFormula)
    }
}

@Composable
private fun FormulaAreas(lang: String, onMohr: () -> Unit, onOpen: (FormulaCategory) -> Unit) {
    val c = LocalCalcColors.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item { Text(stringResource(R.string.formulas_hint), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
        FormulaCategory.entries.forEachIndexed { i, cat ->
            if (i == 0 || FormulaCategory.entries[i - 1].area != cat.area) item {
                Text(
                    cat.area.title.get(lang).uppercase(),
                    color = c.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = if (i == 0) 4.dp else 18.dp, bottom = 4.dp),
                )
            }
            if (i > 0 && cat.area == FormulaArea.ENGINEERING && FormulaCategory.entries[i - 1].area != cat.area) item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(c.keyOperator)
                        .border(1.dp, c.accent, RoundedCornerShape(10.dp)).clickable(onClick = onMohr).padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.mohr_title) + "  ›", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.mohr_desc), color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(c.keyNumber)
                        .clickable { onOpen(cat) }.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat.title.get(lang), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${Formulas.byCategory(cat).size}  ›", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FormulaList(cat: FormulaCategory, settings: AppSettings, onBack: () -> Unit, onFormula: (Formula) -> Unit) {
    val c = LocalCalcColors.current
    val lang = settings.language.code
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.accent)
                Text(cat.title.get(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
        items(Formulas.byCategory(cat)) { f ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(c.keyNumber)
                    .clickable { onFormula(f) }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(f.name.get(lang), style = MaterialTheme.typography.titleSmall)
                val rows = remember(f.id, settings.units) { f.build(settings.units) }
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    StaticMath(rows, 19f, labels = true)
                }
                if (f.vars.isNotEmpty()) {
                    val units = f.vars.joinToString("   ") { v ->
                        "${v.label} [${if (settings.units == UnitSystem.METRIC) v.metric else v.imperial}]"
                    }
                    Text(mathLabel(units), color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ------------------------------------------------------------------------------------------ converter

@Composable
fun ConverterPage(category: UnitCategory, settings: AppSettings, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    val units = Units.all.getValue(category)
    var from by remember(category) { mutableStateOf(Units.defaultUnit(category, settings.units)) }
    var text by rememberSaveable(category) { mutableStateOf("1") }
    val value = text.replace(',', '.').toDoubleOrNull()
    PageScaffold(categoryName(category), onBack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("${stringResource(R.string.conv_value)} (${from.symbol})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = mathStyle(LocalMathFont.current, 24f, c.text),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.conv_hint), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(units) { u -> UnitRow(u, settings.language.code, u == from, value?.let { Units.convert(it, from, u) }) {
                    // Keep the same quantity, expressed in the newly selected unit.
                    if (value != null) text = Format.plain(Units.convert(value, from, u))
                    from = u
                } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun UnitRow(u: MeasureUnit, lang: String, selected: Boolean, value: Double?, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.keyOperator else c.keyNumber)
            .then(if (selected) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(u.symbol, style = mathStyle(LocalMathFont.current, 18f, c.text))
            Text(UnitNames.of(u, lang), color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(value?.let { Format.number(it, 10) } ?: "—", style = mathStyle(LocalMathFont.current, 20f, if (selected) c.accent else c.text))
    }
}

// ------------------------------------------------------------------------------------------ settings

enum class SettingsArea { APPEARANCE, PREFERENCES, ABOUT }

@Composable
fun SettingsPage(
    area: SettingsArea,
    onArea: (SettingsArea) -> Unit,
    settings: AppSettings,
    updateStatus: UpdateStatus,
    updatesSupported: Boolean,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    PageScaffold(stringResource(R.string.settings), onBack) {
        SplitPage(
            listOf(
                SettingsArea.APPEARANCE to stringResource(R.string.settings_appearance),
                SettingsArea.PREFERENCES to stringResource(R.string.settings_preferences),
                SettingsArea.ABOUT to stringResource(R.string.settings_about),
            ),
            area, onArea,
        ) { a ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                when (a) {
                    SettingsArea.APPEARANCE -> {
                        SectionTitle(stringResource(R.string.theme))
                        RadioList(
                            listOf(
                                ThemeChoice.CLEAR to stringResource(R.string.theme_clear),
                                ThemeChoice.DARK to stringResource(R.string.theme_dark),
                                ThemeChoice.SYSTEM to stringResource(R.string.theme_system),
                            ),
                            settings.theme, settings::updateTheme,
                        )
                        SectionTitle(stringResource(R.string.font))
                        RadioList(
                            listOf(
                                FontChoice.LATEX to stringResource(R.string.font_latex),
                                FontChoice.SANS to stringResource(R.string.font_sans),
                            ),
                            settings.font, settings::updateFont,
                        )
                    }
                    SettingsArea.PREFERENCES -> {
                        SectionTitle(stringResource(R.string.units))
                        RadioList(
                            listOf(
                                UnitSystem.METRIC to stringResource(R.string.units_metric),
                                UnitSystem.IMPERIAL to stringResource(R.string.units_imperial),
                            ),
                            settings.units, settings::updateUnits,
                        )
                        Text(stringResource(R.string.units_hint), color = LocalCalcColors.current.muted, style = MaterialTheme.typography.bodySmall)
                        SectionTitle(stringResource(R.string.language))
                        RadioList(AppLanguage.entries.map { it to it.nativeName }, settings.language, onLanguage)
                    }
                    SettingsArea.ABOUT -> AboutSection(settings, updateStatus, updatesSupported, onCheckUpdate, onInstallUpdate)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AboutSection(
    settings: AppSettings,
    status: UpdateStatus,
    supported: Boolean,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
) {
    val c = LocalCalcColors.current
    val context = LocalContext.current
    Text("CalcExpress", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp))
    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = c.muted)

    if (supported) {
        SectionTitle(stringResource(R.string.about_check_updates))
        when (status) {
            UpdateStatus.Idle, UpdateStatus.UpToDate, UpdateStatus.Error -> {
                if (status == UpdateStatus.UpToDate) Text(stringResource(R.string.about_up_to_date))
                if (status == UpdateStatus.Error) Text(stringResource(R.string.about_update_error), color = c.error)
                Button(onClick = onCheck, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.about_check_updates)) }
            }
            UpdateStatus.Checking -> {
                Text(stringResource(R.string.about_checking))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            is UpdateStatus.Available -> {
                Text(stringResource(R.string.about_update_available, status.info.versionName), fontWeight = FontWeight.Bold)
                ReleaseNotes(status.info.notes)
                Button(onClick = onInstall, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.about_download_install)) }
            }
            is UpdateStatus.NeedsPermission -> {
                Text(stringResource(R.string.about_allow_install))
                Button(onClick = onInstall, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.about_download_install)) }
            }
            is UpdateStatus.Downloading -> {
                Text(stringResource(R.string.about_downloading, status.percent))
                LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.about_auto_check), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = settings.autoUpdateCheck, onCheckedChange = settings::updateAutoUpdateCheck)
        }
    } else {
        Text(stringResource(R.string.about_play_updates), modifier = Modifier.padding(top = 8.dp))
    }

    SectionTitle(stringResource(R.string.about_privacy))
    Text(stringResource(R.string.about_privacy_text), style = MaterialTheme.typography.bodyMedium)
    SectionTitle(stringResource(R.string.about_licences))
    Text(stringResource(R.string.about_licences_text), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}")))
    }) { Text(stringResource(R.string.about_source)) }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    Text(stringResource(R.string.about_whats_new), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    Text(notes.take(800), style = MaterialTheme.typography.bodySmall, color = LocalCalcColors.current.muted)
}

package com.arturpelicho.calcexpress

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arturpelicho.calcexpress.core.UnitCategory
import com.arturpelicho.calcexpress.ui.CalcState
import com.arturpelicho.calcexpress.ui.CalcTheme
import com.arturpelicho.calcexpress.ui.CalculatorScreen
import com.arturpelicho.calcexpress.ui.ConverterPage
import com.arturpelicho.calcexpress.ui.LocalCalcColors
import com.arturpelicho.calcexpress.ui.MenuArea
import com.arturpelicho.calcexpress.ui.MenuPage
import com.arturpelicho.calcexpress.ui.SettingsArea
import com.arturpelicho.calcexpress.ui.SettingsPage
import com.arturpelicho.calcexpress.update.InstallResult
import com.arturpelicho.calcexpress.update.UpdateStatus
import com.arturpelicho.calcexpress.update.Updater
import com.arturpelicho.calcexpress.update.UpdaterProvider
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var settings: AppSettings
    private lateinit var updater: Updater
    private val calc = CalcState()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        updater = UpdaterProvider.create(this)
        calc.restoreHistory(File(filesDir, "history.txt"))
        setContent {
            CalcTheme(settings.theme, settings.font) {
                val colors = LocalCalcColors.current
                LaunchedEffect(colors.isDark) {
                    val style = if (colors.isDark) SystemBarStyle.dark(colors.background.toArgb())
                    else SystemBarStyle.light(colors.background.toArgb(), colors.text.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                App(settings, calc, updater, onLanguage = { lang ->
                    settings.updateLanguage(lang)
                    recreate()
                })
            }
        }
    }
}

private enum class Screen { CALCULATOR, MENU, SETTINGS, CONVERTER }

@Composable
private fun App(settings: AppSettings, calc: CalcState, updater: Updater, onLanguage: (AppLanguage) -> Unit) {
    val c = LocalCalcColors.current
    val activity = androidx.activity.compose.LocalActivity.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.CALCULATOR) }
    var menuArea by remember { mutableStateOf(MenuArea.CALCULATOR) }
    var settingsArea by remember { mutableStateOf(SettingsArea.APPEARANCE) }
    var conversion by remember { mutableStateOf(UnitCategory.LENGTH) }
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<File?>(null) }

    fun check() {
        updateStatus = UpdateStatus.Checking
        scope.launch {
            updateStatus = try {
                updater.check()?.let { UpdateStatus.Available(it) } ?: UpdateStatus.UpToDate
            } catch (e: Exception) {
                UpdateStatus.Error
            }
            settings.lastUpdateCheck = System.currentTimeMillis()
        }
    }

    fun install() {
        val info = when (val s = updateStatus) {
            is UpdateStatus.Available -> s.info
            is UpdateStatus.NeedsPermission -> s.info
            else -> return
        }
        val act = activity ?: return
        scope.launch {
            try {
                val file = downloaded?.takeIf { it.exists() && it.name.contains(info.versionName) }
                    ?: updater.download(info) { pct -> updateStatus = UpdateStatus.Downloading(info, pct) }
                downloaded = file
                updateStatus = when (updater.install(act, file)) {
                    InstallResult.NEEDS_PERMISSION -> UpdateStatus.NeedsPermission(info)
                    InstallResult.STARTED -> UpdateStatus.Available(info)
                }
            } catch (e: Exception) {
                updateStatus = UpdateStatus.Error
            }
        }
    }

    LaunchedEffect(Unit) {
        if (updater.supported && settings.autoUpdateCheck) check()
    }

    BackHandler(enabled = screen != Screen.CALCULATOR) {
        screen = if (screen == Screen.CONVERTER) Screen.MENU else Screen.CALCULATOR
    }

    Box(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (screen) {
            Screen.CALCULATOR -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { screen = Screen.MENU }) { Icon(Icons.Filled.Menu, stringResource(R.string.menu)) }
                    Text("CalcExpress", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                }
                val s = updateStatus
                if (s is UpdateStatus.Available && !bannerDismissed) {
                    Row(
                        Modifier.fillMaxWidth().background(c.keyOperator).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.update_banner, s.info.versionName), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { bannerDismissed = true }) { Text(stringResource(R.string.dismiss)) }
                        TextButton(onClick = { install() }) { Text(stringResource(R.string.update_action)) }
                    }
                }
                CalculatorScreen(calc, settings, Modifier.weight(1f))
            }
            Screen.MENU -> MenuPage(
                area = menuArea, onArea = { menuArea = it }, calc = calc, settings = settings,
                onBack = { screen = Screen.CALCULATOR },
                onOpenCalculator = { screen = Screen.CALCULATOR },
                onFormula = { f -> calc.load(f.build(settings.units)); screen = Screen.CALCULATOR },
                onConversion = { conversion = it; screen = Screen.CONVERTER },
            )
            Screen.SETTINGS -> SettingsPage(
                area = settingsArea, onArea = { settingsArea = it }, settings = settings,
                updateStatus = updateStatus, updatesSupported = updater.supported,
                onCheckUpdate = { check() }, onInstallUpdate = { install() },
                onLanguage = onLanguage,
                onBack = { screen = Screen.CALCULATOR },
            )
            Screen.CONVERTER -> ConverterPage(conversion, settings, onBack = { screen = Screen.MENU })
        }
    }
}

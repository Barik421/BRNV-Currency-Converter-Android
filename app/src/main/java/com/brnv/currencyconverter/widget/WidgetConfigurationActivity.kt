package com.brnv.currencyconverter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.Conversion
import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.ThemeMode
import com.brnv.currencyconverter.ui.theme.BRNVCurrencyConverterTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class WidgetConfigurationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || provider != ComponentName(this, RateWidgetReceiver::class.java)) {
            finish(); return
        }
        lifecycleScope.launch {
            val store = WidgetSettingsStore(this@WidgetConfigurationActivity)
            val initial = store.read(widgetId) ?: WidgetSettings()
            setContent {
                var saving by remember { mutableStateOf(false) }
                var failed by remember { mutableStateOf(false) }
                WidgetConfiguration(initial, saving, failed, onCancel = { finish() }) { settings ->
                    saving = true
                    failed = false
                    lifecycleScope.launch {
                        try {
                            store.save(widgetId, settings)
                            RateWidget.refresh(this@WidgetConfigurationActivity, widgetId)
                            setResult(RESULT_OK, result)
                            finish()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { saving = false; failed = true }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetConfiguration(initial: WidgetSettings, saving: Boolean, failed: Boolean,
    onCancel: () -> Unit, onSave: (WidgetSettings) -> Unit) {
    var from by rememberSaveable { mutableStateOf(initial.currencies[0]) }
    var to by rememberSaveable { mutableStateOf(initial.currencies[1]) }
    var amount by rememberSaveable { mutableStateOf(initial.amount) }
    var theme by rememberSaveable { mutableStateOf(initial.theme) }
    var choosing by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val dark = when (theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    BRNVCurrencyConverterTheme(darkTheme = dark) {
        Scaffold { padding ->
            Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BRNV", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.widget_title), style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { query = ""; choosing = 1 }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("${stringResource(R.string.widget_from)}: $from · ${CurrencyCatalog.find(from)!!.name(locale)}")
                }
                OutlinedButton(onClick = { query = ""; choosing = 2 }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("${stringResource(R.string.widget_to)}: $to · ${CurrencyCatalog.find(to)!!.name(locale)}")
                }
                OutlinedTextField(amount, { if (Conversion.accepts(it)) amount = it },
                    modifier = Modifier.fillMaxWidth().testTag("widget_amount"), enabled = !saving, singleLine = true,
                    label = { Text(stringResource(R.string.amount_in, from)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> R.string.follow_device
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                    TextButton(onClick = { theme = mode }, enabled = !saving) {
                        Text((if (theme == mode) "✓  " else "") + stringResource(label))
                    }
                }
                if (failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = { onSave(WidgetSettings(listOf(from, to), amount, theme)) },
                    enabled = !saving && Conversion.parse(amount) != null && from != to,
                    modifier = Modifier.fillMaxWidth().testTag("widget_save")) { Text(stringResource(R.string.widget_save)) }
                TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.close)) }
            }
            if (choosing != 0) AlertDialog(onDismissRequest = { choosing = 0 },
                title = { Text(stringResource(R.string.choose_currency)) },
                text = {
                    Column {
                        OutlinedTextField(query, { query = it }, singleLine = true,
                            label = { Text(stringResource(R.string.search_currency)) })
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(CurrencyCatalog.all.filter { it.matches(query, locale) && it.code != if (choosing == 1) to else from }, key = { it.code }) { currency ->
                                TextButton(onClick = {
                                    if (choosing == 1) from = currency.code else to = currency.code
                                    choosing = 0
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${currency.flag} ${currency.code} · ${currency.name(locale)}", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { choosing = 0 }) { Text(stringResource(R.string.close)) } })
        }
    }
}

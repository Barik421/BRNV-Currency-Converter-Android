package com.brnv.currencyconverter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            val kind = "rate"
            val initial = (store.read(widgetId) ?: WidgetSettings()).copy(kind = kind)
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
    var showFlags by rememberSaveable { mutableStateOf(initial.showFlags) }
    var twoPairs by rememberSaveable { mutableStateOf(initial.second != null) }
    var secondFrom by rememberSaveable { mutableStateOf(initial.second?.from ?: "USD") }
    var secondTo by rememberSaveable { mutableStateOf(initial.second?.to ?: "UAH") }
    var secondAmount by rememberSaveable { mutableStateOf(initial.second?.amount ?: "1") }
    var choosing by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val flagsLabel = stringResource(R.string.widget_flags)
    val secondLabel = stringResource(R.string.widget_second)
    BRNVCurrencyConverterTheme(darkTheme = isSystemInDarkTheme()) {
        Scaffold { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxSize().imePadding()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.widget_settings), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
                        IconButton(onClick = onCancel, enabled = !saving) {
                            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.close))
                        }
                    }
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            WidgetCurrencyField(stringResource(R.string.widget_from), from, !saving) { query = ""; choosing = 1 }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            WidgetCurrencyField(stringResource(R.string.widget_to), to, !saving) { query = ""; choosing = 2 }
                            OutlinedTextField(amount, { if (Conversion.accepts(it)) amount = it },
                                modifier = Modifier.fillMaxWidth().testTag("widget_amount"), enabled = !saving, singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                                label = { Text(stringResource(R.string.amount_in, from)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.widget_flags), Modifier.weight(1f))
                        Switch(checked = showFlags, onCheckedChange = { showFlags = it }, enabled = !saving,
                            modifier = Modifier.testTag("widget_flags").semantics { contentDescription = flagsLabel })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.widget_second), Modifier.weight(1f))
                        Switch(checked = twoPairs, onCheckedChange = { twoPairs = it }, enabled = !saving,
                            modifier = Modifier.testTag("widget_second").semantics { contentDescription = secondLabel })
                    }
                    if (twoPairs) Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            WidgetCurrencyField(stringResource(R.string.widget_from), secondFrom, !saving) { query = ""; choosing = 3 }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            WidgetCurrencyField(stringResource(R.string.widget_to), secondTo, !saving) { query = ""; choosing = 4 }
                            OutlinedTextField(secondAmount, { if (Conversion.accepts(it)) secondAmount = it },
                                modifier = Modifier.fillMaxWidth().testTag("widget_second_amount"), enabled = !saving, singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                                label = { Text(stringResource(R.string.amount_in, secondFrom)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                    if (failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { onSave(WidgetSettings(listOf(from, to), amount, ThemeMode.SYSTEM, "rate", showFlags,
                        if (twoPairs) WidgetPair(secondFrom, secondTo, secondAmount) else null)) },
                        enabled = !saving && Conversion.parse(amount) != null && from != to &&
                            (!twoPairs || WidgetPair(secondFrom, secondTo, secondAmount).valid()),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("widget_save")) {
                        Text(stringResource(R.string.widget_save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (choosing != 0) AlertDialog(onDismissRequest = { choosing = 0 },
                shape = RoundedCornerShape(28.dp),
                title = { Text(stringResource(R.string.choose_currency), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(query, { query = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            label = { Text(stringResource(R.string.search_currency)) })
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(CurrencyCatalog.all.filter { it.matches(query, locale) && it.code != when (choosing) { 1 -> to; 2 -> from; 3 -> secondTo; else -> secondFrom } }, key = { it.code }) { currency ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    when (choosing) { 1 -> from = currency.code; 2 -> to = currency.code; 3 -> secondFrom = currency.code; 4 -> secondTo = currency.code }
                                    choosing = 0
                                }.padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(currency.flag, fontSize = 24.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(currency.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(currency.name(locale), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { choosing = 0 }) { Text(stringResource(R.string.close)) } })
        }
    }
}

@Composable
private fun WidgetCurrencyField(label: String, code: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${CurrencyCatalog.find(code)!!.flag}  $code", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
        }
        Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp)
    }
}

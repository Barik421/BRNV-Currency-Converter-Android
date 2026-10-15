package com.brnv.currencyconverter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.Conversion
import com.brnv.currencyconverter.ui.theme.BRNVCurrencyConverterTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class QuickConverterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider != ComponentName(this, QuickWidgetReceiver::class.java)) {
            finish(); return
        }
        lifecycleScope.launch {
            val store = WidgetSettingsStore(this@QuickConverterActivity)
            val settings = store.read(id) ?: run { finish(); return@launch }
            setContent {
                val data by produceState<WidgetData?>(null) { value = loadWidgetData(this@QuickConverterActivity, settings) }
                var saving by remember { mutableStateOf(false) }
                var failed by remember { mutableStateOf(false) }
                QuickConverter(settings, data, saving, failed, onClose = { finish() }, onConfigure = {
                    startActivity(Intent(this@QuickConverterActivity, WidgetConfigurationActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }) { amount ->
                    saving = true
                    failed = false
                    lifecycleScope.launch {
                        try {
                            store.save(id, settings.copy(amount = amount))
                            RateWidget.refresh(this@QuickConverterActivity, id)
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
internal fun QuickConverter(settings: WidgetSettings, data: WidgetData?, saving: Boolean, failed: Boolean,
    onClose: () -> Unit, onConfigure: () -> Unit, onSave: (String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(settings.amount) }
    BRNVCurrencyConverterTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.quick_title), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose, enabled = !saving) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                    }
                }
                Text("${settings.currencies[0]} → ${settings.currencies[1]}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(amount, { if (Conversion.accepts(it)) amount = it },
                    modifier = Modifier.fillMaxWidth().testTag("quick_amount"), enabled = !saving,
                    shape = RoundedCornerShape(16.dp), singleLine = true,
                    label = { Text(stringResource(R.string.amount_in, settings.currencies[0])) },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("≈ ${data?.convert(amount) ?: "—"} ${settings.currencies[1]}",
                    modifier = Modifier.testTag("quick_result"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (data == null || data.snapshot == null || data.cached) Text(stringResource(when {
                    data == null -> R.string.rates_loading
                    data.snapshot == null -> R.string.rates_unavailable
                    else -> R.string.rates_cached
                }), style = MaterialTheme.typography.bodySmall)
                if (failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = { onSave(amount) }, enabled = !saving && Conversion.parse(amount) != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("quick_save"), shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.quick_apply))
                }
                TextButton(onClick = onConfigure, enabled = !saving) { Text(stringResource(R.string.widget_settings)) }
            }
        }
    }
}

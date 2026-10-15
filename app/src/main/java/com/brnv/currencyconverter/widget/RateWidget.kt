package com.brnv.currencyconverter.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.compose.runtime.*
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.core.net.toUri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider
import com.brnv.currencyconverter.BrnvApplication
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.Conversion
import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.ThemeMode
import com.brnv.currencyconverter.domain.RateResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class RateWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val configure = Intent(context, WidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData("brnv://widget/$widgetId".toUri())
        provideContent {
            val revision = currentState<Preferences>()[Revision] ?: 0L
            val data by produceState<WidgetData?>(null, revision) {
                value = load(context, widgetId)
            }
            val settings = data?.settings
            val result = data?.result
            val cached = data?.cached == true
            val date = data?.date
            val light = Color(0xFFFAFAFC)
            val dark = Color(0xFF1C1C1E)
            fun colors(a: Color, b: Color): ColorProvider = when (settings?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> ColorProvider(day = a, night = b)
                ThemeMode.LIGHT -> ColorProvider(a)
                ThemeMode.DARK -> ColorProvider(b)
            }
            val foreground = colors(Color(0xFF1A1C1D), Color(0xFFF7F7F8))
            Column(GlanceModifier.fillMaxSize().background(colors(light, dark)).appWidgetBackground()
                .cornerRadius(24.dp).padding(16.dp).clickable(actionStartActivity(configure)),
                verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("BRNV", style = TextStyle(color = ColorProvider(Color(0xFFCE233A)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                if (settings == null) {
                    Text(context.getString(if (data == null) R.string.rates_loading else R.string.widget_configure), style = TextStyle(color = foreground, fontSize = 16.sp))
                } else {
                    Text("${settings.amount} ${settings.currencies[0]} → ${settings.currencies[1]}",
                        style = TextStyle(color = foreground, fontSize = 14.sp), maxLines = 2)
                    Text(if (result == null) "—" else "≈ $result ${settings.currencies[1]}",
                        style = TextStyle(color = foreground, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    val status = when {
                        result == null -> context.getString(R.string.rates_unavailable)
                        cached -> context.getString(R.string.rates_cached)
                        else -> ""
                    }
                    if (status.isNotEmpty()) Text(status, style = TextStyle(color = foreground, fontSize = 11.sp), maxLines = 2)
                    if (date != null) Text(context.getString(R.string.widget_updated, date),
                        style = TextStyle(color = foreground, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }

    companion object {
        private val Revision = longPreferencesKey("revision")
        suspend fun refresh(context: Context, widgetId: Int) {
            val id = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
            updateAppWidgetState(context, id) { it[Revision] = System.nanoTime() }
            RateWidget().update(context, id)
        }
    }

    private data class WidgetData(val settings: WidgetSettings?, val result: String?, val cached: Boolean, val date: String?)

    private suspend fun load(context: Context, widgetId: Int): WidgetData {
        val settings = WidgetSettingsStore(context).read(widgetId)
        val app = context.applicationContext as BrnvApplication
        // Glance executes provideGlance in its worker; never network on a receiver's main thread.
        val needsFiat = settings?.currencies?.any { CurrencyCatalog.find(it)?.isCrypto == false && it != "USD" } == true
        val needsCrypto = settings?.currencies?.any { CurrencyCatalog.find(it)?.isCrypto == true } == true
        val fiat = if (settings != null && (needsFiat || !needsCrypto)) app.rates.load() else RateResult(null)
        val crypto = if (needsCrypto) app.cryptoRates.load() else RateResult(null)
        val snapshot = fiat.snapshot?.let { it.copy(rates = it.rates + crypto.snapshot?.rates.orEmpty()) } ?: crypto.snapshot
        val result = settings?.let { config -> snapshot?.let {
            Conversion.convert(Conversion.parse(config.amount)!!, config.currencies[0], config.currencies[1], it)
        } }?.let(Conversion::format)
        val used = listOfNotNull(fiat.takeIf { needsFiat || !needsCrypto }, crypto.takeIf { needsCrypto })
        val cached = used.any { it.cached || it.refreshFailed }
        val updated = used.mapNotNull { it.snapshot?.updatedAt }.minOrNull()
        val date = updated?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(context.resources.configuration.locales[0]).withZone(ZoneId.systemDefault()).format(it) }
        return WidgetData(settings, result, cached, date)
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        WidgetSettingsStore(context).remove(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

class RateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RateWidget()
}

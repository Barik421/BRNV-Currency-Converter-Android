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
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.CurrencyCatalog


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
                value = loadWidgetData(context, WidgetSettingsStore(context).read(widgetId))
            }
            val settings = data?.settings
            val pairs = settings?.pairs.orEmpty()
            val cached = data?.cached == true
            val date = data?.date
            val size = LocalSize.current
            // Two short rows prioritize the rates over repeated headings, even when widened.
            val compactPairLayout = pairs.size == 2 && size.height < 250.dp
            val compact = size.height < (if (pairs.size == 2) 250.dp else 210.dp)
            // Both dimensions constrain typography: tall/narrow widgets must still fit their text.
            val scale = minOf(size.width.value / 240f, size.height.value / (if (pairs.size == 2) 280f else 170f)).coerceIn(0.55f, 1.8f)
            val inset = if (size.height < 140.dp) 8.dp else (size.width.value / 24f).coerceIn(10f, 14f).dp
            val accent = ColorProvider(day = Color(0xFFCE1938), night = Color(0xFFFF5068))
            val foreground = ColorProvider(day = Color(0xFF1A1C1D), night = Color(0xFFF7F7F8))
            val secondary = ColorProvider(day = Color(0xFF62656A), night = Color(0xFFB8BAC1))
            val width = (size.width.value - inset.value * 2).coerceAtLeast(1f)
            fun fitted(text: String, desired: Float, bold: Boolean = false, available: Float = width): Float {
                val metrics = context.resources.displayMetrics
                val paint = android.graphics.Paint().apply {
                    typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
                // Leave room for launcher padding and font differences; measure actual SP pixels,
                // including Android's non-linear font scaling, rather than assuming SP == DP.
                val budget = ((available - 16f) * metrics.density * 0.96f).coerceAtLeast(1f)
                var low = 6f
                var high = desired.coerceAtLeast(low)
                repeat(12) {
                    val candidate = (low + high) / 2
                    paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, candidate, metrics)
                    if (paint.measureText(text) <= budget) low = candidate else high = candidate
                }
                return low
            }
            val brand = if (size.width < 180.dp) "BRNV\nCurrency Converter" else "BRNV Currency Converter"
            val brandLines = if (size.width < 180.dp) 2 else 1
            val brandFont = fitted(brand.lineSequence().maxBy { it.length }, 14f, bold = true)
            val caption = context.getString(R.string.widget_current_rate)
            val captionFont = fitted(caption, 11f)
            Column(GlanceModifier.fillMaxSize()
                .background(ColorProvider(day = Color(0xFFFAFAFC), night = Color(0xFF1C1C1E))).appWidgetBackground()
                .cornerRadius(24.dp).padding(inset).clickable(actionStartActivity(configure)),
                verticalAlignment = Alignment.Vertical.CenterVertically) {
                if (!compactPairLayout) {
                    Text(brand, style = TextStyle(color = accent, fontSize = brandFont.sp, fontWeight = FontWeight.Bold), maxLines = brandLines)
                    Spacer(GlanceModifier.height(if (size.height < 140.dp) 0.dp else 4.dp))
                }
                if (settings == null) {
                    Text(context.getString(if (data == null) R.string.rates_loading else R.string.widget_configure),
                        style = TextStyle(color = foreground, fontSize = (16 * scale).sp))
                } else {
                    val status = when {
                        pairs.any { data?.convert(it) == null } -> context.getString(R.string.rates_unavailable)
                        cached -> context.getString(R.string.rates_cached)
                        else -> ""
                    }
                    val fontScale = context.resources.configuration.fontScale
                    val metadataFont = (11 * scale).coerceAtLeast(10f)
                    val metadataLines = (if (status.isNotEmpty()) if (compact) 1 else 2 else 0) +
                        (if (date != null && !compact) 2 else 0)
                    val metadataHeight = if (metadataLines > 0) metadataLines * metadataFont * fontScale * 1.4f + 16 else 0f
                    val brandHeight = if (compactPairLayout) 0f else brandFont * fontScale * 1.4f * brandLines + 4
                    val safetyHeight = if (size.height < 140.dp) 24f else 8f
                    val rowHeight = ((size.height.value - safetyHeight - 2 * inset.value - metadataHeight - brandHeight - (pairs.size - 1) * 13f) / pairs.size).coerceAtLeast(1f)
                    val headerSize = (size.width.value / 22f).coerceIn(11f, 16f)
                    val valueSize = minOf((rowHeight - (headerSize + if (compactPairLayout) 0f else captionFont) * fontScale * 1.4f - 8f) / (1.4f * fontScale), width * 0.36f).coerceIn(10f, 80f)
                    pairs.forEachIndexed { index, pair ->
                        if (index > 0) {
                            Spacer(GlanceModifier.height(6.dp))
                            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(secondary)) {}
                            Spacer(GlanceModifier.height(6.dp))
                        }
                        Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = if (pairs.size == 2) Alignment.Vertical.CenterVertically else Alignment.Vertical.Top) {
                            fun label(code: String): String =
                                if (settings.showFlags) "${CurrencyCatalog.find(code)?.flag.orEmpty()} $code" else code
                            val source = "${pair.amount} ${label(pair.from)}"
                            val target = label(pair.to)
                            val pairFont = fitted("$source $target", headerSize, available = width - 36 * scale)
                            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                                Text(source, style = TextStyle(color = secondary, fontSize = pairFont.sp), maxLines = 1)
                                Spacer(GlanceModifier.width((8 * scale).dp))
                                Image(ImageProvider(R.drawable.ic_widget_arrow), contentDescription = null,
                                    modifier = GlanceModifier.size((18 * scale).dp), colorFilter = ColorFilter.tint(secondary))
                                Spacer(GlanceModifier.width((8 * scale).dp))
                                Text(target, style = TextStyle(color = secondary, fontSize = pairFont.sp), maxLines = 1)
                            }
                            Spacer(GlanceModifier.height(if (size.height < 140.dp) 0.dp else 4.dp))
                            if (!compactPairLayout) Text(caption, style = TextStyle(color = secondary, fontSize = captionFont.sp), maxLines = 1)
                            val value = data?.convert(pair)?.let { "≈ $it" } ?: "—"
                            Text(value, style = TextStyle(color = foreground,
                                fontSize = fitted(value, valueSize, bold = true).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                    if (metadataLines > 0) Spacer(GlanceModifier.height(8.dp))
                    if (status.isNotEmpty()) Text(status, style = TextStyle(color = secondary, fontSize = (11 * scale).coerceAtLeast(10f).sp), maxLines = if (compact) 1 else 2)
                    if (date != null && !compact) Text(context.getString(R.string.widget_updated, date),
                        style = TextStyle(color = secondary, fontSize = (11 * scale).coerceAtLeast(10f).sp), maxLines = if (compact) 1 else 2)
                    if (metadataLines > 0) Spacer(GlanceModifier.height(8.dp))
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

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        WidgetSettingsStore(context).remove(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

class RateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RateWidget()
}

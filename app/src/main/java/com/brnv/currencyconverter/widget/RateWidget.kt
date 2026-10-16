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
            val compact = size.height < (if (pairs.size == 2) 250.dp else 150.dp)
            // Both dimensions constrain typography: tall/narrow widgets must still fit their text.
            val scale = minOf(size.width.value / 240f, size.height.value / (if (pairs.size == 2) 280f else 170f)).coerceIn(0.55f, 1.8f)
            val inset = (16f * scale).dp
            val foreground = ColorProvider(day = Color(0xFF1A1C1D), night = Color(0xFFF7F7F8))
            val secondary = ColorProvider(day = Color(0xFF62656A), night = Color(0xFFB8BAC1))
            val width = (size.width.value - inset.value * 2).coerceAtLeast(1f)
            fun fitted(text: String, desired: Float, bold: Boolean = false, available: Float = width): Float {
                val paint = android.graphics.Paint().apply {
                    typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    textSize = desired * context.resources.configuration.fontScale
                }
                return (desired * minOf(1f, available / paint.measureText(text).coerceAtLeast(1f))).coerceAtLeast(10f)
            }
            Column(GlanceModifier.fillMaxSize()
                .background(ColorProvider(day = Color(0xFFFAFAFC), night = Color(0xFF1C1C1E))).appWidgetBackground()
                .cornerRadius(24.dp).padding(inset).clickable(actionStartActivity(configure)),
                verticalAlignment = Alignment.Vertical.CenterVertically) {
                if (settings == null) {
                    Text(context.getString(if (data == null) R.string.rates_loading else R.string.widget_configure),
                        style = TextStyle(color = foreground, fontSize = (16 * scale).sp))
                } else {
                    pairs.forEachIndexed { index, pair ->
                        if (index > 0) {
                            Spacer(GlanceModifier.height((8 * scale).dp))
                            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(secondary)) {}
                            Spacer(GlanceModifier.height((8 * scale).dp))
                        }
                        fun label(code: String): String =
                            if (settings.showFlags) "${CurrencyCatalog.find(code)?.flag.orEmpty()} $code" else code
                        val source = "${pair.amount} ${label(pair.from)}"
                        val target = label(pair.to)
                        val pairFont = fitted("$source $target", 16 * scale, available = width - 36 * scale)
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(source, style = TextStyle(color = secondary, fontSize = pairFont.sp), maxLines = 1)
                            Spacer(GlanceModifier.width((8 * scale).dp))
                            Image(ImageProvider(R.drawable.ic_widget_arrow), contentDescription = null,
                                modifier = GlanceModifier.size((18 * scale).dp), colorFilter = ColorFilter.tint(secondary))
                            Spacer(GlanceModifier.width((8 * scale).dp))
                            Text(target, style = TextStyle(color = secondary, fontSize = pairFont.sp), maxLines = 1)
                        }
                        Spacer(GlanceModifier.height((6 * scale).dp))
                        val value = data?.convert(pair)?.let { "≈ $it ${pair.to}" } ?: "—"
                        Text(value, style = TextStyle(color = foreground,
                            fontSize = fitted(value, 30 * scale, bold = true).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.height((8 * scale).dp))
                    val status = when {
                        pairs.any { data?.convert(it) == null } -> context.getString(R.string.rates_unavailable)
                        cached -> context.getString(R.string.rates_cached)
                        else -> ""
                    }
                    if (status.isNotEmpty()) Text(status, style = TextStyle(color = secondary, fontSize = (11 * scale).coerceAtLeast(10f).sp), maxLines = if (compact) 1 else 2)
                    if (date != null && !compact) Text(context.getString(R.string.widget_updated, date),
                        style = TextStyle(color = secondary, fontSize = (11 * scale).coerceAtLeast(10f).sp), maxLines = if (compact) 1 else 2)
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

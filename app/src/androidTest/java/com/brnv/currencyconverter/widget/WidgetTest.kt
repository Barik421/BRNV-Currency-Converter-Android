package com.brnv.currencyconverter.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.brnv.currencyconverter.domain.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WidgetTest {
    @Test fun originalSinglePairSettingsRemainReadable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("widgets-v1", android.content.Context.MODE_PRIVATE)
        val store = WidgetSettingsStore(context)
        try {
            prefs.edit().putString("90003", """{"version":1,"kind":"rate","currencies":["EUR","USD"],"amount":"5","theme":"SYSTEM"}""").commit()
            val loaded = store.read(90003)!!
            assertEquals("5", loaded.amount)
            assertFalse(loaded.showFlags)
            assertNull(loaded.second)
        } finally { store.remove(90003) }
    }
    @Test fun settingsSurviveNewStoreAndDeletionIsIsolated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WidgetSettingsStore(context)
        val first = WidgetSettings(listOf("EUR", "USD"), "100", ThemeMode.DARK)
        val second = WidgetSettings(listOf("BTC", "UAH"), "0.1", showFlags = true, second = WidgetPair("EUR", "USD", "2"))
        try {
            store.save(90001, first); store.save(90002, second)
            assertEquals(first, WidgetSettingsStore(context).read(90001))
            store.remove(90001)
            assertNull(store.read(90001))
            assertEquals(second, store.read(90002))
            assertFalse(first.copy(currencies = listOf("EUR", "EUR")).valid())
            assertFalse(first.copy(amount = "-").valid())
        } finally { store.remove(90001); store.remove(90002) }
    }

    @Test fun twoBoundWidgetsRenderIndependentConfigurations() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 42142)
        val store = WidgetSettingsStore(context)
        val ids = listOf(host.allocateAppWidgetId(), host.allocateAppWidgetId())
        val codes = listOf(listOf("EUR", "USD"), listOf("USD", "UAH"))
        val views = mutableListOf<android.appwidget.AppWidgetHostView>()
        instrumentation.uiAutomation.executeShellCommand("appwidget grantbind --package ${context.packageName} --user 0")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        try {
            ids.forEachIndexed { index, id ->
                val provider = RateWidgetReceiver::class.java
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider)))
                manager.updateAppWidgetOptions(id, android.os.Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(android.util.SizeF(180f, 180f)))
                })
                store.save(id, WidgetSettings(codes[index], showFlags = index == 1, second = if (index == 1) WidgetPair("GBP", "EUR", "2") else null))
                instrumentation.runOnMainSync { views += host.createView(context, id, manager.getAppWidgetInfo(id)) }
            }
            instrumentation.runOnMainSync { host.startListening() }
            ids.forEach { RateWidget.refresh(context, it) }
            fun labels(view: View): String = when (view) {
                is TextView -> view.text.toString()
                is ViewGroup -> (0 until view.childCount).joinToString(" ") { labels(view.getChildAt(it)) }
                else -> ""
            }
            var rendered = false
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (!rendered && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    rendered = labels(views[0]).replace(Regex("\\s+"), " ").contains("1 EUR USD") && labels(views[1]).replace(Regex("\\s+"), " ").contains("1 🇺🇸 USD 🇺🇦 UAH")
                }
                if (!rendered) SystemClock.sleep(250)
            }
            assertTrue("Both real RemoteViews should contain their own currency pair", rendered)
            instrumentation.runOnMainSync {
                assertTrue(labels(views[1]).contains("2 🇬🇧 GBP"))
                assertTrue(labels(views[1]).contains("🇪🇺 EUR"))
                val pixels = (180 * context.resources.displayMetrics.density).toInt()
                val spec = View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY)
                views[1].measure(spec, spec)
                views[1].layout(0, 0, pixels, pixels)
                val bitmap = android.graphics.Bitmap.createBitmap(pixels, pixels, android.graphics.Bitmap.Config.ARGB_8888)
                views[1].draw(android.graphics.Canvas(bitmap))
                java.io.File(context.cacheDir, "widget-two-pairs.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            store.save(ids[0], WidgetSettings(listOf("GBP", "USD"), "10"))
            RateWidget.refresh(context, ids[0])
            rendered = false
            val changedDeadline = SystemClock.elapsedRealtime() + 30_000
            while (!rendered && SystemClock.elapsedRealtime() < changedDeadline) {
                instrumentation.runOnMainSync {
                    rendered = labels(views[0]).replace(Regex("\\s+"), " ").contains("10 GBP USD") && labels(views[1]).replace(Regex("\\s+"), " ").contains("1 🇺🇸 USD 🇺🇦 UAH")
                }
                if (!rendered) SystemClock.sleep(250)
            }
            assertTrue("Reconfiguration must update an already active Glance session without changing the other widget", rendered)
            fun sourceSize(view: View): Float = when (view) {
                is TextView -> if (view.text.toString() == "10 GBP") view.textSize else 0f
                is ViewGroup -> (0 until view.childCount).maxOfOrNull { sourceSize(view.getChildAt(it)) } ?: 0f
                else -> 0f
            }
            var originalSize = 0f
            fun resultSize(view: View): Float = when (view) {
                is TextView -> if (view.text.startsWith("≈") || view.text.toString() == "—") view.textSize else 0f
                is ViewGroup -> (0 until view.childCount).maxOfOrNull { resultSize(view.getChildAt(it)) } ?: 0f
                else -> 0f
            }
            var originalResultSize = 0f
            instrumentation.runOnMainSync { originalResultSize = resultSize(views[0]) }
            assertTrue(originalResultSize > 0f)
            instrumentation.runOnMainSync { originalSize = sourceSize(views[0]) }
            assertTrue(originalSize > 0f)
            manager.updateAppWidgetOptions(ids[0], android.os.Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 360)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 360)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 340)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 340)
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(android.util.SizeF(360f, 340f)))
            })
            var enlarged = false
            val resizedDeadline = SystemClock.elapsedRealtime() + 30_000
            while (!enlarged && SystemClock.elapsedRealtime() < resizedDeadline) {
                instrumentation.runOnMainSync {
                    enlarged = sourceSize(views[0]) > originalSize && resultSize(views[0]) > originalResultSize
                }
                if (!enlarged) SystemClock.sleep(250)
            }
            assertTrue("Resizing must enlarge actual text, not only the widget background", enlarged)
        } finally {
            instrumentation.runOnMainSync { host.stopListening(); host.deleteHost() }
            ids.forEach { store.remove(it) }
            instrumentation.uiAutomation.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0").close()
        }
    }
}

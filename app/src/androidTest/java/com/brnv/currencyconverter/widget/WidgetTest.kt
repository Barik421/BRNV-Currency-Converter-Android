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
    @Test fun settingsSurviveNewStoreAndDeletionIsIsolated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WidgetSettingsStore(context)
        val first = WidgetSettings(listOf("EUR", "USD"), "100", ThemeMode.DARK)
        val second = WidgetSettings(listOf("BTC", "UAH"), "0.1")
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
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, RateWidgetReceiver::class.java)))
                store.save(id, WidgetSettings(codes[index]))
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
                    rendered = labels(views[0]).contains("1 EUR → USD") && labels(views[1]).contains("1 USD → UAH")
                }
                if (!rendered) SystemClock.sleep(250)
            }
            assertTrue("Both real RemoteViews should contain their own currency pair", rendered)
            store.save(ids[0], WidgetSettings(listOf("GBP", "USD"), "10"))
            RateWidget.refresh(context, ids[0])
            rendered = false
            val changedDeadline = SystemClock.elapsedRealtime() + 30_000
            while (!rendered && SystemClock.elapsedRealtime() < changedDeadline) {
                instrumentation.runOnMainSync {
                    rendered = labels(views[0]).contains("10 GBP → USD") && labels(views[1]).contains("1 USD → UAH")
                }
                if (!rendered) SystemClock.sleep(250)
            }
            assertTrue("Reconfiguration must update an already active Glance session without changing the other widget", rendered)
        } finally {
            instrumentation.runOnMainSync { host.stopListening(); host.deleteHost() }
            ids.forEach { store.remove(it) }
            instrumentation.uiAutomation.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0").close()
        }
    }
}

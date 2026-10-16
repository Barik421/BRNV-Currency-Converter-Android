package com.brnv.currencyconverter.widget

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetConfigurationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun configurationUsesSystemThemeAndCodeOnlyWithAccessibleClose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var saved: WidgetSettings? = null
        var closed = false
        compose.setContent {
            WidgetConfiguration(WidgetSettings(theme = ThemeMode.DARK), false, false,
                onCancel = { closed = true }, onSave = { saved = it })
        }
        compose.onNodeWithText(context.getString(R.string.widget_settings)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.appearance)).assertDoesNotExist()
        compose.onNodeWithText("🇪🇺  EUR").assertIsDisplayed().performClick()
        compose.onNodeWithText("GBP").performClick()
        compose.onNodeWithTag("widget_flags").performScrollTo().performClick()
        compose.onNodeWithTag("widget_second").performScrollTo().performClick()
        compose.onNodeWithTag("widget_second_amount").performScrollTo().performTextReplacement("25")
        compose.onNodeWithTag("widget_save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("GBP", "UAH"), saved?.currencies)
            assertEquals(ThemeMode.SYSTEM, saved?.theme)
            assertEquals(true, saved?.showFlags)
            assertEquals(WidgetPair("USD", "UAH", "25"), saved?.second)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}

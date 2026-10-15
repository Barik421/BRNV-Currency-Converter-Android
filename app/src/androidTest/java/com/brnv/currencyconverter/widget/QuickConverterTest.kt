package com.brnv.currencyconverter.widget

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.brnv.currencyconverter.domain.RateSnapshot
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickConverterTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editingAmountPreviewsConversionAndSavesOnlyOnApply() {
        val settings = WidgetSettings(kind = "quick")
        val snapshot = RateSnapshot("EUR", mapOf("EUR" to BigDecimal.ONE, "UAH" to BigDecimal("50.3423")),
            Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), Instant.EPOCH)
        var saved: String? = null
        compose.setContent {
            QuickConverter(settings, WidgetData(settings, snapshot, false, null), false, false,
                onClose = {}, onConfigure = {}, onSave = { saved = it })
        }
        compose.onNodeWithTag("quick_amount").performTextReplacement("100,5")
        compose.onNodeWithTag("quick_result").assertTextEquals("≈ 5059.40 UAH")
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("quick_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("100,5", saved) }
    }
}

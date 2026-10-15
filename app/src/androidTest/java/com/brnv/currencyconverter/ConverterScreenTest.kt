package com.brnv.currencyconverter

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.brnv.currencyconverter.domain.RateResult
import com.brnv.currencyconverter.domain.RateSnapshot
import com.brnv.currencyconverter.ui.converter.ConverterScreen
import com.brnv.currencyconverter.ui.converter.ConverterUiState
import com.brnv.currencyconverter.ui.theme.BRNVCurrencyConverterTheme
import java.math.BigDecimal
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class ConverterScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun showConverter(dark: Boolean = false, onRefresh: () -> Unit = {}) {
        val snapshot = RateSnapshot("USD", mapOf("USD" to BigDecimal.ONE,
            "EUR" to BigDecimal("0.8"), "UAH" to BigDecimal("40"), "GBP" to BigDecimal("0.64")),
            Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), Instant.EPOCH)
        compose.setContent {
            var state by remember { mutableStateOf(ConverterUiState(
                rates = RateResult(snapshot), loading = false, ready = true)) }
            BRNVCurrencyConverterTheme(darkTheme = dark) {
                ConverterScreen(state,
                    onEdit = { code, value -> state = state.copy(session = state.session.edit(code, value)) },
                    onSelect = { old, code -> state = state.copy(session =
                        if (old == null) state.session.add(code) else state.session.replace(old, code)) },
                    onRemove = { state = state.copy(session = state.session.remove(it, snapshot)) },
                    onMove = { code, offset -> state = state.copy(session = state.session.move(code, offset)) },
                    onFavorite = { code -> state = state.copy(favorites = state.favorites + code) },
                    onRefresh = onRefresh,
                )
            }
        }
    }

    @Test fun pullDownRefreshesAtTopOfConverter() {
        var requests = 0
        showConverter(onRefresh = { requests++ })
        compose.onNodeWithTag("converter_list").performTouchInput { swipeDown() }
        compose.runOnIdle { org.junit.Assert.assertEquals(1, requests) }
    }

    @Test fun footerIsBelowFirstScreenAndAccessibleByScrolling() {
        showConverter()
        compose.onNodeWithTag("rate_app").assertIsNotDisplayed()
        scrollTo("rate_app")
        compose.onNodeWithTag("rate_app").assertIsDisplayed()
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("converter_list").performScrollToNode(hasTestTag(tag))
    }

    private fun add(code: String) {
        scrollTo("add_currency")
        compose.onNodeWithTag("add_currency").performClick()
        compose.onNodeWithTag("currency_search").performTextInput(code)
        compose.onNodeWithTag("select_$code").performClick()
    }

    @Test fun fourCurrenciesCanBeAddedAndEditedInDarkTheme() {
        showConverter(dark = true)
        add("UAH")
        add("GBP")
        scrollTo("amount_GBP")
        compose.onNodeWithTag("amount_GBP").performTextReplacement("64")
        scrollTo("amount_USD")
        compose.onNodeWithTag("amount_USD").assertTextContains("100")
        scrollTo("amount_EUR")
        compose.onNodeWithTag("amount_EUR").assertTextContains("80")
        scrollTo("amount_UAH")
        compose.onNodeWithTag("amount_UAH").assertTextContains("4000")
    }

    @Test fun commaInputConvertsAndClearingRemovesOtherAmounts() {
        showConverter()
        scrollTo("amount_EUR")
        compose.onNodeWithTag("amount_EUR").performTextReplacement("1,2")
        scrollTo("amount_USD")
        compose.onNodeWithTag("amount_USD").assertTextContains("1.5")
        scrollTo("amount_EUR")
        compose.onNodeWithTag("amount_EUR").performTextClearance()
        scrollTo("amount_USD")
        compose.onNodeWithTag("amount_USD").assert(hasText("", substring = false))
    }
}

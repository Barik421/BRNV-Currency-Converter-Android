package com.brnv.currencyconverter.domain

import com.brnv.currencyconverter.ui.converter.ConverterUiState
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MixedRatesTest {
    private fun snapshot(vararg values: Pair<String, String>) = RateSnapshot("USD",
        mapOf("USD" to BigDecimal.ONE) + values.associate { it.first to it.second.toBigDecimal() },
        Instant.EPOCH, Instant.EPOCH.plusSeconds(300), Instant.EPOCH)

    @Test fun fiatAndCryptoCanBeEditedInEitherDirection() {
        val state = ConverterUiState(rates = RateResult(snapshot("EUR" to "0.8", "UAH" to "40")),
            cryptoRates = RateResult(snapshot("BTC" to "0.00002", "ETH" to "0.0004")))
        val session = ConverterSession().add("BTC").add("ETH").edit("BTC", "0.5")
        assertEquals("20000", session.amount("EUR", state.conversionRates))
        assertEquals("10", session.amount("ETH", state.conversionRates))
        assertEquals("0.00002", session.edit("USD", "1").amount("BTC", state.conversionRates))
    }

    @Test fun cryptoFailureLeavesFiatConversionAvailable() {
        val state = ConverterUiState(rates = RateResult(snapshot("EUR" to "0.8")),
            cryptoRates = RateResult(null, refreshFailed = true))
        assertEquals("1.25", state.session.amount("USD", state.conversionRates))
        assertEquals("", state.session.add("BTC").amount("BTC", state.conversionRates))
    }

    @Test fun fiatFailureStillAllowsUsdAndCryptoConversion() {
        val state = ConverterUiState(cryptoRates = RateResult(snapshot("BTC" to "0.00002")))
        assertEquals("50000", ConverterSession(listOf("BTC", "USD"), "BTC", "1").amount("USD", state.conversionRates))
    }
}

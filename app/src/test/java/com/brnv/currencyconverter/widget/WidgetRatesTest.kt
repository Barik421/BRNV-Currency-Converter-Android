package com.brnv.currencyconverter.widget

import com.brnv.currencyconverter.domain.*
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WidgetRatesTest {
    @Test fun independentPairsShareRatesWithoutSharingAmounts() {
        val settings = WidgetSettings(listOf("USD", "UAH"), "1", second = WidgetPair("EUR", "USD", "10"))
        val snapshot = RateSnapshot("USD", mapOf("USD" to BigDecimal.ONE, "EUR" to BigDecimal("0.8"), "UAH" to BigDecimal("40")),
            Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), Instant.EPOCH)
        val data = WidgetData(settings, snapshot, false, null)
        assertEquals(2, settings.pairs.size)
        assertEquals("40.00", data.convert(settings.pairs[0]))
        assertEquals("12.50", data.convert(settings.pairs[1]))
        assertFalse(settings.copy(second = WidgetPair("EUR", "EUR")).valid())
    }
    @Test fun fiatUsesCentsButCryptoKeepsSmallAmounts() {
        assertEquals("50.34", formatWidgetAmount(BigDecimal("50.3423"), "UAH"))
        assertEquals("50.35", formatWidgetAmount(BigDecimal("50.345"), "UAH"))
        assertEquals("0.00001234", formatWidgetAmount(BigDecimal("0.00001234"), "BTC"))
    }
    @Test fun freshCacheIsNormalButStaleOrFailedRefreshShowsNotice() {
        val now = Instant.parse("2026-10-15T12:00:00Z")
        val snapshot = RateSnapshot("USD", mapOf("USD" to BigDecimal.ONE),
            now.minusSeconds(3600), now.plusSeconds(3600), now.minusSeconds(60))
        val result = RateResult(snapshot)
        assertFalse(widgetNeedsCacheNotice(result, Duration.ofDays(1), now))
        assertTrue(widgetNeedsCacheNotice(result.copy(refreshFailed = true), Duration.ofDays(1), now))
        assertTrue(widgetNeedsCacheNotice(result, Duration.ofDays(1), now.plusSeconds(3601)))
        assertTrue(widgetNeedsCacheNotice(result, Duration.ofMinutes(5), now.plusSeconds(300)))
    }
}

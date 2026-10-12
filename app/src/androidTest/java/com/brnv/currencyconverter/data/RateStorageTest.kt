package com.brnv.currencyconverter.data

import androidx.test.platform.app.InstrumentationRegistry
import com.brnv.currencyconverter.domain.ConverterSession
import com.brnv.currencyconverter.domain.RateSnapshot
import com.brnv.currencyconverter.domain.ThemeMode
import com.brnv.currencyconverter.domain.Conversion
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RateStorageTest {
    @Test fun diskCacheRoundTripAndCorruptionRecovery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "test-rates.json")
        val snapshot = RateSnapshot("USD", mapOf("USD" to BigDecimal.ONE, "EUR" to BigDecimal("0.9123456789")),
            Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), Instant.EPOCH.plusSeconds(100))
        try {
            FileRateCache(file).write(snapshot)
            assertEquals(snapshot, FileRateCache(file).read())
            file.writeText("{broken}")
            assertNull(FileRateCache(file).read())
        } finally { file.delete() }
    }

    @Test fun invalidApiResponseDoesNotBecomeASnapshot() {
        listOf("{}", "{\"result\":\"error\"}", "not json").forEach {
            try { RateJson.decode(it); fail("Invalid response accepted") } catch (_: IOException) { }
        }
    }

    @Test fun selectionAmountOrderAndFavoritesSurviveNewStoreInstance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("test-converter", 0)
        try {
            val expected = SavedConverter(ConverterSession(listOf("UAH", "EUR", "USD", "BTC"), "UAH", "123,45"), setOf("UAH", "BTC"), ThemeMode.DARK)
            ConverterPreferences(prefs).save(expected)
            assertEquals(expected, ConverterPreferences(prefs).read())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun cryptoQuotesUseReciprocalUsdRatesAndActualStablecoinPrice() {
        val snapshot = CryptoRateJson.decode("""{
            "bitcoin":{"usd":50000,"last_updated_at":100},
            "ethereum":{"usd":2500,"last_updated_at":110},
            "tether":{"usd":0.99,"last_updated_at":105}
        }""", Instant.ofEpochSecond(120))
        assertEquals("50000", Conversion.format(Conversion.convert(BigDecimal.ONE, "BTC", "USD", snapshot)!!))
        assertEquals("20", Conversion.format(Conversion.convert(BigDecimal.ONE, "BTC", "ETH", snapshot)!!))
        assertEquals("0.99", Conversion.format(Conversion.convert(BigDecimal.ONE, "USDT", "USD", snapshot)!!))
        assertEquals(Instant.ofEpochSecond(100), snapshot.updatedAt)
        assertFalse(snapshot.rates.containsKey("DOGE"))
    }

    @Test fun invalidCryptoPricesNeverBecomeOneToOneRates() {
        listOf("{}", """{"bitcoin":{"usd":0,"last_updated_at":100}}""",
            """{"bitcoin":{"usd":-1,"last_updated_at":100}}""",
            """{"bitcoin":{"usd":50000}}""").forEach {
            try { CryptoRateJson.decode(it, Instant.ofEpochSecond(120)); fail("Invalid crypto price accepted") }
            catch (_: IOException) { }
        }
    }
}

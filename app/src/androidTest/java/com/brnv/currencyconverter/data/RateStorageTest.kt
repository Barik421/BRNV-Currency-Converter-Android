package com.brnv.currencyconverter.data

import androidx.test.platform.app.InstrumentationRegistry
import com.brnv.currencyconverter.domain.ConverterSession
import com.brnv.currencyconverter.domain.RateSnapshot
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
            val expected = SavedConverter(ConverterSession(listOf("UAH", "EUR", "USD", "GBP"), "UAH", "123,45"), setOf("UAH", "GBP"))
            ConverterPreferences(prefs).save(expected)
            assertEquals(expected, ConverterPreferences(prefs).read())
        } finally { prefs.edit().clear().commit() }
    }
}

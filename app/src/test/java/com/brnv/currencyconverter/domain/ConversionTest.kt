package com.brnv.currencyconverter.domain

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ConversionTest {
    private val rates = RateSnapshot("USD", mapOf(
        "USD" to BigDecimal.ONE, "EUR" to BigDecimal("0.8"),
        "UAH" to BigDecimal("40"), "GBP" to BigDecimal("0.64")),
        Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), Instant.EPOCH)

    @Test fun editingAnyRowRecalculatesEveryOtherCurrency() {
        val session = ConverterSession().add("UAH").add("GBP").edit("EUR", "100")
        assertEquals("125", session.amount("USD", rates))
        assertEquals("5000", session.amount("UAH", rates))
        assertEquals("80", session.amount("GBP", rates))
        val edited = session.edit("UAH", "200")
        assertEquals("4", edited.amount("EUR", rates))
        assertEquals("5", edited.amount("USD", rates))
        assertEquals("3.2", edited.amount("GBP", rates))
    }

    @Test fun decimalCommaAndIncompleteInputRemainEditable() {
        val session = ConverterSession().edit("EUR", "1,20")
        assertEquals("1,20", session.amount("EUR", rates))
        assertEquals("1.5", session.amount("USD", rates))
        assertEquals("", session.edit("EUR", "").amount("USD", rates))
        assertEquals("", session.edit("EUR", ",").amount("USD", rates))
        assertEquals("1.", session.edit("EUR", "1.").input)
    }

    @Test fun invalidAndUnboundedInputIsRejected() {
        listOf("NaN", "-1", "1e5", "1.2.3", "1,2.3", "9".repeat(25)).forEach {
            assertFalse(it, Conversion.accepts(it))
        }
        assertEquals("1", ConverterSession().edit("EUR", "-2").input)
    }

    @Test fun displayPrecisionMatchesWebsite() {
        assertEquals("123.46", Conversion.format(BigDecimal("123.456")))
        assertEquals("1.2346", Conversion.format(BigDecimal("1.234567")))
        assertEquals("0.00012346", Conversion.format(BigDecimal("0.000123456789")))
        assertEquals("0.0000001235", Conversion.format(BigDecimal("0.000000123456")))
        assertEquals("0", Conversion.format(BigDecimal.ZERO))
    }

    @Test fun reorderKeepsSourceAndAmounts() {
        val session = ConverterSession().add("UAH").edit("USD", "20").move("USD", -1)
        assertEquals(listOf("USD", "EUR", "UAH"), session.currencies)
        assertEquals("USD", session.source)
        assertEquals("16", session.amount("EUR", rates))
        assertEquals(session, session.move("USD", -1))
    }

    @Test fun removingSourcePreservesConversion() {
        val session = ConverterSession().add("UAH").edit("UAH", "200").remove("UAH", rates)
        assertEquals("EUR", session.source)
        assertEquals("4", session.input)
        assertEquals("5", session.amount("USD", rates))
        assertEquals(session, session.remove("EUR", rates))
    }

    @Test fun currenciesAreUniqueAndNotLimitedToFour() {
        val session = ConverterSession().add("UAH").add("GBP").add("PLN")
        assertEquals(5, session.currencies.size)
        assertEquals(session, session.add("EUR"))
        assertEquals(session, session.replace("USD", "EUR"))
        assertEquals("PLN", session.replace("EUR", "JPY").currencies.last())
    }

    @Test fun missingRatesDoNotInventValues() {
        assertNull(Conversion.convert(BigDecimal.ONE, "EUR", "PLN", rates))
        assertEquals("", ConverterSession().amount("USD", null))
        assertEquals("1", ConverterSession().amount("EUR", null))
    }

    @Test fun catalogKeepsWebsiteNamesAndSearchAliases() {
        assertEquals(77, CurrencyCatalog.fiat.size)
        assertEquals(19, CurrencyCatalog.crypto.size)
        assertEquals(96, CurrencyCatalog.all.map { it.code }.toSet().size)
        assertTrue(CurrencyCatalog.find("UAH")!!.matches("гривня"))
        assertTrue(CurrencyCatalog.find("HUF")!!.matches(" forint "))
        assertTrue(CurrencyCatalog.find("EUR")!!.matches("eur"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroRatesAreRejected() {
        rates.copy(rates = rates.rates + ("EUR" to BigDecimal.ZERO))
    }
}

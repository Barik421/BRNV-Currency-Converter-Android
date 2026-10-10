package com.brnv.currencyconverter.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant

data class RateSnapshot(
    val base: String,
    val rates: Map<String, BigDecimal>,
    val updatedAt: Instant,
    val nextUpdateAt: Instant,
    val fetchedAt: Instant,
) {
    init {
        require(rates.isNotEmpty() && rates.values.all { it.signum() > 0 })
        require(rates[base]?.compareTo(BigDecimal.ONE) == 0)
        require(nextUpdateAt > updatedAt)
    }
}

/** One shared calculation path for the app and, later, widgets. No rounded intermediates. */
object Conversion {
    fun accepts(text: String): Boolean = text.length <= 24 &&
        text.matches(Regex("[0-9]*([.,][0-9]*)?"))

    fun parse(text: String): BigDecimal? =
        if (accepts(text)) text.replace(',', '.').toBigDecimalOrNull() else null

    fun convert(amount: BigDecimal, from: String, to: String, rates: RateSnapshot): BigDecimal? {
        val source = rates.rates[from] ?: return null
        val target = rates.rates[to] ?: return null
        return amount.multiply(target).divide(source, MathContext.DECIMAL128)
    }

    // Same display precision thresholds as the BRNV website.
    fun format(amount: BigDecimal): String {
        val absolute = amount.abs()
        val scale = when {
            absolute >= BigDecimal("100") -> 2
            absolute >= BigDecimal.ONE -> 4
            absolute >= BigDecimal("0.0001") -> 8
            else -> 10
        }
        return amount.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

data class ConverterSession(
    val currencies: List<String> = listOf("EUR", "USD"),
    val source: String = "EUR",
    val input: String = "1",
) {
    fun edit(code: String, text: String): ConverterSession =
        if (code in currencies && Conversion.accepts(text)) copy(source = code, input = text) else this

    fun amount(code: String, rates: RateSnapshot?): String {
        if (code == source) return input
        val number = Conversion.parse(input) ?: return ""
        return rates?.let { Conversion.convert(number, source, code, it) }
            ?.let(Conversion::format).orEmpty()
    }

    fun add(code: String): ConverterSession =
        if (code in currencies) this else copy(currencies = currencies + code)

    fun replace(old: String, new: String): ConverterSession = when {
        old !in currencies || new in currencies -> this
        else -> copy(currencies = currencies.map { if (it == old) new else it },
            source = if (source == old) new else source)
    }

    fun remove(code: String, rates: RateSnapshot?): ConverterSession {
        if (currencies.size <= 2 || code !in currencies) return this
        val remaining = currencies - code
        return if (source == code) copy(currencies = remaining, source = remaining.first(),
            input = amount(remaining.first(), rates)) else copy(currencies = remaining)
    }

    fun move(code: String, offset: Int): ConverterSession {
        val index = currencies.indexOf(code)
        val target = index + offset
        if (index < 0 || target !in currencies.indices) return this
        return copy(currencies = currencies.toMutableList().apply {
            add(target, removeAt(index))
        })
    }
}

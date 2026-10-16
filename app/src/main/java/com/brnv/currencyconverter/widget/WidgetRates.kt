package com.brnv.currencyconverter.widget

import android.content.Context
import com.brnv.currencyconverter.BrnvApplication
import com.brnv.currencyconverter.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun formatWidgetAmount(amount: BigDecimal, target: String): String =
    if (CurrencyCatalog.find(target)?.isCrypto == true) Conversion.format(amount)
    else amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

internal fun widgetNeedsCacheNotice(result: RateResult, maxAge: Duration, now: Instant): Boolean =
    result.refreshFailed || result.snapshot?.let {
        now >= minOf(it.nextUpdateAt, it.fetchedAt.plus(maxAge)) || now < it.fetchedAt
    } == true

internal data class WidgetData(val settings: WidgetSettings?, val snapshot: RateSnapshot?,
    val cached: Boolean, val date: String?) {
    fun convert(amount: String): String? = settings?.let { config ->
        val number = Conversion.parse(amount) ?: return null
        snapshot?.let { Conversion.convert(number, config.currencies[0], config.currencies[1], it) }
            ?.let { formatWidgetAmount(it, config.currencies[1]) }
    }
    val result: String? get() = settings?.let { convert(it.amount) }
    fun convert(pair: WidgetPair): String? {
        val number = Conversion.parse(pair.amount) ?: return null
        return snapshot?.let { Conversion.convert(number, pair.from, pair.to, it) }
            ?.let { formatWidgetAmount(it, pair.to) }
    }
}

internal suspend fun loadWidgetData(context: Context, settings: WidgetSettings?): WidgetData {
    if (settings == null) return WidgetData(null, null, false, null)
    val app = context.applicationContext as BrnvApplication
    val codes = settings.pairs.flatMap { listOf(it.from, it.to) }
    val needsCrypto = codes.any { CurrencyCatalog.find(it)?.isCrypto == true }
    val needsFiat = !needsCrypto || codes.any { CurrencyCatalog.find(it)?.isCrypto == false && it != "USD" }
    val fiat = if (needsFiat) app.rates.load() else RateResult(null)
    val crypto = if (needsCrypto) app.cryptoRates.load() else RateResult(null)
    val snapshot = fiat.snapshot?.let { it.copy(rates = it.rates + crypto.snapshot?.rates.orEmpty()) } ?: crypto.snapshot
    val now = Instant.now()
    val cached = (needsFiat && widgetNeedsCacheNotice(fiat, Duration.ofDays(1), now)) ||
        (needsCrypto && widgetNeedsCacheNotice(crypto, Duration.ofMinutes(5), now))
    val updated = listOfNotNull(fiat.snapshot?.updatedAt, crypto.snapshot?.updatedAt).minOrNull()
    val date = updated?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(context.resources.configuration.locales[0]).withZone(ZoneId.systemDefault()).format(it) }
    return WidgetData(settings, snapshot, cached, date)
}

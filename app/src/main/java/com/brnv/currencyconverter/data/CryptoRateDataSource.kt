package com.brnv.currencyconverter.data

import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.RateDataSource
import com.brnv.currencyconverter.domain.RateSnapshot
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object CryptoRateJson {
    fun decode(text: String, fetchedAt: Instant): RateSnapshot = try {
        val json = JSONObject(text)
        val dates = mutableListOf<Instant>()
        val rates = mutableMapOf("USD" to BigDecimal.ONE)
        CurrencyCatalog.crypto.forEach { coin ->
            val quote = json.optJSONObject(coin.coinGeckoId!!) ?: return@forEach
            val price = quote.optString("usd").toBigDecimalOrNull() ?: return@forEach
            val timestamp = quote.optLong("last_updated_at", 0)
            if (price.signum() <= 0 || timestamp <= 0) return@forEach
            val updated = Instant.ofEpochSecond(timestamp)
            if (updated > fetchedAt.plusSeconds(300)) return@forEach
            rates[coin.code] = BigDecimal.ONE.divide(price, MathContext.DECIMAL128)
            dates += updated
        }
        require(dates.isNotEmpty())
        val updatedAt = dates.min()
        RateSnapshot("USD", rates, updatedAt, updatedAt.plusSeconds(300), fetchedAt)
    } catch (error: Exception) {
        throw IOException("Invalid crypto rate response", error)
    }
}

/** Same public endpoint as BRNV web; a provider failure never invalidates the fiat cache. */
class CoinGeckoRateDataSource : RateDataSource {
    override suspend fun fetch(): RateSnapshot = withContext(Dispatchers.IO) {
        val ids = CurrencyCatalog.crypto.joinToString(",") { it.coinGeckoId!! }
        val connection = URL("https://api.coingecko.com/api/v3/simple/price?ids=$ids" +
            "&vs_currencies=usd&include_last_updated_at=true&precision=full")
            .openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != 200) throw IOException("Crypto service unavailable")
            connection.inputStream.bufferedReader().use {
                CryptoRateJson.decode(it.readText(), Instant.now())
            }
        } finally { connection.disconnect() }
    }
}

package com.brnv.currencyconverter.data

import android.util.AtomicFile
import com.brnv.currencyconverter.domain.RateCache
import com.brnv.currencyconverter.domain.RateDataSource
import com.brnv.currencyconverter.domain.RateSnapshot
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object RateJson {
    fun decode(text: String, fetchedAt: Instant? = null): RateSnapshot = try {
        val json = JSONObject(text)
        require(json.getString("result") == "success")
        val values = json.getJSONObject("rates")
        RateSnapshot(
            base = json.getString("base_code"),
            rates = values.keys().asSequence().associateWith { values.getString(it).toBigDecimal() },
            updatedAt = Instant.ofEpochSecond(json.getLong("time_last_update_unix")),
            nextUpdateAt = Instant.ofEpochSecond(json.getLong("time_next_update_unix")),
            fetchedAt = fetchedAt ?: Instant.ofEpochSecond(json.getLong("fetched_at")),
        )
    } catch (error: Exception) {
        throw IOException("Invalid exchange-rate response", error)
    }

    fun encode(snapshot: RateSnapshot): String = JSONObject().apply {
        put("result", "success")
        put("base_code", snapshot.base)
        put("rates", JSONObject(snapshot.rates.mapValues { it.value.toPlainString() }))
        put("time_last_update_unix", snapshot.updatedAt.epochSecond)
        put("time_next_update_unix", snapshot.nextUpdateAt.epochSecond)
        put("fetched_at", snapshot.fetchedAt.epochSecond)
    }.toString()
}

class OpenExchangeRateDataSource : RateDataSource {
    override suspend fun fetch(): RateSnapshot = withContext(Dispatchers.IO) {
        // One base response supplies every cross-rate without a request on each edit.
        val connection = URL("https://open.er-api.com/v6/latest/USD")
            .openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != 200) throw IOException("Rate service unavailable")
            val snapshot = connection.inputStream.bufferedReader().use {
                RateJson.decode(it.readText(), Instant.now())
            }
            if (snapshot.base != "USD") throw IOException("Unexpected rate base")
            snapshot
        } finally {
            connection.disconnect()
        }
    }
}

class FileRateCache(file: File) : RateCache {
    private val atomicFile = AtomicFile(file)
    override suspend fun read(): RateSnapshot? = withContext(Dispatchers.IO) {
        try {
            atomicFile.openRead().bufferedReader().use { RateJson.decode(it.readText()) }
        } catch (_: IOException) { null }
    }

    override suspend fun write(snapshot: RateSnapshot): Unit = withContext(Dispatchers.IO) {
        val output = atomicFile.startWrite()
        try {
            output.write(RateJson.encode(snapshot).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: IOException) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}

package com.brnv.currencyconverter.widget

import android.content.Context
import com.brnv.currencyconverter.domain.Conversion
import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class WidgetSettings(
    val currencies: List<String> = listOf("EUR", "UAH"),
    val amount: String = "1",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val kind: String = "rate",
) {
    fun valid() = kind == "rate" && currencies.size == 2 && currencies.distinct().size == 2 &&
        currencies.all { CurrencyCatalog.find(it) != null } && Conversion.parse(amount)?.signum()?.let { it >= 0 } == true
}

/** One versioned record per widget. The list supports future multi-currency widget types. */
class WidgetSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widgets-v1", Context.MODE_PRIVATE)
    suspend fun read(id: Int): WidgetSettings? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(prefs.getString(id.toString(), null) ?: return@withContext null)
            require(json.getInt("version") == 1)
            val codes = json.getJSONArray("currencies")
            WidgetSettings(List(codes.length()) { codes.getString(it) }, json.getString("amount"),
                ThemeMode.valueOf(json.getString("theme")), json.getString("kind")).takeIf { it.valid() }
        }.getOrNull()
    }
    suspend fun save(id: Int, settings: WidgetSettings) = withContext(Dispatchers.IO) {
        require(id > 0 && settings.valid())
        val json = JSONObject().put("version", 1).put("kind", settings.kind)
            .put("currencies", JSONArray(settings.currencies)).put("amount", settings.amount).put("theme", settings.theme.name)
        if (!prefs.edit().putString(id.toString(), json.toString()).commit()) throw IOException("Widget settings not saved")
    }
    suspend fun remove(id: Int) = withContext(Dispatchers.IO) {
        if (!prefs.edit().remove(id.toString()).commit()) throw IOException("Widget settings not removed")
    }
}

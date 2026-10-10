package com.brnv.currencyconverter.data

import android.content.SharedPreferences
import com.brnv.currencyconverter.domain.ConverterSession
import com.brnv.currencyconverter.domain.Conversion
import com.brnv.currencyconverter.domain.CurrencyCatalog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedConverter(
    val session: ConverterSession = ConverterSession(),
    val favorites: Set<String> = emptySet(),
)

class ConverterPreferences(private val preferences: SharedPreferences) {
    suspend fun read(): SavedConverter = withContext(Dispatchers.IO) {
        val codes = preferences.getString("currencies", "EUR,USD").orEmpty()
            .split(',').distinct().filter { CurrencyCatalog.find(it) != null }
            .takeIf { it.size >= 2 } ?: listOf("EUR", "USD")
        val source = preferences.getString("source", codes.first())
            ?.takeIf { it in codes } ?: codes.first()
        val input = preferences.getString("input", "1").orEmpty()
            .takeIf(Conversion::accepts) ?: "1"
        SavedConverter(ConverterSession(codes, source, input),
            preferences.getStringSet("favorites", emptySet()).orEmpty()
                .filter { CurrencyCatalog.find(it) != null }.toSet())
    }

    suspend fun save(value: SavedConverter): Unit = withContext(Dispatchers.IO) {
        val saved = preferences.edit()
            .putString("currencies", value.session.currencies.joinToString(","))
            .putString("source", value.session.source)
            .putString("input", value.session.input)
            .putStringSet("favorites", value.favorites.toSet()).commit()
        if (!saved) throw IOException("Could not save converter preferences")
    }
}

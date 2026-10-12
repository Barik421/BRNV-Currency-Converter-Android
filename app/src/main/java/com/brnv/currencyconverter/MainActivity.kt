package com.brnv.currencyconverter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.isSystemInDarkTheme
import com.brnv.currencyconverter.domain.ThemeMode
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brnv.currencyconverter.ui.converter.ConverterScreen
import com.brnv.currencyconverter.ui.converter.ConverterViewModel
import com.brnv.currencyconverter.ui.theme.BRNVCurrencyConverterTheme

class MainActivity : AppCompatActivity() {
    private val app get() = application as BrnvApplication
    override fun onResume() { super.onResume(); app.reviews.resume() }
    override fun onPause() { app.reviews.pause(); super.onPause() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ConverterViewModel(app.rates, app.preferences, app.cryptoRates) as T
        })[ConverterViewModel::class.java]
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            val dark = when (state.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            BRNVCurrencyConverterTheme(darkTheme = dark) {
                ConverterScreen(state,
                    onEdit = { code, input ->
                        val before = model.state.value.conversionRevision
                        model.edit(code, input)
                        if (model.state.value.conversionRevision != before) app.reviews.recordConversion()
                    }, onSelect = model::select, onRemove = model::remove, onMove = model::move,
                    onFavorite = model::favorite, onRefresh = model::refresh,
                    selectedLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags(),
                    onLanguage = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
                    onTheme = model::theme, onReview = { app.reviews.openStore(this) },
                    onReviewOpportunity = { app.reviews.maybeRequest(this) })
            }
        }
    }
}

package com.brnv.currencyconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brnv.currencyconverter.ui.converter.ConverterScreen
import com.brnv.currencyconverter.ui.converter.ConverterViewModel
import com.brnv.currencyconverter.ui.theme.BRNVCurrencyConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BrnvApplication
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ConverterViewModel(app.rates, app.preferences) as T
        })[ConverterViewModel::class.java]
        setContent {
            BRNVCurrencyConverterTheme {
                ConverterScreen(model.state.collectAsStateWithLifecycle().value,
                    model::edit, model::select, model::remove, model::move,
                    model::favorite, model::refresh)
            }
        }
    }
}

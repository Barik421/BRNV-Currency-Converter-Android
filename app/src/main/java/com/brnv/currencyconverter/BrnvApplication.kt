package com.brnv.currencyconverter

import android.app.Application
import com.brnv.currencyconverter.data.ConverterPreferences
import com.brnv.currencyconverter.data.FileRateCache
import com.brnv.currencyconverter.data.OpenExchangeRateDataSource
import com.brnv.currencyconverter.domain.CachedExchangeRateRepository
import java.io.File

class BrnvApplication : Application() {
    // Shared by activities and future widget workers; never hold an Activity here.
    val rates by lazy {
        CachedExchangeRateRepository(OpenExchangeRateDataSource(),
            FileRateCache(File(filesDir, "exchange-rates-v1.json")))
    }
    val preferences by lazy {
        ConverterPreferences(getSharedPreferences("converter-v1", MODE_PRIVATE))
    }
}

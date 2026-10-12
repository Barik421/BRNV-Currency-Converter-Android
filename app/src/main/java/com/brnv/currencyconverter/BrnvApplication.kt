package com.brnv.currencyconverter

import android.app.Application
import com.brnv.currencyconverter.data.ConverterPreferences
import com.brnv.currencyconverter.data.CoinGeckoRateDataSource
import com.brnv.currencyconverter.data.FileRateCache
import com.brnv.currencyconverter.data.OpenExchangeRateDataSource
import com.brnv.currencyconverter.domain.CachedExchangeRateRepository
import java.io.File
import java.time.Duration
import com.brnv.currencyconverter.review.ReviewCoordinator

class BrnvApplication : Application() {
    val reviews by lazy { ReviewCoordinator(getSharedPreferences("review-usage-v1", MODE_PRIVATE)) }
    // Shared by activities and future widget workers; never hold an Activity here.
    val rates by lazy {
        CachedExchangeRateRepository(OpenExchangeRateDataSource(),
            FileRateCache(File(filesDir, "exchange-rates-v1.json")))
    }
    val cryptoRates by lazy {
        CachedExchangeRateRepository(CoinGeckoRateDataSource(),
            FileRateCache(File(filesDir, "crypto-rates-v1.json")),
            minimumRefreshInterval = Duration.ofMinutes(5),
            maximumCacheAge = Duration.ofMinutes(5))
    }
    val preferences by lazy {
        ConverterPreferences(getSharedPreferences("converter-v1", MODE_PRIVATE))
    }
}

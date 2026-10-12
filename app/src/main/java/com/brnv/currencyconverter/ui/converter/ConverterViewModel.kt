package com.brnv.currencyconverter.ui.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brnv.currencyconverter.data.ConverterPreferences
import com.brnv.currencyconverter.data.SavedConverter
import com.brnv.currencyconverter.domain.ConverterSession
import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.ExchangeRateRepository
import com.brnv.currencyconverter.domain.RateResult
import com.brnv.currencyconverter.domain.ThemeMode
import com.brnv.currencyconverter.domain.Conversion
import com.brnv.currencyconverter.domain.RateSnapshot
import java.io.IOException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConverterUiState(
    val session: ConverterSession = ConverterSession(),
    val favorites: Set<String> = emptySet(),
    val rates: RateResult = RateResult(null),
    val loading: Boolean = true,
    val ready: Boolean = false,
    val saveFailed: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val cryptoRates: RateResult = RateResult(null),
    val cryptoLoading: Boolean = false,
    val conversionRevision: Int = 0,
) {
    val hasCrypto: Boolean get() = session.currencies.any { CurrencyCatalog.find(it)?.isCrypto == true }
    // Calculation-only union. The UI always shows each provider's own date and cache status.
    val conversionRates: RateSnapshot? get() {
        val fiat = rates.snapshot
        val crypto = cryptoRates.snapshot
        return when {
            fiat == null -> crypto
            crypto == null -> fiat
            else -> fiat.copy(rates = fiat.rates + crypto.rates)
        }
    }
}

@OptIn(FlowPreview::class)
class ConverterViewModel(
    private val repository: ExchangeRateRepository,
    private val preferences: ConverterPreferences,
    private val cryptoRepository: ExchangeRateRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ConverterUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = preferences.read()
            mutableState.update { it.copy(session = saved.session, favorites = saved.favorites, theme = saved.theme, ready = true) }
            loadCrypto()
            launch {
                state.map { SavedConverter(it.session, it.favorites, it.theme) }.distinctUntilChanged()
                    .debounce(300).collect { value ->
                        val failed = try { preferences.save(value); false } catch (_: IOException) { true }
                        mutableState.update { it.copy(saveFailed = failed) }
                    }
            }
            val cached = repository.cached()
            mutableState.update { it.copy(rates = RateResult(cached)) }
            loadRates(false)
        }
    }

    fun refresh() {
        if (state.value.loading) return
        mutableState.update { it.copy(loading = true) }
        loadCrypto(force = true)
        viewModelScope.launch { loadRates(true) }
    }

    private suspend fun loadRates(force: Boolean) {
        val result = repository.load(force)
        mutableState.update { it.copy(rates = result, loading = false) }
    }

    private fun change(transform: (ConverterSession) -> ConverterSession) {
        if (state.value.ready) mutableState.update { it.copy(session = transform(it.session)) }
    }

    fun edit(code: String, input: String) {
        val before = state.value.session
        change { it.edit(code, input) }
        if (before != state.value.session && Conversion.parse(input)?.signum() == 1 &&
            state.value.conversionRates?.rates?.containsKey(code) == true) {
            mutableState.update { it.copy(conversionRevision = it.conversionRevision + 1) }
        }
    }
    fun theme(mode: ThemeMode) { mutableState.update { it.copy(theme = mode) } }
    fun select(old: String?, code: String) {
        if (CurrencyCatalog.find(code) == null) return
        change { if (old == null) it.add(code) else it.replace(old, code) }
        loadCrypto()
    }
    private fun loadCrypto(force: Boolean = false) {
        if (!state.value.hasCrypto || state.value.cryptoLoading) return
        mutableState.update { it.copy(cryptoLoading = true) }
        viewModelScope.launch {
            val cached = cryptoRepository.cached()
            mutableState.update { it.copy(cryptoRates = RateResult(cached)) }
            val result = cryptoRepository.load(force)
            mutableState.update { it.copy(cryptoRates = result, cryptoLoading = false) }
        }
    }
    fun remove(code: String) = change { it.remove(code, state.value.conversionRates) }
    fun move(code: String, offset: Int) = change { it.move(code, offset) }
    fun favorite(code: String) {
        mutableState.update {
            it.copy(favorites = if (code in it.favorites) it.favorites - code else it.favorites + code)
        }
    }
}

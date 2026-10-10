package com.brnv.currencyconverter.ui.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brnv.currencyconverter.data.ConverterPreferences
import com.brnv.currencyconverter.data.SavedConverter
import com.brnv.currencyconverter.domain.ConverterSession
import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.ExchangeRateRepository
import com.brnv.currencyconverter.domain.RateResult
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
)

@OptIn(FlowPreview::class)
class ConverterViewModel(
    private val repository: ExchangeRateRepository,
    private val preferences: ConverterPreferences,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ConverterUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = preferences.read()
            mutableState.update { it.copy(session = saved.session, favorites = saved.favorites, ready = true) }
            launch {
                state.map { SavedConverter(it.session, it.favorites) }.distinctUntilChanged()
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
        viewModelScope.launch { loadRates(true) }
    }

    private suspend fun loadRates(force: Boolean) {
        val result = repository.load(force)
        mutableState.update { it.copy(rates = result, loading = false) }
    }

    private fun change(transform: (ConverterSession) -> ConverterSession) {
        if (state.value.ready) mutableState.update { it.copy(session = transform(it.session)) }
    }

    fun edit(code: String, input: String) = change { it.edit(code, input) }
    fun select(old: String?, code: String) {
        if (CurrencyCatalog.find(code) == null) return
        change { if (old == null) it.add(code) else it.replace(old, code) }
    }
    fun remove(code: String) = change { it.remove(code, state.value.rates.snapshot) }
    fun move(code: String, offset: Int) = change { it.move(code, offset) }
    fun favorite(code: String) {
        mutableState.update {
            it.copy(favorites = if (code in it.favorites) it.favorites - code else it.favorites + code)
        }
    }
}

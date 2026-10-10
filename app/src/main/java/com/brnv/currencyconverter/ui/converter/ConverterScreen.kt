package com.brnv.currencyconverter.ui.converter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.CurrencyInfo
import com.brnv.currencyconverter.ui.theme.BrnvRed
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    state: ConverterUiState,
    onEdit: (String, String) -> Unit,
    onSelect: (String?, String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    // Empty string means add; a currency code means replace; null means closed.
    var selecting by rememberSaveable { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    val focus = LocalFocusManager.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().imePadding().testTag("converter_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = BrnvRed, shape = RoundedCornerShape(14.dp)) {
                            Text("B", Modifier.padding(horizontal = 17.dp, vertical = 10.dp),
                                color = Color.White, style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold)
                        }
                        Column {
                            Text("BRNV", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.converter_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.converter_hint), Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
                item { RateStatus(state, onRefresh) }
                items(state.session.currencies, key = { it }) { code ->
                    val currency = CurrencyCatalog.find(code) ?: return@items
                    CurrencyCard(currency, state, { onEdit(code, it) },
                        { focus.clearFocus(); selecting = code }, { onRemove(code) },
                        { onMove(code, it) })
                }
                item {
                    OutlinedButton(
                        onClick = { focus.clearFocus(); selecting = "" },
                        enabled = state.ready && state.session.currencies.size < CurrencyCatalog.all.size,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add_currency"),
                    ) { Text(stringResource(R.string.add_currency)) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.rate_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { uri.openUri("https://www.exchangerate-api.com") }) {
                            Text(stringResource(R.string.rate_attribution))
                        }
                        TextButton(onClick = { uri.openUri("https://brnvconverter.com") }) {
                            Text("brnvconverter.com")
                        }
                    }
                }
            }
        }
    }
    selecting?.let { old ->
        CurrencySelector(state, onFavorite, onDismiss = { selecting = null }) { code ->
            onSelect(old.takeIf { it.isNotEmpty() }, code)
            selecting = null
        }
    }
}

@Composable
private fun RateStatus(state: ConverterUiState, onRefresh: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val snapshot = state.rates.snapshot
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(when {
                state.loading -> R.string.rates_loading
                snapshot == null -> R.string.rates_unavailable
                state.rates.refreshFailed -> R.string.rates_offline
                state.rates.cached -> R.string.rates_cached
                else -> R.string.rates_loaded
            }), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge)
            if (snapshot != null) {
                val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(locale).withZone(ZoneId.systemDefault()).format(snapshot.updatedAt)
                Text(stringResource(R.string.updated_at, date), style = MaterialTheme.typography.bodySmall)
            } else if (!state.loading) {
                Text(stringResource(R.string.first_connection), style = MaterialTheme.typography.bodySmall)
            }
            if (state.rates.cacheWriteFailed || state.saveFailed) {
                Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRefresh, enabled = !state.loading) {
                Text(stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun CurrencyCard(
    currency: CurrencyInfo,
    state: ConverterUiState,
    onEdit: (String) -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var menu by remember { mutableStateOf(false) }
    val code = currency.code
    val index = state.session.currencies.indexOf(code)
    val unavailable = state.rates.snapshot?.rates?.containsKey(code) == false
    Card(shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChoose, modifier = Modifier.weight(1f), enabled = state.ready) {
                    Text("${currency.flag}  $code  ▾", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth())
                }
                Box {
                    val description = stringResource(R.string.currency_options, code)
                    IconButton(onClick = { menu = true }, enabled = state.ready,
                        modifier = Modifier.semantics { contentDescription = description }) {
                        Text("⋮", style = MaterialTheme.typography.headlineSmall)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) },
                            enabled = index > 0, onClick = { onMove(-1); menu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) },
                            enabled = index < state.session.currencies.lastIndex,
                            onClick = { onMove(1); menu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_currency)) },
                            enabled = state.session.currencies.size > 2,
                            onClick = { onRemove(); menu = false })
                    }
                }
            }
            Text(currency.name(locale), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.session.amount(code, state.rates.snapshot), onValueChange = onEdit,
                modifier = Modifier.fillMaxWidth().testTag("amount_$code"),
                enabled = state.ready, singleLine = true,
                label = { Text(stringResource(R.string.amount_in, code)) },
                placeholder = { Text(if (state.rates.snapshot == null) "—" else "0") },
                suffix = { Text(currency.symbol) },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                isError = unavailable,
                supportingText = if (unavailable) ({ Text(stringResource(R.string.missing_rate)) }) else null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySelector(
    state: ConverterUiState,
    onFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val currencies = remember(query, state.favorites) {
        CurrencyCatalog.all.filter { it.matches(query) }
            .sortedByDescending { it.code in state.favorites }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.choose_currency), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
            OutlinedTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("currency_search"),
                label = { Text(stringResource(R.string.search_currency)) }, singleLine = true,
                shape = RoundedCornerShape(16.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (currencies.isEmpty()) item { Text(stringResource(R.string.no_results)) }
                items(currencies, key = { it.code }) { currency ->
                    val selected = currency.code in state.session.currencies
                    val favorite = currency.code in state.favorites
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSelect(currency.code) }, enabled = !selected,
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("select_${currency.code}")) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("${currency.flag}  ${currency.code} · ${currency.symbol}",
                                    style = MaterialTheme.typography.titleMedium)
                                Text(currency.name(locale), style = MaterialTheme.typography.bodyMedium)
                                if (selected) Text(stringResource(R.string.already_selected),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val description = stringResource(
                            if (favorite) R.string.remove_favorite else R.string.add_favorite, currency.code)
                        IconToggleButton(checked = favorite, onCheckedChange = { onFavorite(currency.code) },
                            modifier = Modifier.semantics { contentDescription = description }) {
                            Text(if (favorite) "★" else "☆", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

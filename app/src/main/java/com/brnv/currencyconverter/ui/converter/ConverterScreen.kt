package com.brnv.currencyconverter.ui.converter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.CurrencyCatalog
import com.brnv.currencyconverter.domain.CurrencyInfo
import com.brnv.currencyconverter.domain.ThemeMode
import com.brnv.currencyconverter.domain.RateResult
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConverterScreen(
    state: ConverterUiState,
    onEdit: (String, String) -> Unit,
    onSelect: (String?, String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    selectedLanguage: String = "",
    onLanguage: (String) -> Unit = {},
    onTheme: (ThemeMode) -> Unit = {},
    onReview: () -> Unit = {},
    onReviewOpportunity: () -> Unit = {},
) {
    // Empty string means add; a currency code means replace; null means closed.
    var selecting by rememberSaveable { mutableStateOf<String?>(null) }
    var appearanceMenu by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val reviewOpportunity by rememberUpdatedState(onReviewOpportunity)
    LaunchedEffect(state.conversionRevision, state.ready, selecting, appearanceMenu, imeVisible, lifecycle) {
        if (state.ready && selecting == null && !appearanceMenu && !imeVisible && lifecycle == Lifecycle.State.RESUMED) {
            delay(15_000)
            reviewOpportunity()
        }
    }
    val uri = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val refreshLabel = stringResource(R.string.refresh)
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val showNotice = state.rates.needsNotice() || (state.hasCrypto && state.cryptoRates.needsNotice()) ||
        state.rates.cacheWriteFailed || state.cryptoRates.cacheWriteFailed || state.saveFailed
    val mainKeys = listOf("header", "intro", "add") + state.session.currencies + if (showNotice) listOf("status") else emptyList()
    fun measured(key: String) = Modifier.onSizeChanged { itemHeights[key] = it.height }
    // Fill the remaining viewport with whitespace; the footer always starts below the fold.
    val footerGap = with(density) {
        (viewportHeight - mainKeys.sumOf { itemHeights[it] ?: 0 }).toDp()
    } - 24.dp - (14.dp * mainKeys.size)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(isRefreshing = state.loading || state.cryptoLoading, onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding).semantics {
                customActions = listOf(CustomAccessibilityAction(refreshLabel) { onRefresh(); true })
            }, contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight().imePadding()
                    .onSizeChanged { viewportHeight = it.height }.testTag("converter_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Box(measured("header")) { AppHeader(state.theme, selectedLanguage, onLanguage, onTheme) { appearanceMenu = it } }
                }
                item {
                    Text(stringResource(R.string.converter_hint), modifier = measured("intro"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showNotice) {
                    item { Box(measured("status")) { RateStatus(state) } }
                }
                items(state.session.currencies, key = { it }) { code ->
                    val currency = CurrencyCatalog.find(code) ?: return@items
                    Box(measured(code).animateItem(fadeInSpec = tween(180), placementSpec = tween(220), fadeOutSpec = tween(150))) {
                        CurrencyCard(currency, state, { onEdit(code, it) },
                            { focus.clearFocus(); selecting = code }, { onRemove(code) },
                            { onMove(code, it) })
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { focus.clearFocus(); selecting = "" },
                        enabled = state.ready && state.session.currencies.size < CurrencyCatalog.all.size,
                        modifier = measured("add").fillMaxWidth().heightIn(min = 52.dp).testTag("add_currency"),
                    ) { Text(stringResource(R.string.add_currency)) }
                }
                item {
                    Column(Modifier.padding(top = footerGap.coerceAtLeast(0.dp) + 40.dp).testTag("footer"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.rate_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onReview, modifier = Modifier.weight(1f).testTag("rate_app"),
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text(stringResource(R.string.footer_rate), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            TextButton(onClick = { uri.openUri("https://brnvconverter.com") }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                                Text(stringResource(R.string.footer_site), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            TextButton(onClick = { uri.openUri("https://brnvconverter.com/donate.html") }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text(stringResource(R.string.footer_support), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
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
private fun RateStatus(state: ConverterUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.rates.needsNotice() || (state.hasCrypto && state.cryptoRates.needsNotice())) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.rates.needsNotice()) ProviderStatus(state.rates, state.loading, if (state.hasCrypto) R.string.fiat else null)
                    if (state.hasCrypto && state.cryptoRates.needsNotice()) ProviderStatus(state.cryptoRates, state.cryptoLoading, R.string.crypto)
                }
            }
        }
        if (state.rates.cacheWriteFailed || state.cryptoRates.cacheWriteFailed || state.saveFailed) {
            Text(stringResource(R.string.save_failed), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun RateResult.needsNotice() = snapshot == null || cached || refreshFailed

@Composable
private fun ProviderStatus(result: RateResult, loading: Boolean, title: Int?) {
    val snapshot = result.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val status = stringResource(when {
            loading -> R.string.rates_loading
            snapshot == null -> R.string.rates_unavailable
            result.refreshFailed -> R.string.rates_offline
            else -> R.string.rates_cached
        })
        Text(if (title == null) status else "${stringResource(title)} · $status",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelMedium)
        if (snapshot == null && !loading) {
            Text(stringResource(R.string.first_connection), style = MaterialTheme.typography.bodySmall)
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
    val unavailable = !state.loading && !state.cryptoLoading && state.conversionRates?.rates?.containsKey(code) != true
    Card(shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChoose, modifier = Modifier.weight(1f), enabled = state.ready,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text("${currency.flag}  $code  ▾", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth())
                }
                Box {
                    val description = stringResource(R.string.currency_options, code)
                    FilledTonalIconButton(onClick = { menu = true }, enabled = state.ready,
                        shape = RoundedCornerShape(14.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.testTag("actions_$code").semantics { contentDescription = description }) {
                        Icon(painterResource(R.drawable.ic_currency_actions), null, Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp, shadowElevation = 4.dp) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) },
                            leadingIcon = { Text("↑", style = MaterialTheme.typography.titleLarge) },
                            enabled = index > 0, onClick = { onMove(-1); menu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) },
                            leadingIcon = { Text("↓", style = MaterialTheme.typography.titleLarge) },
                            enabled = index < state.session.currencies.lastIndex,
                            onClick = { onMove(1); menu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_currency)) },
                            leadingIcon = { Text("−", style = MaterialTheme.typography.titleLarge) },
                            enabled = state.session.currencies.size > 2,
                            onClick = { onRemove(); menu = false })
                    }
                }
            }
            Text(currency.name(locale), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.session.amount(code, state.conversionRates), onValueChange = onEdit,
                modifier = Modifier.fillMaxWidth().testTag("amount_$code"),
                enabled = state.ready, singleLine = true,
                label = { Text(stringResource(R.string.amount_in, code)) },
                placeholder = { Text(if (state.conversionRates == null) "—" else "0") },
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
    var category by rememberSaveable { mutableIntStateOf(0) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun dismiss(afterHide: () -> Unit = onDismiss) {
        scope.launch { sheet.hide(); afterHide() }
    }
    val locale = LocalConfiguration.current.locales[0]
    val currencies = remember(query, state.favorites, category, locale) {
        CurrencyCatalog.all.filter { it.matches(query, locale) &&
            (category == 0 || (category == 1 && !it.isCrypto) || (category == 2 && it.isCrypto)) }
            .sortedByDescending { it.code in state.favorites }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.choose_currency), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.close)) }
            }
            OutlinedTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("currency_search"),
                label = { Text(stringResource(R.string.search_currency)) }, singleLine = true,
                shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.all_currencies, R.string.fiat, R.string.crypto).forEachIndexed { index, label ->
                    FilterChip(selected = category == index, onClick = { category = index },
                        label = { Text(stringResource(label)) })
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (currencies.isEmpty()) item { Text(stringResource(R.string.no_results)) }
                items(currencies, key = { it.code }) { currency ->
                    val selected = currency.code in state.session.currencies
                    val favorite = currency.code in state.favorites
                    ListItem(
                        modifier = Modifier.testTag("select_${currency.code}")
                            .clickable(enabled = !selected) { dismiss { onSelect(currency.code) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(12.dp)) {
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Text(currency.flag, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        },
                        headlineContent = { Text(currency.code, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Column {
                                Text(currency.name(locale), style = MaterialTheme.typography.bodyMedium)
                                if (selected) Text(stringResource(R.string.already_selected),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        trailingContent = {
                        val description = stringResource(
                            if (favorite) R.string.remove_favorite else R.string.add_favorite, currency.code)
                        IconToggleButton(checked = favorite, onCheckedChange = { onFavorite(currency.code) },
                            modifier = Modifier.semantics { contentDescription = description }) {
                            Text(if (favorite) "★" else "☆", style = MaterialTheme.typography.headlineSmall)
                        }
                        },
                    )
                }
            }
        }
    }
}

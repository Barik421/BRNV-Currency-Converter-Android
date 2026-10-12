package com.brnv.currencyconverter.ui.converter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.AppLanguages
import com.brnv.currencyconverter.domain.ThemeMode

@Composable
internal fun AppHeader(
    theme: ThemeMode,
    selectedLanguage: String,
    onLanguage: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onMenuVisibility: (Boolean) -> Unit,
) {
    var languages by rememberSaveable { mutableStateOf(false) }
    var themes by rememberSaveable { mutableStateOf(false) }
    val languageVisibility = remember { MutableTransitionState(false) }
    languageVisibility.targetState = languages
    val languageMotion = rememberTransition(languageVisibility, label = "language dialog")
    val opacity by languageMotion.animateFloat(transitionSpec = { tween(160) }, label = "opacity") { if (it) 1f else 0f }
    val scale by languageMotion.animateFloat(transitionSpec = { tween(200) }, label = "scale") { if (it) 1f else 0.96f }
    LaunchedEffect(languages, themes) { onMenuVisibility(languages || themes) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.brnv_mark), contentDescription = null,
            tint = Color.Unspecified, modifier = Modifier.size(42.dp))
        Column(Modifier.weight(1f)) {
            Text("BRNV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.converter_title), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val languageLabel = stringResource(R.string.language)
        TextButton(onClick = { languages = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.heightIn(min = 48.dp).testTag("language_menu")
                .semantics { contentDescription = languageLabel }) {
            val locale = LocalConfiguration.current.locales[0]
            Text(locale.language.uppercase(java.util.Locale.ROOT))
        }
        Box {
            IconButton(onClick = { themes = true }, modifier = Modifier.testTag("theme_menu")) {
                Icon(painterResource(R.drawable.ic_appearance), stringResource(R.string.appearance),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = themes, onDismissRequest = { themes = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp, shadowElevation = 4.dp) {
                ThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(stringResource(mode.label())) },
                        trailingIcon = { if (theme == mode) Text("✓") },
                        modifier = Modifier.testTag("theme_${mode.name}"),
                        onClick = { themes = false; onTheme(mode) })
                }
            }
        }
    }
    if (languageVisibility.currentState || languageVisibility.targetState) {
        AlertDialog(onDismissRequest = { languages = false },
            modifier = Modifier.graphicsLayer { alpha = opacity; scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.language)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item {
                        LanguageRow("", stringResource(R.string.follow_device), selectedLanguage) {
                            languages = false; onLanguage(it)
                        }
                    }
                    items(AppLanguages.supported.entries.toList(), key = { it.key }) { entry ->
                        LanguageRow(entry.key, entry.value, selectedLanguage) {
                            languages = false; onLanguage(it)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languages = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun LanguageRow(tag: String, label: String, selected: String, onClick: (String) -> Unit) {
    val checked = tag == selected || (tag == "zh-Hans" && selected.startsWith("zh"))
    Surface(onClick = { onClick(tag) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("language_$tag"),
        color = Color.Transparent) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            if (checked) Text("✓", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.follow_device
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

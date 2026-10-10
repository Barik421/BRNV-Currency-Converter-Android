package com.brnv.currencyconverter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrnvRedStrong, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD8), onPrimaryContainer = Color(0xFF680017),
    secondary = BrnvRedStrong,
    background = Color(0xFFF9F9FB), surface = Color.White,
    surfaceVariant = Color(0xFFF3F3F5), surfaceContainer = Color(0xFFF3F3F5),
    onSurface = Color(0xFF1A1C1D), onBackground = Color(0xFF1A1C1D),
    onSurfaceVariant = Color(0xFF636264), outline = Color(0xFF797477),
)
private val DarkColors = darkColorScheme(
    primary = BrnvRedDark, onPrimary = Color(0xFF250006),
    primaryContainer = Color(0xFF541D26), onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = BrnvRedDark,
    background = Color(0xFF121212), surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF242426), surfaceContainer = Color(0xFF242426),
    onSurface = Color(0xFFF7F7F8), onBackground = Color(0xFFF7F7F8),
    onSurfaceVariant = Color(0xFFB9B7BA), outline = Color(0xFF938D90),
)

@Composable
fun BRNVCurrencyConverterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)
}

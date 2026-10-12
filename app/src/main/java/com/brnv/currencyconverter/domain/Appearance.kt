package com.brnv.currencyconverter.domain

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object AppLanguages {
    val supported = linkedMapOf(
        "en" to "English", "uk" to "Українська", "de" to "Deutsch", "pl" to "Polski",
        "fr" to "Français", "it" to "Italiano", "es" to "Español", "pt" to "Português", "zh-Hans" to "简体中文",
    )
}

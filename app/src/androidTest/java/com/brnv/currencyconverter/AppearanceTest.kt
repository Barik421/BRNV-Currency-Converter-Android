package com.brnv.currencyconverter

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import com.brnv.currencyconverter.domain.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun restoreDeviceLanguage() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        compose.waitForIdle()
    }

    @Test fun languagePickerChangesResourcesAndCanReturnToDevice() {
        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_fr").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Convertisseur de devises").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Convertisseur de devises").assertExists()
        compose.onNodeWithTag("language_menu").performClick()
        compose.onNodeWithTag("language_").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
    }

    @Test fun themePickerPersistsAndProvidesSystemMode() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("amount_EUR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("theme_menu").performClick()
        compose.onNodeWithTag("theme_DARK").performClick()
        val app = compose.activity.application as BrnvApplication
        compose.waitUntil(5_000) { runBlocking { app.preferences.read().theme } == ThemeMode.DARK }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, runBlocking { app.preferences.read().theme })
        compose.onNodeWithTag("theme_menu").performClick()
        compose.onNodeWithTag("theme_SYSTEM").performClick()
        compose.waitUntil(5_000) { runBlocking { app.preferences.read().theme } == ThemeMode.SYSTEM }
    }
}

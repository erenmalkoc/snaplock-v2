package com.erenium.snaplock.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.GeneratorPrefsState
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.model.PassphraseOptions
import com.erenium.snaplock.domain.model.PasswordOptions
import com.erenium.snaplock.domain.model.AppSettings
import com.erenium.snaplock.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun freshInstallUsesBrandColoursSystemThemeAndThirtySecondLock() {
        val settings = SettingsPrefs(context).settings.value

        assertFalse(settings.dynamicColor)
        assertEquals(30, settings.autoLockSeconds)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun existingInstallKeepsTheOldDefaultsItNeverChanged() {
        context.getSharedPreferences("snaplock_settings", Context.MODE_PRIVATE)
            .edit().remove("defaults_version").putString("theme_mode", ThemeMode.DARK.name).commit()

        val settings = SettingsPrefs(context).settings.value

        assertEquals(0, settings.autoLockSeconds)
        assertTrue(settings.dynamicColor)
        assertEquals(ThemeMode.DARK, settings.themeMode)
    }

    @Test
    fun existingInstallKeepsWhatItSaved() {
        context.getSharedPreferences("snaplock_settings", Context.MODE_PRIVATE)
            .edit().remove("defaults_version").putInt("auto_lock_seconds", 300).putBoolean("dynamic_color", false).commit()

        val settings = SettingsPrefs(context).settings.value

        assertEquals(300, settings.autoLockSeconds)
        assertFalse(settings.dynamicColor)
    }

    @Test
    fun generatorOptionsSurviveANewInstance() {
        val state = GeneratorPrefsState(
            passphraseMode = true,
            password = PasswordOptions(length = 40, useLowercase = false, useSymbols = true),
            passphrase = PassphraseOptions(wordCount = 7, separator = ".", capitalize = false, includeNumber = false)
        )
        SettingsPrefs(context).writeGenerator(state)

        assertEquals(state, SettingsPrefs(context).readGenerator())
    }

    @Test
    fun generatorStartsFromTheOriginalDefaults() {
        val state = SettingsPrefs(context).readGenerator()

        assertFalse(state.passphraseMode)
        assertEquals(PasswordOptions(), state.password)
        assertEquals(PassphraseOptions(), state.passphrase)
    }

    @Test
    fun changesSurviveANewInstance() {
        SettingsPrefs(context).apply {
            setDynamicColor(false)
            setThemeMode(ThemeMode.DARK)
            setAutoLockSeconds(60)
            setAutoSiteIcons(true)
        }

        val settings = SettingsPrefs(context).settings.value
        assertTrue(settings.autoSiteIcons)

        assertFalse(settings.dynamicColor)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(60, settings.autoLockSeconds)
    }
}

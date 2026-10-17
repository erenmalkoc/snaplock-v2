package com.erenium.snaplock.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
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
    fun freshInstallUsesWallpaperColoursAndSystemTheme() {
        val settings = SettingsPrefs(context).settings.value

        assertTrue(settings.dynamicColor)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun changesSurviveANewInstance() {
        SettingsPrefs(context).apply {
            setDynamicColor(false)
            setThemeMode(ThemeMode.DARK)
            setAutoLockSeconds(60)
        }

        val settings = SettingsPrefs(context).settings.value

        assertFalse(settings.dynamicColor)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(60, settings.autoLockSeconds)
    }
}

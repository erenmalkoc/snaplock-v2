package com.erenium.snaplock.presentation

import com.erenium.snaplock.domain.model.ThemeMode
import com.erenium.snaplock.ui.isDarkTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarsTest {
    @Test
    fun chosenThemeOverridesTheSystemOne() {
        assertTrue(isDarkTheme(ThemeMode.DARK, systemIsDark = false))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemIsDark = true))
    }

    @Test
    fun systemThemeFollowsTheSystem() {
        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemIsDark = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemIsDark = false))
    }
}

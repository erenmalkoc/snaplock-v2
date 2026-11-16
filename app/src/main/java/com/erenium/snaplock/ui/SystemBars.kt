package com.erenium.snaplock.ui

import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.model.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

fun isDarkTheme(mode: ThemeMode, systemIsDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun ComponentActivity.followAppTheme(settingsPrefs: SettingsPrefs) {
    applyBars(settingsPrefs.settings.value.themeMode)
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            settingsPrefs.settings.map { it.themeMode }.distinctUntilChanged().collect(::applyBars)
        }
    }
}

private fun ComponentActivity.applyBars(mode: ThemeMode) {
    val systemIsDark =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val style = if (isDarkTheme(mode, systemIsDark)) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

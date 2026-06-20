package com.erenium.snaplock.data.datasource.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.erenium.snaplock.domain.model.AppSettings
import com.erenium.snaplock.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        _settings.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
        _settings.update { it.copy(dynamicColor = enabled) }
    }

    fun setClipboardAutoClear(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CLIPBOARD_AUTO_CLEAR, enabled) }
        _settings.update { it.copy(clipboardAutoClear = enabled) }
    }

    fun setClipboardTimeoutSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_CLIPBOARD_TIMEOUT, seconds) }
        _settings.update { it.copy(clipboardTimeoutSeconds = seconds) }
    }

    fun setAutoLockSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_AUTO_LOCK, seconds) }
        _settings.update { it.copy(autoLockSeconds = seconds) }
    }

    private fun readSettings(): AppSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val themeMode = runCatching { ThemeMode.valueOf(themeName ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        return AppSettings(
            themeMode = themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            clipboardAutoClear = prefs.getBoolean(KEY_CLIPBOARD_AUTO_CLEAR, true),
            clipboardTimeoutSeconds = prefs.getInt(KEY_CLIPBOARD_TIMEOUT, DEFAULT_CLIPBOARD_TIMEOUT),
            autoLockSeconds = prefs.getInt(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK)
        )
    }

    private companion object {
        const val PREFS_NAME = "snaplock_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_CLIPBOARD_AUTO_CLEAR = "clipboard_auto_clear"
        const val KEY_CLIPBOARD_TIMEOUT = "clipboard_timeout_seconds"
        const val KEY_AUTO_LOCK = "auto_lock_seconds"
        const val DEFAULT_CLIPBOARD_TIMEOUT = 30
        const val DEFAULT_AUTO_LOCK = 0
    }
}

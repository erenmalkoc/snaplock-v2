package com.erenium.snaplock.data.datasource.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.erenium.snaplock.domain.model.AppSettings
import com.erenium.snaplock.domain.model.PassphraseOptions
import com.erenium.snaplock.domain.model.PasswordOptions
import com.erenium.snaplock.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratorPrefsState(
    val passphraseMode: Boolean,
    val password: PasswordOptions,
    val passphrase: PassphraseOptions
)

@Singleton
class SettingsPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        pinLegacyDefaults(context)
    }

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

    fun setBreachCheckEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BREACH_CHECK, enabled) }
        _settings.update { it.copy(breachCheckEnabled = enabled) }
    }

    fun setAutoSiteIcons(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SITE_ICONS, enabled) }
        _settings.update { it.copy(autoSiteIcons = enabled) }
    }

    fun readGenerator(): GeneratorPrefsState {
        val password = PasswordOptions()
        val passphrase = PassphraseOptions()
        val mode = prefs.getString(KEY_GEN_MODE, null)
        return GeneratorPrefsState(
            passphraseMode = mode == GEN_MODE_PASSPHRASE,
            password = PasswordOptions(
                length = prefs.getInt(KEY_GEN_LENGTH, password.length),
                useLowercase = prefs.getBoolean(KEY_GEN_LOWERCASE, password.useLowercase),
                useUppercase = prefs.getBoolean(KEY_GEN_UPPERCASE, password.useUppercase),
                useDigits = prefs.getBoolean(KEY_GEN_DIGITS, password.useDigits),
                useSymbols = prefs.getBoolean(KEY_GEN_SYMBOLS, password.useSymbols)
            ),
            passphrase = PassphraseOptions(
                wordCount = prefs.getInt(KEY_GEN_WORDS, passphrase.wordCount),
                separator = prefs.getString(KEY_GEN_SEPARATOR, null) ?: passphrase.separator,
                capitalize = prefs.getBoolean(KEY_GEN_CAPITALIZE, passphrase.capitalize),
                includeNumber = prefs.getBoolean(KEY_GEN_NUMBER, passphrase.includeNumber)
            )
        )
    }

    fun writeGenerator(state: GeneratorPrefsState) {
        prefs.edit {
            putString(KEY_GEN_MODE, if (state.passphraseMode) GEN_MODE_PASSPHRASE else GEN_MODE_PASSWORD)
            putInt(KEY_GEN_LENGTH, state.password.length)
            putBoolean(KEY_GEN_LOWERCASE, state.password.useLowercase)
            putBoolean(KEY_GEN_UPPERCASE, state.password.useUppercase)
            putBoolean(KEY_GEN_DIGITS, state.password.useDigits)
            putBoolean(KEY_GEN_SYMBOLS, state.password.useSymbols)
            putInt(KEY_GEN_WORDS, state.passphrase.wordCount)
            putString(KEY_GEN_SEPARATOR, state.passphrase.separator)
            putBoolean(KEY_GEN_CAPITALIZE, state.passphrase.capitalize)
            putBoolean(KEY_GEN_NUMBER, state.passphrase.includeNumber)
        }
    }

    private fun pinLegacyDefaults(context: Context) {
        if (prefs.contains(KEY_DEFAULTS_VERSION)) return
        val existingInstall = prefs.all.isNotEmpty() || wasUpdated(context)
        prefs.edit {
            if (existingInstall) {
                if (!prefs.contains(KEY_AUTO_LOCK)) putInt(KEY_AUTO_LOCK, LEGACY_AUTO_LOCK)
                if (!prefs.contains(KEY_DYNAMIC_COLOR)) putBoolean(KEY_DYNAMIC_COLOR, true)
            }
            putInt(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION)
        }
    }

    private fun wasUpdated(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime != info.lastUpdateTime
    }.getOrDefault(true)

    private fun readSettings(): AppSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val themeMode = runCatching { ThemeMode.valueOf(themeName ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        return AppSettings(
            themeMode = themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            clipboardAutoClear = prefs.getBoolean(KEY_CLIPBOARD_AUTO_CLEAR, true),
            clipboardTimeoutSeconds = prefs.getInt(KEY_CLIPBOARD_TIMEOUT, DEFAULT_CLIPBOARD_TIMEOUT),
            autoLockSeconds = prefs.getInt(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK),
            breachCheckEnabled = prefs.getBoolean(KEY_BREACH_CHECK, false),
            autoSiteIcons = prefs.getBoolean(KEY_AUTO_SITE_ICONS, false)
        )
    }

    private companion object {
        const val PREFS_NAME = "snaplock_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_CLIPBOARD_AUTO_CLEAR = "clipboard_auto_clear"
        const val KEY_CLIPBOARD_TIMEOUT = "clipboard_timeout_seconds"
        const val KEY_AUTO_LOCK = "auto_lock_seconds"
        const val KEY_BREACH_CHECK = "breach_check_enabled"
        const val KEY_AUTO_SITE_ICONS = "auto_site_icons"
        const val DEFAULT_CLIPBOARD_TIMEOUT = 30
        const val DEFAULT_AUTO_LOCK = 30
        const val LEGACY_AUTO_LOCK = 0
        const val KEY_DEFAULTS_VERSION = "defaults_version"
        const val DEFAULTS_VERSION = 1
        const val KEY_GEN_MODE = "gen_mode"
        const val GEN_MODE_PASSWORD = "password"
        const val GEN_MODE_PASSPHRASE = "passphrase"
        const val KEY_GEN_LENGTH = "gen_length"
        const val KEY_GEN_LOWERCASE = "gen_lowercase"
        const val KEY_GEN_UPPERCASE = "gen_uppercase"
        const val KEY_GEN_DIGITS = "gen_digits"
        const val KEY_GEN_SYMBOLS = "gen_symbols"
        const val KEY_GEN_WORDS = "gen_words"
        const val KEY_GEN_SEPARATOR = "gen_separator"
        const val KEY_GEN_CAPITALIZE = "gen_capitalize"
        const val KEY_GEN_NUMBER = "gen_number"
    }
}

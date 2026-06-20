package com.erenium.snaplock.presentation.settings

import androidx.lifecycle.ViewModel
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.model.AppSettings
import com.erenium.snaplock.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPrefs: SettingsPrefs
) : ViewModel() {

    val uiState: StateFlow<AppSettings> = settingsPrefs.settings

    fun onThemeModeChange(mode: ThemeMode) = settingsPrefs.setThemeMode(mode)

    fun onDynamicColorChange(enabled: Boolean) = settingsPrefs.setDynamicColor(enabled)

    fun onClipboardAutoClearChange(enabled: Boolean) = settingsPrefs.setClipboardAutoClear(enabled)

    fun onClipboardTimeoutChange(seconds: Int) = settingsPrefs.setClipboardTimeoutSeconds(seconds)

    fun onAutoLockChange(seconds: Int) = settingsPrefs.setAutoLockSeconds(seconds)
}

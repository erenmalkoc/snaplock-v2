package com.erenium.snaplock.data.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var clearJob: Job? = null

    fun copyToClipboard(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()

        val settings = settingsPrefs.settings.value
        if (!settings.clipboardAutoClear) return

        clearJob = scope.launch {
            delay(settings.clipboardTimeoutSeconds * MILLIS_PER_SECOND)
            val emptyClip = ClipData.newPlainText("", "")
            clipboard.setPrimaryClip(emptyClip)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

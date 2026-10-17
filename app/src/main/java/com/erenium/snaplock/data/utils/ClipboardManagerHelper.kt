package com.erenium.snaplock.data.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
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
    @param:ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var clearJob: Job? = null

    /**
     * @param sensitive hides the text from the system's copy preview and asks keyboards not
     * to keep it in their clipboard history.
     */
    fun copyToClipboard(label: String, text: String, sensitive: Boolean = true) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
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

        // ClipDescription.EXTRA_IS_SENSITIVE from Android 13; older keyboards that honour
        // the flag read the same key, so it is set on every version.
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}

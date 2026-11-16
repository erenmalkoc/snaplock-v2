package com.erenium.snaplock.data.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
    private var ownedClipToken: Long? = null

    /**
     * @param sensitive hides the text from the system's copy preview and asks keyboards not
     * to keep it in their clipboard history.
     */
    fun copyToClipboard(label: String, text: String, sensitive: Boolean = true) {
        val clip = ClipData.newPlainText(label, text)
        val token = System.nanoTime()
        clip.description.extras = PersistableBundle().apply {
            putLong(EXTRA_CLIP_TOKEN, token)
            if (sensitive) putBoolean(EXTRA_IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        ownedClipToken = token
        clearJob?.cancel()

        val settings = settingsPrefs.settings.value
        if (!settings.clipboardAutoClear) return

        clearJob = scope.launch {
            delay(settings.clipboardTimeoutSeconds * MILLIS_PER_SECOND)
            clearIfStillOurs(token)
        }
    }

    /** Seconds until a copied value is removed, or null when auto-clear is off. */
    fun autoClearSeconds(): Int? =
        settingsPrefs.settings.value.takeIf { it.clipboardAutoClear }?.clipboardTimeoutSeconds

    private fun clearIfStillOurs(token: Long) {
        if (ownedClipToken != token) return
        ownedClipToken = null
        val current = clipboard.primaryClipDescription?.extras?.getLong(EXTRA_CLIP_TOKEN)
        if (current != token) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L

        // ClipDescription.EXTRA_IS_SENSITIVE from Android 13; older keyboards that honour
        // the flag read the same key, so it is set on every version.
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        const val EXTRA_CLIP_TOKEN = "com.erenium.snaplock.clip_token"
    }
}

package com.erenium.snaplock.autofill

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.core.net.toUri
import com.erenium.snaplock.credentials.SnaplockCredentialProviderService

/** How far Snaplock is set up as the device's password service. */
enum class AutofillSetup {
    /** Autofill, and on Android 14+ also passkeys and the sign-in sheet. */
    ENABLED,

    /** Autofill only: Android 14+ still sends passkeys and the sign-in sheet elsewhere. */
    PASSKEYS_OFF,
    DISABLED
}

/** Whether Snaplock is the system autofill service and credential provider, and how to make it one. */
object AutofillStatus {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun isEnabled(context: Context): Boolean =
        isSupported() &&
            context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true

    fun setup(context: Context): AutofillSetup = setupOf(
        autofill = isEnabled(context),
        credentialManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        provider = isCredentialProvider(context)
    )

    internal fun setupOf(autofill: Boolean, credentialManager: Boolean, provider: Boolean): AutofillSetup = when {
        !autofill -> AutofillSetup.DISABLED
        credentialManager && !provider -> AutofillSetup.PASSKEYS_OFF
        else -> AutofillSetup.ENABLED
    }

    private fun isCredentialProvider(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val manager = context.getSystemService(android.credentials.CredentialManager::class.java) ?: return false
        return runCatching {
            manager.isEnabledCredentialProviderService(ComponentName(context, SnaplockCredentialProviderService::class.java))
        }.getOrDefault(false)
    }

    /**
     * System screens for choosing Snaplock, best first; start the first one that opens. On
     * Android 14+ that is the passwords, passkeys and autofill page: picking Snaplock as the
     * preferred service there turns on autofill and Credential Manager together, which the
     * older autofill picker does not.
     */
    // The page's action is public from Android 15, but Android 14's settings already open it;
    // where they do not, the autofill picker that follows still works.
    @SuppressLint("InlinedApi")
    fun enableIntents(context: Context): List<Intent> {
        val app = "package:${context.packageName}".toUri()
        val autofillPicker = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, app)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(Intent(Settings.ACTION_CREDENTIAL_PROVIDER, app), autofillPicker)
        } else {
            listOf(autofillPicker)
        }
    }
}

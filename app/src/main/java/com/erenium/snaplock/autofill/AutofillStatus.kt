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

enum class AutofillSetup {
    ENABLED,

    PASSKEYS_OFF,
    DISABLED
}

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

    internal fun setupOf(autofill: Boolean, credentialManager: Boolean, provider: Boolean?): AutofillSetup = when {
        !autofill -> AutofillSetup.DISABLED
        credentialManager && provider == false -> AutofillSetup.PASSKEYS_OFF
        else -> AutofillSetup.ENABLED
    }

    private fun isCredentialProvider(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val manager = context.getSystemService(android.credentials.CredentialManager::class.java) ?: return null
        return runCatching {
            manager.isEnabledCredentialProviderService(ComponentName(context, SnaplockCredentialProviderService::class.java))
        }.getOrNull()
    }

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

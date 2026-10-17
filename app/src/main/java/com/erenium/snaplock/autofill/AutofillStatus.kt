package com.erenium.snaplock.autofill

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.core.net.toUri

/** Whether Snaplock is the system autofill service, and how to make it one. */
object AutofillStatus {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun isEnabled(context: Context): Boolean =
        isSupported() &&
            context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true

    /** Opens the system screen that asks to make Snaplock the autofill service. */
    fun enableIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, "package:${context.packageName}".toUri())
}

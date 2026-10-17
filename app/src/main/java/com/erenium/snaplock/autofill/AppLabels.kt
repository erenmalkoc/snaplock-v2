package com.erenium.snaplock.autofill

import android.content.Context
import android.content.pm.PackageManager

/** The user-visible name of an installed app, or its package name if it cannot be read. */
internal fun Context.appLabel(packageName: String): String = runCatching {
    val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

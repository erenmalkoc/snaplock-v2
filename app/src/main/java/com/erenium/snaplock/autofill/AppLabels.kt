package com.erenium.snaplock.autofill

import android.content.Context
import android.content.pm.PackageManager

internal fun Context.appLabel(packageName: String): String = runCatching {
    val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

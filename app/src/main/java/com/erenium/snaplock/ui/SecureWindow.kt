package com.erenium.snaplock.ui

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.view.WindowManager

fun Activity.blockScreenshots() {
    val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!isDebuggable) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}

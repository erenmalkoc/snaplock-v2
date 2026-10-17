package com.erenium.snaplock.ui

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.view.WindowManager

/**
 * Keeps vault contents out of screenshots, screen recordings and the recents preview.
 * Debug builds stay capturable for development.
 */
fun Activity.blockScreenshots() {
    val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!isDebuggable) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}

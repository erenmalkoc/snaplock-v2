package com.erenium.snaplock.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.erenium.snaplock.MainActivity
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.main.AppShortcut

/**
 * Publishes the launcher shortcuts (long press on the app icon). They are set from code
 * rather than res/xml so their intents always name this build's package and their labels
 * follow the current language.
 */
fun publishAppShortcuts(context: Context) {
    val shortcuts = AppShortcut.entries.mapIndexed { rank, shortcut ->
        val (label, longLabel, icon) = when (shortcut) {
            AppShortcut.SEARCH -> Triple(R.string.shortcut_search, R.string.shortcut_search_long, R.drawable.ic_shortcut_search)
            AppShortcut.NEW_ENTRY -> Triple(R.string.shortcut_new_entry, R.string.shortcut_new_entry, R.drawable.ic_shortcut_new_entry)
            AppShortcut.GENERATOR -> Triple(R.string.shortcut_generator, R.string.shortcut_generator, R.drawable.ic_shortcut_generator)
            AppShortcut.LOCK -> Triple(R.string.shortcut_lock, R.string.shortcut_lock_long, R.drawable.ic_shortcut_lock)
        }
        ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(context.getString(label))
            .setLongLabel(context.getString(longLabel))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(shortcut.action).setClass(context, MainActivity::class.java))
            .setRank(rank)
            .build()
    }
    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
}

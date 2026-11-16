package com.erenium.snaplock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.FragmentActivity
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.presentation.main.AutoLockManager
import com.erenium.snaplock.presentation.main.AppShortcut
import com.erenium.snaplock.presentation.main.MainViewModel
import com.erenium.snaplock.ui.blockScreenshots
import com.erenium.snaplock.ui.followAppTheme
import com.erenium.snaplock.ui.publishAppShortcuts
import com.erenium.snaplock.ui.navigation.AppNavigation
import com.erenium.snaplock.ui.theme.SnaplockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var settingsPrefs: SettingsPrefs

    @Inject
    lateinit var autoLockManager: AutoLockManager

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        followAppTheme(settingsPrefs)
        blockScreenshots()
        publishAppShortcuts(this)
        // A recreated activity has already handled the intent that started it.
        if (savedInstanceState == null) handleShortcut(intent)
        setContent {
            val settings by settingsPrefs.settings.collectAsState()
            SnaplockTheme(settings) {
                AppNavigation(mainViewModel)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        autoLockManager.pickerLaunched()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        autoLockManager.pickerFinished()
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent?) {
        val shortcut = AppShortcut.fromAction(intent?.action) ?: return
        // Lets the launcher rank the shortcuts people actually use first.
        ShortcutManagerCompat.reportShortcutUsed(this, shortcut.id)
        mainViewModel.onShortcut(shortcut)
    }
}

package com.erenium.snaplock

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.model.ThemeMode
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import com.erenium.snaplock.ui.navigation.AppNavigation
import com.erenium.snaplock.ui.theme.SnaplockV2Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var lockDatabaseUseCase: LockDatabaseUseCase

    @Inject
    lateinit var settingsPrefs: SettingsPrefs

    private var autoLockJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        setContent {
            val settings by settingsPrefs.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SnaplockV2Theme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor
            ) {
                AppNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        autoLockJob?.cancel()
        autoLockJob = null
    }

    override fun onStop() {
        super.onStop()
        val delaySeconds = settingsPrefs.settings.value.autoLockSeconds
        autoLockJob?.cancel()
        autoLockJob = lifecycleScope.launch {
            if (delaySeconds > 0) {
                delay(delaySeconds * MILLIS_PER_SECOND)
            }
            lockDatabaseUseCase.invoke()
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}




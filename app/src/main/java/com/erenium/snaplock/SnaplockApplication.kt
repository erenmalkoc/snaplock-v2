package com.erenium.snaplock

import android.app.Application
import com.erenium.snaplock.presentation.main.AutoLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SnaplockApplication : Application() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    override fun onCreate() {
        super.onCreate()
        autoLockManager.start()
    }
}

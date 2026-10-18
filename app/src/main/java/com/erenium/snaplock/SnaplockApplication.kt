package com.erenium.snaplock

import android.app.Application
import com.erenium.snaplock.presentation.main.AutoLockManager
import com.erenium.snaplock.presentation.main.ExternalChangesWatcher
import com.erenium.snaplock.presentation.main.SiteIconAutoFetcher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SnaplockApplication : Application() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    @Inject
    lateinit var externalChangesWatcher: ExternalChangesWatcher

    @Inject
    lateinit var siteIconAutoFetcher: SiteIconAutoFetcher

    override fun onCreate() {
        super.onCreate()
        autoLockManager.start()
        externalChangesWatcher.start()
        siteIconAutoFetcher.start()
    }
}

package com.erenium.snaplock.presentation.main

import android.util.Log
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.icons.DownloadSiteIconsUseCase
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When the user turned automatic site icons on, gives entries that have a website but no
 * icon one shortly after they appear: new, imported or synced. A site that had no usable
 * icon is not asked again until the app restarts.
 */
@Singleton
class SiteIconAutoFetcher @Inject constructor(
    private val settingsPrefs: SettingsPrefs,
    private val repository: KdbxRepository,
    private val downloadSiteIcons: DownloadSiteIconsUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val failedHosts = mutableSetOf<String>()

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(
                settingsPrefs.settings.map { it.autoSiteIcons }.distinctUntilChanged(),
                repository.getEntries()
            ) { enabled, entries -> enabled to entries }
                // Let a burst of saves (an import, a sync) settle before going online.
                .debounce(SETTLE_MILLIS)
                .collectLatest { (enabled, entries) -> fetchMissing(enabled, entries) }
        }
    }

    /** One pass; the entries only tell whether a vault is open. */
    suspend fun fetchMissing(enabled: Boolean, entries: List<Entry>) {
        if (!enabled || entries.isEmpty()) return
        downloadSiteIcons.download(skipHosts = failedHosts.toSet())
            .onSuccess { failedHosts += it.failedHosts }
            .onFailure { Log.w(TAG, "Automatic site icons failed", it) }
    }

    private companion object {
        const val TAG = "SiteIconAutoFetcher"
        const val SETTLE_MILLIS = 2_000L
    }
}

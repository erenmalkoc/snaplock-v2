package com.erenium.snaplock.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import com.erenium.snaplock.MainActivity
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.usecase.IsDatabaseLockedUseCase
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the tile shows for an open or locked vault. */
internal data class LockTileLook(val state: Int, @param:StringRes val subtitle: Int)

internal fun lockTileLook(locked: Boolean): LockTileLook =
    if (locked) LockTileLook(Tile.STATE_INACTIVE, R.string.tile_locked) else LockTileLook(Tile.STATE_ACTIVE, R.string.tile_unlocked)

/**
 * Quick Settings tile: locks an open vault with one tap, or opens Snaplock to unlock it.
 */
@AndroidEntryPoint
class LockTileService : TileService() {

    @Inject
    lateinit var isDatabaseLocked: IsDatabaseLockedUseCase

    @Inject
    lateinit var lockDatabase: LockDatabaseUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        listening?.cancel()
        listening = scope.launch {
            isDatabaseLocked().collect(::render)
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onClick() {
        // Read the state now: it may have changed while the panel was closed and the tile
        // was not listening.
        if (!isDatabaseLocked().value) {
            scope.launch { lockDatabase() }
        } else if (isLocked) {
            // The device is locked too: open Snaplock once the user has unlocked it.
            unlockAndRun(::openApp)
        } else {
            openApp()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(locked: Boolean) {
        val tile = qsTile ?: return
        val look = lockTileLook(locked)
        tile.state = look.state
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(look.subtitle)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

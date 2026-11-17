package com.erenium.snaplock.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
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

internal data class LockTileLook(
    val state: Int,
    @param:StringRes val subtitle: Int,
    @param:DrawableRes val icon: Int
)

internal fun lockTileLook(locked: Boolean): LockTileLook =
    if (locked) {
        LockTileLook(Tile.STATE_INACTIVE, R.string.tile_locked, R.drawable.ic_tile_lock)
    } else {
        LockTileLook(Tile.STATE_ACTIVE, R.string.tile_unlocked, R.drawable.ic_tile_unlock)
    }

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
        if (!isDatabaseLocked().value) {
            scope.launch { lockDatabase() }
        } else if (isLocked) {
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
        tile.icon = Icon.createWithResource(this, look.icon)
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

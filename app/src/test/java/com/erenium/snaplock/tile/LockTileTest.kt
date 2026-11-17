package com.erenium.snaplock.tile

import android.service.quicksettings.Tile
import com.erenium.snaplock.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LockTileTest {
    @Test
    fun anOpenVaultShowsAnActiveTileThatLocksIt() {
        assertEquals(LockTileLook(Tile.STATE_ACTIVE, R.string.tile_unlocked, R.drawable.ic_tile_unlock), lockTileLook(locked = false))
    }

    @Test
    fun aLockedVaultShowsAnInactiveTile() {
        assertEquals(LockTileLook(Tile.STATE_INACTIVE, R.string.tile_locked, R.drawable.ic_tile_lock), lockTileLook(locked = true))
    }
}

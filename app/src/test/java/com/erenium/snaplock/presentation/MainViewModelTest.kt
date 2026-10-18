package com.erenium.snaplock.presentation

import android.net.Uri
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.domain.usecase.IsDatabaseLockedUseCase
import com.erenium.snaplock.presentation.main.MainViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @Test
    fun anOpenVaultIsKnownBeforeAnythingCollects() {
        val repository = FakeKdbxRepository().apply { locked.value = false }

        val viewModel = MainViewModel(IsDatabaseLockedUseCase(repository))

        assertFalse(viewModel.isLocked.value)
    }

    @Test
    fun sessionCacheReportsLockChangesRightAway() {
        val cache = SessionCache()
        val database = KeePassDatabase.Ver4x.create("Root", Meta(), Credentials.from(EncryptedValue.fromString("pw")))

        assertTrue(cache.isLocked.value)
        cache.setDatabase(database, Uri.parse("content://vault"))
        assertFalse(cache.isLocked.value)
        cache.lock()
        assertTrue(cache.isLocked.value)
    }
}

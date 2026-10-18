package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryCredentialsChangeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context, VaultBackups(context)), sessionCache, RecentDatabasesPrefs(context)
    )
    private val keyFile = Random(42).nextBytes(256)

    private fun newVaultUri(): Uri = Uri.fromFile(File(folder.root, "vault.kdbx").apply { createNewFile() })

    private suspend fun opens(uri: Uri, password: String, key: ByteArray?): Boolean {
        repository.lockDatabase()
        return repository.unlockDatabase(uri, password, key).isSuccess
    }

    @Test
    fun passwordPlusKeyFileNeedsBoth() = runTest {
        val uri = newVaultUri()
        repository.createDatabase(uri, "master", keyFile).getOrThrow()

        assertTrue(opens(uri, "master", keyFile))
        assertTrue(!opens(uri, "master", null))
        assertTrue(!opens(uri, "", keyFile))
    }

    @Test
    fun keyFileOnlyDatabase() = runTest {
        val uri = newVaultUri()
        repository.createDatabase(uri, "", keyFile).getOrThrow()

        assertTrue(opens(uri, "", keyFile))
        assertTrue(!opens(uri, "", Random(7).nextBytes(256)))
    }

    @Test
    fun changingCredentialsReplacesTheOldOnes() = runTest {
        val uri = newVaultUri()
        repository.createDatabase(uri, "old").getOrThrow()

        repository.changeCredentials("new", keyFile).getOrThrow()

        assertTrue(opens(uri, "new", keyFile))
        assertTrue(!opens(uri, "old", null))
    }

    @Test
    fun emptyCredentialsAreRejected() = runTest {
        val uri = newVaultUri()
        repository.createDatabase(uri, "old").getOrThrow()

        assertTrue(repository.changeCredentials("", null).isFailure)
        assertTrue(opens(uri, "old", null))
    }
}

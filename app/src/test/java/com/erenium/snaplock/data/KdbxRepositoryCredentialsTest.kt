package com.erenium.snaplock.data

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryCredentialsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context,
        KdbxLocalDataSource(context),
        sessionCache,
        RecentDatabasesPrefs(context)
    )

    @Test
    fun lockedDatabaseHasNoCredentials() = runTest {
        assertTrue(repository.getCredentials().isEmpty())
    }

    @Test
    fun returnsNestedEntriesButSkipsTheRecycleBin() = runTest {
        val recycleBin = UUID.randomUUID()
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(recycleBinUuid = recycleBin, recycleBinEnabled = true),
            credentials = Credentials.from(EncryptedValue.fromString("master"))
        )
        val filled = database.modifyGroup(database.content.group.uuid) {
            copy(
                entries = entries + entry("Kept"),
                groups = groups +
                    Group(uuid = UUID.randomUUID(), name = "Web", entries = listOf(entry("Nested"))) +
                    Group(uuid = recycleBin, name = "Recycle Bin", entries = listOf(entry("Deleted")))
            )
        }
        sessionCache.setDatabase(filled, "content://test/vault.kdbx".toUri())

        val titles = repository.getCredentials().map { it.title }

        assertEquals(setOf("Kept", "Nested"), titles.toSet())
    }

    private fun entry(title: String) = Entry(
        uuid = UUID.randomUUID(),
        fields = EntryFields(mapOf("Title" to EntryValue.Plain(title)))
    )
}

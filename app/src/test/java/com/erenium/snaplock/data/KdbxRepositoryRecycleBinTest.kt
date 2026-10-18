package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.getEntry
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
import com.erenium.snaplock.domain.model.EntryFormData
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryRecycleBinTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context), sessionCache, RecentDatabasesPrefs(context)
    )
    private val webGroup = UUID.randomUUID()
    private val entryUuid = UUID.randomUUID()
    private lateinit var file: File

    private fun open(meta: Meta = Meta(recycleBinEnabled = true), extraGroups: List<Group> = emptyList()) {
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = meta,
            credentials = Credentials.from(EncryptedValue.fromString(PASSWORD))
        )
        val filled = database.modifyGroup(database.content.group.uuid) {
            copy(groups = groups + Group(uuid = webGroup, name = "Web", entries = listOf(entry(entryUuid, "GitHub"))) + extraGroups)
        }
        file = folder.newFile("vault.kdbx")
        sessionCache.setDatabase(filled, Uri.fromFile(file))
    }

    private fun entry(uuid: UUID, title: String, previousGroup: UUID? = null) = Entry(
        uuid = uuid,
        fields = EntryFields(mapOf("Title" to EntryValue.Plain(title), "Password" to EntryValue.Plain("v1"))),
        previousParentGroup = previousGroup
    )

    private fun reopen(): KeePassDatabase = file.inputStream().use {
        KeePassDatabase.decode(it, Credentials.from(EncryptedValue.fromString(PASSWORD)))
    }

    @Test
    fun deleteMovesTheEntryIntoANewRecycleBin() = runTest {
        open()

        repository.deleteEntry(entryUuid).getOrThrow()

        assertTrue(repository.getEntries().first().isEmpty())
        assertTrue(repository.getCredentials().isEmpty())
        assertEquals(listOf(entryUuid), repository.getRecycledEntries().first().map { it.uuid })
        val saved = reopen()
        val bin = saved.content.meta.recycleBinUuid
        assertNotNull(bin)
        val (group, entry) = saved.getEntry { it.uuid == entryUuid }!!
        assertEquals(bin, group.uuid)
        assertEquals(webGroup, entry.previousParentGroup)
        assertTrue(repository.getGroups().first().none { it.uuid == bin })
    }

    @Test
    fun deletingFromTheRecycleBinIsPermanent() = runTest {
        open()
        repository.deleteEntry(entryUuid).getOrThrow()

        repository.deleteEntry(entryUuid).getOrThrow()

        assertNull(reopen().getEntry { it.uuid == entryUuid })
        assertTrue(repository.getRecycledEntries().first().isEmpty())
    }

    @Test
    fun restorePutsTheEntryBackInItsGroup() = runTest {
        open()
        repository.deleteEntry(entryUuid).getOrThrow()

        repository.restoreEntry(entryUuid).getOrThrow()

        val (group, entry) = reopen().getEntry { it.uuid == entryUuid }!!
        assertEquals(webGroup, group.uuid)
        assertNotNull(entry.times)
        assertEquals(listOf("GitHub"), repository.getEntries().first().map { it.title })
    }

    @Test
    fun restoreFallsBackToTheRootWhenTheGroupIsGone() = runTest {
        val bin = UUID.randomUUID()
        val orphan = UUID.randomUUID()
        open(
            meta = Meta(recycleBinEnabled = true, recycleBinUuid = bin),
            extraGroups = listOf(Group(uuid = bin, name = "Recycle Bin", entries = listOf(entry(orphan, "Old", UUID.randomUUID()))))
        )

        repository.restoreEntry(orphan).getOrThrow()

        val saved = reopen()
        assertEquals(saved.content.group.uuid, saved.getEntry { it.uuid == orphan }!!.first.uuid)
    }

    @Test
    fun turnedOffRecycleBinDeletesForGood() = runTest {
        open(meta = Meta(recycleBinEnabled = false))

        repository.deleteEntry(entryUuid).getOrThrow()

        val saved = reopen()
        assertNull(saved.getEntry { it.uuid == entryUuid })
        assertNull(saved.content.meta.recycleBinUuid)
    }

    @Test
    fun danglingRecycleBinIdGetsAFreshBinInsteadOfLosingTheEntry() = runTest {
        open(meta = Meta(recycleBinEnabled = true, recycleBinUuid = UUID.randomUUID()))

        repository.deleteEntry(entryUuid).getOrThrow()

        val saved = reopen()
        val group = saved.getEntry { it.uuid == entryUuid }?.first
        assertNotNull(group)
        assertEquals(saved.content.meta.recycleBinUuid, group!!.uuid)
    }

    @Test
    fun emptyingTheRecycleBinRemovesEverythingInIt() = runTest {
        open()
        repository.deleteEntry(entryUuid).getOrThrow()

        repository.emptyRecycleBin().getOrThrow()

        assertNull(reopen().getEntry { it.uuid == entryUuid })
        assertTrue(repository.getRecycledEntries().first().isEmpty())
    }

    @Test
    fun newDatabasesHaveTheRecycleBinTurnedOn() = runTest {
        file = folder.newFile("new.kdbx")

        repository.createDatabase(Uri.fromFile(file), PASSWORD).getOrThrow()

        assertTrue(reopen().content.meta.recycleBinEnabled)
    }

    @Test
    fun editsKeepPreviousVersionsUpToTheHistoryLimit() = runTest {
        open(meta = Meta(recycleBinEnabled = true, historyMaxItems = 2))

        listOf("v2", "v3", "v4").forEach { password ->
            repository.updateEntry(entryUuid, EntryFormData("GitHub", null, password, null, null, webGroup)).getOrThrow()
        }

        val entry = reopen().getEntry { it.uuid == entryUuid }!!.second
        assertEquals("v4", entry.fields["Password"]?.content)
        assertEquals(listOf("v2", "v3"), entry.history.map { it.fields["Password"]?.content })
    }

    private companion object {
        const val PASSWORD = "master"
    }
}

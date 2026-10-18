package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryGroupsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context), sessionCache, RecentDatabasesPrefs(context)
    )
    private val web = UUID.randomUUID()
    private lateinit var root: UUID
    private lateinit var file: File

    private fun open(recycleBin: Boolean = true) {
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(recycleBinEnabled = recycleBin),
            credentials = Credentials.from(EncryptedValue.fromString(PASSWORD))
        )
        root = database.content.group.uuid
        val entry = Entry(uuid = UUID.randomUUID(), fields = EntryFields(mapOf("Title" to EntryValue.Plain("GitHub"))))
        val filled = database.modifyGroup(root) {
            copy(groups = groups + Group(uuid = web, name = "Web", entries = listOf(entry)))
        }
        file = folder.newFile("vault.kdbx")
        sessionCache.setDatabase(filled, Uri.fromFile(file))
    }

    private fun saved(): KeePassDatabase = file.inputStream().use {
        KeePassDatabase.decode(it, Credentials.from(EncryptedValue.fromString(PASSWORD)))
    }

    private fun KeePassDatabase.groupNamed(name: String): Group? {
        fun search(group: Group): Group? =
            if (group.name == name) group else group.groups.firstNotNullOfOrNull { search(it) }
        return search(content.group)
    }

    @Test
    fun groupsKnowTheirParentAndChildren() = runTest {
        open()

        val groups = repository.getGroups().first()

        val rootGroup = groups.single { it.uuid == root }
        val webGroup = groups.single { it.uuid == web }
        assertNull(rootGroup.parentUuid)
        assertEquals(1, rootGroup.groupCount)
        assertEquals(root, webGroup.parentUuid)
        assertEquals(1, webGroup.entryCount)
    }

    @Test
    fun createAndRenameNestedGroups() = runTest {
        open()

        val social = repository.createGroup(web, "  Social ").getOrThrow()
        repository.renameGroup(social, "Social media").getOrThrow()

        val group = saved().groupNamed("Social media")
        assertEquals(social, group?.uuid)
        assertEquals(web, repository.getGroups().first().single { it.uuid == social }.parentUuid)
    }

    @Test
    fun deletingAGroupMovesItAndItsEntriesToTheRecycleBin() = runTest {
        open()

        repository.deleteGroup(web).getOrThrow()

        assertTrue(repository.getEntries().first().isEmpty())
        assertTrue(repository.getGroups().first().none { it.uuid == web })
        assertEquals(listOf("GitHub"), repository.getRecycledEntries().first().map { it.title })
        val database = saved()
        val bin = database.groupNamed("Recycle Bin")
        assertEquals(database.content.meta.recycleBinUuid, bin?.uuid)
        assertEquals(listOf(web), bin?.groups?.map { it.uuid })
    }

    @Test
    fun groupsAreRemovedForGoodWithoutARecycleBin() = runTest {
        open(recycleBin = false)

        repository.deleteGroup(web).getOrThrow()

        assertNull(saved().groupNamed("Web"))
    }

    @Test
    fun theRootGroupCannotBeDeleted() = runTest {
        open()

        assertTrue(repository.deleteGroup(root).isFailure)
        assertEquals(setOf(root, web), repository.getGroups().first().map { it.uuid }.toSet())
    }

    private companion object {
        const val PASSWORD = "master"
    }
}

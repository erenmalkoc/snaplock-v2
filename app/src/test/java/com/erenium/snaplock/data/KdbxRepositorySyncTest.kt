package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.getEntry
import app.keemobile.kotpass.database.modifiers.modifyCredentials
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.encodeToByteArray
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.error.VaultChangedElsewhereException
import com.erenium.snaplock.domain.model.EntryFormData
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Saving after another device or app (Syncthing, a cloud drive) wrote the vault file. */
@RunWith(RobolectricTestRunner::class)
class KdbxRepositorySyncTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context), SessionCache(), RecentDatabasesPrefs(context)
    )
    private lateinit var file: File

    private suspend fun openNewVault(): UUID {
        file = File(folder.root, "vault.kdbx").apply { createNewFile() }
        val uri = Uri.fromFile(file)
        repository.createDatabase(uri, PASSWORD).getOrThrow()
        repository.addEntry(EntryFormData("GitHub", "octocat", "v1", null, null, null)).getOrThrow()
        repository.lockDatabase()
        repository.unlockDatabase(uri, PASSWORD).getOrThrow()
        return repository.getEntries().first().single().uuid
    }

    /** What another KeePass app does: open the file, change it, write it back. */
    private fun changeElsewhere(change: KeePassDatabase.() -> KeePassDatabase) {
        val database = file.inputStream().use { KeePassDatabase.decode(it, credentials(PASSWORD)) }
        file.writeBytes(database.change().encodeToByteArray())
    }

    private fun reopen(password: String = PASSWORD): KeePassDatabase =
        file.inputStream().use { KeePassDatabase.decode(it, credentials(password)) }

    @Test
    fun changesMadeElsewhereSurviveTheNextSave() = runTest {
        val github = openNewVault()
        val added = UUID.randomUUID()
        changeElsewhere {
            modifyGroup(content.group.uuid) { copy(entries = entries + entry(added, "Laptop")) }
        }

        repository.updateEntry(github, EntryFormData("GitHub", "octocat", "v2", null, null, null)).getOrThrow()

        val saved = reopen()
        assertEquals("v2", saved.getEntry { it.uuid == github }!!.second.fields["Password"]?.content)
        assertEquals("Laptop", saved.getEntry { it.uuid == added }!!.second.fields["Title"]?.content)
        assertEquals(setOf("GitHub", "Laptop"), repository.getEntries().first().map { it.title }.toSet())
    }

    @Test
    fun aSecondSaveDoesNotMergeWithItsOwnWrite() = runTest {
        val github = openNewVault()

        repository.updateEntry(github, EntryFormData("GitHub", "octocat", "v2", null, null, null)).getOrThrow()
        repository.updateEntry(github, EntryFormData("GitHub", "octocat", "v3", null, null, null)).getOrThrow()

        val entry = reopen().getEntry { it.uuid == github }!!.second
        assertEquals("v3", entry.fields["Password"]?.content)
        assertEquals(listOf("v1", "v2"), entry.history.map { it.fields["Password"]?.content })
    }

    @Test
    fun aVaultReEncryptedElsewhereIsLeftAlone() = runTest {
        val github = openNewVault()
        changeElsewhere { modifyCredentials { credentials("changed on laptop") } }
        val before = file.readBytes()

        val result = repository.updateEntry(github, EntryFormData("GitHub", "octocat", "v2", null, null, null))

        assertTrue(result.exceptionOrNull() is VaultChangedElsewhereException)
        assertArrayEquals(before, file.readBytes())
        assertEquals("v1", reopen("changed on laptop").getEntry { it.uuid == github }!!.second.fields["Password"]?.content)
    }

    private fun entry(uuid: UUID, title: String) =
        Entry(uuid = uuid, fields = EntryFields(mapOf("Title" to EntryValue.Plain(title))))

    private fun credentials(password: String) = Credentials.from(EncryptedValue.fromString(password))

    private companion object {
        const val PASSWORD = "master"
    }
}

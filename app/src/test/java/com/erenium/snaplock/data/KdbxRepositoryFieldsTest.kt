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
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryFormData
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryFieldsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context), sessionCache, RecentDatabasesPrefs(context)
    )
    private val entryUuid = UUID.randomUUID()
    private lateinit var file: File

    private fun open(fields: Map<String, EntryValue>) {
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(recycleBinEnabled = true),
            credentials = Credentials.from(EncryptedValue.fromString(PASSWORD))
        )
        val filled = database.modifyGroup(database.content.group.uuid) {
            copy(entries = entries + Entry(uuid = entryUuid, fields = EntryFields(fields)))
        }
        file = folder.newFile("vault.kdbx")
        sessionCache.setDatabase(filled, Uri.fromFile(file))
    }

    private fun savedEntry(): Entry = file.inputStream().use {
        KeePassDatabase.decode(it, Credentials.from(EncryptedValue.fromString(PASSWORD)))
    }.getEntry { it.uuid == entryUuid }!!.second

    private fun form(password: String, customFields: List<CustomField>?) =
        EntryFormData("Bank", "client", password, null, null, null, customFields)

    @Test
    fun detailListsCustomFieldsButNotLinkFields() = runTest {
        open(
            mapOf(
                "Title" to EntryValue.Plain("Bank"),
                "PIN" to EntryValue.Encrypted(EncryptedValue.fromString("1234")),
                "Branch" to EntryValue.Plain("Manisa"),
                "AndroidApp" to EntryValue.Plain("com.bank.app"),
                "KP2A_URL" to EntryValue.Plain("https://bank.example")
            )
        )

        val detail = repository.getEntryByUuid(entryUuid).getOrThrow()

        assertEquals(
            setOf(CustomField("PIN", "1234", true), CustomField("Branch", "Manisa", false)),
            detail.customFields.toSet()
        )
    }

    @Test
    fun savingReplacesCustomFieldsAndKeepsLinks() = runTest {
        open(
            mapOf(
                "Title" to EntryValue.Plain("Bank"),
                "Old" to EntryValue.Plain("gone"),
                "AndroidApp" to EntryValue.Plain("com.bank.app")
            )
        )

        repository.updateEntry(
            entryUuid,
            form("pw", listOf(CustomField("PIN", "1234", isProtected = true), CustomField("Branch", "Manisa", false)))
        ).getOrThrow()

        val fields = savedEntry().fields
        assertTrue("Old" !in fields)
        assertEquals("com.bank.app", fields["AndroidApp"]?.content)
        assertTrue(fields["PIN"] is EntryValue.Encrypted)
        assertEquals("1234", fields["PIN"]?.content)
        assertTrue(fields["Branch"] is EntryValue.Plain)
    }

    @Test
    fun nullCustomFieldsLeaveThemUntouched() = runTest {
        open(mapOf("Title" to EntryValue.Plain("Bank"), "PIN" to EntryValue.Plain("1234")))

        repository.updateEntry(entryUuid, form("pw", customFields = null)).getOrThrow()

        assertEquals("1234", savedEntry().fields["PIN"]?.content)
    }

    @Test
    fun historyIsNewestFirstAndAVersionCanBeRestored() = runTest {
        open(mapOf("Title" to EntryValue.Plain("Bank"), "Password" to EntryValue.Plain("v1")))
        repository.updateEntry(entryUuid, form("v2", null)).getOrThrow()
        repository.updateEntry(entryUuid, form("v3", null)).getOrThrow()

        val history = repository.getEntryByUuid(entryUuid).getOrThrow().history
        assertEquals(listOf("v2", "v1"), history.map { it.password })

        repository.restoreVersion(entryUuid, history.last().index).getOrThrow()

        val saved = savedEntry()
        assertEquals("v1", saved.fields["Password"]?.content)
        assertEquals(listOf("v1", "v2", "v3"), saved.history.map { it.fields["Password"]?.content })
    }

    private companion object {
        const val PASSWORD = "master"
    }
}

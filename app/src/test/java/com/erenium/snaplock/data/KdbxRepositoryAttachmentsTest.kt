package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.getEntry
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.model.Attachment
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryAttachmentsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context), sessionCache, RecentDatabasesPrefs(context)
    )
    private val entryUuid = UUID.randomUUID()
    private lateinit var file: File

    private fun open() {
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(recycleBinEnabled = true),
            credentials = Credentials.from(EncryptedValue.fromString(PASSWORD))
        )
        val filled = database.modifyGroup(database.content.group.uuid) {
            copy(entries = entries + Entry(uuid = entryUuid, fields = EntryFields(mapOf("Title" to EntryValue.Plain("Bank")))))
        }
        file = folder.newFile("vault.kdbx")
        sessionCache.setDatabase(filled, Uri.fromFile(file))
    }

    private fun saved(): KeePassDatabase = file.inputStream().use {
        KeePassDatabase.decode(it, Credentials.from(EncryptedValue.fromString(PASSWORD)))
    }

    @Test
    fun addedFilesAreListedReadableAndSaved() = runTest {
        open()
        val pdf = "%PDF-1.7 statement".toByteArray()

        repository.addAttachment(entryUuid, "statement.pdf", pdf).getOrThrow()

        assertEquals(
            listOf(Attachment("statement.pdf", pdf.size.toLong())),
            repository.getEntryByUuid(entryUuid).getOrThrow().attachments
        )
        assertArrayEquals(pdf, repository.getAttachment(entryUuid, "statement.pdf").getOrThrow())
        val database = saved()
        val reference = database.getEntry { it.uuid == entryUuid }!!.second.binaries.single()
        assertEquals("statement.pdf", reference.name)
        assertArrayEquals(pdf, database.binaries.getValue(reference.hash).getContent())
    }

    @Test
    fun clashingNamesGetANumber() = runTest {
        open()

        repository.addAttachment(entryUuid, "scan.png", byteArrayOf(1)).getOrThrow()
        repository.addAttachment(entryUuid, "scan.png", byteArrayOf(2)).getOrThrow()
        repository.addAttachment(entryUuid, "scan.png", byteArrayOf(3)).getOrThrow()

        assertEquals(
            listOf("scan.png", "scan (1).png", "scan (2).png"),
            repository.getEntryByUuid(entryUuid).getOrThrow().attachments.map { it.name }
        )
    }

    @Test
    fun removingDropsTheFileFromTheEntryButKeepsItInHistory() = runTest {
        open()
        repository.addAttachment(entryUuid, "key.txt", "secret".toByteArray()).getOrThrow()

        repository.removeAttachment(entryUuid, "key.txt").getOrThrow()

        assertTrue(repository.getEntryByUuid(entryUuid).getOrThrow().attachments.isEmpty())
        val entry = saved().getEntry { it.uuid == entryUuid }!!.second
        assertTrue(entry.binaries.isEmpty())
        assertEquals(listOf("key.txt"), entry.history.last().binaries.map { it.name })
    }

    private companion object {
        const val PASSWORD = "master"
    }
}

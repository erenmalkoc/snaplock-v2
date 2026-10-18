package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.model.EntryFormData
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
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

@RunWith(RobolectricTestRunner::class)
class VaultBackupsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vault = Uri.parse("content://com.example.documents/document/vault.kdbx")
    private val backups by lazy { VaultBackups(folder.newFolder("backups")) { ZoneOffset.UTC } }

    @Test
    fun keepsOneCopyPerDayFromBeforeTheFirstSave() {
        backups.keep(vault, byteArrayOf(1), at("2026-10-18T09:00:00Z"))
        backups.keep(vault, byteArrayOf(2), at("2026-10-18T18:00:00Z"))
        backups.keep(vault, byteArrayOf(3), at("2026-10-19T08:00:00Z"))

        val kept = backups.list(vault)
        assertEquals(listOf(at("2026-10-19T08:00:00Z"), at("2026-10-18T09:00:00Z")), kept.map { it.created })
        assertArrayEquals(byteArrayOf(1), backups.read(vault, kept.last().id))
    }

    @Test
    fun onlyTheNewestWeekIsKeptAndVaultsStaySeparate() {
        (1..9).forEach { day -> backups.keep(vault, byteArrayOf(day.toByte()), at("2026-10-%02dT12:00:00Z".format(day))) }
        val other = Uri.parse("content://com.example.documents/document/work.kdbx")
        backups.keep(other, byteArrayOf(42), at("2026-10-09T12:00:00Z"))

        val kept = backups.list(vault)
        assertEquals(7, kept.size)
        assertEquals(at("2026-10-03T12:00:00Z"), kept.last().created)
        assertEquals(1, backups.list(other).size)
    }

    @Test
    fun emptyFilesAreNotKept() {
        backups.keep(vault, ByteArray(0), at("2026-10-18T09:00:00Z"))

        assertTrue(backups.list(vault).isEmpty())
    }

    @Test
    fun savingKeepsTheVaultAsItWasBefore() = runTest {
        val sessionCache = SessionCache()
        val repository = KdbxRepositoryImpl(
            context, KdbxLocalDataSource(context, backups), sessionCache, RecentDatabasesPrefs(context)
        )
        val file = File(folder.root, "vault.kdbx").apply { createNewFile() }
        repository.createDatabase(Uri.fromFile(file), PASSWORD).getOrThrow()
        val beforeFirstSave = file.readBytes()

        repository.addEntry(EntryFormData("GitHub", "octocat", "pw", null, null, null)).getOrThrow()
        repository.addEntry(EntryFormData("Mail", "me", "pw", null, null, null)).getOrThrow()

        val kept = repository.getBackups()
        assertEquals(1, kept.size)
        val copy = repository.getBackup(kept.single().id).getOrThrow()
        assertArrayEquals(beforeFirstSave, copy)
        val reopened = KeePassDatabase.decode(ByteArrayInputStream(copy), Credentials.from(EncryptedValue.fromString(PASSWORD)))
        assertTrue(reopened.content.group.entries.isEmpty())
        assertEquals(2, repository.getEntries().first().size)
    }

    private fun at(time: String): Instant = Instant.parse(time)

    private companion object {
        const val PASSWORD = "master"
    }
}

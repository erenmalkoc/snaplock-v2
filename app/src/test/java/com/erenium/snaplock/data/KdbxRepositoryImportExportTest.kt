package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.models.EntryValue
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.importer.CsvExporter
import com.erenium.snaplock.domain.importer.CsvImporter
import com.erenium.snaplock.domain.importer.ImportedEntry
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryImportExportTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context), SessionCache(), RecentDatabasesPrefs(context)
    )
    private lateinit var file: File

    private suspend fun newVault() {
        file = File(folder.root, "vault.kdbx").apply { createNewFile() }
        repository.createDatabase(Uri.fromFile(file), PASSWORD).getOrThrow()
    }

    private fun saved(): KeePassDatabase = file.inputStream().use {
        KeePassDatabase.decode(it, Credentials.from(EncryptedValue.fromString(PASSWORD)))
    }

    private val otp = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&period=30&digits=6&algorithm=SHA1"
    private val logins = listOf(
        ImportedEntry("Twitter", "me", "pa,ss\"word", "https://twitter.com", null, null, "Social"),
        ImportedEntry("GitHub", "octocat", "gh-pass", "https://github.com", "two\nlines", otp, "Work/Dev"),
        ImportedEntry("Bank", "client", "1234", null, null, null, null)
    )

    @Test
    fun importBuildsTheFolderTreeInOneGroup() = runTest {
        newVault()

        assertEquals(3, repository.importEntries("Imported", logins).getOrThrow())

        val imported = saved().content.group.groups.single { it.name == "Imported" }
        assertEquals(listOf("Bank"), imported.entries.map { it.fields["Title"]?.content })
        assertEquals(setOf("Social", "Work"), imported.groups.map { it.name }.toSet())
        val dev = imported.groups.single { it.name == "Work" }.groups.single { it.name == "Dev" }
        val github = dev.entries.single()
        assertTrue(github.fields["Password"] is EntryValue.Encrypted)
        assertTrue(github.fields["otp"] is EntryValue.Encrypted)
    }

    @Test
    fun exportedCsvImportsBackTheSameLogins() = runTest {
        newVault()
        repository.importEntries("Imported", logins).getOrThrow()

        val csv = CsvExporter.write(repository.getExportEntries())
        val roundTrip = CsvImporter.parse(csv)!!

        val byTitle = roundTrip.entries.associateBy { it.title }
        assertEquals("pa,ss\"word", byTitle.getValue("Twitter").password)
        assertEquals("Imported/Work/Dev", byTitle.getValue("GitHub").folder)
        assertEquals("two\nlines", byTitle.getValue("GitHub").notes)
        assertEquals(otp, byTitle.getValue("GitHub").otpUri)
        assertEquals("Imported", byTitle.getValue("Bank").folder)
    }

    private companion object {
        const val PASSWORD = "master"
    }
}

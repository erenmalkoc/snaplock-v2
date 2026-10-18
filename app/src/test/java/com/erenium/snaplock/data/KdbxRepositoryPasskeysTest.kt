package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.getEntry
import app.keemobile.kotpass.models.EntryValue
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.local.decodeFromByteArray
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.passkeys.Passkey
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
class KdbxRepositoryPasskeysTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context, VaultBackups(context)), SessionCache(), RecentDatabasesPrefs(context)
    )
    private val passkey = Passkey("Y3JlZA", "-----BEGIN PRIVATE KEY-----\nAA\n-----END PRIVATE KEY-----", "github.com", "octocat", "dXNlcg")
    private lateinit var file: File

    private suspend fun openNewVault() {
        file = File(folder.root, "vault.kdbx").apply { createNewFile() }
        repository.createDatabase(Uri.fromFile(file), "pw").getOrThrow()
    }

    private fun reopen(): KeePassDatabase = decodeFromByteArray(file.readBytes(), Credentials.from(EncryptedValue.fromString("pw")))

    @Test
    fun aNewPasskeyIsSavedTheWayKeePassXcReadsIt() = runTest {
        openNewVault()

        val uuid = repository.addPasskey("GitHub", "https://github.com", passkey).getOrThrow()

        val fields = reopen().getEntry { it.uuid == uuid }!!.second.fields
        assertEquals("octocat", fields["UserName"]?.content)
        assertEquals("https://github.com", fields["URL"]?.content)
        assertEquals("github.com", fields[Passkey.FIELD_RELYING_PARTY]?.content)
        assertTrue(fields[Passkey.FIELD_PRIVATE_KEY_PEM] is EntryValue.Encrypted)
        assertEquals(passkey, repository.getPasskeys().single().passkey)
    }

    @Test
    fun editingTheEntryKeepsThePasskeyOutOfCustomFields() = runTest {
        openNewVault()
        val uuid = repository.addPasskey("GitHub", "https://github.com", passkey).getOrThrow()
        val detail = repository.getEntryByUuid(uuid).getOrThrow()
        assertTrue(detail.customFields.isEmpty())
        assertEquals(passkey, detail.passkey)

        repository.updateEntry(
            uuid,
            EntryFormData("GitHub", "octocat", "pw", "https://github.com", null, null, customFields = listOf(CustomField("PIN", "1234", true)))
        ).getOrThrow()

        val edited = repository.getEntryByUuid(uuid).getOrThrow()
        assertEquals(passkey, edited.passkey)
        assertEquals(listOf("PIN"), edited.customFields.map { it.name })
    }
}

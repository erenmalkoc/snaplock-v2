package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.findEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
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
class KdbxRepositoryLinkTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context, VaultBackups(context)), sessionCache, RecentDatabasesPrefs(context)
    )
    private val entryUuid = UUID.randomUUID()
    private lateinit var file: File

    private fun open(url: String?) {
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(),
            credentials = Credentials.from(EncryptedValue.fromString(PASSWORD))
        )
        val fields = buildMap {
            put("Title", EntryValue.Plain("Login"))
            if (url != null) put("URL", EntryValue.Plain(url))
        }
        val filled = database.modifyGroup(database.content.group.uuid) {
            copy(entries = entries + Entry(uuid = entryUuid, fields = EntryFields(fields)))
        }
        file = folder.newFile("vault.kdbx")
        sessionCache.setDatabase(filled, Uri.fromFile(file))
    }

    /** Reads the saved file back, so the test covers what other KeePass apps will see. */
    private fun savedFields(): Map<String, String> {
        val reopened = file.inputStream().use {
            KeePassDatabase.decode(it, Credentials.from(EncryptedValue.fromString(PASSWORD)))
        }
        val entry = reopened.findEntry { it.uuid == entryUuid }!!.second
        return entry.fields.entries.associate { it.key to it.value.content }
    }

    @Test
    fun appsGoIntoNumberedAndroidAppFields() = runTest {
        open(url = null)

        repository.linkToApp(entryUuid, "com.first.app").getOrThrow()
        repository.linkToApp(entryUuid, "com.second.app").getOrThrow()
        repository.linkToApp(entryUuid, "com.first.app").getOrThrow()

        val fields = savedFields()
        assertEquals("com.first.app", fields["AndroidApp"])
        assertEquals("com.second.app", fields["AndroidApp_1"])
        assertEquals(setOf("com.first.app", "com.second.app"), repository.getCredentials().single().androidApps.toSet())
    }

    @Test
    fun firstSiteFillsTheEmptyUrlField() = runTest {
        open(url = null)

        repository.linkToSite(entryUuid, "example.com").getOrThrow()

        assertEquals("https://example.com", savedFields()["URL"])
    }

    @Test
    fun extraSitesKeepTheExistingUrl() = runTest {
        open(url = "https://github.com/login")

        repository.linkToSite(entryUuid, "gist.github.com").getOrThrow()
        repository.linkToSite(entryUuid, "gist.github.com").getOrThrow()

        val fields = savedFields()
        assertEquals("https://github.com/login", fields["URL"])
        assertEquals("https://gist.github.com", fields["KP2A_URL"])
        assertTrue("KP2A_URL_1" !in fields)
    }

    private companion object {
        const val PASSWORD = "master"
    }
}

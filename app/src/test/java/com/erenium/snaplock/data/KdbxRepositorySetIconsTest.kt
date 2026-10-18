package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.getEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.local.decodeFromByteArray
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositorySetIconsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context, VaultBackups(context)), sessionCache, RecentDatabasesPrefs(context)
    )
    private val credentials = Credentials.from(EncryptedValue.fromString("pw"))

    @Test
    fun identicalIconsAreStoredOnceAndSavedInTheFile() = runTest {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val file = File(folder.root, "vault.kdbx").apply { createNewFile() }
        val database = KeePassDatabase.Ver4x.create("Root", Meta(), credentials)
        sessionCache.setDatabase(
            database.modifyGroup(database.content.group.uuid) { copy(entries = listOf(entry(a), entry(b), entry(c))) },
            Uri.fromFile(file)
        )
        val github = byteArrayOf(1, 2, 3)
        val mail = byteArrayOf(4, 5)

        repository.setEntryIcons(mapOf(a to github, b to github.copyOf(), c to mail)).getOrThrow()
        repository.setEntryIcons(mapOf(c to mail.copyOf())).getOrThrow()

        val saved = decodeFromByteArray(file.readBytes(), credentials)
        assertEquals(2, saved.content.meta.customIcons.size)
        val iconOf = { uuid: UUID -> saved.getEntry { it.uuid == uuid }!!.second.customIconUuid }
        assertEquals(iconOf(a), iconOf(b))
        assertArrayEquals(mail, saved.content.meta.customIcons.getValue(iconOf(c)!!).data)
        assertEquals(3, repository.getEntries().first().count { it.icon != null })
    }

    private fun entry(uuid: UUID) = Entry(uuid = uuid, fields = EntryFields(mapOf("Title" to EntryValue.Plain("x"))))
}

package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyCustomIcons
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.models.CustomIcon
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.model.EntryIcon
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Custom icons written by KeePassXC and other clients show next to their entries. */
@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryIconsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context, VaultBackups(context)), sessionCache, RecentDatabasesPrefs(context)
    )
    private val iconId = UUID.randomUUID()
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    @Test
    fun entriesAndCredentialsCarryTheirCustomIcon() = runTest {
        val withIcon = entry("GitHub").copy(customIconUuid = iconId)
        val missingIcon = entry("Mail").copy(customIconUuid = UUID.randomUUID())
        val plain = entry("Bank")
        open(listOf(withIcon, missingIcon, plain))

        val icons = repository.getEntries().first().associate { it.title to it.icon }
        assertEquals(EntryIcon(iconId, png), icons["GitHub"])
        assertNull(icons["Mail"])
        assertNull(icons["Bank"])
        assertEquals(EntryIcon(iconId, png), repository.getCredentials().single { it.title == "GitHub" }.icon)
    }

    private fun open(entries: List<Entry>) {
        val database = KeePassDatabase.Ver4x.create("Root", Meta(), Credentials.from(EncryptedValue.fromString("pw")))
        val filled = database
            .modifyGroup(database.content.group.uuid) { copy(entries = entries) }
            .modifyCustomIcons { it + (iconId to CustomIcon(png, "github.com", Instant.now())) }
        sessionCache.setDatabase(filled, Uri.parse("content://vault"))
    }

    private fun entry(title: String) =
        Entry(uuid = UUID.randomUUID(), fields = EntryFields(mapOf("Title" to EntryValue.Plain(title))))
}

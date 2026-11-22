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
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.otp.Base32
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositoryOtpTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sessionCache = SessionCache()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context, VaultBackups(context)), sessionCache, RecentDatabasesPrefs(context)
    )
    private val entryUuid = UUID.randomUUID()
    private lateinit var file: File

    private fun open(fields: Map<String, String>, protectedNames: Set<String> = emptySet()) {
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = Meta(recycleBinEnabled = true),
            credentials = Credentials.from(EncryptedValue.fromString(PASSWORD))
        )
        val values = fields.mapValues {
            if (it.key in protectedNames) EntryValue.Encrypted(EncryptedValue.fromString(it.value)) else EntryValue.Plain(it.value)
        } + ("Title" to EntryValue.Plain("GitHub"))
        val filled = database.modifyGroup(database.content.group.uuid) {
            copy(entries = entries + Entry(uuid = entryUuid, fields = EntryFields(values)))
        }
        file = folder.newFile("vault.kdbx")
        sessionCache.setDatabase(filled, Uri.fromFile(file))
    }

    private fun savedEntry(): Entry = file.inputStream().use {
        KeePassDatabase.decode(it, Credentials.from(EncryptedValue.fromString(PASSWORD)))
    }.getEntry { it.uuid == entryUuid }!!.second

    private fun savedFields(): Map<String, EntryValue> = savedEntry().fields

    private fun form(otpUri: String?, customFields: List<CustomField>? = null) =
        EntryFormData("GitHub", "octocat", "pw", null, null, null, customFields, otpUri)

    @Test
    fun theListCarriesOnlyPlainCustomFieldsNeverProtectedOnesOrOneTimeCodes() = runTest {
        open(
            mapOf(
                "Customer" to "4711",
                "PIN" to "9999",
                "Blank" to "  ",
                "otp" to "otpauth://totp/x?secret=$SECRET",
                "TimeOtp-Secret-Base32" to SECRET
            ),
            protectedNames = setOf("PIN")
        )

        val entry = repository.getEntries().first().single()

        assertEquals(mapOf("Customer" to "4711"), entry.customFields)
    }

    @Test
    fun detailReadsKeePassFieldsAndHidesThemFromCustomFields() = runTest {
        open(mapOf("TimeOtp-Secret-Base32" to SECRET, "TimeOtp-Period" to "60", "Branch" to "Manisa"))

        val detail = repository.getEntryByUuid(entryUuid).getOrThrow()

        assertArrayEquals(Base32.decode(SECRET), detail.otp!!.secret)
        assertEquals(60, detail.otp!!.period)
        assertEquals(listOf("Branch"), detail.customFields.map { it.name })
    }

    @Test
    fun untouchedTotpKeepsItsOriginalFormat() = runTest {
        open(mapOf("TimeOtp-Secret-Base32" to SECRET))

        repository.updateEntry(entryUuid, form(otpUri = null, customFields = emptyList())).getOrThrow()

        val fields = savedFields()
        assertEquals(SECRET, fields["TimeOtp-Secret-Base32"]?.content)
        assertTrue("otp" !in fields)
    }

    @Test
    fun newTotpReplacesOldFieldsWithAProtectedOtpUri() = runTest {
        open(mapOf("TOTP Seed" to SECRET, "TOTP Settings" to "30;6"))
        val uri = "otpauth://totp/GitHub?secret=$SECRET&digits=8"

        repository.updateEntry(entryUuid, form(otpUri = uri)).getOrThrow()

        val fields = savedFields()
        assertEquals(uri, fields["otp"]?.content)
        assertTrue(fields["otp"] is EntryValue.Encrypted)
        assertTrue("TOTP Seed" !in fields && "TOTP Settings" !in fields)
    }

    @Test
    fun emptyUriRemovesTotp() = runTest {
        open(mapOf("otp" to "otpauth://totp/GitHub?secret=$SECRET"))

        repository.updateEntry(entryUuid, form(otpUri = "")).getOrThrow()

        assertNull(savedFields()["otp"])
        assertNull(repository.getEntryByUuid(entryUuid).getOrThrow().otp)
    }

    @Test
    fun advancingACounterBasedUriSavesTheNextCounterAndKeepsItProtected() = runTest {
        open(mapOf("otp" to "otpauth://hotp/GitHub:octocat?secret=$SECRET&counter=4&digits=8"), protectedNames = setOf("otp"))

        val next = repository.advanceHotpCounter(entryUuid).getOrThrow()

        assertEquals(5L, next)
        val field = savedFields()["otp"]!!
        assertEquals("otpauth://hotp/GitHub:octocat?secret=$SECRET&digits=8&counter=5", field.content)
        assertTrue(field is EntryValue.Encrypted)
        assertEquals(5L, repository.getEntryByUuid(entryUuid).getOrThrow().otp!!.counter)
    }

    @Test
    fun advancingWritesNoHistoryEntry() = runTest {
        open(mapOf("otp" to "otpauth://hotp/GitHub?secret=$SECRET&counter=0"))

        repeat(3) { repository.advanceHotpCounter(entryUuid).getOrThrow() }

        assertEquals(3L, repository.getEntryByUuid(entryUuid).getOrThrow().otp!!.counter)
        assertTrue(savedEntry().history.isEmpty())
    }

    @Test
    fun advancingKeePassHmacOtpFieldsBumpsTheirCounter() = runTest {
        open(mapOf("HmacOtp-Secret-Base32" to SECRET, "HmacOtp-Counter" to "3"))

        assertEquals(4L, repository.advanceHotpCounter(entryUuid).getOrThrow())

        val fields = savedFields()
        assertEquals("4", fields["HmacOtp-Counter"]?.content)
        assertEquals(SECRET, fields["HmacOtp-Secret-Base32"]?.content)
        assertEquals(listOf("Title"), fields.keys.filter { !it.startsWith("HmacOtp-") })
    }

    @Test
    fun advancingATimeBasedCodeOrAMissingEntryFails() = runTest {
        open(mapOf("otp" to "otpauth://totp/GitHub?secret=$SECRET"))

        assertTrue(repository.advanceHotpCounter(entryUuid).isFailure)
        assertTrue(repository.advanceHotpCounter(UUID.randomUUID()).isFailure)
        assertEquals(false, repository.getEntryByUuid(entryUuid).getOrThrow().otp!!.isHotp)
    }

    private companion object {
        const val PASSWORD = "master"
        const val SECRET = "JBSWY3DPEHPK3PXP"
    }
}

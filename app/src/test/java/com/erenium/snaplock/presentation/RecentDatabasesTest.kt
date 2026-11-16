package com.erenium.snaplock.presentation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.datasource.security.BiometricVault
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.data.repository.folderOfDocumentId
import com.erenium.snaplock.presentation.main.AppMessages
import com.erenium.snaplock.presentation.selectfile.SelectFileViewModel
import com.erenium.snaplock.testing.FakeBiometricKeys
import com.erenium.snaplock.testing.FakeDocumentProvider
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentDatabasesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val encryptedPrefs = EncryptedPrefs(context)
    private val vault = BiometricVault(FakeBiometricKeys(), encryptedPrefs)
    private val keyFilePrefs = KeyFilePrefs(context)
    private val recentPrefs = RecentDatabasesPrefs(context)
    private val repository = RecentDatabasesRepository(context, recentPrefs, keyFilePrefs, vault)
    private val messages = AppMessages()

    @Before
    fun registerProvider() {
        Robolectric.setupContentProvider(FakeDocumentProvider::class.java, FakeDocumentProvider.AUTHORITY)
    }

    private fun uri(id: String) = FakeDocumentProvider.uri(id)

    @Test
    fun everyEntryIsProbedForNameAndSize() = runTest {
        listOf("gone", "empty", "ok").forEach { recentPrefs.add(uri(it)) }

        val entries = repository.entries.first()

        assertEquals(listOf("ok", "empty", "gone"), entries.map { it.uri.lastPathSegment })
        assertTrue(entries[0].available)
        assertEquals("ok.kdbx", entries[0].name)
        assertFalse(entries[1].available)
        assertFalse(entries[2].available)
    }

    @Test
    fun aProviderThatReportsNoSizeCountsAsPresent() = runTest {
        recentPrefs.add(uri("unknown-size"))

        assertTrue(repository.entries.first().single().available)
    }

    @Test
    fun opaqueIdsNeverReachTheList() = runTest {
        val opaque = Uri.parse("content://fake.documents/document/msf%3A123")
        recentPrefs.add(opaque)

        val entry = repository.entries.first().single()

        assertEquals(context.getString(R.string.recent_unknown_name), entry.name)
        assertFalse(entry.available)
    }

    @Test
    fun theLastOpenedTimeIsRemembered() = runTest {
        recentPrefs.add(uri("ok"))

        assertTrue(repository.entries.first().single().lastOpened!! > 0)
    }

    @Test
    fun folderNamesComeOnlyFromPathLikeDocumentIds() {
        assertEquals("Documents", folderOfDocumentId("primary:Documents/vault.kdbx"))
        assertEquals("Vaults", folderOfDocumentId("primary:Documents/Vaults/vault.kdbx"))
        assertNull(folderOfDocumentId("msf:123"))
        assertNull(folderOfDocumentId("primary:vault.kdbx"))
    }

    @Test
    fun removingForgetsTheKeyFileFingerprintCopyAndListEntry() = runTest {
        val database = uri("ok")
        recentPrefs.add(database)
        keyFilePrefs.set(database, uri("key"))
        encryptedPrefs.save(database, byteArrayOf(1), byteArrayOf(2))

        repository.remove(database)

        assertFalse(database in recentPrefs.uris.value)
        assertNull(keyFilePrefs.get(database))
        assertFalse(encryptedPrefs.has(database))
    }

    @Test
    fun aRemovalCanBeUndoneUntilItIsCommitted() = runTest {
        val first = uri("ok")
        val second = uri("empty")
        recentPrefs.add(second)
        recentPrefs.add(first)
        keyFilePrefs.set(first, uri("key"))
        val viewModel = SelectFileViewModel(repository, messages)

        viewModel.onRemoveRecent(first)
        assertEquals(listOf(second), recentPrefs.uris.value)
        assertEquals(uri("key"), keyFilePrefs.get(first))

        viewModel.onUndoRemove()
        assertEquals(listOf(first, second), recentPrefs.uris.value)
        assertEquals(uri("key"), keyFilePrefs.get(first))
    }

    @Test
    fun aDismissedUndoMessageMakesTheRemovalFinal() = runTest {
        val database = uri("ok")
        recentPrefs.add(database)
        keyFilePrefs.set(database, uri("key"))
        encryptedPrefs.save(database, byteArrayOf(1), byteArrayOf(2))
        val viewModel = SelectFileViewModel(repository, messages)

        viewModel.onRemoveRecent(database)
        viewModel.onRemovalDismissed()
        viewModel.onUndoRemove()

        assertTrue(recentPrefs.uris.value.isEmpty())
        assertNull(keyFilePrefs.get(database))
        assertFalse(encryptedPrefs.has(database))
    }

    @Test
    fun removingASecondEntryCommitsTheFirst() = runTest {
        val first = uri("ok")
        val second = uri("empty")
        recentPrefs.add(first)
        recentPrefs.add(second)
        keyFilePrefs.set(first, uri("key"))
        val viewModel = SelectFileViewModel(repository, messages)

        viewModel.onRemoveRecent(first)
        viewModel.onRemoveRecent(second)

        assertNull(keyFilePrefs.get(first))
    }
}

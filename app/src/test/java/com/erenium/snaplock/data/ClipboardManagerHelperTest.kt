package com.erenium.snaplock.data

import kotlinx.coroutines.test.advanceUntilIdle
import com.erenium.snaplock.data.datasource.cache.SessionCache
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.cryptography.EncryptedValue
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClipboardManagerHelperTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val settings = SettingsPrefs(context)

    private fun primaryText() = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

    private fun primaryIsSensitive() =
        clipboard.primaryClip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true

    @Test
    fun passwordsAreMarkedSensitive() = runTest(dispatcher) {
        ClipboardManagerHelper(context, settings).copyToClipboard("Password", "s3cret")

        assertEquals("s3cret", primaryText())
        assertTrue(primaryIsSensitive())
    }

    @Test
    fun usernamesAreNotMarkedSensitive() = runTest(dispatcher) {
        ClipboardManagerHelper(context, settings).copyToClipboard("Username", "octocat", sensitive = false)

        assertFalse(primaryIsSensitive())
    }

    @Test
    fun clipboardIsClearedAfterTheTimeout() = runTest(dispatcher) {
        settings.setClipboardAutoClear(true)
        settings.setClipboardTimeoutSeconds(15)

        ClipboardManagerHelper(context, settings).copyToClipboard("Password", "s3cret")
        advanceTimeBy(14_000)
        assertEquals("s3cret", primaryText())

        advanceTimeBy(2_000)
        assertTrue(primaryText().isNullOrEmpty())
    }

    @Test
    fun somethingCopiedLaterIsLeftAlone() = runTest(dispatcher) {
        settings.setClipboardAutoClear(true)
        settings.setClipboardTimeoutSeconds(15)

        ClipboardManagerHelper(context, settings).copyToClipboard("Password", "s3cret")
        advanceTimeBy(5_000)
        clipboard.setPrimaryClip(ClipData.newPlainText("other", "my address"))
        advanceTimeBy(20_000)

        assertEquals("my address", primaryText())
    }

    @Test
    fun aNewCopyRestartsTheTimeoutAndClearsOnlyTheNewValue() = runTest(dispatcher) {
        settings.setClipboardAutoClear(true)
        settings.setClipboardTimeoutSeconds(15)
        val helper = ClipboardManagerHelper(context, settings)

        helper.copyToClipboard("Password", "first")
        advanceTimeBy(10_000)
        helper.copyToClipboard("Password", "second")
        advanceTimeBy(10_000)
        assertEquals("second", primaryText())

        advanceTimeBy(6_000)
        assertTrue(primaryText().isNullOrEmpty())
    }

    @Test
    fun lockingTheVaultClearsAClipSnaplockStillOwns() = runTest(dispatcher) {
        settings.setClipboardAutoClear(true)
        settings.setClipboardTimeoutSeconds(120)
        val session = SessionCache()
        val helper = ClipboardManagerHelper(context, settings, session)
        advanceUntilIdle()
        session.setDatabase(KeePassDatabase.Ver4x.create(rootName = "x", meta = Meta(name = "x"), credentials = Credentials.from(EncryptedValue.fromString("p"))), Uri.parse("content://t/v.kdbx"))
        advanceUntilIdle()

        helper.copyToClipboard("Password", "s3cret")
        assertEquals("s3cret", primaryText())

        session.lock()
        advanceUntilIdle()

        assertTrue(primaryText().isNullOrEmpty())
    }

    @Test
    fun lockingLeavesSomethingCopiedLaterAlone() = runTest(dispatcher) {
        settings.setClipboardAutoClear(true)
        val session = SessionCache()
        val helper = ClipboardManagerHelper(context, settings, session)
        advanceUntilIdle()
        session.setDatabase(KeePassDatabase.Ver4x.create(rootName = "x", meta = Meta(name = "x"), credentials = Credentials.from(EncryptedValue.fromString("p"))), Uri.parse("content://t/v.kdbx"))
        advanceUntilIdle()

        helper.copyToClipboard("Password", "s3cret")
        clipboard.setPrimaryClip(ClipData.newPlainText("other", "my address"))
        session.lock()
        advanceUntilIdle()

        assertEquals("my address", primaryText())
    }
}

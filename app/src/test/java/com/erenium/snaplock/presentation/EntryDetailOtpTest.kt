package com.erenium.snaplock.presentation

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.otp.OtpParameters
import com.erenium.snaplock.domain.usecase.AddAttachmentUseCase
import com.erenium.snaplock.domain.usecase.AdvanceHotpCounterUseCase
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.GetAttachmentUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.GetRecycleBinEnabledUseCase
import com.erenium.snaplock.domain.usecase.RemoveAttachmentUseCase
import com.erenium.snaplock.domain.usecase.RestoreVersionUseCase
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import com.erenium.snaplock.testing.awaitReal
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntryDetailOtpTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uuid = UUID.randomUUID()
    private val repository = FakeKdbxRepository().apply {
        entryDetails[uuid] = EntryDetail(
            uuid, "Bank", "client", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID,
            otp = OtpParameters("12345678901234567890".toByteArray(), counter = 4)
        )
    }

    private fun viewModel() = EntryDetailViewModel(
        SavedStateHandle(mapOf("uuid" to uuid.toString())),
        GetEntryDetailsUseCase(repository),
        DeleteEntryUseCase(repository),
        RestoreVersionUseCase(repository),
        AddAttachmentUseCase(repository),
        GetAttachmentUseCase(repository),
        RemoveAttachmentUseCase(repository),
        DocumentFiles(context),
        ClipboardManagerHelper(context, SettingsPrefs(context)),
        GetRecycleBinEnabledUseCase(repository),
        RecentlyDeleted(),
        AdvanceHotpCounterUseCase(repository)
    )

    private suspend fun nextMessage(viewModel: EntryDetailViewModel) =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitReal { viewModel.messages.first() }
        }

    private fun clipboardText(): String? =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun theNextCodeAdvancesTheCounterAndShowsTheNewOne() = runTest {
        val viewModel = viewModel()

        viewModel.onNextOtp()

        assertEquals(listOf(uuid), repository.advancedCounters)
        assertEquals(5L, viewModel.uiState.value.entry!!.otp!!.counter)
    }

    @Test
    fun aFailedSaveKeepsTheCounterAndSaysSo() = runTest {
        repository.advanceFailure = IllegalStateException("The file is read only")
        val viewModel = viewModel()

        viewModel.onNextOtp()

        assertEquals(R.string.otp_next_failed, nextMessage(viewModel).first)
        assertEquals(4L, viewModel.uiState.value.entry!!.otp!!.counter)
        assertTrue(repository.advancedCounters.isEmpty())
    }

    @Test
    fun copyingACounterBasedCodeUsesTheCurrentCounterAndDoesNotAdvanceIt() = runTest {
        val viewModel = viewModel()

        viewModel.onCopyOtp("One-time code")

        assertEquals("338314", clipboardText())
        assertTrue(repository.advancedCounters.isEmpty())
        assertEquals(4L, viewModel.uiState.value.entry!!.otp!!.counter)
    }

    @Test
    fun nothingHappensWithoutAnEntry() = runTest {
        val viewModel = viewModel()
        viewModel.clear()

        viewModel.onNextOtp()

        assertTrue(repository.advancedCounters.isEmpty())
    }
}

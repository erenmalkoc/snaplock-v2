package com.erenium.snaplock.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.usecase.AddAttachmentUseCase
import com.erenium.snaplock.domain.usecase.AdvanceHotpCounterUseCase
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.GetAttachmentUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.GetRecycleBinEnabledUseCase
import com.erenium.snaplock.domain.usecase.RemoveAttachmentUseCase
import com.erenium.snaplock.domain.usecase.RestoreVersionUseCase
import com.erenium.snaplock.presentation.entrydetail.CopyEvent
import com.erenium.snaplock.presentation.entrydetail.EntryDetailUiState
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntryDetailPaneTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bank = UUID.randomUUID()
    private val mail = UUID.randomUUID()
    private val repository = FakeKdbxRepository().apply {
        entryDetails[bank] = EntryDetail(bank, "Bank", "client", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID)
        entryDetails[mail] = EntryDetail(mail, "Mail", "me", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID)
    }
    private val recentlyDeleted = RecentlyDeleted()

    private val viewModel = EntryDetailViewModel(
        SavedStateHandle(),
        GetEntryDetailsUseCase(repository),
        DeleteEntryUseCase(repository),
        RestoreVersionUseCase(repository),
        AddAttachmentUseCase(repository),
        GetAttachmentUseCase(repository),
        RemoveAttachmentUseCase(repository),
        DocumentFiles(context),
        ClipboardManagerHelper(context, SettingsPrefs(context)),
        GetRecycleBinEnabledUseCase(repository),
        recentlyDeleted,
        AdvanceHotpCounterUseCase(repository)
    )

    @Test
    fun waitsForAnEntryWithoutFailing() {
        viewModel.reload()

        assertNull(viewModel.uiState.value.entry)
        assertNull(viewModel.uiState.value.errorStringId)
    }

    @Test
    fun followsTheSelectionAndForgetsWhatWasRevealed() {
        viewModel.show(bank)
        assertEquals("Bank", viewModel.uiState.value.entry?.title)
        viewModel.togglePasswordVisibility()

        viewModel.show(mail)

        assertEquals("Mail", viewModel.uiState.value.entry?.title)
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun clearingTheSelectionForgetsTheEntryAndWhatWasRevealed() {
        viewModel.show(bank)
        viewModel.togglePasswordVisibility()

        viewModel.clear()
        assertNull(viewModel.uiState.value.entry)

        viewModel.show(bank)
        assertEquals("Bank", viewModel.uiState.value.entry?.title)
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun reloadingAnEntryOnScreenIsSilent() = runTest {
        viewModel.show(bank)
        val seen = mutableListOf<EntryDetailUiState>()
        backgroundScope.launch { viewModel.uiState.collect { seen += it } }
        seen.clear()

        viewModel.reload()

        assertTrue(seen.none { it.isLoading || it.entry == null })
        assertEquals("Bank", viewModel.uiState.value.entry?.title)
    }

    @Test
    fun aLoadFailureOffersRetryAndClearsOnSuccess() {
        val missing = UUID.randomUUID()
        viewModel.show(missing)
        assertEquals(R.string.entry_load_error, viewModel.uiState.value.errorStringId)
        assertNull(viewModel.uiState.value.entry)

        repository.entryDetails[missing] =
            EntryDetail(missing, "Late", null, "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID)
        viewModel.reload()

        assertNull(viewModel.uiState.value.errorStringId)
        assertEquals("Late", viewModel.uiState.value.entry?.title)
    }

    @Test
    fun aFailedDeleteKeepsTheEntryAndReportsThroughMessages() = runTest {
        viewModel.show(bank)
        repository.deleteFailure = IOException("disk full")

        viewModel.onDelete()

        assertEquals(R.string.entry_delete_error to null, viewModel.messages.first())
        assertEquals("Bank", viewModel.uiState.value.entry?.title)
        assertNull(viewModel.uiState.value.errorStringId)
    }

    @Test
    fun deleteWordingFollowsTheRecycleBinSetting() {
        viewModel.show(bank)
        assertTrue(viewModel.uiState.value.recycleBinEnabled)

        repository.databaseSettings = repository.databaseSettings?.copy(recycleBinEnabled = false)
        viewModel.reload()

        assertFalse(viewModel.uiState.value.recycleBinEnabled)
    }

    @Test
    fun deletingIntoTheBinOffersUndoButPermanentDeleteDoesNot() = runTest {
        viewModel.show(bank)
        viewModel.onDelete()
        assertEquals(DeletedEntry(bank, "Bank"), recentlyDeleted.events.first())
        viewModel.deletedEvents.first()

        repository.databaseSettings = repository.databaseSettings?.copy(recycleBinEnabled = false)
        viewModel.show(mail)
        viewModel.onDelete()
        viewModel.deletedEvents.first()
        recentlyDeleted.moved(DeletedEntry(UUID.randomUUID(), "marker"))

        assertEquals("marker", recentlyDeleted.events.first().title)
    }

    @Test
    fun copyingReportsWhenTheClipboardClears() = runTest {
        viewModel.show(bank)

        viewModel.onCopyPassword("Password")

        val event = viewModel.copyEvents.first()
        assertEquals(CopyEvent("Password", 30), event)
        assertNotNull(event.clearSeconds)
    }
}

package com.erenium.snaplock.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.usecase.AddAttachmentUseCase
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.GetAttachmentUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.RemoveAttachmentUseCase
import com.erenium.snaplock.domain.usecase.RestoreVersionUseCase
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The detail pane beside the list on wide screens has no navigation argument. */
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

    private val viewModel = EntryDetailViewModel(
        SavedStateHandle(),
        GetEntryDetailsUseCase(repository),
        DeleteEntryUseCase(repository),
        RestoreVersionUseCase(repository),
        AddAttachmentUseCase(repository),
        GetAttachmentUseCase(repository),
        RemoveAttachmentUseCase(repository),
        DocumentFiles(context),
        ClipboardManagerHelper(context, SettingsPrefs(context))
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
}

package com.erenium.snaplock.presentation

import androidx.lifecycle.SavedStateHandle
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.usecase.AddEntryUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import com.erenium.snaplock.domain.usecase.UpdateEntryUseCase
import com.erenium.snaplock.presentation.entryform.EntryFormViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EntryFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeKdbxRepository()

    private fun viewModel(uuid: UUID? = null) = EntryFormViewModel(
        SavedStateHandle(if (uuid != null) mapOf("uuid" to uuid.toString()) else emptyMap()),
        GetEntryDetailsUseCase(repository),
        GetGroupsUseCase(repository),
        AddEntryUseCase(repository),
        UpdateEntryUseCase(repository)
    )

    @Test
    fun newEntrySavesItsCustomFieldsAndSkipsUnnamedOnes() {
        val viewModel = viewModel()
        viewModel.onTitleChange("Bank")
        viewModel.onAddCustomField()
        viewModel.onAddCustomField()
        val (pin, unnamed) = viewModel.uiState.value.customFields
        viewModel.onCustomFieldChange(pin.copy(name = " PIN ", value = "1234", isProtected = true))
        viewModel.onCustomFieldChange(unnamed.copy(value = "no name"))

        viewModel.onSave()

        assertEquals(listOf(CustomField("PIN", "1234", isProtected = true)), repository.savedForms.single().customFields)
    }

    @Test
    fun editingLoadsExistingFieldsAndCanRemoveThem() {
        val uuid = UUID.randomUUID()
        repository.entryDetails[uuid] = EntryDetail(
            uuid, "Bank", "client", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID,
            customFields = listOf(CustomField("PIN", "1234", true), CustomField("Branch", "Manisa", false))
        )
        val viewModel = viewModel(uuid)
        val fields = viewModel.uiState.value.customFields
        assertEquals(listOf("PIN", "Branch"), fields.map { it.name })

        viewModel.onRemoveCustomField(fields.first().key)
        viewModel.onSave()

        assertEquals(listOf(CustomField("Branch", "Manisa", false)), repository.savedForms.single().customFields)
    }
}

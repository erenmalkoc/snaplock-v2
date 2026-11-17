package com.erenium.snaplock.presentation

import androidx.lifecycle.SavedStateHandle
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.error.VaultChangedElsewhereException
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.presentation.entryform.groupPaths
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.Expiry
import com.erenium.snaplock.domain.otp.OtpParser
import com.erenium.snaplock.domain.passwords.PasswordStrength
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.WordList
import com.erenium.snaplock.domain.usecase.GeneratePasswordUseCase
import com.erenium.snaplock.domain.usecase.AddEntryUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import com.erenium.snaplock.domain.usecase.UpdateEntryUseCase
import com.erenium.snaplock.presentation.entryform.EntryFormViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        UpdateEntryUseCase(repository),
        GeneratePasswordUseCase(),
        PasswordStrengthEstimator(object : WordList {
            override val words = listOf("abacus", "abdomen", "abide")
        })
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
    fun generatedPasswordIsStrongAndRated() {
        val viewModel = viewModel()

        viewModel.onGeneratePassword()

        val state = viewModel.uiState.value
        assertEquals(20, state.password.length)
        assertEquals(PasswordStrength.VERY_STRONG, state.passwordStrength?.strength)
        viewModel.onPasswordChange("123456")
        assertEquals(PasswordStrength.VERY_WEAK, viewModel.uiState.value.passwordStrength?.strength)
    }

    @Test
    fun twoFactorIsOnlyWrittenWhenTheUserChangesIt() {
        val uuid = UUID.randomUUID()
        repository.entryDetails[uuid] = EntryDetail(
            uuid, "GitHub", "octocat", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID,
            otp = OtpParser.parse(SECRET)
        )

        viewModel(uuid).onSave()
        assertNull(repository.savedForms.last().otpUri)

        viewModel(uuid).apply { onRemoveOtp() }.onSave()
        assertEquals("", repository.savedForms.last().otpUri)
    }

    @Test
    fun expiryIsOnlyWrittenWhenChanged() {
        val uuid = UUID.randomUUID()
        val date = Instant.parse("2027-03-01T00:00:00Z")
        repository.entryDetails[uuid] = EntryDetail(
            uuid, "VPN", "eren", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID, expires = date
        )

        val untouched = viewModel(uuid)
        assertEquals(date, untouched.uiState.value.expires)
        untouched.onSave()
        assertNull(repository.savedForms.last().expiry)

        viewModel(uuid).apply { onExpiryChange(null) }.onSave()
        assertEquals(Expiry(null), repository.savedForms.last().expiry)
    }

    @Test
    fun scannedOrTypedKeysAreValidated() {
        val viewModel = viewModel()
        viewModel.onTitleChange("GitHub")

        viewModel.onOtpEntered("not a key!")
        assertTrue(viewModel.uiState.value.otpInputError)
        assertNull(viewModel.uiState.value.otp)

        viewModel.onOtpEntered("otpauth://totp/GitHub:octocat?secret=$SECRET&issuer=GitHub")
        assertEquals("GitHub", viewModel.uiState.value.otp?.issuer)
        viewModel.onSave()

        val saved = OtpParser.parse(repository.savedForms.single().otpUri!!)
        assertEquals(viewModel.uiState.value.otp, saved)
    }

    @Test
    fun aKeyTypedButNotConfirmedIsSavedToo() {
        val viewModel = viewModel()
        viewModel.onTitleChange("GitHub")
        viewModel.onOtpInputChange(SECRET)

        viewModel.onSave()

        val saved = OtpParser.parse(repository.savedForms.single().otpUri!!)!!
        val typed = OtpParser.parse(SECRET)!!
        assertEquals(typed.secret.toList(), saved.secret.toList())
        assertEquals(typed.period to typed.digits, saved.period to saved.digits)
    }

    @Test
    fun anUnconfirmedBadKeyStopsTheSave() {
        val viewModel = viewModel()
        viewModel.onTitleChange("GitHub")
        viewModel.onOtpInputChange("not a key!")

        viewModel.onSave()

        assertTrue(viewModel.uiState.value.otpInputError)
        assertTrue(repository.savedForms.isEmpty())
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

    private fun storedEntry(): UUID {
        val uuid = UUID.randomUUID()
        repository.entryDetails[uuid] = EntryDetail(
            uuid, "Bank", "client", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID,
            customFields = listOf(CustomField("PIN", "1234", true))
        )
        return uuid
    }

    @Test
    fun aFreshFormIsCleanUntilTheUserTypesAndCleanAgainWhenTheyUndo() {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.isDirty)

        viewModel.onTitleChange("Bank")
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.onTitleChange("")
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun anEditedEntryIsCleanAfterLoadingAndDirtyAfterAnyChange() {
        val viewModel = viewModel(storedEntry())
        assertFalse(viewModel.uiState.value.isDirty)

        val pin = viewModel.uiState.value.customFields.single()
        viewModel.onCustomFieldChange(pin.copy(value = "9999"))
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.onCustomFieldChange(pin)
        assertFalse(viewModel.uiState.value.isDirty)

        viewModel.onAddCustomField()
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun theDefaultGroupDoesNotMakeAFreshFormDirty() {
        repository.groups.value = listOf(
            Group(FakeKdbxRepository.ROOT_GROUP_UUID, "Passwords", 0),
            Group(UUID.randomUUID(), "Work", 0, FakeKdbxRepository.ROOT_GROUP_UUID)
        )
        val viewModel = viewModel()

        assertEquals(FakeKdbxRepository.ROOT_GROUP_UUID, viewModel.uiState.value.groupUuid)
        assertFalse(viewModel.uiState.value.isDirty)

        viewModel.onGroupSelected(repository.groups.value.last().uuid)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun aBlankTitleShowsTheTitleErrorOnly() {
        val viewModel = viewModel()

        viewModel.onTitleChange("  ")
        assertTrue(viewModel.uiState.value.titleError)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onTitleChange("Bank")
        assertFalse(viewModel.uiState.value.titleError)
    }

    @Test
    fun aFailedSaveGoesToTheMessagesNotTheTitle() = runTest {
        repository.saveFailure = VaultChangedElsewhereException(IOException("wrong key"))
        val viewModel = viewModel()
        viewModel.onTitleChange("Bank")

        viewModel.onSave()

        assertEquals(R.string.vault_changed_elsewhere, viewModel.messages.first())
        assertFalse(viewModel.uiState.value.titleError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun savingShowsProgressUntilItFinishes() {
        val viewModel = viewModel()
        viewModel.onTitleChange("Bank")

        viewModel.onSave()

        assertEquals(1, repository.savedForms.size)
        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun aFailedLoadBlocksSavingAndCanBeRetried() = runTest {
        val uuid = UUID.randomUUID()
        val viewModel = viewModel(uuid)
        assertTrue(viewModel.uiState.value.loadFailed)
        assertEquals(R.string.entry_load_error, viewModel.messages.first())
        viewModel.onSave()
        assertTrue(repository.savedForms.isEmpty())

        repository.entryDetails[uuid] = EntryDetail(
            uuid, "Late", null, "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID
        )
        viewModel.onRetryLoad()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals("Late", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun duplicateOrReservedFieldNamesAreRefused() = runTest {
        val viewModel = viewModel()
        viewModel.onTitleChange("Bank")
        viewModel.onAddCustomField()
        viewModel.onAddCustomField()
        val (first, second) = viewModel.uiState.value.customFields
        viewModel.onCustomFieldChange(first.copy(name = "PIN"))
        viewModel.onCustomFieldChange(second.copy(name = " PIN "))

        viewModel.onSave()

        assertEquals(R.string.entry_form_duplicate_field, viewModel.messages.first())
        assertTrue(repository.savedForms.isEmpty())

        viewModel.onCustomFieldChange(second.copy(name = "Password"))
        viewModel.onSave()
        assertEquals(R.string.entry_form_duplicate_field, viewModel.messages.first())
        assertTrue(repository.savedForms.isEmpty())

        viewModel.onCustomFieldChange(second.copy(name = "Branch"))
        viewModel.onSave()
        assertEquals(1, repository.savedForms.size)
    }

    @Test
    fun generatingRevealsTheValueUntilTheUserEdits() {
        val viewModel = viewModel()

        viewModel.onGeneratePassword()
        assertTrue(viewModel.uiState.value.passwordGenerated)

        viewModel.onPasswordChange(viewModel.uiState.value.password + "x")
        assertFalse(viewModel.uiState.value.passwordGenerated)
    }

    @Test
    fun nestedGroupsWithTheSameNameGetDistinctPaths() {
        val work = UUID.randomUUID()
        val home = UUID.randomUUID()
        val workServers = UUID.randomUUID()
        val homeServers = UUID.randomUUID()
        val root = FakeKdbxRepository.ROOT_GROUP_UUID
        val paths = groupPaths(
            listOf(
                Group(root, "Passwords", 0),
                Group(work, "Work", 0, root),
                Group(workServers, "Servers", 0, work),
                Group(home, "Home", 0, root),
                Group(homeServers, "Servers", 0, home)
            )
        )

        assertEquals("Passwords", paths[root])
        assertEquals("Work", paths[work])
        assertEquals("Work / Servers", paths[workServers])
        assertEquals("Home / Servers", paths[homeServers])
    }

    private companion object {
        const val SECRET = "JBSWY3DPEHPK3PXP"
    }
}

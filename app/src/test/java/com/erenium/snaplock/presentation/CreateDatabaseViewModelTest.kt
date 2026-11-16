package com.erenium.snaplock.presentation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.WordList
import com.erenium.snaplock.domain.usecase.CreateDatabaseUseCase
import com.erenium.snaplock.presentation.createdatabase.CreateDatabaseViewModel
import com.erenium.snaplock.presentation.createdatabase.createErrorMessage
import com.erenium.snaplock.presentation.main.AppMessages
import com.erenium.snaplock.testing.FakeDocumentProvider
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class CreateDatabaseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository()
    private val messages = AppMessages()
    private val target = FakeDocumentProvider.uri("ok")

    @Before
    fun registerProvider() {
        Robolectric.setupContentProvider(FakeDocumentProvider::class.java, FakeDocumentProvider.AUTHORITY)
        FakeDocumentProvider.deleted.clear()
    }

    private fun viewModel() = CreateDatabaseViewModel(
        CreateDatabaseUseCase(repository),
        DocumentFiles(context),
        PasswordStrengthEstimator(object : WordList {
            override val words = listOf("alpha", "bravo", "charlie")
        }),
        messages
    )

    private fun CreateDatabaseViewModel.fill(password: String, confirmation: String = password) {
        onPasswordChange(password)
        onConfirmPasswordChange(confirmation)
    }

    @Test
    fun aMismatchIsNotReportedWhileTyping() {
        val viewModel = viewModel()

        viewModel.fill("correct horse", "correct h")

        assertFalse(viewModel.uiState.value.showMismatch)
    }

    @Test
    fun aMismatchIsReportedWhenTheFieldLosesFocus() {
        val viewModel = viewModel()
        viewModel.fill("correct horse", "correct h")

        viewModel.onConfirmFocusLost()

        assertTrue(viewModel.uiState.value.showMismatch)
    }

    @Test
    fun leavingAnEmptyConfirmationReportsNothing() {
        val viewModel = viewModel()
        viewModel.onPasswordChange("correct horse")

        viewModel.onConfirmFocusLost()

        assertFalse(viewModel.uiState.value.showMismatch)
    }

    @Test
    fun submittingAMismatchReportsItAndKeepsThePickerClosed() {
        val viewModel = viewModel()
        viewModel.fill("correct horse", "correct h")

        assertFalse(viewModel.onSubmit())
        assertTrue(viewModel.uiState.value.showMismatch)
    }

    @Test
    fun matchingPasswordsMayChooseALocation() {
        val viewModel = viewModel()
        viewModel.fill("correct horse")

        assertTrue(viewModel.onSubmit())
    }

    @Test
    fun shortPasswordsGetASoftWarningButStillWork() {
        val viewModel = viewModel()
        viewModel.fill("abc12")

        assertTrue(viewModel.uiState.value.isShort)
        assertNotNull(viewModel.uiState.value.strength)
        assertTrue(viewModel.onSubmit())

        viewModel.fill("a longer password")
        assertFalse(viewModel.uiState.value.isShort)
    }

    @Test
    fun aFailedCreationDeletesTheEmptyDocumentAndSaysWhy() = runTest {
        repository.createFailure = IOException("No space left on device")
        val viewModel = viewModel()
        viewModel.fill("correct horse")

        viewModel.onCreate(target)
        withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.uiState.first { it.errorStringId != null } } }

        assertEquals(R.string.create_db_write_error, viewModel.uiState.value.errorStringId)
        assertFalse(viewModel.uiState.value.isCreating)
        assertEquals(1, FakeDocumentProvider.deleted.size)
    }

    @Test
    fun theFailureReasonSeparatesReadOnlyLocationsFromWriteErrors() {
        assertEquals(R.string.create_db_read_only, SecurityException("denied").createErrorMessage())
        assertEquals(
            R.string.create_db_read_only,
            FileNotFoundException("open failed: EROFS (Read-only file system)").createErrorMessage()
        )
        assertEquals(R.string.create_db_write_error, IOException("disk full").createErrorMessage())
        assertEquals(R.string.create_db_error, IllegalStateException("x").createErrorMessage())
    }

    @Test
    fun aSuccessfulCreationAnnouncesTheFileAndKeepsTheDocument() = runTest {
        val viewModel = viewModel()
        viewModel.fill("correct horse")

        viewModel.onCreate(target)

        assertNotNull(withTimeoutOrNull(1_000) { viewModel.createdEvents.first() })
        assertEquals(R.string.create_db_saved, messages.messages.first().id)
        assertTrue(FakeDocumentProvider.deleted.isEmpty())
        assertNull(viewModel.uiState.value.errorStringId)
    }
}

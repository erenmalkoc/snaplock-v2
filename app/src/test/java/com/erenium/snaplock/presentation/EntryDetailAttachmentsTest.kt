package com.erenium.snaplock.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.local.FileTooLargeException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntryDetailAttachmentsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uuid = UUID.randomUUID()
    private val repository = FakeKdbxRepository().apply {
        entryDetails[uuid] = EntryDetail(uuid, "Bank", "client", "pw", null, null, FakeKdbxRepository.ROOT_GROUP_UUID)
    }
    private val documentFiles = DocumentFiles(context)

    private fun viewModel() = EntryDetailViewModel(
        SavedStateHandle(mapOf("uuid" to uuid.toString())),
        GetEntryDetailsUseCase(repository),
        DeleteEntryUseCase(repository),
        RestoreVersionUseCase(repository),
        AddAttachmentUseCase(repository),
        GetAttachmentUseCase(repository),
        RemoveAttachmentUseCase(repository),
        documentFiles,
        ClipboardManagerHelper(context, SettingsPrefs(context))
    )

    // DocumentFiles runs on a real IO thread, so wait in real time rather than test time.
    private suspend fun nextMessage(viewModel: EntryDetailViewModel) =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { viewModel.messages.first() }
        }

    @Test
    fun pickedFileIsStoredWithTheEntry() = runTest {
        val file = folder.newFile("statement.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val viewModel = viewModel()

        viewModel.onAddAttachment(Uri.fromFile(file))

        val message = nextMessage(viewModel)
        assertEquals(R.string.attachment_added, message.first)
        assertArrayEquals(byteArrayOf(1, 2, 3), repository.attachments.getValue(uuid).getValue("statement.pdf"))
    }

    @Test
    fun savedAttachmentLandsInTheChosenFile() = runTest {
        repository.attachments[uuid] = mutableMapOf("key.txt" to "secret".toByteArray())
        val target = folder.newFile("export.txt")
        val viewModel = viewModel()

        viewModel.onSaveAttachment("key.txt", Uri.fromFile(target))

        assertEquals(R.string.attachment_saved, nextMessage(viewModel).first)
        assertEquals("secret", target.readText())
    }

    @Test
    fun removingDropsTheAttachment() = runTest {
        repository.attachments[uuid] = mutableMapOf("key.txt" to "secret".toByteArray())

        viewModel().onRemoveAttachment("key.txt")

        assertTrue(repository.attachments.getValue(uuid).isEmpty())
    }

    @Test
    fun documentFilesRefuseFilesOverTheLimit() = runTest {
        val file = folder.newFile("big.bin").apply { writeBytes(ByteArray(11)) }

        val result = documentFiles.read(Uri.fromFile(file), maxBytes = 10)

        assertTrue(result.exceptionOrNull() is FileTooLargeException)
        assertEquals("big.bin", documentFiles.read(Uri.fromFile(file), maxBytes = 11).getOrThrow().name)
    }
}

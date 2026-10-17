package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.testing.FakeKdbxRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KdbxUseCaseTest {

    private val repository = FakeKdbxRepository()

    @Test
    fun getEntriesReturnsRepositoryEntries() = runTest {
        val expected = listOf(
            Entry(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                title = "Email",
                username = "eren",
                groupUuid = ROOT_GROUP_UUID,
                groupName = "Passwords"
            )
        )
        repository.entries.value = expected

        val actual = GetEntriesUseCase(repository)().first()

        assertEquals(expected, actual)
    }

    @Test
    fun getEntryDetailsReturnsRepositoryEntry() = runTest {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val expected = EntryDetail(
            uuid = uuid,
            title = "Server",
            username = "root",
            password = "secret",
            url = "https://example.com",
            notes = "production",
            groupUuid = ROOT_GROUP_UUID
        )
        repository.entryDetails[uuid] = expected

        val result = GetEntryDetailsUseCase(repository)(uuid)

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun lockDatabaseMarksRepositoryAsLocked() = runTest {
        repository.locked.value = false

        LockDatabaseUseCase(repository)()

        assertTrue(repository.locked.value)
    }

    @Test
    fun addEntryAppendsEntry() = runTest {
        AddEntryUseCase(repository)(
            EntryFormData(title = "Bank", username = "eren", password = "secret", url = null, notes = null, groupUuid = null)
        )

        val entries = repository.getEntries().first()
        assertEquals(1, entries.size)
        assertEquals("Bank", entries.first().title)
    }

    @Test
    fun deleteEntryRemovesEntry() = runTest {
        AddEntryUseCase(repository)(
            EntryFormData(title = "Bank", username = null, password = null, url = null, notes = null, groupUuid = null)
        )
        val uuid = repository.getEntries().first().first().uuid

        DeleteEntryUseCase(repository)(uuid)

        assertTrue(repository.getEntries().first().isEmpty())
    }

    private companion object {
        val ROOT_GROUP_UUID: UUID = FakeKdbxRepository.ROOT_GROUP_UUID
    }
}

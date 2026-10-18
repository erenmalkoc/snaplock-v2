package com.erenium.snaplock.domain.icons

import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.EntryIcon
import com.erenium.snaplock.testing.FakeKdbxRepository
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteIconsTest {

    @Test
    fun hostsComeFromWebAddressesOnly() {
        assertEquals("www.github.com", SiteHosts.hostOf("https://www.github.com/login?next=/"))
        assertEquals("example.org", SiteHosts.hostOf("example.org"))
        assertEquals("bank.example", SiteHosts.hostOf(" HTTP://Bank.Example.:8443/x "))
        assertNull(SiteHosts.hostOf("192.168.1.1"))
        assertNull(SiteHosts.hostOf("http://[::1]/"))
        assertNull(SiteHosts.hostOf("http://localhost:8080"))
        assertNull(SiteHosts.hostOf("nas"))
        assertNull(SiteHosts.hostOf("androidapp://com.example.app"))
        assertNull(SiteHosts.hostOf("ftp://files.example.com"))
        assertNull(SiteHosts.hostOf(""))
    }

    @Test
    fun eachSiteIsAskedOnceAndEntriesWithIconsAreSkipped() = runTest {
        val repository = FakeKdbxRepository().apply { locked.value = false }
        val first = credential("https://github.com/login")
        val second = credential("github.com")
        val hasIcon = credential("https://gitlab.com").copy(icon = EntryIcon(UUID.randomUUID(), byteArrayOf(1)))
        val noIcon = credential("https://nothing.example")
        val noSite = credential("192.168.0.1")
        repository.credentials.value = listOf(first, second, hasIcon, noIcon, noSite)
        val asked = mutableListOf<String>()
        val useCase = DownloadSiteIconsUseCase(repository) { host ->
            asked += host
            if (host == "github.com") byteArrayOf(7) else null
        }
        val progress = mutableListOf<Pair<Int, Int>>()

        assertEquals(3, useCase.pendingCount())
        val added = useCase { done, total -> progress += done to total }.getOrThrow()

        assertEquals(2, added)
        assertEquals(setOf("github.com", "nothing.example"), asked.toSet())
        assertEquals(2, asked.size)
        assertArrayEquals(byteArrayOf(7), repository.iconsSet[first.uuid])
        assertArrayEquals(byteArrayOf(7), repository.iconsSet[second.uuid])
        assertEquals(listOf(1 to 2, 2 to 2), progress.sortedBy { it.first })
    }

    @Test
    fun aFailingSiteDoesNotStopTheOthers() = runTest {
        val repository = FakeKdbxRepository().apply { locked.value = false }
        val broken = credential("https://broken.example")
        val working = credential("https://working.example")
        repository.credentials.value = listOf(broken, working)

        val added = DownloadSiteIconsUseCase(repository) { host ->
            if (host == "broken.example") error("connection reset") else byteArrayOf(3)
        }().getOrThrow()

        assertEquals(1, added)
        assertEquals(setOf(working.uuid), repository.iconsSet.keys)
    }

    private fun credential(url: String) =
        Credential(UUID.randomUUID(), "Entry", null, null, listOf(url), emptyList())
}

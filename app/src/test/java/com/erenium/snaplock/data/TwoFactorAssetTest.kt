package com.erenium.snaplock.data

import com.erenium.snaplock.domain.passwords.TwoFactorDomains
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFactorAssetTest {
    private val file = File("src/main/assets/tfa-totp-domains.txt")
    private val lines = file.readLines()
    private val domains = lines.filter { it.isNotBlank() && !it.startsWith("#") }

    @Test
    fun theShippedListNamesItsSourceAndLicense() {
        val header = lines.takeWhile { it.startsWith("#") }.joinToString("\n")

        assertTrue(header, "2fa.directory" in header)
        assertTrue(header, "MIT" in header && "2factorauth" in header)
        assertTrue(header, Regex("Retrieved: \\d{4}-\\d{2}-\\d{2}").containsMatchIn(header))
    }

    @Test
    fun theShippedListHasPlainSortedLowercaseDomainsOnly() {
        assertTrue("only ${domains.size} domains", domains.size > 1000)
        val shape = Regex("^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$")
        domains.forEach { assertTrue("not a plain domain: $it", shape.matches(it)) }
        assertEquals(domains.sorted(), domains)
        assertEquals(domains.distinct().size, domains.size)
    }

    @Test
    fun wellKnownSitesAreInTheShippedList() {
        val directory = TwoFactorDomains.parse(lines.asSequence())

        listOf("https://github.com/login", "https://www.dropbox.com", "https://www.paypal.com", "https://www.amazon.com")
            .forEach { assertTrue(it, directory.offersTotp(it)) }
        assertTrue(!directory.offersTotp("https://example.invalid"))
    }
}

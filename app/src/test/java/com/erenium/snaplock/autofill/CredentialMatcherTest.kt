package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialMatcherTest {

    private val github = credential("GitHub", urls = listOf("https://github.com/login"))
    private val exampleWork = credential("Example work", urls = listOf("login.example.com"))
    private val example = credential("Example", urls = listOf("https://www.example.com"))
    private val bankApp = credential("Bank", apps = listOf("com.bank.app"))
    private val edevlet = credential("e-Devlet", urls = listOf("https://giris.turkiye.gov.tr"))
    private val all = listOf(github, exampleWork, example, bankApp, edevlet)

    @Test
    fun browserMatchesExactHostFirstThenSameSite() {
        val matches = CredentialMatcher.match(all, CHROME, "example.com")

        assertEquals(listOf(example, exampleWork), matches)
    }

    @Test
    fun subdomainRequestMatchesTheParentSiteEntry() {
        assertEquals(listOf(github), CredentialMatcher.match(all, CHROME, "gist.github.com"))
    }

    @Test
    fun multiLabelSuffixesDoNotMergeUnrelatedSites() {
        val other = credential("Other gov", urls = listOf("https://www.other.gov.tr"))

        val matches = CredentialMatcher.match(all + other, CHROME, "turkiye.gov.tr")

        assertEquals(listOf(edevlet), matches)
    }

    @Test
    fun nativeAppMatchesOnItsPackage() {
        assertEquals(listOf(bankApp), CredentialMatcher.match(all, "com.bank.app", null))
    }

    @Test
    fun webViewInAnUntrustedAppDoesNotGetSiteCredentials() {
        val matches = CredentialMatcher.match(all, "com.evil.app", "github.com")

        assertTrue(matches.isEmpty())
    }

    @Test
    fun lookalikeDomainDoesNotMatch() {
        assertTrue(CredentialMatcher.match(all, CHROME, "github.com.evil.io").isEmpty())
        assertTrue(CredentialMatcher.match(all, CHROME, "notgithub.com").isEmpty())
    }

    @Test
    fun hostOfHandlesBareDomainsPortsAndCase() {
        assertEquals("example.com", CredentialMatcher.hostOf("WWW.Example.com:8443/path"))
        assertEquals("example.com", CredentialMatcher.hostOf("https://example.com/"))
        assertEquals(null, CredentialMatcher.hostOf("  "))
    }

    @Test
    fun registrableDomainKeepsIpAddressesWhole() {
        assertEquals("192.168.1.10", Domains.registrableDomain("192.168.1.10"))
        assertEquals("example.co.uk", Domains.registrableDomain("login.example.co.uk"))
    }

    private fun credential(title: String, urls: List<String> = emptyList(), apps: List<String> = emptyList()) =
        Credential(UUID.randomUUID(), title, "user", "pass", urls, apps)

    private companion object {
        const val CHROME = "com.android.chrome"
    }
}

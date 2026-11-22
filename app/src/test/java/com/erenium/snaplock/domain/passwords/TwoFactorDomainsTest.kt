package com.erenium.snaplock.domain.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFactorDomainsTest {
    private val directory = TwoFactorDomains.parse(
        sequenceOf("# a comment", "", "GitHub.com", "  bbc.co.uk  ", "accounts.example.org", "proton.me")
    )

    @Test
    fun theListSkipsCommentsAndBlankLinesAndIgnoresCase() {
        assertTrue(directory.offersTotp("https://github.com"))
        assertTrue(directory.offersTotp("https://bbc.co.uk"))
        assertFalse(directory.offersTotp("# a comment"))
    }

    @Test
    fun aListedDomainAndAnythingBelowItMatches() {
        assertTrue(directory.offersTotp("https://github.com/login"))
        assertTrue(directory.offersTotp("https://gist.github.com"))
        assertTrue(directory.offersTotp("https://a.b.c.github.com/x?y=1#z"))
        assertTrue(directory.offersTotp("https://www.proton.me"))
    }

    @Test
    fun theWholeHostHasToLineUpWithADotBoundary() {
        assertFalse(directory.offersTotp("https://notgithub.com"))
        assertFalse(directory.offersTotp("https://github.com.evil.example"))
        assertFalse(directory.offersTotp("https://githubx.com"))
    }

    @Test
    fun aSubdomainEntryDoesNotCoverItsParent() {
        assertTrue(directory.offersTotp("https://accounts.example.org"))
        assertTrue(directory.offersTotp("https://eu.accounts.example.org"))
        assertFalse(directory.offersTotp("https://example.org"))
        assertFalse(directory.offersTotp("https://shop.example.org"))
    }

    @Test
    fun domainsWithAMultiPartEndingWork() {
        assertTrue(directory.offersTotp("https://www.bbc.co.uk/iplayer"))
        assertFalse(directory.offersTotp("https://example.co.uk"))
    }

    @Test
    fun addressesWithoutASchemeOrWithAPortOrLoginDetailsStillWork() {
        assertTrue(directory.offersTotp("github.com"))
        assertTrue(directory.offersTotp("  GITHUB.COM/login "))
        assertTrue(directory.offersTotp("https://github.com:8443/x"))
        assertTrue(directory.offersTotp("https://me:secret@github.com/"))
        assertTrue(directory.offersTotp("https://github.com./"))
        assertTrue(directory.offersTotp("https://my_site.github.com/"))
    }

    @Test
    fun onlyWebsitesCount() {
        assertFalse(directory.offersTotp("androidapp://com.github.android"))
        assertFalse(directory.offersTotp("ftp://github.com"))
        assertFalse(directory.offersTotp("cmd://github.com"))
    }

    @Test
    fun addressesThatAreNotNamedHostsNeverMatch() {
        assertFalse(directory.offersTotp(null))
        assertFalse(directory.offersTotp(""))
        assertFalse(directory.offersTotp("   "))
        assertFalse(directory.offersTotp("localhost"))
        assertFalse(directory.offersTotp("http://192.168.1.1/admin"))
        assertFalse(directory.offersTotp("https://10.0.0.5:8080"))
        assertFalse(directory.offersTotp("com"))
    }

    @Test
    fun anEmptyListMatchesNothing() {
        assertFalse(TwoFactorDomains.parse(emptySequence()).offersTotp("https://github.com"))
        assertEquals(false, TwoFactorDomains(emptySet()).offersTotp("https://github.com"))
    }
}

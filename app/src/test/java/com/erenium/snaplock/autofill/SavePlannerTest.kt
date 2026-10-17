package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class SavePlannerTest {

    private val github = Credential(UUID.randomUUID(), "GitHub", "octocat", "old-pass", listOf("https://github.com"), emptyList())
    private val bank = Credential(UUID.randomUUID(), "Bank", "client", "1234", emptyList(), listOf("com.bank.app"))

    @Test
    fun unchangedLoginIsAlreadySaved() {
        val decision = SavePlanner.decide(listOf(github), CHROME, "github.com", "octocat", "old-pass")

        assertEquals(SaveDecision.AlreadySaved, decision)
    }

    @Test
    fun newPasswordForAKnownUserIsAnUpdate() {
        val decision = SavePlanner.decide(listOf(github), CHROME, "github.com", "octocat", "new-pass")

        assertEquals(SaveDecision.Update(github), decision)
    }

    @Test
    fun otherUsernameOnTheSameSiteIsNew() {
        val decision = SavePlanner.decide(listOf(github), CHROME, "github.com", "someone-else", "x")

        assertEquals(SaveDecision.New, decision)
    }

    @Test
    fun appLoginsMatchOnThePackage() {
        assertEquals(SaveDecision.Update(bank), SavePlanner.decide(listOf(bank), "com.bank.app", null, "client", "5678"))
        assertEquals(SaveDecision.New, SavePlanner.decide(listOf(bank), "com.other.app", null, "client", "5678"))
    }

    @Test
    fun urlUsesTheSiteOnlyForKnownBrowsers() {
        assertEquals("https://github.com", SavePlanner.urlFor(CHROME, "github.com"))
        assertEquals("androidapp://com.evil.app", SavePlanner.urlFor("com.evil.app", "github.com"))
        assertEquals("androidapp://com.bank.app", SavePlanner.urlFor("com.bank.app", null))
    }

    private companion object {
        const val CHROME = "com.android.chrome"
    }
}

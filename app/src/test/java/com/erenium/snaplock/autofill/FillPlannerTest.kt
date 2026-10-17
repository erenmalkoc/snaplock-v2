package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class FillPlannerTest {

    private val logins = (1..8).map { Credential(UUID.randomUUID(), "Login $it", "u$it", "p$it", emptyList(), emptyList()) }

    @Test
    fun lockedVaultOffersOnlyUnlock() {
        assertEquals(listOf(FillItem.Unlock), FillPlanner.plan(isLocked = true, matches = logins))
    }

    @Test
    fun unlockedVaultListsMatchesThenSearch() {
        val plan = FillPlanner.plan(isLocked = false, matches = logins.take(2))

        assertEquals(listOf(FillItem.Fill(logins[0]), FillItem.Fill(logins[1]), FillItem.Search), plan)
    }

    @Test
    fun capsDirectMatches() {
        val plan = FillPlanner.plan(isLocked = false, matches = logins)

        assertEquals(FillPlanner.MAX_DIRECT_MATCHES + 1, plan.size)
        assertEquals(FillItem.Search, plan.last())
    }

    @Test
    fun keepsSearchWithinTheKeyboardLimit() {
        assertEquals(3, FillPlanner.plan(isLocked = false, matches = logins, maxSuggestions = 3).size)
        assertEquals(listOf(FillItem.Search), FillPlanner.plan(isLocked = false, matches = logins, maxSuggestions = 1))
    }

    @Test
    fun noMatchesStillOffersSearch() {
        assertEquals(listOf(FillItem.Search), FillPlanner.plan(isLocked = false, matches = emptyList()))
    }
}

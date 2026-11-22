package com.erenium.snaplock.domain.search

import com.erenium.snaplock.domain.model.Entry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrySearchTest {
    private val group = UUID.randomUUID()

    private fun entry(
        title: String,
        username: String? = null,
        url: String? = null,
        notes: String? = null,
        fields: Map<String, String> = emptyMap()
    ) = Entry(UUID.randomUUID(), title, username, group, "Group", url = url, notes = notes, customFields = fields)

    private fun search(entries: List<Entry>, query: String) = EntrySearch.index(entries).search(query)

    private fun titles(hits: List<SearchHit>) = hits.map { it.entry.title }

    @Test
    fun findsTheTextInEveryKindOfField() {
        val all = listOf(
            entry("Alpha"),
            entry("Shop", username = "bravo"),
            entry("Site", url = "https://charlie.example"),
            entry("Account", fields = mapOf("Customer" to "delta-4711")),
            entry("Memo", notes = "echo foxtrot")
        )

        assertEquals(SearchMatch.TITLE, search(all, "alpha").single().match)
        assertEquals(SearchMatch.USERNAME, search(all, "bravo").single().match)
        assertEquals(SearchMatch.URL, search(all, "charlie").single().match)
        assertEquals(SearchMatch.FIELD, search(all, "4711").single().match)
        assertEquals(SearchMatch.NOTES, search(all, "foxtrot").single().match)
    }

    @Test
    fun theNameOfACustomFieldCanBeSearchedToo() {
        val all = listOf(entry("Account", fields = mapOf("Customer number" to "4711")), entry("Other"))

        assertEquals(listOf("Account"), titles(search(all, "customer")))
        assertEquals(listOf("Account"), titles(search(all, "customer 4711")))
    }

    @Test
    fun caseAndTurkishLettersDoNotGetInTheWay() {
        val all = listOf(
            entry("Şifre Kasası"),
            entry("ISPARTA Belediyesi"),
            entry("İş Bankası"),
            entry("Çağrı Merkezi")
        )

        assertEquals(listOf("Şifre Kasası"), titles(search(all, "sifre")))
        assertEquals(listOf("Şifre Kasası"), titles(search(all, "ŞİFRE")))
        assertEquals(listOf("ISPARTA Belediyesi"), titles(search(all, "ısparta")))
        assertEquals(listOf("İş Bankası"), titles(search(all, "IS BANKASI")))
        assertEquals(listOf("Çağrı Merkezi"), titles(search(all, "cagri")))
    }

    @Test
    fun everyWordHasToBeFoundButNotInTheSameField() {
        val all = listOf(
            entry("GitHub", username = "ahmet"),
            entry("GitLab", username = "ahmet"),
            entry("Mail", username = "mehmet")
        )

        assertEquals(listOf("GitHub"), titles(search(all, "github ahmet")))
        assertEquals(listOf("GitHub"), titles(search(all, "ahmet github")))
        assertEquals(listOf("GitHub", "GitLab"), titles(search(all, "ahmet")))
        assertTrue(search(all, "github zzzz").isEmpty())
    }

    @Test
    fun betterMatchesComeFirst() {
        val all = listOf(
            entry("Misc", url = "https://bank.example"),
            entry("Memo", notes = "my bank account"),
            entry("Rebank"),
            entry("Shop", username = "bank"),
            entry("Banking app"),
            entry("Bank of Mars"),
            entry("Bank")
        )

        assertEquals(
            listOf("Bank", "Bank of Mars", "Banking app", "Rebank", "Shop", "Misc", "Memo"),
            titles(search(all, "bank"))
        )
    }

    @Test
    fun entriesThatScoreTheSameAreOrderedByTheGivenComparator() {
        val all = listOf(entry("Beta Bank"), entry("Alpha Bank"), entry("Gamma Bank"))
        val index = EntrySearch.index(all)

        assertEquals(listOf("Alpha Bank", "Beta Bank", "Gamma Bank"), titles(index.search("bank")))
        assertEquals(
            listOf("Gamma Bank", "Beta Bank", "Alpha Bank"),
            titles(index.search("bank", Comparator.reverseOrder()))
        )
    }

    @Test
    fun theLabelNamesTheBestPlaceNotJustTheFirstOne() {
        val github = entry("GitHub", username = "octocat", url = "https://github.com")

        assertEquals(SearchMatch.TITLE, search(listOf(github), "git").single().match)
        assertEquals(SearchMatch.USERNAME, search(listOf(github), "octo").single().match)
    }

    @Test
    fun aBlankQueryFindsNothing() {
        val all = listOf(entry("Alpha"))

        assertTrue(search(all, "").isEmpty())
        assertTrue(search(all, "   ").isEmpty())
    }

    @Test
    fun forgivesATypoWhenNothingElseMatches() {
        val all = listOf(entry("GitHub"), entry("Google"), entry("Dropbox"))

        val hit = search(all, "githbu").single()

        assertEquals("GitHub", hit.entry.title)
        assertEquals(SearchMatch.SIMILAR, hit.match)
        assertEquals(listOf("Google"), titles(search(all, "gogle")))
        assertEquals(listOf("Dropbox"), titles(search(all, "dropbxo")))
    }

    @Test
    fun doesNotGuessWhenSomethingMatchesExactly() {
        val all = listOf(entry("Bank"), entry("Tank Wallet"))

        assertEquals(listOf("Bank"), titles(search(all, "bank")))
    }

    @Test
    fun shortWordsGetNoAllowance() {
        val all = listOf(entry("GitHub"), entry("Cat"))

        assertTrue(search(all, "gth").isEmpty())
        assertTrue(search(all, "cut").isEmpty())
    }

    @Test
    fun longerWordsAllowMoreMistakes() {
        val all = listOf(entry("Wikipedia Account"), entry("GitHub"))

        // Eight letters or more: two edits are fine.
        assertEquals(listOf("Wikipedia Account"), titles(search(all, "wkipeida")))
        // Four to seven letters: one edit only, two are too many.
        assertTrue(search(all, "gxtgub").isEmpty())
        assertEquals(listOf("GitHub"), titles(search(all, "gihub")))
    }

    @Test
    fun aWordStillBeingTypedIsComparedByItsBeginning() {
        val all = listOf(entry("GitHub"))

        assertEquals(listOf("GitHub"), titles(search(all, "gitgu")))
    }

    @Test
    fun notesAreNeverMatchedBySimilarity() {
        val all = listOf(entry("Bank", notes = "my birthday"))

        assertTrue(search(all, "birthdya").isEmpty())
    }

    @Test
    fun aTypoInOneWordStillNeedsTheOtherWordsExactly() {
        val all = listOf(entry("GitHub", username = "ahmet"), entry("GitHub", username = "mehmet"))

        val hits = search(all, "githbu ahmet")

        assertEquals(listOf("ahmet"), hits.map { it.entry.username })
        assertEquals(SearchMatch.SIMILAR, hits.single().match)
    }

    @Test
    fun similarResultsAreOrderedByWhereTheyMatch() {
        val all = listOf(entry("Shop", username = "github-bot"), entry("Github"))

        assertEquals(listOf("Github", "Shop"), titles(search(all, "githbu")))
    }

    @Test
    fun theTypoPassIsNotUsedForTheGroupFilter() {
        assertTrue(EntrySearch.matches("wor", "Work"))
        assertFalse(EntrySearch.matches("wrok", "Work"))
    }

    @Test
    fun matchesChecksEveryWordAgainstAnyOfTheTexts() {
        assertTrue(EntrySearch.matches("sifre", "Şifre Kasası"))
        assertTrue(EntrySearch.matches("kasa ahmet", "Şifre Kasası", "AHMET", null))
        assertFalse(EntrySearch.matches("kasa mehmet", "Şifre Kasası", "ahmet"))
        assertTrue(EntrySearch.matches("", "anything"))
        assertTrue(EntrySearch.matches("   "))
        assertFalse(EntrySearch.matches("x", null))
    }

    @Test
    fun typoDistanceCountsEditsAndSwaps() {
        assertEquals(0, typoDistance("github", "github"))
        assertEquals(3, typoDistance("kitten", "sitting"))
        assertEquals(1, typoDistance("ab", "ba"))
        assertEquals(1, typoDistance("github", "githbu"))
        assertEquals(1, typoDistance("github", "gthub"))
        assertEquals(3, typoDistance("", "abc"))
        assertEquals(3, typoDistance("abc", ""))
        assertEquals(2, typoDistance("wkipeida", "wikipedia"))
    }

    @Test
    fun theAllowanceGrowsWithTheLengthOfTheWord() {
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 1, 2, 2), (1..9).map { typoAllowance(it) })
    }
}

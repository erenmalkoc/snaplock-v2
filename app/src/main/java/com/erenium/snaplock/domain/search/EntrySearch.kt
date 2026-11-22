package com.erenium.snaplock.domain.search

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.text.foldForSearch
import kotlin.math.min

/** Where a result matched. SIMILAR means it only matched with a typing mistake allowed. */
enum class SearchMatch { TITLE, USERNAME, URL, FIELD, NOTES, SIMILAR }

class SearchHit(val entry: Entry, val match: SearchMatch, val score: Int)

/**
 * Searches entries the way people type: accents and case do not matter (so "sifre" finds "Şifre"),
 * every word of the query has to be found somewhere in the entry, and better matches come first.
 * When nothing matches at all, a second pass forgives a typing mistake in longer words.
 */
object EntrySearch {
    private val whitespace = Regex("\\s+")
    // A hyphen, apostrophe or underscore between two letters or digits joins them: "wifi" finds "Wi-Fi".
    private val joiners = Regex("(?<=[\\p{L}\\p{N}])[-'’_](?=[\\p{L}\\p{N}])")

    /** Prepares the text of every entry once, so each keystroke only compares strings. */
    fun index(entries: List<Entry>): SearchIndex = SearchIndex(entries, entries.map { Item(it) })

    /** True when every word of the query is found in at least one of the texts. A blank query matches. */
    fun matches(query: String, vararg texts: String?): Boolean {
        val tokens = tokensOf(query)
        if (tokens.isEmpty()) return true
        val folded = texts.mapNotNull { it?.foldForSearch() }
        return tokens.all { token ->
            folded.any { it.contains(token.text) || it.withoutJoiners().contains(token.joined) }
        }
    }

    internal fun tokensOf(query: String): List<Token> =
        query.foldForSearch().split(whitespace).filter { it.isNotEmpty() }.map { Token(it) }

    internal fun String.withoutJoiners(): String = replace(joiners, "")
}

internal class Token(val text: String) {
    val joined: String = with(EntrySearch) { text.withoutJoiners() }
    val length: Int get() = text.length
}

class SearchIndex internal constructor(val entries: List<Entry>, private val items: List<Item>) {
    /** Best matches first. Entries that score the same are ordered by title with [ties]. */
    fun search(query: String, ties: Comparator<String> = String.CASE_INSENSITIVE_ORDER): List<SearchHit> {
        val tokens = EntrySearch.tokensOf(query)
        if (tokens.isEmpty()) return emptyList()
        var hits = items.mapNotNull { it.strictHit(tokens) }
        if (hits.isEmpty() && tokens.any { typoAllowance(it.length) > 0 }) {
            hits = items.mapNotNull { it.similarHit(tokens) }
        }
        return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy(ties) { it.entry.title })
    }
}

internal class Folded(raw: String) {
    val text = raw.foldForSearch()
    val joined: String = with(EntrySearch) { text.withoutJoiners() }

    // Both spellings of a word count, "wi-fi" and "wifi".
    val words: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        (text.split(NON_WORD) + joined.split(NON_WORD)).filter { it.isNotEmpty() }.distinct()
    }

    /** How well the token is in this text, trying the punctuation-free spelling of both when it differs. */
    fun quality(token: Token): Int {
        val direct = quality(text, token.text)
        if (joined == text && token.joined == token.text) return direct
        return maxOf(direct, quality(joined, token.joined))
    }

    private companion object {
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    }
}

internal class Item(val entry: Entry) {
    // Field order is the ranking order: a title beats a user name beats a URL and so on.
    val searchable: List<Pair<Folded, SearchMatch>> = buildList {
        add(Folded(entry.title) to SearchMatch.TITLE)
        entry.username?.takeIf { it.isNotBlank() }?.let { add(Folded(it) to SearchMatch.USERNAME) }
        entry.url?.takeIf { it.isNotBlank() }?.let { add(Folded(it) to SearchMatch.URL) }
        entry.customFields.forEach { (name, value) -> add(Folded("$name $value") to SearchMatch.FIELD) }
        entry.notes?.takeIf { it.isNotBlank() }?.let { add(Folded(it) to SearchMatch.NOTES) }
    }
}

private class Found(val score: Int, val field: SearchMatch)

private fun SearchMatch.tier(): Int = when (this) {
    SearchMatch.TITLE -> 5
    SearchMatch.USERNAME -> 4
    SearchMatch.URL -> 3
    SearchMatch.FIELD -> 2
    SearchMatch.NOTES -> 1
    SearchMatch.SIMILAR -> 0
}

private fun Item.strictHit(tokens: List<Token>): SearchHit? {
    var total = 0
    var best: Found? = null
    for (token in tokens) {
        val found = bestStrict(token) ?: return null
        total += found.score
        if (best == null || found.score > best.score) best = found
    }
    return best?.let { SearchHit(entry, it.field, total) }
}

private fun Item.similarHit(tokens: List<Token>): SearchHit? {
    var total = 0
    for (token in tokens) {
        total += bestStrict(token)?.score ?: bestSimilar(token) ?: return null
    }
    return SearchHit(entry, SearchMatch.SIMILAR, total)
}

private fun Item.bestStrict(token: Token): Found? {
    var best: Found? = null
    for ((folded, field) in searchable) {
        val quality = folded.quality(token)
        if (quality == 0) continue
        // Where it matched counts first, how closely it matched breaks the tie inside that field.
        val score = field.tier() * 10 + quality
        if (best == null || score > best.score) best = Found(score, field)
    }
    return best
}

/** 4 the whole text, 3 a whole word, 2 the start of a word, 1 somewhere inside, 0 not there. */
private fun quality(text: String, token: String): Int {
    var best = 0
    var from = 0
    while (true) {
        val at = text.indexOf(token, from)
        if (at < 0) return best
        val end = at + token.length
        val startsWord = at == 0 || !text[at - 1].isLetterOrDigit()
        val endsWord = end == text.length || !text[end].isLetterOrDigit()
        val found = when {
            text.length == token.length -> 4
            startsWord && endsWord -> 3
            startsWord -> 2
            else -> 1
        }
        if (found > best) best = found
        from = at + 1
    }
}

// Short words are too easy to hit by accident, so they get no allowance at all.
internal fun typoAllowance(length: Int): Int = when {
    length >= 8 -> 2
    length >= 4 -> 1
    else -> 0
}

// Notes are long free text, a typo there would match almost anything, so only the other fields count.
private fun Item.bestSimilar(token: Token): Int? {
    val allowance = typoAllowance(token.length)
    if (allowance == 0) return null
    var best: Int? = null
    for ((folded, field) in searchable) {
        if (field == SearchMatch.NOTES) continue
        for (word in folded.words) {
            val distance = distanceToWord(token.joined, word, allowance)
            if (distance > allowance) continue
            val score = field.tier() * 3 + (3 - distance)
            if (best == null || score > best) best = score
        }
    }
    return best
}

// A word that is still being typed is compared with the same number of letters from its start.
private fun distanceToWord(token: String, word: String, limit: Int): Int {
    if (word.length < token.length - limit) return limit + 1
    val whole = typoDistance(token, word)
    if (word.length <= token.length) return whole
    return min(whole, typoDistance(token, word.substring(0, token.length)))
}

/** Edits needed to turn one text into the other. Swapping two neighbouring letters counts as one. */
internal fun typoDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var twoAgo = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var value = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                value = min(value, twoAgo[j - 2] + 1)
            }
            current[j] = value
        }
        val spare = twoAgo
        twoAgo = previous
        previous = current
        current = spare
    }
    return previous[b.length]
}

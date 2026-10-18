package com.erenium.snaplock.domain.passwords

import javax.inject.Inject
import kotlin.math.log2
import kotlin.math.min

enum class PasswordStrength { VERY_WEAK, WEAK, FAIR, STRONG, VERY_STRONG }

data class StrengthEstimate(val strength: PasswordStrength, val entropyBits: Double)

/**
 * A quick offline estimate: character-pool entropy, discounted for repeats and runs,
 * with known passwords rated very weak and wordlist passphrases rated by word count.
 * Not a full zxcvbn, but it catches the mistakes people actually make.
 */
class PasswordStrengthEstimator @Inject constructor(
    private val wordList: WordList
) {

    private val knownWords: Set<String> by lazy { wordList.words.toHashSet() }

    fun estimate(password: String): StrengthEstimate {
        if (password.isEmpty()) return StrengthEstimate(PasswordStrength.VERY_WEAK, 0.0)
        if (password.lowercase() in COMMON_PASSWORDS) return StrengthEstimate(PasswordStrength.VERY_WEAK, 0.0)
        val bits = min(characterEntropy(password), passphraseEntropy(password) ?: Double.MAX_VALUE)
        return StrengthEstimate(strengthFor(bits), bits)
    }

    private fun characterEntropy(password: String): Double {
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { it.code < 128 && !it.isLetterOrDigit() }) pool += 33
        if (password.any { it.code >= 128 }) pool += 100
        // Every character after a repeat or a +/-1 run (aaa, abc, 321) adds almost nothing.
        var effectiveLength = 1.0
        for (i in 1 until password.length) {
            val step = password[i] - password[i - 1]
            effectiveLength += if (step == 0 || step == 1 || step == -1) 0.25 else 1.0
        }
        if (KEYBOARD_ROWS.any { row -> password.lowercase().windowed(4).any { row.contains(it) } }) {
            effectiveLength *= 0.7
        }
        return effectiveLength * log2(pool.toDouble())
    }

    /** Bits for passwords built from wordlist words, such as generated passphrases. */
    private fun passphraseEntropy(password: String): Double? {
        val parts = password.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (parts.size < 3 || parts.any { it.lowercase() !in knownWords }) return null
        val perWord = log2(wordList.words.size.toDouble())
        val extras = if (password.any(Char::isDigit)) log2(10.0) else 0.0
        return parts.size * perWord + extras
    }

    private fun strengthFor(bits: Double): PasswordStrength = when {
        bits < 28 -> PasswordStrength.VERY_WEAK
        bits < 40 -> PasswordStrength.WEAK
        bits < 60 -> PasswordStrength.FAIR
        bits < 90 -> PasswordStrength.STRONG
        else -> PasswordStrength.VERY_STRONG
    }

    private companion object {
        val KEYBOARD_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "qazwsxedc")

        // The most used passwords in public breach lists, plus common Turkish ones.
        val COMMON_PASSWORDS = setOf(
            "123456", "123456789", "12345678", "12345", "1234567", "1234567890", "1234", "111111",
            "000000", "123123", "654321", "666666", "121212", "112233", "123321", "987654321",
            "password", "password1", "password123", "passw0rd", "qwerty", "qwerty123", "qwertyuiop",
            "abc123", "iloveyou", "1q2w3e4r", "1q2w3e4r5t", "1qaz2wsx", "zaq12wsx", "asdfghjkl",
            "dragon", "sunshine", "princess", "letmein", "monkey", "superman", "football", "baseball",
            "welcome", "admin", "admin123", "login", "master", "shadow", "michael", "trustno1",
            "sifre", "şifre", "sifre123", "parola", "parola123", "fenerbahce", "galatasaray",
            "besiktas", "trabzonspor", "turkiye", "türkiye", "istanbul", "ankara", "izmir", "ataturk"
        )
    }
}

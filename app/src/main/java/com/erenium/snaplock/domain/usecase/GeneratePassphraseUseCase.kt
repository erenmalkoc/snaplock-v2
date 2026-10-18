package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.PassphraseOptions
import com.erenium.snaplock.domain.passwords.WordList
import java.security.SecureRandom
import javax.inject.Inject

/** Diceware-style passphrases: each word adds log2(word count) bits, about 12.9 with the EFF list. */
class GeneratePassphraseUseCase @Inject constructor(
    private val wordList: WordList
) {

    private val secureRandom = SecureRandom()

    operator fun invoke(options: PassphraseOptions): String {
        val words = wordList.words
        if (words.isEmpty()) return ""
        val count = options.wordCount.coerceIn(MIN_WORDS, MAX_WORDS)
        val chosen = MutableList(count) { words[secureRandom.nextInt(words.size)] }
            .map { word -> if (options.capitalize) word.replaceFirstChar(Char::uppercaseChar) else word }
            .toMutableList()
        if (options.includeNumber) {
            val index = secureRandom.nextInt(count)
            chosen[index] = chosen[index] + secureRandom.nextInt(10)
        }
        return chosen.joinToString(options.separator)
    }

    companion object {
        const val MIN_WORDS = 3
        const val MAX_WORDS = 12
    }
}

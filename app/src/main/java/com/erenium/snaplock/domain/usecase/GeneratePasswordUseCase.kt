package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.PasswordOptions
import java.security.SecureRandom
import javax.inject.Inject

class GeneratePasswordUseCase @Inject constructor() {

    private val secureRandom = SecureRandom()

    operator fun invoke(options: PasswordOptions): String {
        val pools = buildList {
            if (options.useLowercase) add(LOWERCASE)
            if (options.useUppercase) add(UPPERCASE)
            if (options.useDigits) add(DIGITS)
            if (options.useSymbols) add(SYMBOLS)
        }
        if (pools.isEmpty()) return ""

        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val combined = pools.joinToString(separator = "")
        val result = CharArray(length)

        for (i in pools.indices) {
            result[i] = pools[i].randomChar()
        }
        for (i in pools.size until length) {
            result[i] = combined.randomChar()
        }

        for (i in result.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }
        return String(result)
    }

    private fun String.randomChar(): Char = this[secureRandom.nextInt(length)]

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private const val SYMBOLS = "!@#$%^&*()-_=+[]{}?"
    }
}

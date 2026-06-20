package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.PasswordOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratePasswordUseCaseTest {

    private val useCase = GeneratePasswordUseCase()

    @Test
    fun generatesPasswordWithRequestedLength() {
        val password = useCase(PasswordOptions(length = 24))
        assertEquals(24, password.length)
    }

    @Test
    fun clampsLengthToBounds() {
        assertEquals(GeneratePasswordUseCase.MIN_LENGTH, useCase(PasswordOptions(length = 1)).length)
        assertEquals(GeneratePasswordUseCase.MAX_LENGTH, useCase(PasswordOptions(length = 999)).length)
    }

    @Test
    fun returnsEmptyWhenNoCharSetSelected() {
        val password = useCase(
            PasswordOptions(
                useLowercase = false,
                useUppercase = false,
                useDigits = false,
                useSymbols = false
            )
        )
        assertEquals("", password)
    }

    @Test
    fun usesOnlySelectedCharSet() {
        val password = useCase(
            PasswordOptions(
                length = 40,
                useLowercase = false,
                useUppercase = false,
                useDigits = true,
                useSymbols = false
            )
        )
        assertTrue(password.all { it.isDigit() })
    }

    @Test
    fun includesAtLeastOneOfEachSelectedSet() {
        repeat(50) {
            val password = useCase(
                PasswordOptions(
                    length = 8,
                    useLowercase = true,
                    useUppercase = true,
                    useDigits = true,
                    useSymbols = true
                )
            )
            assertTrue(password.any { it.isLowerCase() })
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isDigit() })
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }
}

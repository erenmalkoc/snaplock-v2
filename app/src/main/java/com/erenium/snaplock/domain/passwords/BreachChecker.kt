package com.erenium.snaplock.domain.passwords

interface BreachChecker {
    suspend fun breachCount(password: String): Result<Int>
}

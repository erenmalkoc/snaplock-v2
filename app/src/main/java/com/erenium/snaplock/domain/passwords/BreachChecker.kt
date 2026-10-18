package com.erenium.snaplock.domain.passwords

/** Looks a password up in known data breaches. */
interface BreachChecker {
    /** How often the password appears in breaches; 0 if it does not. */
    suspend fun breachCount(password: String): Result<Int>
}

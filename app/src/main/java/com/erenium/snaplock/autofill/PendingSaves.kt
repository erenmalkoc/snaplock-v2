package com.erenium.snaplock.autofill

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object PendingSaves {

    private const val MAX_AGE_MILLIS = 5 * 60 * 1000L

    private class Held(val password: String, val storedAt: Long)

    private val held = ConcurrentHashMap<String, Held>()

    fun put(password: String, now: Long = System.currentTimeMillis()): String {
        purge(now)
        val token = UUID.randomUUID().toString()
        held[token] = Held(password, now)
        return token
    }

    fun take(token: String?, now: Long = System.currentTimeMillis()): String? {
        if (token == null) return null
        val entry = held.remove(token) ?: return null
        return entry.password.takeIf { now - entry.storedAt <= MAX_AGE_MILLIS }
    }

    fun clear() = held.clear()

    private fun purge(now: Long) {
        held.entries.removeIf { now - it.value.storedAt > MAX_AGE_MILLIS }
    }
}

package com.erenium.snaplock.autofill

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingSavesTest {

    @After
    fun tearDown() = PendingSaves.clear()

    @Test
    fun handsThePasswordOverOnceAndForgetsIt() {
        val token = PendingSaves.put("hunter2", now = 0)

        assertNotEquals("hunter2", token)
        assertEquals("hunter2", PendingSaves.take(token, now = 1_000))
        assertNull(PendingSaves.take(token, now = 1_000))
    }

    @Test
    fun anOldPasswordIsNotHandedOver() {
        val token = PendingSaves.put("hunter2", now = 0)

        assertNull(PendingSaves.take(token, now = 6 * 60 * 1000L))
    }

    @Test
    fun unknownOrMissingTokensGiveNothing() {
        assertNull(PendingSaves.take("nope"))
        assertNull(PendingSaves.take(null))
    }

    @Test
    fun expiredEntriesAreDroppedWhenAnotherPasswordArrives() {
        val old = PendingSaves.put("old", now = 0)
        PendingSaves.put("new", now = 10 * 60 * 1000L)

        assertNull(PendingSaves.take(old, now = 10 * 60 * 1000L))
    }
}

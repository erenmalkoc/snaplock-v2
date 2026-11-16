package com.erenium.snaplock.presentation

import com.erenium.snaplock.presentation.entryform.pickerMillisToDate
import com.erenium.snaplock.presentation.entryform.toExpiryDate
import com.erenium.snaplock.presentation.entryform.toExpiryInstant
import com.erenium.snaplock.presentation.entryform.toPickerMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryDatesTest {

    private val newYork = ZoneId.of("America/New_York")
    private val istanbul = ZoneId.of("Europe/Istanbul")

    @Test
    fun theChosenDayStartsAtLocalMidnightAndReadsBackAsTheSameDay() {
        val day = LocalDate.of(2027, 1, 31)

        listOf(newYork, istanbul, ZoneId.of("UTC"), ZoneId.of("Pacific/Auckland")).forEach { zone ->
            assertEquals(day, day.toExpiryInstant(zone).toExpiryDate(zone))
        }
        assertEquals(Instant.parse("2027-01-31T05:00:00Z"), day.toExpiryInstant(newYork))
        assertEquals(Instant.parse("2027-01-30T21:00:00Z"), day.toExpiryInstant(istanbul))
    }

    @Test
    fun theDatePickerSpeaksUtcDays() {
        val day = LocalDate.of(2027, 1, 31)

        assertEquals(day, pickerMillisToDate(day.toPickerMillis()))
        assertEquals(Instant.parse("2027-01-31T00:00:00Z").toEpochMilli(), day.toPickerMillis())
    }

    @Test
    fun anExpiryWrittenAtUtcMidnightByAnotherAppShowsItsLocalDay() {
        val stored = Instant.parse("2027-01-31T00:00:00Z")

        assertEquals(LocalDate.of(2027, 1, 31), stored.toExpiryDate(istanbul))
        assertEquals(LocalDate.of(2027, 1, 30), stored.toExpiryDate(newYork))
    }
}

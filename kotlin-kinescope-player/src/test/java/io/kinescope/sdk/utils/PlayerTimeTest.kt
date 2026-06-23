package io.kinescope.sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTimeTest {

    @Test
    fun formatPlayerTime_zeroOrNegative_returnsCompactZero() {
        assertEquals("0:00", formatPlayerTime(0))
        assertEquals("0:00", formatPlayerTime(-1))
    }

    @Test
    fun formatPlayerTime_underOneHour_noLeadingZeroOnMinutes() {
        assertEquals("0:05", formatPlayerTime(5_000))
        assertEquals("1:30", formatPlayerTime(90_000))
        assertEquals("9:59", formatPlayerTime(599_000))
    }

    @Test
    fun formatPlayerTime_oneHourOrMore_includesHours() {
        assertEquals("1:00:00", formatPlayerTime(3_600_000))
        assertEquals("1:05:07", formatPlayerTime(3_907_000))
    }
}

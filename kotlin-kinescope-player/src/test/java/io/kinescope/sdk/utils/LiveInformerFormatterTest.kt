package io.kinescope.sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class LiveInformerFormatterTest {

    @Test
    fun parseLiveStartDateMillis_parsesIso8601WithMilliseconds() {
        val parsed = LiveInformerFormatter.parseLiveStartDateMillis("2026-07-14T10:00:00.000Z")
        assertNotNull(parsed)
    }

    @Test
    fun parseLiveStartDateMillis_parsesIso8601WithoutMilliseconds() {
        val parsed = LiveInformerFormatter.parseLiveStartDateMillis("2026-07-14T10:00:00Z")
        assertNotNull(parsed)
    }

    @Test
    fun parseLiveStartDateMillis_parsesIso8601WithOffset() {
        val parsed = LiveInformerFormatter.parseLiveStartDateMillis("2026-07-14T10:00:00+00:00")
        val expected = LiveInformerFormatter.parseLiveStartDateMillis("2026-07-14T10:00:00Z")
        assertNotNull(parsed)
        assertEquals(expected, parsed)
    }

    @Test
    fun formatSubtitle_usesLocalTimezone() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
            val subtitle = LiveInformerFormatter.formatSubtitle(
                startDate = "2026-07-14T10:00:00Z",
                locale = Locale.US,
            )
            assertEquals("July 14, 13:00", subtitle)
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun formatSubtitle_usesRussianLocaleWithoutAmPm() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
            val subtitle = LiveInformerFormatter.formatSubtitle(
                startDate = "2026-07-14T10:00:00Z",
                locale = Locale("ru"),
            )
            assertEquals("14 июля, 13:00", subtitle)
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }
}

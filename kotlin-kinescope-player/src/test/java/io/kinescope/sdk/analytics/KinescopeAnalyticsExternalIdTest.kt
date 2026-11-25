package io.kinescope.sdk.analytics

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KinescopeAnalyticsExternalIdTest {

    private lateinit var analytics: KinescopeAnalytics

    @Before
    fun setUp() {
        analytics = KinescopeAnalytics(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun externalId_isSentInTheSession() {
        val event = build(externalId = "user-42")

        assertEquals("user-42", event.session.externalID)
    }

    @Test
    fun externalId_isTrimmed() {
        val event = build(externalId = "  user-42 \n")

        assertEquals("user-42", event.session.externalID)
    }

    @Test
    fun nullOrBlankExternalId_sendsNothing() {
        assertEquals("", build(externalId = null).session.externalID)
        assertEquals("", build(externalId = "   ").session.externalID)
    }

    @Test
    fun externalId_doesNotChangeOtherSessionFields() {
        val without = build(externalId = null)
        val with = build(externalId = "user-42")

        assertEquals(without.session.id, with.session.id)
        assertEquals(without.session.viewID, with.session.viewID)
        assertEquals(without.session.watchedDuration, with.session.watchedDuration)
        assertFalse(with.session.id.isEmpty)
    }

    private fun build(externalId: String?) =
        analytics.buildEvent(
            event = KinescopeAnalytics.Event.PLAY,
            source = "https://kinescope.io/test/master.m3u8",
            watchedDuration = 7,
            args = KinescopeAnalyticsArgs(duration = 120, externalId = externalId),
        )
}

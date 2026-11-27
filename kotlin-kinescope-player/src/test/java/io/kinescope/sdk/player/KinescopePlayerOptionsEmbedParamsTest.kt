package io.kinescope.sdk.player

import io.kinescope.sdk.api.KinescopeApiConfig
import io.kinescope.sdk.network.FetchBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web-embed parameters ported to [KinescopePlayerOptions] in 0.1.22. */
class KinescopePlayerOptionsEmbedParamsTest {

    @Test
    fun defaults_keepPreviousBehaviour() {
        val options = KinescopePlayerOptions()

        assertNull(options.startTime)
        assertFalse(options.dnt)
        assertFalse(options.autopause)
        assertTrue(options.header)
        assertFalse(options.transparent)
        assertFalse(options.autofocus)
        assertNull(options.baseUrl)
        assertNull(options.userAgent)
        assertFalse(options.background)
    }

    @Test
    fun background_turnsOnAutoplayMutedLoopAndHidesControls() {
        val options = KinescopePlayerOptions()

        options.background = true

        assertTrue(options.autoplay)
        assertTrue(options.muted)
        assertTrue(options.loop)
        assertFalse(options.controls)
    }

    @Test
    fun background_off_leavesOptionsAsTheyAre() {
        val options = KinescopePlayerOptions().apply { background = true }

        options.background = false

        assertTrue(options.autoplay)
        assertFalse(options.controls)
    }

    @Test
    fun startPosition_isInMilliseconds() {
        assertEquals(90_500L, KinescopePlayerOptions.startPositionMs(90.5))
    }

    @Test
    fun startPosition_noneForNullZeroNegativeOrNaN() {
        assertNull(KinescopePlayerOptions.startPositionMs(null))
        assertNull(KinescopePlayerOptions.startPositionMs(0.0))
        assertNull(KinescopePlayerOptions.startPositionMs(-5.0))
        assertNull(KinescopePlayerOptions.startPositionMs(Double.NaN))
    }

    @Test
    fun baseUrl_blankUsesDefaultHost() {
        assertEquals(KinescopeApiConfig.FETCH_BASE_URL, FetchBuilder.normalizeBaseUrl(null))
        assertEquals(KinescopeApiConfig.FETCH_BASE_URL, FetchBuilder.normalizeBaseUrl("  "))
    }

    @Test
    fun baseUrl_addsSchemeAndTrailingSlash() {
        assertEquals("https://video.example.com/", FetchBuilder.normalizeBaseUrl("video.example.com"))
        assertEquals("https://kinescope.dev/", FetchBuilder.normalizeBaseUrl("https://kinescope.dev"))
        assertEquals("http://10.0.2.2:8080/", FetchBuilder.normalizeBaseUrl("http://10.0.2.2:8080/"))
    }
}

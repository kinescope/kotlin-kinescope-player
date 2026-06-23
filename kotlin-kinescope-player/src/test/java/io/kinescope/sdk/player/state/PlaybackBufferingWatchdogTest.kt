package io.kinescope.sdk.player.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackBufferingWatchdogTest {

    @Test
    fun evaluate_returnsNull_whenNotBuffering() {
        val watchdog = watchdogWithTime { 1_000L }
        watchdog.onBufferingStarted(0L)

        assertNull(watchdog.evaluate(isBuffering = false, positionMs = 0L, hasActiveError = false))
    }

    @Test
    fun evaluate_returnsNull_beforeTimeout() {
        var time = 0L
        val watchdog = watchdogWithTime { time }
        watchdog.onBufferingStarted(0L)
        time = 5_000L

        assertNull(watchdog.evaluate(isBuffering = true, positionMs = 0L, hasActiveError = false))
    }

    @Test
    fun evaluate_returnsStallMessage_afterTimeoutWithoutPositionChange() {
        var time = 0L
        val watchdog = watchdogWithTime { time }
        watchdog.onBufferingStarted(1_000L)
        time = 16_000L

        assertEquals(
            PlaybackBufferingWatchdog.STALL_MESSAGE,
            watchdog.evaluate(isBuffering = true, positionMs = 1_000L, hasActiveError = false),
        )
    }

    @Test
    fun evaluate_returnsNull_whenPositionMovedBeyondTolerance() {
        var time = 0L
        val watchdog = watchdogWithTime { time }
        watchdog.onBufferingStarted(1_000L)
        time = 16_000L

        assertNull(watchdog.evaluate(isBuffering = true, positionMs = 2_000L, hasActiveError = false))
    }

    @Test
    fun evaluate_returnsNull_whenActiveError() {
        var time = 0L
        val watchdog = watchdogWithTime { time }
        watchdog.onBufferingStarted(0L)
        time = 20_000L

        assertNull(watchdog.evaluate(isBuffering = true, positionMs = 0L, hasActiveError = true))
    }

    @Test
    fun onBufferingStopped_resetsWatchdog() {
        var time = 0L
        val watchdog = watchdogWithTime { time }
        watchdog.onBufferingStarted(0L)
        watchdog.onBufferingStopped()
        time = 20_000L

        assertNull(watchdog.evaluate(isBuffering = true, positionMs = 0L, hasActiveError = false))
    }

    @Test
    fun stallMessage_resetsInternalState() {
        var time = 0L
        val watchdog = watchdogWithTime { time }
        watchdog.onBufferingStarted(500L)
        time = 16_000L
        watchdog.evaluate(isBuffering = true, positionMs = 500L, hasActiveError = false)

        assertNull(watchdog.evaluate(isBuffering = true, positionMs = 500L, hasActiveError = false))
    }

    private fun watchdogWithTime(nowMs: () -> Long): PlaybackBufferingWatchdog =
        PlaybackBufferingWatchdog(
            timeoutMs = PlaybackBufferingWatchdog.TIMEOUT_MS,
            stallToleranceMs = PlaybackBufferingWatchdog.STALL_TOLERANCE_MS,
            nowMs = nowMs,
        )
}

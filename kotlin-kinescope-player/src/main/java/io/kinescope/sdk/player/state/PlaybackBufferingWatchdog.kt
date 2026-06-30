package io.kinescope.sdk.player.state

import kotlin.math.abs

internal class PlaybackBufferingWatchdog(
    private val timeoutMs: Long = TIMEOUT_MS,
    private val stallToleranceMs: Long = STALL_TOLERANCE_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private var startedAtMs: Long = NOT_STARTED
    private var positionAtStartMs: Long = 0L

    fun onBufferingStarted(positionMs: Long) {
        if (startedAtMs == NOT_STARTED) {
            startedAtMs = nowMs()
            positionAtStartMs = positionMs
        }
    }

    fun onBufferingStopped() {
        startedAtMs = NOT_STARTED
    }

    fun reset() {
        startedAtMs = NOT_STARTED
        positionAtStartMs = 0L
    }

    fun evaluate(isBuffering: Boolean, positionMs: Long, hasActiveError: Boolean): String? {
        if (!isBuffering || startedAtMs == NOT_STARTED || hasActiveError) {
            return null
        }
        val elapsedMs = nowMs() - startedAtMs
        if (elapsedMs >= timeoutMs && abs(positionMs - positionAtStartMs) < stallToleranceMs) {
            reset()
            return STALL_MESSAGE
        }
        return null
    }

    companion object {
        private const val NOT_STARTED = -1L
        const val TIMEOUT_MS = 15_000L
        const val POLL_MS = 1_000L
        const val STALL_TOLERANCE_MS = 500L
        const val STALL_MESSAGE = "Воспроизведение зависло. Проверьте соединение."
    }
}

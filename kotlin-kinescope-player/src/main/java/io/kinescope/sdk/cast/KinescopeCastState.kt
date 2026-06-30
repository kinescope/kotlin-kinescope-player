package io.kinescope.sdk.cast

data class KinescopeCastState(
    val isCasting: Boolean = false,
    val deviceName: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

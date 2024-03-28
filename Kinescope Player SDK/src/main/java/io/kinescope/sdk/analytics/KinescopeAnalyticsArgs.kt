package io.kinescope.sdk.analytics

import io.kinescope.sdk.utils.EMPTY

data class KinescopeAnalyticsArgs(
    val duration: Int = 0,
    val rate: Float = 0f,
    val volume: Int = 0,
    val quality: String = String.EMPTY,
    val isMuted: Boolean = false,
    val isFullScreen: Boolean = false,
    val previewPosition: Int = 0,
    val currentPosition: Int = 0,
)
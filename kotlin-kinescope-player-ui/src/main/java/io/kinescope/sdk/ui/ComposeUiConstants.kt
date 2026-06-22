package io.kinescope.sdk.ui

import io.kinescope.sdk.player.state.SUBTITLES_OFF_ID
import io.kinescope.sdk.settings.qualityBadgeForVariant

const val SUBTITLES_OFF = SUBTITLES_OFF_ID

val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

val SUBTITLE_COLORS: List<Pair<String, Int>> = listOf(
    "White" to 0xFFFFFFFF.toInt(),
    "Green" to 0xFF00FF00.toInt(),
    "Cyan" to 0xFF00FFFF.toInt(),
    "Blue" to 0xFF0000FF.toInt(),
    "Magenta" to 0xFFFF00FF.toInt(),
    "Red" to 0xFFFF0000.toInt(),
    "Black" to 0xFF000000.toInt(),
)

val SUBTITLE_FONT_SIZES = listOf(50, 75, 100, 125, 150, 200, 300)
val SUBTITLE_OPACITIES = listOf(0, 25, 50, 75, 100)

fun subtitleColorLabel(color: Int): String =
    SUBTITLE_COLORS.firstOrNull { it.second == color }?.first ?: "Свой"

fun qualityBadge(heightPx: Int): String? = qualityBadgeForVariant(heightPx)

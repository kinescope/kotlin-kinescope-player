package io.kinescope.sdk.settings

import io.kinescope.sdk.R
import io.kinescope.sdk.player.quality.KinescopeQualityVariant

fun qualityBadge(heightPx: Int): String? = when {
    heightPx >= 4320 -> "8K"
    heightPx >= 2160 -> "4K"
    heightPx >= 1080 -> "HD"
    else -> null
}

fun qualityBadgeForVariant(id: Int): String? {
    if (id <= 0 || id == KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID ||
        id == KinescopeQualityVariant.QUALITY_VARIANT_AUDIO_ONLY_ID
    ) {
        return null
    }
    return qualityBadge(id)
}

fun qualitySettingsIconRes(
    isAudioOnlyQuality: Boolean,
    playbackHeightPx: Int,
): Int = when {
    isAudioOnlyQuality || playbackHeightPx <= 0 -> R.drawable.ic_settings
    playbackHeightPx >= 2160 -> R.drawable.ic_settings_4k
    playbackHeightPx >= 1080 -> R.drawable.ic_settings_hd
    else -> R.drawable.ic_settings
}

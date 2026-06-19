package io.kinescope.sdk.settings

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

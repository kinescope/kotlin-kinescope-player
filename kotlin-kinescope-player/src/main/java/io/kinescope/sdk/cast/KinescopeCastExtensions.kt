package io.kinescope.sdk.cast

import io.kinescope.sdk.models.videos.KinescopeVideo

fun KinescopeVideo.toCastData(): KinescopeCastData? {
    val dash = dashLink?.takeIf { it.isNotEmpty() }
    val manifest = dash ?: hlsLink?.takeIf { it.isNotEmpty() } ?: return null
    val license = dash?.let {
        "https://license.kinescope.io/v1/vod/$id/acquire/widevine"
    }
    return KinescopeCastData(
        manifestUrl = manifest,
        title = title,
        subtitle = subtitle,
        posterUrl = poster?.url,
        durationSec = duration.toDouble(),
        isLive = isLive,
        drmLicenseUrl = license,
    )
}

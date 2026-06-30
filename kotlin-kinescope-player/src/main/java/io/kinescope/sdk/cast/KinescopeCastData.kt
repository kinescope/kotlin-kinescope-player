package io.kinescope.sdk.cast

/**
 * Stored in [androidx.media3.common.MediaItem] tag and converted to Cast [com.google.android.gms.cast.MediaInfo].
 */
data class KinescopeCastData(
    val manifestUrl: String,
    val contentType: String = "video/mp4",
    val title: String? = null,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val durationSec: Double = 0.0,
    val isLive: Boolean = false,
    val drmLicenseUrl: String? = null,
)

package io.kinescope.sdk.models.videos

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KinescopeSdk(
    @Json(name = "metric_url") val metricUrl: String?,
    /** Watermark from the player template; `null` when the template has none. */
    @Json(name = "watermark") val watermark: KinescopeSdkWatermark? = null,
)

/**
 * Watermark sent in `sdk.watermark` of `{videoId}.json` when it is enabled in the
 * player template. Same meaning as `watermark` in the web player.
 */
@JsonClass(generateAdapter = true)
data class KinescopeSdkWatermark(
    @Json(name = "text") val text: String? = null,
    /** `random` (default) or `stripes`. */
    @Json(name = "mode") val mode: String? = null,
    /** Text width relative to the video width. Web default `0.25`. */
    @Json(name = "scale") val scale: Float? = null,
    /**
     * Show / hide duration in ms: a number (same for both) or
     * `{ "visible": ms, "hidden": ms }`. Absent: always visible.
     */
    @Json(name = "display_timeout") val displayTimeout: Any? = null,
    /** CSS colour `#RRGGBB` or `#RRGGBBAA`. */
    @Json(name = "color") val color: String? = null,
)

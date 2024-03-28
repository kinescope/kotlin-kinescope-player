package io.kinescope.sdk.models.videos

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KinescopeVideoLive(
    @Json(name = "starts_at") val startsAt: String?,
    @Json(name = "metrics_url") val metricsUrl: String?,
)

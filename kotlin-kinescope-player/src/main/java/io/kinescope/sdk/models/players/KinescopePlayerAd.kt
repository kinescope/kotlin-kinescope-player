package io.kinescope.sdk.models.players

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopePlayerAd(
    @Json(name = "type") val type: String,
    @Json(name = "tag_url") val tagUrl: String,
    @Json(name = "skip_after") val skipAfter: Int? = null,
    @Json(name = "time") val time: Int? = null,
) : Serializable

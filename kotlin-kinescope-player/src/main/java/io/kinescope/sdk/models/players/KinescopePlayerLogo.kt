package io.kinescope.sdk.models.players

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopePlayerLogo(
    @Json(name = "url") val url: String,
    @Json(name = "position") val position: String? = null,
    @Json(name = "link") val link: String? = null,
    @Json(name = "opacity") val opacity: Double? = null,
) : Serializable

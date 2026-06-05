package io.kinescope.sdk.models.players

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopeCreatePlayerRequest(
    @Json(name = "name") val name: String,
    @Json(name = "settings") val settings: KinescopePlayerSettings? = null,
    @Json(name = "ads") val ads: List<KinescopePlayerAd>? = null,
) : Serializable

package io.kinescope.sdk.models.videos

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopeVideoDrm(
    @Json(name = "widevine") val widevine: KinescopeVideoWidevine? = null,
) : Serializable

@JsonClass(generateAdapter = true)
data class KinescopeVideoWidevine(
    @Json(name = "licenseUrl") val licenseUrl: String? = null,
) : Serializable

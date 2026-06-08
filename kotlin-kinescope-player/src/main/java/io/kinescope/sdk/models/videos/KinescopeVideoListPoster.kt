package io.kinescope.sdk.models.videos

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopeVideoListPoster(
    @Json(name = "xs") val xs: String? = null,
    @Json(name = "sm") val sm: String? = null,
    @Json(name = "md") val md: String? = null,
    @Json(name = "original") val original: String? = null,
) : Serializable {

    fun thumbnailUrl(): String? = sm ?: xs ?: md ?: original
}

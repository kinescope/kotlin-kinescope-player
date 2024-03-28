package io.kinescope.sdk.models.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.kinescope.sdk.models.videos.KinescopeVideoApi
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopeMetaResponse<A, B>(
    @Json(name = "data") val data: A,
    @Json(name = "meta") val meta: B
) : Serializable

typealias KinescopeAllVideosResponse = KinescopeMetaResponse<List<KinescopeVideoApi>, KinescopeMetaData>

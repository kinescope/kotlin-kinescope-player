package io.kinescope.sdk.models.players

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.kinescope.sdk.models.common.KinescopeDataResponse
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopeDeletePlayerResult(
    @Json(name = "success") val success: Boolean,
) : Serializable

typealias KinescopeDeletePlayerResponse = KinescopeDataResponse<KinescopeDeletePlayerResult>

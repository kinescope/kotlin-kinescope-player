package io.kinescope.sdk.models.videos

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopeVideoApi (
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "subtitle") val subtitle: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "folder_id") val folderId: String? = null,
    @Json(name = "hls_link") val hlsLink: String? = null,
    @Json(name = "poster") val poster: KinescopeVideoListPoster? = null,
    @Json(name = "duration") val duration: Double? = null,
): Serializable
package io.kinescope.sdk.models.videos

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopeVideo (
    @Json(name = "id") val id:String,
    @Json(name = "workspace_id")  val workspaceId:String,
    @Json(name = "project_id")  val projectId:String,
    @Json(name = "folder_id")  val folderId:String?,
    @Json(name = "type")  val type:String,
    @Json(name = "title")  val title: String,
    @Json(name = "subtitle")  val subtitle: String?,
    @Json(name = "description") val description: String,
    @Json(name = "chapters") val chapters: KinescopeVideoChapter,
    @Json(name = "poster") val poster: KinescopeVideoPoster?,
    @Json(name = "attachments") val attachments: List<KinescopeVideoAttachments>,
    @Json(name = "subtitles") val subtitles: List<KinescopeVideoSubtitle>,
    @Json(name = "duration") val duration: Float,
    @Json(name = "live") val live: KinescopeVideoLive?,
    @Json(name = "hls_link") val hlsLink:String?,
    @Json(name = "dash_link") val dashLink:String?,
): Serializable {

    val isLive: Boolean
        get() = type == TYPE_LIVE

    companion object {
        private const val TYPE_LIVE = "live"
    }
}
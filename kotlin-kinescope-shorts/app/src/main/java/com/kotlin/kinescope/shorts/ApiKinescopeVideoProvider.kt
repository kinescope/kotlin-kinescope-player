package com.kotlin.kinescope.shorts

import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider
import io.kinescope.sdk.shorts.models.DrmInfo
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.models.VideoQualityMapEntry
import io.kinescope.sdk.shorts.models.WidevineInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(InternalSerializationApi::class)
class ApiKinescopeVideoProvider(
    private val apiToken: String,
    private val referer: String = "https://kinescope.io/",
) : KinescopeVideoProvider {

    private val httpClient = OkHttpClient()

    override suspend fun loadVideo(videoId: String): VideoData? = withContext(Dispatchers.IO) {
        loadPlaybackVideo(videoId)?.toVideoData(apiToken)
    }

    private fun loadPlaybackVideo(videoId: String): PlaybackVideo? {
        val request = Request.Builder()
            .url("https://kinescope.io/$videoId.json?sdk=android")
            .header("Referer", referer)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                AppJson.decodeFromString(PlaybackVideo.serializer(), body)
            }
        }.getOrNull()
    }

    override suspend fun loadVideos(
        projectId: String?,
        folderId: String?,
        limit: Int,
        offset: Int,
    ): List<VideoData> = withContext(Dispatchers.IO) {
        if (apiToken.isBlank()) return@withContext emptyList()

        val page = (offset / limit) + 1
        val url = buildString {
            append("https://api.kinescope.io/v1/videos?page=$page&per_page=$limit")
            if (!projectId.isNullOrBlank()) append("&project_id=$projectId")
            if (!folderId.isNullOrBlank()) append("&folder_id=$folderId")
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiToken")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val body = response.body?.string().orEmpty()
                val catalog = AppJson.decodeFromString(CatalogResponse.serializer(), body)
                catalog.data
                    .mapNotNull { item -> loadPlaybackVideo(item.id) }
                    .mapNotNull { it.toVideoData(apiToken) }
            }
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class CatalogResponse(
        val data: List<CatalogItem> = emptyList(),
    )

    @Serializable
    private data class CatalogItem(
        val id: String,
    )

    @Serializable
    private data class PlaybackVideo(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val description: String? = null,
        @SerialName("project_id") val projectId: String? = null,
        @SerialName("folder_id") val folderId: String? = null,
        @SerialName("hls_link") val hlsLink: String? = null,
        val poster: Poster? = null,
        val drm: PlaybackDrm? = null,
        @SerialName("quality_map") val qualityMap: List<PlaybackQuality>? = null,
    ) {
        fun toVideoData(apiKey: String): VideoData? {
            val hls = hlsLink?.takeIf { it.isNotBlank() } ?: return null
            val licenseUrl = drm?.widevine?.licenseUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { url ->
                    when {
                        url.endsWith("token=") -> url + apiKey
                        url.contains("token=") -> url
                        else -> url
                    }
                }
            return VideoData(
                hlsLink = hls,
                drm = licenseUrl?.let { DrmInfo(widevine = WidevineInfo(licenseUrl = it)) },
                title = title,
                subtitle = subtitle,
                description = description,
                videoId = id,
                qualityMap = qualityMap?.mapNotNull { item ->
                    val name = item.name ?: return@mapNotNull null
                    val height = item.height ?: return@mapNotNull null
                    VideoQualityMapEntry(
                        label = item.label,
                        name = name,
                        height = height,
                    )
                },
                posterUrl = poster?.url,
            )
        }
    }

    @Serializable
    private data class Poster(
        val url: String? = null,
    )

    @Serializable
    private data class PlaybackDrm(
        val widevine: PlaybackWidevine? = null,
    )

    @Serializable
    private data class PlaybackWidevine(
        @SerialName("licenseUrl") val licenseUrl: String? = null,
    )

    @Serializable
    private data class PlaybackQuality(
        val label: String? = null,
        val name: String? = null,
        val height: Int? = null,
    )
}

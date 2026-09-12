package io.kinescope.sdk.shorts.utils

import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
class KinescopeUrls(
    private val videoProvider: KinescopeVideoProvider? = null,
    private val projectId: String? = null,
    private val folderId: String? = null,
    private val limit: Int = 50,
) {

    suspend fun getVideosFromApi(): List<VideoData> {
        val provider = videoProvider ?: return emptyList()
        return try {
            provider.loadVideos(
                projectId = projectId,
                folderId = folderId,
                limit = limit,
                offset = 0,
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getVideoById(videoId: String): VideoData? {
        val provider = videoProvider ?: return null
        return try {
            provider.loadVideo(videoId)
        } catch (_: Exception) {
            null
        }
    }
}

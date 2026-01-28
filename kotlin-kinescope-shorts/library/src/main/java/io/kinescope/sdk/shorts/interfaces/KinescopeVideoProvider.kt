package io.kinescope.sdk.shorts.interfaces

import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
interface KinescopeVideoProvider {

    suspend fun loadVideo(videoId: String): VideoData?

    suspend fun loadVideos(
        projectId: String? = null,
        folderId: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<VideoData>
}


package io.kinescope.sdk.shorts.interfaces

import android.content.Intent
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi

interface ActivityProvider {
    fun getMainActivityIntent(): Intent
    @OptIn(InternalSerializationApi::class)
    fun getOfflinePlayerActivityIntent(videoData: VideoData, downloadId: String): Intent
    fun pauseCurrentPlayer()
}


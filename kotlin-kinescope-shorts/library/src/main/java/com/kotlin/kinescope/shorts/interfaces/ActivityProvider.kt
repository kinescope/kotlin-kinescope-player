package com.kotlin.kinescope.shorts.interfaces

import android.content.Intent
import com.kotlin.kinescope.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi

interface ActivityProvider {
    fun getMainActivityIntent(): Intent
    @OptIn(InternalSerializationApi::class)
    fun getOfflinePlayerActivityIntent(videoData: VideoData, downloadId: String): Intent
    fun pauseCurrentPlayer()
}


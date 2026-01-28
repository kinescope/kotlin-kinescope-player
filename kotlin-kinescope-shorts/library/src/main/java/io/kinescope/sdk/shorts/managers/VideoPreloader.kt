package io.kinescope.sdk.shorts.managers

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.offline.Download
import io.kinescope.sdk.shorts.cache.VideoCache
import io.kinescope.sdk.shorts.drm.DrmContentProtection
import io.kinescope.sdk.shorts.drm.DrmHelper
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@InternalSerializationApi
@OptIn(UnstableApi::class)
class VideoPreloader(
    private val context: Context,
    private val playerFactory: PlayerFactory
) {
    companion object {
        private const val TAG = "VideoPreloader"

        private fun getPreloadCount(isWeakDevice: Boolean): Int {
            return if (isWeakDevice) 1 else 2
        }

        private const val PRELOAD_BACK_COUNT = 1
    }
    
    private val isWeakDevice: Boolean by lazy {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val processorCount = Runtime.getRuntime().availableProcessors()
        maxMemory < 2048 || processorCount < 4
    }

    private val preloadPlayers = ConcurrentHashMap<Int, ExoPlayer>()
    private val preloadJobs = ConcurrentHashMap<Int, Job>()
    private val preloadScope = CoroutineScope(Dispatchers.IO)
    private var currentPosition = -1
    private var videoList: List<VideoData> = emptyList()

    fun updateVideoList(videos: List<VideoData>, currentPos: Int) {
        videoList = videos
        currentPosition = currentPos
        schedulePreloads(currentPos)
    }

    fun onPageChanged(newPosition: Int) {
        if (currentPosition != newPosition) {
            currentPosition = newPosition
            schedulePreloads(newPosition)
            cleanupDistantPlayers(newPosition)
        }
    }

    private fun schedulePreloads(position: Int) {
        cancelOldPreloads()

        val preloadCount = getPreloadCount(isWeakDevice)

        for (i in 1..preloadCount) {
            val nextPos = position + i
            if (nextPos < videoList.size && !preloadPlayers.containsKey(nextPos)) {
                preloadVideo(nextPos, if (isWeakDevice && i > 1) 100L else 0L)
            }
        }

        if (!isWeakDevice) {
            for (i in 1..PRELOAD_BACK_COUNT) {
                val prevPos = position - i
                if (prevPos >= 0 && !preloadPlayers.containsKey(prevPos)) {
                    preloadVideo(prevPos)
                }
            }
        }
    }

    private fun preloadVideo(position: Int, delayMs: Long = 0L) {
        if (position < 0 || position >= videoList.size) return

        val videoData = videoList[position]
        val job = preloadScope.launch {
            try {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                val contentId = generateContentId(videoData.hlsLink)
                val download = VideoDownloadManager.getDownloadIndex(context).getDownload(contentId)

                if (download != null && download.state == Download.STATE_COMPLETED) {
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val player = playerFactory.createPreloadPlayer()
                    preloadPlayers[position] = player

                    val hasDrm = !videoData.drm?.widevine?.licenseUrl.isNullOrBlank()

                    if (hasDrm) {
                        setupDrmPreload(player, videoData)
                    } else {
                        setupClearPreload(player, videoData)
                    }

                    player.prepare()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    preloadPlayers.remove(position)?.release()
                }
            }
        }
        
        preloadJobs[position] = job
    }

    private fun setupDrmPreload(player: ExoPlayer, videoData: VideoData) {
        val drmData = DrmContentProtection(
            schemeUri = C.WIDEVINE_UUID.toString(),
            licenseUrl = videoData.drm?.widevine?.licenseUrl,
            schemeUuid = C.WIDEVINE_UUID
        )
        
        val mediaItem = MediaItem.Builder()
            .setUri(videoData.hlsLink)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(drmData.licenseUrl)
            .setDrmMultiSession(true)
            .build()

        player.setMediaItem(mediaItem)
    }

    private fun setupClearPreload(player: ExoPlayer, videoData: VideoData) {
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoCache.getCache())
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())

        val mediaItem = MediaItem.fromUri(videoData.hlsLink)
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        player.setMediaSource(hlsSource)
    }

    fun getPreloadedPlayer(position: Int): ExoPlayer? {
        return preloadPlayers.remove(position)?.also {
            preloadJobs.remove(position)
        }
    }

    private fun cleanupDistantPlayers(currentPos: Int) {
        val maxDistance = getPreloadCount(isWeakDevice) + 2
        val playersToRemove = preloadPlayers.keys.filter { pos ->
            kotlin.math.abs(pos - currentPos) > maxDistance
        }

        playersToRemove.forEach { pos ->
            releasePreloadPlayer(pos)
        }
    }

    private fun cancelOldPreloads() {
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
    }

    private fun releasePreloadPlayer(position: Int) {
        preloadJobs.remove(position)?.cancel()

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            preloadPlayers.remove(position)?.let { player ->
                try {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                } catch (e: Exception) {
                }
            }
        }
    }

    fun cleanup() {
        cancelOldPreloads()

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            preloadPlayers.values.forEach { player ->
                try {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                } catch (e: Exception) {
                }
            }
            preloadPlayers.clear()
        }
    }

    private fun generateContentId(url: String): String {
        return try {
            val stablePart = url.substringBefore("?")
            val digest = MessageDigest.getInstance("SHA-256").digest(stablePart.toByteArray())
            android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
}


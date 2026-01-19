package io.kinescope.sdk.shorts.managers

import android.content.Context
import android.util.Log
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

/**
 * Менеджер предзагрузки видео для быстрой прокрутки как в TikTok
 * Предзагружает следующие видео в фоне для плавной прокрутки
 */
@InternalSerializationApi
@OptIn(UnstableApi::class)
class VideoPreloader(
    private val context: Context,
    private val playerFactory: PlayerFactory
) {
    companion object {
        private const val TAG = "VideoPreloader"
        // Количество видео для предзагрузки вперед (адаптивно в зависимости от устройства)
        private fun getPreloadCount(isWeakDevice: Boolean): Int {
            return if (isWeakDevice) 1 else 2
        }
        // Количество видео для предзагрузки назад
        private const val PRELOAD_BACK_COUNT = 1
    }
    
    private val isWeakDevice: Boolean by lazy {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val processorCount = Runtime.getRuntime().availableProcessors()
        maxMemory < 2048 || processorCount < 4
    }

    private val preloadPlayers = ConcurrentHashMap<Int, ExoPlayer>()
    private val preloadJobs = ConcurrentHashMap<Int, Job>()
    private val preloadScope = CoroutineScope(Dispatchers.IO)
    private var currentPosition = -1
    private var videoList: List<VideoData> = emptyList()

    /**
     * Обновляет список видео и текущую позицию
     */
    fun updateVideoList(videos: List<VideoData>, currentPos: Int) {
        videoList = videos
        currentPosition = currentPos
        schedulePreloads(currentPos)
    }

    /**
     * Обновляет текущую позицию и планирует предзагрузку
     */
    fun onPageChanged(newPosition: Int) {
        if (currentPosition != newPosition) {
            currentPosition = newPosition
            schedulePreloads(newPosition)
            // Освобождаем плееры, которые больше не нужны
            cleanupDistantPlayers(newPosition)
        }
    }

    /**
     * Планирует предзагрузку видео вокруг текущей позиции
     */
    private fun schedulePreloads(position: Int) {
        // Отменяем старые задачи предзагрузки
        cancelOldPreloads()

        val preloadCount = getPreloadCount(isWeakDevice)

        // Предзагружаем следующие видео (приоритет на следующее)
        for (i in 1..preloadCount) {
            val nextPos = position + i
            if (nextPos < videoList.size && !preloadPlayers.containsKey(nextPos)) {
                preloadVideo(nextPos, if (isWeakDevice && i > 1) 100L else 0L)
            }
        }

        // Предзагружаем предыдущие видео (для прокрутки назад) - только если не слабое устройство
        if (!isWeakDevice) {
            for (i in 1..PRELOAD_BACK_COUNT) {
                val prevPos = position - i
                if (prevPos >= 0 && !preloadPlayers.containsKey(prevPos)) {
                    preloadVideo(prevPos)
                }
            }
        }
    }

    /**
     * Предзагружает видео по указанной позиции
     */
    private fun preloadVideo(position: Int, delayMs: Long = 0L) {
        if (position < 0 || position >= videoList.size) return

        val videoData = videoList[position]
        val job = preloadScope.launch {
            try {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                // Проверяем, не загружено ли видео офлайн (это можно делать в фоне)
                val contentId = generateContentId(videoData.hlsLink)
                val download = VideoDownloadManager.getDownloadIndex(context).getDownload(contentId)
                
                if (download != null && download.state == Download.STATE_COMPLETED) {
                    // Видео уже загружено, не нужно предзагружать
                    return@launch
                }

                // Все операции с ExoPlayer должны выполняться в главном потоке
                withContext(Dispatchers.Main) {
                    // Создаем плеер для предзагрузки
                    val player = playerFactory.createPreloadPlayer()
                    preloadPlayers[position] = player

                    // Настраиваем источник видео
                    val hasDrm = !videoData.drm?.widevine?.licenseUrl.isNullOrBlank()
                    
                    if (hasDrm) {
                        setupDrmPreload(player, videoData)
                    } else {
                        setupClearPreload(player, videoData)
                    }

                    // Начинаем загрузку (но не воспроизведение)
                    player.prepare()
                    
                    Log.d(TAG, "Preloading started for position $position")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading video at position $position", e)
                // Освобождаем плеер в главном потоке
                withContext(Dispatchers.Main) {
                    preloadPlayers.remove(position)?.release()
                }
            }
        }
        
        preloadJobs[position] = job
    }

    /**
     * Настраивает предзагрузку для DRM видео
     */
    private fun setupDrmPreload(player: ExoPlayer, videoData: VideoData) {
        if (videoData.hlsLink.isNullOrBlank()) {
            android.util.Log.e(TAG, "HLS link is null or empty in setupDrmPreload")
            return
        }
        
        val licenseUrl = videoData.drm?.widevine?.licenseUrl
        if (licenseUrl.isNullOrBlank()) {
            android.util.Log.e(TAG, "DRM license URL is null or empty in setupDrmPreload")
            return
        }
        
        val drmData = DrmContentProtection(
            schemeUri = C.WIDEVINE_UUID.toString(),
            licenseUrl = licenseUrl,
            schemeUuid = C.WIDEVINE_UUID
        )
        
        val mediaItem = MediaItem.Builder()
            .setUri(videoData.hlsLink)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(licenseUrl)
            .setDrmMultiSession(true)
            .build()

        player.setMediaItem(mediaItem)
    }

    /**
     * Настраивает предзагрузку для обычного видео
     */
    private fun setupClearPreload(player: ExoPlayer, videoData: VideoData) {
        if (videoData.hlsLink.isNullOrBlank()) {
            android.util.Log.e(TAG, "HLS link is null or empty in setupClearPreload")
            return
        }
        
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoCache.getCache())
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())

        val mediaItem = MediaItem.fromUri(videoData.hlsLink)
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        player.setMediaSource(hlsSource)
    }

    /**
     * Получает предзагруженный плеер для позиции (если есть)
     */
    fun getPreloadedPlayer(position: Int): ExoPlayer? {
        return preloadPlayers.remove(position)?.also {
            preloadJobs.remove(position)
        }
    }

    /**
     * Освобождает плееры, которые далеко от текущей позиции
     */
    private fun cleanupDistantPlayers(currentPos: Int) {
        val maxDistance = getPreloadCount(isWeakDevice) + 2
        val playersToRemove = preloadPlayers.keys.filter { pos ->
            kotlin.math.abs(pos - currentPos) > maxDistance
        }

        playersToRemove.forEach { pos ->
            releasePreloadPlayer(pos)
        }
    }

    /**
     * Отменяет старые задачи предзагрузки
     */
    private fun cancelOldPreloads() {
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
    }

    /**
     * Освобождает плеер предзагрузки для указанной позиции
     */
    private fun releasePreloadPlayer(position: Int) {
        preloadJobs.remove(position)?.cancel()
        
        // Используем Handler для выполнения в главном потоке
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            preloadPlayers.remove(position)?.let { player ->
                try {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                    Log.d(TAG, "Released preload player for position $position")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing preload player", e)
                }
            }
        }
    }

    /**
     * Освобождает все ресурсы
     */
    fun cleanup() {
        cancelOldPreloads()
        
        // Используем Handler для выполнения в главном потоке
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            preloadPlayers.values.forEach { player ->
                try {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up player", e)
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


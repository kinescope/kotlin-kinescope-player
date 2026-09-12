package io.kinescope.sdk.shorts.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.offline.Download
import io.kinescope.sdk.shorts.cache.MediaCachePrefetcher
import io.kinescope.sdk.shorts.cache.VideoCache
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Prepares the next Shorts pages with adaptive depth from [PreloadHealth]:
 * - **+1**: full [ExoPlayer] preload (handoff via [getPreloadedPlayer]) when plan allows
 * - **+2 / back**: [MediaCachePrefetcher] segment warm without a player (tier-dependent)
 *
 * Also supports hard pause while the feed is scrolling/flinging.
 */
@InternalSerializationApi
@OptIn(UnstableApi::class)
class VideoPreloader(
    private val context: Context,
    private val playerFactory: PlayerFactory,
) {
    companion object {
        private const val PRELOAD_DEBOUNCE_MS = 180L
        private const val PLAYER_FORWARD_COUNT = 1
    }

    private val isWeakDevice: Boolean by lazy {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val processorCount = Runtime.getRuntime().availableProcessors()
        maxMemory < 2048 || processorCount < 4
    }

    private val preloadPlayers = ConcurrentHashMap<Int, ExoPlayer>()
    private val preloadJobs = ConcurrentHashMap<Int, Job>()
    private val cacheJobs = ConcurrentHashMap<Int, Job>()
    private val prefetchers = ConcurrentHashMap<Int, MediaCachePrefetcher>()
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentPosition = -1
    private var videoList: List<VideoData> = emptyList()
    private var debounceJob: Job? = null
    private val scrollPaused = AtomicBoolean(false)
    private val currentPlan = AtomicReference(
        PreloadHealth.planFor(PreloadTier.MEDIUM, weakDevice = false),
    )

    fun updateVideoList(videos: List<VideoData>, currentPos: Int) {
        videoList = videos
        currentPosition = currentPos
        schedulePreloadsDebounced(currentPos)
    }

    fun onPageChanged(newPosition: Int) {
        if (currentPosition == newPosition) return
        currentPosition = newPosition
        cleanupDistant(newPosition)
        schedulePreloadsDebounced(newPosition)
    }

    /**
     * Hard pause while ViewPager is scrolling/flinging.
     * Existing +1 prepared players are kept for handoff.
     */
    fun setPreloadPaused(paused: Boolean) {
        if (scrollPaused.getAndSet(paused) == paused) return
        if (paused) {
            cancelNonEssentialWork(keepReadyPlayers = true)
        } else if (currentPosition >= 0) {
            schedulePreloadsDebounced(currentPosition)
        }
    }

    /** Applies buffer / bandwidth / network health to preload depth. */
    fun onHealthUpdated(snapshot: PlaybackHealthSnapshot) {
        val next = PreloadHealth.evaluate(snapshot, isWeakDevice)
        val previous = currentPlan.getAndSet(next)
        if (previous.tier == next.tier) return

        if (next.tier == PreloadTier.CRITICAL) {
            cancelNonEssentialWork(keepReadyPlayers = true)
            return
        }

        // Downgrade: drop cache jobs that exceed the new plan.
        if (next.cacheForwardCount < previous.cacheForwardCount ||
            next.cacheBackCount < previous.cacheBackCount
        ) {
            trimCacheToPlan(next)
        }

        if (!scrollPaused.get() && currentPosition >= 0) {
            schedulePreloadsDebounced(currentPosition)
        }
    }

    private fun schedulePreloadsDebounced(position: Int) {
        debounceJob?.cancel()
        debounceJob = preloadScope.launch {
            delay(PRELOAD_DEBOUNCE_MS)
            if (currentPosition != position || scrollPaused.get()) return@launch
            schedulePreloads(position)
        }
    }

    private fun schedulePreloads(position: Int) {
        val plan = currentPlan.get()
        if (plan.tier == PreloadTier.CRITICAL || scrollPaused.get()) {
            cancelNonEssentialWork(keepReadyPlayers = true)
            return
        }

        cancelStaleJobs(position, plan)

        val nextPlayerPos = position + PLAYER_FORWARD_COUNT
        if (plan.allowPlayerPreload &&
            nextPlayerPos < videoList.size &&
            !preloadPlayers.containsKey(nextPlayerPos)
        ) {
            preloadWithPlayer(nextPlayerPos)
        }

        for (i in 1..plan.cacheForwardCount) {
            val pos = position + PLAYER_FORWARD_COUNT + i
            if (pos < videoList.size) {
                prefetchWithCache(pos, plan)
            }
        }

        for (i in 1..plan.cacheBackCount) {
            val pos = position - i
            if (pos >= 0) {
                prefetchWithCache(pos, plan)
            }
        }
    }

    private fun preloadWithPlayer(position: Int) {
        if (position < 0 || position >= videoList.size) return
        if (preloadJobs.containsKey(position) || preloadPlayers.containsKey(position)) return

        val videoData = videoList[position]
        val job = preloadScope.launch {
            try {
                if (scrollPaused.get() || currentPlan.get().tier == PreloadTier.CRITICAL) return@launch
                if (!currentPlan.get().allowPlayerPreload) return@launch
                val contentId = generateContentId(videoData.hlsLink)
                val download = VideoDownloadManager.getDownloadIndex(context).getDownload(contentId)
                if (download != null && download.state == Download.STATE_COMPLETED) {
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    if (scrollPaused.get() || currentPlan.get().tier == PreloadTier.CRITICAL) return@withContext
                    if (!currentPlan.get().allowPlayerPreload) return@withContext
                    if (currentPosition < 0) return@withContext
                    if (kotlin.math.abs(position - currentPosition) > 2) return@withContext

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
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    preloadPlayers.remove(position)?.release()
                }
            } finally {
                preloadJobs.remove(position)
            }
        }
        preloadJobs[position] = job
    }

    private fun prefetchWithCache(position: Int, plan: PreloadPlan) {
        if (position < 0 || position >= videoList.size) return
        if (plan.maxPrefetchSegments <= 0) return
        if (cacheJobs.containsKey(position) || preloadPlayers.containsKey(position)) return

        val videoData = videoList[position]
        if (!videoData.drm?.widevine?.licenseUrl.isNullOrBlank()) {
            return
        }

        val prefetcher = MediaCachePrefetcher(
            maxSegments = plan.maxPrefetchSegments,
            maxBytesPerResource = plan.maxBytesPerResource,
        )
        prefetchers[position] = prefetcher
        val job = preloadScope.launch {
            try {
                if (scrollPaused.get() || currentPlan.get().tier == PreloadTier.CRITICAL) return@launch
                val contentId = generateContentId(videoData.hlsLink)
                val download = VideoDownloadManager.getDownloadIndex(context).getDownload(contentId)
                if (download != null && download.state == Download.STATE_COMPLETED) {
                    return@launch
                }
                prefetcher.prefetchHls(videoData.hlsLink)
            } catch (_: Exception) {
            } finally {
                cacheJobs.remove(position)
                prefetchers.remove(position)
            }
        }
        cacheJobs[position] = job
    }

    private fun setupDrmPreload(player: ExoPlayer, videoData: VideoData) {
        val mediaItem = MediaItem.Builder()
            .setUri(videoData.hlsLink)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(videoData.drm?.widevine?.licenseUrl)
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

    /** Removes and returns a prepared preload player for [position] (caller owns it). */
    fun getPreloadedPlayer(position: Int): ExoPlayer? {
        preloadJobs.remove(position)?.cancel()
        return preloadPlayers.remove(position)
    }

    private fun cancelStaleJobs(position: Int, plan: PreloadPlan) {
        val keepPlayer = if (plan.allowPlayerPreload) position + PLAYER_FORWARD_COUNT else -1
        val keepCache = buildSet {
            for (i in 1..plan.cacheForwardCount) {
                add(position + PLAYER_FORWARD_COUNT + i)
            }
            for (i in 1..plan.cacheBackCount) {
                add(position - i)
            }
        }

        preloadJobs.keys.toList().forEach { pos ->
            if (pos != keepPlayer) {
                preloadJobs.remove(pos)?.cancel()
            }
        }
        preloadPlayers.keys.toList().forEach { pos ->
            if (pos != keepPlayer) {
                releasePreloadPlayer(pos)
            }
        }

        cacheJobs.keys.toList().forEach { pos ->
            if (pos !in keepCache) {
                prefetchers.remove(pos)?.cancel()
                cacheJobs.remove(pos)?.cancel()
            }
        }
    }

    private fun trimCacheToPlan(plan: PreloadPlan) {
        if (currentPosition < 0) {
            cancelCacheJobs()
            return
        }
        val keepCache = buildSet {
            for (i in 1..plan.cacheForwardCount) {
                add(currentPosition + PLAYER_FORWARD_COUNT + i)
            }
            for (i in 1..plan.cacheBackCount) {
                add(currentPosition - i)
            }
        }
        cacheJobs.keys.toList().forEach { pos ->
            if (pos !in keepCache) {
                prefetchers.remove(pos)?.cancel()
                cacheJobs.remove(pos)?.cancel()
            }
        }
    }

    private fun cancelNonEssentialWork(keepReadyPlayers: Boolean) {
        cancelCacheJobs()
        preloadJobs.forEach { (pos, job) ->
            if (!keepReadyPlayers || !preloadPlayers.containsKey(pos)) {
                job.cancel()
                preloadJobs.remove(pos)
            }
        }
        if (!keepReadyPlayers) {
            preloadPlayers.keys.toList().forEach { releasePreloadPlayer(it) }
        }
    }

    private fun cleanupDistant(currentPos: Int) {
        val maxDistance = 3
        preloadPlayers.keys.filter { kotlin.math.abs(it - currentPos) > maxDistance }.forEach {
            releasePreloadPlayer(it)
        }
        cacheJobs.keys.filter { kotlin.math.abs(it - currentPos) > maxDistance }.forEach { pos ->
            prefetchers.remove(pos)?.cancel()
            cacheJobs.remove(pos)?.cancel()
        }
    }

    private fun cancelCacheJobs() {
        prefetchers.values.forEach { it.cancel() }
        prefetchers.clear()
        cacheJobs.values.forEach { it.cancel() }
        cacheJobs.clear()
    }

    private fun releasePreloadPlayer(position: Int) {
        preloadJobs.remove(position)?.cancel()
        mainHandler.post {
            preloadPlayers.remove(position)?.let { player ->
                try {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun cleanup() {
        debounceJob?.cancel()
        cancelCacheJobs()
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
        mainHandler.post {
            preloadPlayers.values.forEach { player ->
                try {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                } catch (_: Exception) {
                }
            }
            preloadPlayers.clear()
        }
    }

    private fun generateContentId(url: String): String {
        return try {
            val stablePart = url.substringBefore("?")
            val digest = MessageDigest.getInstance("SHA-256").digest(stablePart.toByteArray())
            android.util.Base64.encodeToString(
                digest,
                android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
            )
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }
}

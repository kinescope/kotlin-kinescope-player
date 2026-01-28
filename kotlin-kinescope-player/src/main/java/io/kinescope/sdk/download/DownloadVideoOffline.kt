package io.kinescope.sdk.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.download.VideoDownloadService

/**
 * Entry point for offline video downloads in apps using kotlin-kinescope-player.
 *
 * The download pipeline (VideoDownloadService, cache, DRM) is built into the library.
 * The service and permissions are declared in the library manifest and merged at build time.
 *
 * ## Minimal setup
 *
 * 1. Add dependency: `implementation 'com.github.kinescope:kotlin-kinescope-player:x'`
 * 2. Call [initialize] at app startup (Application or first Activity).
 * 3. Start a download: [startDownload]
 *
 * ## Without DRM (HLS)
 *
 * ```kotlin
 * DownloadVideoOffline.initialize(context)
 * val request = DownloadRequest.Builder(contentId, uri)
 *     .setMimeType(MimeTypes.APPLICATION_M3U8)
 *     .setData(metadataJson.toByteArray(Charsets.UTF_8)) // optional
 *     .build()
 * DownloadVideoOffline.startDownload(context, request)
 * ```
 *
 * ## With DRM (Widevine)
 *
 * You need: PSSH from the stream, license URL, and keySetId after acquiring the offline license.
 * [io.kinescope.sdk.shorts.drm.DrmConfigurator] and [io.kinescope.sdk.shorts.drm.DrmHelper]
 * can obtain PSSH from ExoPlayer and acquire the offline license. Then:
 *
 * ```kotlin
 * val request = DownloadRequest.Builder(contentId, uri)
 *     .setMimeType(MimeTypes.APPLICATION_M3U8)
 *     .setData(metadataJson.toByteArray(Charsets.UTF_8))
 *     .setKeySetId(keySetId)
 *     .build()
 * DownloadVideoOffline.startDownload(context, request)
 * ```
 *
 * ## DASH (instead of HLS)
 *
 * Same flow, different MIME and manifest URL:
 * `setMimeType(MimeTypes.APPLICATION_MPD)`, URI to `.mpd`. DRM (keySetId) — same as for HLS.
 *
 * ## Offline playback
 *
 * [getDownloadCache] — download cache. Use CacheDataSource + MediaItem with [Download.request.keySetId] for DRM.
 * [io.kinescope.sdk.shorts.player.OfflinePlayer] supports HLS only; for DASH use
 * `DashMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem)` with the same cache and keySetId.
 */
@OptIn(UnstableApi::class)
object DownloadVideoOffline {

    /**
     * Initializes the download manager and cache. Call at app startup.
     * Idempotent.
     */
    fun initialize(context: Context) {
        VideoDownloadManager.initialize(context)
    }

    /**
     * Starts a download via VideoDownloadService (foreground, with notification).
     */
    fun startDownload(context: Context, request: DownloadRequest) {
        initialize(context)
        VideoDownloadService.startDownload(context, request)
    }

    /**
     * Removes a download.
     */
    fun removeDownload(context: Context, downloadId: String) {
        VideoDownloadManager.removeDownload(context, downloadId)
    }

    /**
     * All completed downloads.
     */
    fun getAllCompletedDownloads(context: Context): List<Download> {
        initialize(context)
        return VideoDownloadManager.getAllCompletedDownloads(context)
    }

    /**
     * Download by contentId (id from DownloadRequest).
     */
    fun getDownloadById(context: Context, contentId: String): Download? {
        initialize(context)
        return VideoDownloadManager.getDownloadById(contentId)
    }

    /**
     * Download cache for offline playback (CacheDataSource, OfflinePlayer, etc.).
     */
    fun getDownloadCache(context: Context): Cache {
        initialize(context)
        return VideoDownloadManager.getDownloadCache(context)
    }

    /**
     * DownloadManager (Media3) for advanced use: index, iterate over all downloads.
     */
    fun getDownloadManager(context: Context): DownloadManager {
        initialize(context)
        return VideoDownloadManager.getDownloadManager(context)
    }

    /**
     * Download progress: (percentage 0–100, bytes).
     */
    fun getDownloadProgress(download: Download): Pair<Int, Long> {
        return VideoDownloadManager.getDownloadProgress(download)
    }

    /**
     * Subscribe to download changes/removals (for UI updates).
     */
    fun addDownloadListener(context: Context, listener: DownloadManager.Listener) {
        initialize(context)
        VideoDownloadManager.addDownloadListener(context, listener)
    }

    /**
     * Unsubscribe from updates. Call from onDestroy.
     */
    fun removeDownloadListener(listener: DownloadManager.Listener) {
        VideoDownloadManager.removeDownloadListener(listener)
    }
}

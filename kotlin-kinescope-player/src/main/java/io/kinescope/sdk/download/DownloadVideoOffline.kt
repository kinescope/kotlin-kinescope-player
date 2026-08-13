package io.kinescope.sdk.download

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import io.kinescope.sdk.shorts.download.OfflineDownloadQualityHelper
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
 * 3. Prefer [listDownloadQualities] + [startDownloadWithQuality] so only one height is cached.
 *    A bare [DownloadRequest] on a master playlist downloads **all** variants.
 *
 * ## Single quality (recommended)
 *
 * ```kotlin
 * DownloadVideoOffline.initialize(context)
 * DownloadVideoOffline.listDownloadQualities(context, uri, qualityMapHints) { result ->
 *     val height = result.getOrNull()?.firstOrNull()?.height ?: return@listDownloadQualities
 *     DownloadVideoOffline.startDownloadWithQuality(
 *         context = context,
 *         contentId = contentId,
 *         manifestUri = uri,
 *         videoHeightPx = height,
 *         keySetId = keySetId, // optional Widevine
 *     )
 * }
 * ```
 *
 * ## With DRM (Widevine)
 *
 * You need: PSSH from the stream, license URL, and keySetId after acquiring the offline license.
 * [io.kinescope.sdk.shorts.drm.DrmConfigurator] and [io.kinescope.sdk.shorts.drm.DrmHelper]
 * can obtain PSSH from ExoPlayer and acquire the offline license. Then pass [keySetId] to
 * [startDownloadWithQuality] / [startDownload].
 *
 * ## Offline playback
 *
 * Prefer `download.request.toMediaItem()` so [DownloadRequest.streamKeys] match the cached tracks.
 * [getDownloadCache] — download cache. Use CacheDataSource + MediaItem with keySetId for DRM.
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
     * Lists downloadable video heights from an HLS/DASH manifest.
     * [qualityMap] supplies display names from embed `quality_map` (height / short side / name digits).
     */
    fun listDownloadQualities(
        context: Context,
        manifestUri: Uri,
        qualityMap: List<OfflineDownloadQualityHelper.QualityMapHint>? = null,
        mimeType: String = MimeTypes.APPLICATION_M3U8,
        drmLicenseUrl: String? = null,
        callback: (Result<List<OfflineDownloadQualityHelper.QualityOption>>) -> Unit,
    ) {
        initialize(context)
        OfflineDownloadQualityHelper.listQualities(
            context = context,
            manifestUri = manifestUri,
            mimeType = mimeType,
            qualityMap = qualityMap,
            drmLicenseUrl = drmLicenseUrl,
            callback = callback,
        )
    }

    fun listDownloadQualities(
        context: Context,
        manifestUri: String,
        qualityMap: List<OfflineDownloadQualityHelper.QualityMapHint>? = null,
        mimeType: String = MimeTypes.APPLICATION_M3U8,
        drmLicenseUrl: String? = null,
        callback: (Result<List<OfflineDownloadQualityHelper.QualityOption>>) -> Unit,
    ) = listDownloadQualities(
        context,
        manifestUri.toUri(),
        qualityMap,
        mimeType,
        drmLicenseUrl,
        callback,
    )

    /**
     * Builds a single-quality [DownloadRequest] (stream keys) and starts the download.
     */
    fun startDownloadWithQuality(
        context: Context,
        contentId: String,
        manifestUri: Uri,
        videoHeightPx: Int,
        videoWidthPx: Int = androidx.media3.common.C.LENGTH_UNSET,
        mimeType: String = MimeTypes.APPLICATION_M3U8,
        data: ByteArray? = null,
        keySetId: ByteArray? = null,
        drmLicenseUrl: String? = null,
        qualityHint: String? = null,
        onError: ((Throwable) -> Unit)? = null,
        onStarted: (() -> Unit)? = null,
    ) {
        initialize(context)
        OfflineDownloadQualityHelper.buildDownloadRequest(
            context = context,
            contentId = contentId,
            manifestUri = manifestUri,
            videoHeightPx = videoHeightPx,
            videoWidthPx = videoWidthPx,
            mimeType = mimeType,
            data = data,
            keySetId = keySetId,
            drmLicenseUrl = drmLicenseUrl,
            qualityHint = qualityHint,
        ) { result ->
            result.onSuccess { request ->
                VideoDownloadService.startDownload(context.applicationContext, request)
                onStarted?.invoke()
            }.onFailure { error ->
                onError?.invoke(error)
            }
        }
    }

    fun startDownloadWithQuality(
        context: Context,
        contentId: String,
        manifestUri: String,
        videoHeightPx: Int,
        videoWidthPx: Int = androidx.media3.common.C.LENGTH_UNSET,
        mimeType: String = MimeTypes.APPLICATION_M3U8,
        data: ByteArray? = null,
        keySetId: ByteArray? = null,
        drmLicenseUrl: String? = null,
        qualityHint: String? = null,
        onError: ((Throwable) -> Unit)? = null,
        onStarted: (() -> Unit)? = null,
    ) = startDownloadWithQuality(
        context,
        contentId,
        manifestUri.toUri(),
        videoHeightPx,
        videoWidthPx,
        mimeType,
        data,
        keySetId,
        drmLicenseUrl,
        qualityHint,
        onError,
        onStarted,
    )

    /**
     * Starts a download via VideoDownloadService (foreground, with notification).
     * Prefer [startDownloadWithQuality] so only one height is cached.
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

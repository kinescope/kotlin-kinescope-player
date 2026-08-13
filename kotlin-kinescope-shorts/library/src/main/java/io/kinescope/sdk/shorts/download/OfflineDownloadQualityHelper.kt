package io.kinescope.sdk.shorts.download

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.trackselection.MappingTrackSelector.MappedTrackInfo
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lists HLS/DASH video qualities and builds a [DownloadRequest] for a single selected track
 * (plus audio / text) via Media3 [DownloadHelper] / stream keys.
 *
 * For Widevine content, pass [drmLicenseUrl] so the helper can prepare tracks; if the manifest
 * still exposes no video tracks, [qualityMap] from embed JSON is used as a fallback picker list.
 */
@UnstableApi
object OfflineDownloadQualityHelper {

    private const val USER_AGENT = "KinescopeAndroidVideoKotlin"
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "kinescope-offline-quality").apply { isDaemon = true }
    }

    data class QualityOption(
        /** Actual [Format.height] used for track selection / stream keys when known. */
        val height: Int,
        val width: Int = C.LENGTH_UNSET,
        val bitrate: Int = C.RATE_UNSET_INT,
        /** Display name from embed `quality_map.name` when available. */
        val qualityName: String? = null,
    ) {
        val label: String
            get() {
                val mapped = qualityName?.trim().orEmpty()
                if (mapped.isNotEmpty()) return mapped
                val shortSide = qualityDisplayHeightPx(width, height)
                val px = shortSide.takeIf { it > 0 } ?: height
                return when {
                    px >= 2160 -> "2160p"
                    px >= 1440 -> "1440p"
                    px >= 1080 -> "1080p"
                    px >= 720 -> "720p"
                    px >= 480 -> "480p"
                    px >= 360 -> "360p"
                    px > 0 -> "${px}p"
                    else -> "Auto"
                }
            }
    }

    /**
     * Optional quality_map entries (height / name) from embed JSON for display labels.
     */
    data class QualityMapHint(
        val height: Int,
        val name: String,
        val label: String? = null,
    )

    fun qualitiesFromQualityMap(qualityMap: List<QualityMapHint>?): List<QualityOption> {
        if (qualityMap.isNullOrEmpty()) return emptyList()
        return qualityMap
            .mapNotNull { entry ->
                val height = entry.height.takeIf { it > 0 } ?: return@mapNotNull null
                val name = entry.name.trim().ifEmpty { entry.label?.trim().orEmpty() }
                QualityOption(
                    height = height,
                    qualityName = name.ifEmpty { null },
                )
            }
            .distinctBy { it.height }
            .sortedByDescending { option ->
                digitsFromName(option.qualityName)
                    ?: qualityDisplayHeightPx(option.width, option.height).takeIf { it > 0 }
                    ?: option.height
            }
    }

    fun listQualities(
        context: Context,
        manifestUri: Uri,
        mimeType: String = MimeTypes.APPLICATION_M3U8,
        qualityMap: List<QualityMapHint>? = null,
        drmLicenseUrl: String? = null,
        dataSourceFactory: DataSource.Factory = defaultDataSourceFactory(),
        callback: (Result<List<QualityOption>>) -> Unit,
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val delivered = AtomicBoolean(false)
        fun deliver(result: Result<List<QualityOption>>) {
            if (delivered.compareAndSet(false, true)) {
                mainHandler.post { callback(result) }
            }
        }

        // Prefer embed quality_map for DRM — DownloadHelper often cannot list tracks
        // until a license session exists, and even then variants may be empty.
        val fromMap = qualitiesFromQualityMap(qualityMap)
        if (!drmLicenseUrl.isNullOrBlank() && fromMap.isNotEmpty()) {
            deliver(Result.success(fromMap))
            return
        }

        val helper = createHelper(
            context = context,
            manifestUri = manifestUri,
            mimeType = mimeType,
            dataSourceFactory = dataSourceFactory,
            drmLicenseUrl = drmLicenseUrl,
        )
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                try {
                    val fromManifest = collectQualities(helper, qualityMap)
                    val options = fromManifest.ifEmpty { fromMap }
                    deliver(Result.success(options))
                } catch (e: Exception) {
                    if (fromMap.isNotEmpty()) {
                        deliver(Result.success(fromMap))
                    } else {
                        deliver(Result.failure(e))
                    }
                } finally {
                    helper.release()
                }
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                helper.release()
                if (fromMap.isNotEmpty()) {
                    deliver(Result.success(fromMap))
                } else {
                    deliver(Result.failure(e))
                }
            }
        })
    }

    fun buildDownloadRequest(
        context: Context,
        contentId: String,
        manifestUri: Uri,
        videoHeightPx: Int,
        videoWidthPx: Int = C.LENGTH_UNSET,
        mimeType: String = MimeTypes.APPLICATION_M3U8,
        data: ByteArray? = null,
        keySetId: ByteArray? = null,
        drmLicenseUrl: String? = null,
        qualityHint: String? = null,
        dataSourceFactory: DataSource.Factory = defaultDataSourceFactory(),
        callback: (Result<DownloadRequest>) -> Unit,
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val delivered = AtomicBoolean(false)
        fun deliver(result: Result<DownloadRequest>) {
            if (delivered.compareAndSet(false, true)) {
                mainHandler.post { callback(result) }
            }
        }

        val qualityDigits = digitsFromName(qualityHint) ?: videoHeightPx.takeIf { it > 0 }

        // Prefer a single-variant master (absolute URLs) so HlsDownloader cannot pick the wrong
        // STREAM-INF via StreamKey index mismatches (seen: want 1080 → HTTP 360/720).
        if (mimeType == MimeTypes.APPLICATION_M3U8 && qualityDigits != null) {
            val appContext = context.applicationContext
            ioExecutor.execute {
                try {
                    val masterText = readUtf8(dataSourceFactory, manifestUri)
                    val variant = pickHlsVariant(masterText, qualityDigits)
                        ?: throw IllegalStateException(
                            "No HLS variant for quality=$qualityDigits in $manifestUri",
                        )
                    val filteredMaster = rewriteMasterForSingleVariant(manifestUri, masterText, variant)
                    if (!filteredMaster.contains("#EXT-X-STREAM-INF") ||
                        !filteredMaster.contains("quality=$qualityDigits")
                    ) {
                        throw IllegalStateException(
                            "Filtered master missing quality=$qualityDigits variant",
                        )
                    }
                    val mastersDir = File(appContext.filesDir, "kinescope_offline_masters")
                    if (!mastersDir.exists()) mastersDir.mkdirs()
                    val masterFile = File(
                        mastersDir,
                        "master_${contentId.hashCode()}_$qualityDigits.m3u8",
                    )
                    masterFile.writeText(filteredMaster, StandardCharsets.UTF_8)
                    val builder = DownloadRequest.Builder(contentId, Uri.fromFile(masterFile))
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                    if (data != null) {
                        builder.setData(data)
                    }
                    if (keySetId != null) {
                        builder.setKeySetId(keySetId)
                    }
                    deliver(Result.success(builder.build()))
                } catch (e: Exception) {
                    deliver(Result.failure(e))
                }
            }
            return
        }

        // Non-HLS (e.g. DASH) or missing quality digits — DownloadHelper path.
        val helper = createHelper(
            context = context,
            manifestUri = manifestUri,
            mimeType = mimeType,
            dataSourceFactory = dataSourceFactory,
            drmLicenseUrl = drmLicenseUrl,
        )
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                try {
                    val selected = applyExactVideoTrackSelection(
                        helper = helper,
                        videoWidthPx = videoWidthPx,
                        videoHeightPx = videoHeightPx,
                        qualityHint = qualityHint,
                    )
                    var request = helper.getDownloadRequest(contentId, data)
                    if (request.streamKeys.isEmpty()) {
                        throw IllegalStateException(
                            "No stream keys for ${videoWidthPx}x$videoHeightPx — quality not selected",
                        )
                    }
                    request = forceHlsVariantStreamKey(request, selected)
                    if (keySetId != null) {
                        request = request.copyWithKeySetId(keySetId)
                    }
                    deliver(Result.success(request))
                } catch (e: Exception) {
                    deliver(Result.failure(e))
                } finally {
                    helper.release()
                }
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                helper.release()
                deliver(Result.failure(e))
            }
        })
    }

    private fun createHelper(
        context: Context,
        manifestUri: Uri,
        mimeType: String,
        dataSourceFactory: DataSource.Factory,
        drmLicenseUrl: String? = null,
    ): DownloadHelper {
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(manifestUri)
            .setMimeType(mimeType)
        var drmSessionManager: DrmSessionManager? = null
        if (!drmLicenseUrl.isNullOrBlank()) {
            mediaItemBuilder
                .setDrmUuid(C.WIDEVINE_UUID)
                .setDrmLicenseUri(drmLicenseUrl)
                .setDrmMultiSession(true)
            drmSessionManager = buildWidevineSessionManager(context, drmLicenseUrl)
        }
        val mediaItem = mediaItemBuilder.build()
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
        @Suppress("DEPRECATION")
        return if (drmSessionManager != null) {
            DownloadHelper.forMediaItem(
                mediaItem,
                DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS,
                renderersFactory,
                dataSourceFactory,
                drmSessionManager,
            )
        } else {
            DownloadHelper.forMediaItem(
                context.applicationContext,
                mediaItem,
                renderersFactory,
                dataSourceFactory,
            )
        }
    }

    private fun buildWidevineSessionManager(
        context: Context,
        licenseUrl: String,
    ): DrmSessionManager {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(context, USER_AGENT))
        val callback = HttpMediaDrmCallback(licenseUrl, httpFactory)
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID) { uuid ->
                FrameworkMediaDrm.newInstance(uuid)
            }
            .build(callback)
    }

    private fun collectQualities(
        helper: DownloadHelper,
        qualityMap: List<QualityMapHint>?,
    ): List<QualityOption> {
        val seenHeights = LinkedHashSet<Int>()
        val options = mutableListOf<QualityOption>()
        for (periodIndex in 0 until helper.periodCount) {
            val mapped = helper.getMappedTrackInfo(periodIndex)
            for (rendererIndex in 0 until mapped.rendererCount) {
                if (mapped.getRendererType(rendererIndex) != C.TRACK_TYPE_VIDEO) continue
                if (mapped.getTrackGroups(rendererIndex).length == 0) continue
                if (mapped.getRendererSupport(rendererIndex) == MappedTrackInfo.RENDERER_SUPPORT_NO_TRACKS) {
                    continue
                }
                val groups = mapped.getTrackGroups(rendererIndex)
                for (groupIndex in 0 until groups.length) {
                    val group = groups[groupIndex]
                    for (trackIndex in 0 until group.length) {
                        val support = mapped.getTrackSupport(rendererIndex, groupIndex, trackIndex)
                        if (support != C.FORMAT_HANDLED && support != C.FORMAT_EXCEEDS_CAPABILITIES) {
                            continue
                        }
                        val format = group.getFormat(trackIndex)
                        val height = format.height
                        if (height <= 0 || height == C.LENGTH_UNSET || !seenHeights.add(height)) continue
                        options.add(
                            QualityOption(
                                height = height,
                                width = format.width,
                                bitrate = format.bitrate,
                                qualityName = resolveName(qualityMap, format),
                            )
                        )
                    }
                }
            }
        }
        return options.sortedByDescending { option ->
            qualityDisplayHeightPx(option.width, option.height).takeIf { it > 0 } ?: option.height
        }
    }

    private fun resolveName(qualityMap: List<QualityMapHint>?, format: Format): String? {
        if (qualityMap.isNullOrEmpty()) return null
        val height = format.height
        val shortSide = qualityDisplayHeightPx(format.width, height)

        qualityMap.firstOrNull { it.height == height && height > 0 }?.name?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        if (shortSide > 0) {
            qualityMap.firstOrNull { it.height == shortSide }?.name?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        qualityMap.firstOrNull { entry ->
            val digits = digitsFromName(entry.name) ?: return@firstOrNull false
            digits == shortSide || (height > 0 && digits == height)
        }?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        return null
    }

    /**
     * Selects one video track via [TrackSelectionOverride] (exact Format), plus default audio/text.
     * Size min/max constraints alone are unreliable for HLS multivariant + DRM and can silently
     * fall back to a lower playlist (e.g. want 1080 → download `480p.mp4`).
     */
    private fun applyExactVideoTrackSelection(
        helper: DownloadHelper,
        videoWidthPx: Int,
        videoHeightPx: Int,
        qualityHint: String? = null,
    ): Format {
        val hintDigits = digitsFromName(qualityHint) ?: videoHeightPx.takeIf { it > 0 }
        var lastError: String? = null
        for (periodIndex in 0 until helper.periodCount) {
            val mapped = helper.getMappedTrackInfo(periodIndex)
            val match = findVideoTrack(mapped, videoWidthPx, videoHeightPx, hintDigits)
            if (match == null) {
                lastError = describeVideoTracks(helper)
                continue
            }
            val group = mapped.getTrackGroups(match.rendererIndex)[match.groupIndex]
            val format = group.getFormat(match.trackIndex)
            val override = TrackSelectionOverride(group, listOf(match.trackIndex))
            val parameters = DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS
                .buildUpon()
                .setForceHighestSupportedBitrate(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setOverrideForType(override)
                .build()

            helper.clearTrackSelections(periodIndex)
            helper.addTrackSelection(periodIndex, parameters)
            return format
        }
        throw IllegalStateException(
            "Video track not found for ${videoWidthPx}x$videoHeightPx hint=$qualityHint. $lastError",
        )
    }

    private data class VideoTrackRef(
        val rendererIndex: Int,
        val groupIndex: Int,
        val trackIndex: Int,
    )

    private fun findVideoTrack(
        mapped: MappedTrackInfo,
        videoWidthPx: Int,
        videoHeightPx: Int,
        hintDigits: Int?,
    ): VideoTrackRef? {
        var exactWxH: VideoTrackRef? = null
        var byHeight: VideoTrackRef? = null
        var byShortSide: VideoTrackRef? = null
        var byHintLabel: VideoTrackRef? = null
        var byHintHeight: VideoTrackRef? = null

        for (rendererIndex in 0 until mapped.rendererCount) {
            if (mapped.getRendererType(rendererIndex) != C.TRACK_TYPE_VIDEO) continue
            val groups = mapped.getTrackGroups(rendererIndex)
            for (groupIndex in 0 until groups.length) {
                val group = groups[groupIndex]
                for (trackIndex in 0 until group.length) {
                    val format = group.getFormat(trackIndex)
                    val ref = VideoTrackRef(rendererIndex, groupIndex, trackIndex)
                    val shortSide = qualityDisplayHeightPx(format.width, format.height)

                    if (format.height == videoHeightPx && videoHeightPx > 0) {
                        if (videoWidthPx > 0 &&
                            videoWidthPx != C.LENGTH_UNSET &&
                            format.width == videoWidthPx
                        ) {
                            exactWxH = ref
                        }
                        if (byHeight == null) byHeight = ref
                    }
                    if (shortSide == videoHeightPx && videoHeightPx > 0 && byShortSide == null) {
                        byShortSide = ref
                    }
                    if (hintDigits != null && hintDigits > 0) {
                        if ((format.height == hintDigits || shortSide == hintDigits) &&
                            byHintHeight == null
                        ) {
                            byHintHeight = ref
                        }
                        if (byHintLabel == null && formatMatchesQualityDigits(format, hintDigits)) {
                            byHintLabel = ref
                        }
                    }
                }
            }
        }
        return exactWxH ?: byHeight ?: byShortSide ?: byHintHeight ?: byHintLabel
    }

    private fun formatMatchesQualityDigits(format: Format, digits: Int): Boolean {
        val tokens = listOfNotNull(format.id, format.label, format.containerMimeType)
        return tokens.any { token ->
            val t = token.lowercase()
            t.contains("${digits}p") ||
                t.contains("quality=$digits") ||
                Regex("""(?:^|[^\d])$digits(?:[^\d]|$)""").containsMatchIn(t)
        }
    }

    private fun describeVideoTracks(helper: DownloadHelper): String {
        val parts = mutableListOf<String>()
        for (periodIndex in 0 until helper.periodCount) {
            val mapped = helper.getMappedTrackInfo(periodIndex)
            for (rendererIndex in 0 until mapped.rendererCount) {
                if (mapped.getRendererType(rendererIndex) != C.TRACK_TYPE_VIDEO) continue
                val groups = mapped.getTrackGroups(rendererIndex)
                for (groupIndex in 0 until groups.length) {
                    val group = groups[groupIndex]
                    for (trackIndex in 0 until group.length) {
                        val f = group.getFormat(trackIndex)
                        parts.add(
                            "${f.width}x${f.height}@${f.bitrate} id=${f.id} label=${f.label}",
                        )
                    }
                }
            }
        }
        return if (parts.isEmpty()) "tracks=none" else "tracks=[${parts.joinToString("; ")}]"
    }

    private fun defaultDataSourceFactory(): DataSource.Factory =
        DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)

    private fun qualityDisplayHeightPx(width: Int, height: Int): Int {
        val w = if (width == C.LENGTH_UNSET || width <= 0) 0 else width
        val h = if (height == C.LENGTH_UNSET || height <= 0) 0 else height
        return when {
            w > 0 && h > 0 -> minOf(w, h)
            h > 0 -> h
            w > 0 -> w
            else -> 0
        }
    }


    private data class HlsVariantLine(val index: Int, val info: String, val uri: String)

    private fun readUtf8(dataSourceFactory: DataSource.Factory, uri: Uri): String {
        val dataSource = dataSourceFactory.createDataSource()
        try {
            dataSource.open(DataSpec(uri))
            val bytes = DataSourceUtil.readToEnd(dataSource)
            return String(bytes, StandardCharsets.UTF_8)
        } finally {
            DataSourceUtil.closeQuietly(dataSource)
        }
    }

    private fun parseHlsVariants(masterText: String): List<HlsVariantLine> {
        val lines = masterText.lines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }
        val variants = mutableListOf<HlsVariantLine>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val uri = lines.getOrNull(i + 1)?.takeUnless { next -> next.startsWith("#") }.orEmpty()
                if (uri.isNotEmpty()) {
                    variants.add(HlsVariantLine(variants.size, line, uri))
                    i += 2
                    continue
                }
            }
            i++
        }
        return variants
    }

    private fun pickHlsVariant(masterText: String, qualityDigits: Int): HlsVariantLine? {
        val variants = parseHlsVariants(masterText)
        variants.firstOrNull { variant ->
            variant.uri.contains("quality=$qualityDigits")
        }?.let { return it }
        variants.firstOrNull { variant ->
            val match = Regex("""RESOLUTION=(\d+)x(\d+)""").find(variant.info) ?: return@firstOrNull false
            val width = match.groupValues[1].toInt()
            val height = match.groupValues[2].toInt()
            height == qualityDigits || minOf(width, height) == qualityDigits
        }?.let { return it }
        return null
    }

    private fun rewriteMasterForSingleVariant(
        masterUri: Uri,
        masterText: String,
        selected: HlsVariantLine,
    ): String {
        val out = StringBuilder()
        out.append("#EXTM3U\n")
        out.append("#EXT-X-VERSION:6\n")
        out.append("#EXT-X-INDEPENDENT-SEGMENTS\n")
        for (raw in masterText.lines()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-MEDIA:")) {
                out.append(rewriteMediaTagUris(masterUri, line)).append('\n')
            }
        }
        out.append(selected.info.trim()).append('\n')
        out.append(resolveAgainstMaster(masterUri, selected.uri)).append('\n')
        return out.toString()
    }

    private fun rewriteMediaTagUris(masterUri: Uri, line: String): String {
        val regex = Regex("""URI="([^"]*)"""")
        val match = regex.find(line) ?: return line
        val absolute = resolveAgainstMaster(masterUri, match.groupValues[1])
        return line.replaceRange(match.range.first, match.range.last + 1, "URI=\"$absolute\"")
    }

    private fun resolveAgainstMaster(masterUri: Uri, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        val base = masterUri.toString().substringBeforeLast('/') + "/"
        return base + ref.removePrefix("/")
    }

    private fun forceHlsVariantStreamKey(request: DownloadRequest, selected: Format): DownloadRequest {
        val variantIndex = selected.id?.toIntOrNull() ?: return request
        val nonVariant = request.streamKeys.filter { key ->
            key.groupIndex != HlsMultivariantPlaylist.GROUP_INDEX_VARIANT
        }
        val keys = ArrayList<StreamKey>(nonVariant.size + 1)
        keys.add(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, variantIndex))
        keys.addAll(nonVariant)
        val builder = DownloadRequest.Builder(request.id, request.uri)
            .setMimeType(request.mimeType)
            .setStreamKeys(keys)
            .setCustomCacheKey(request.customCacheKey)
        if (request.data != null) {
            builder.setData(request.data)
        }
        if (request.keySetId != null) {
            builder.setKeySetId(request.keySetId)
        }
        return builder.build()
    }


    private fun digitsFromName(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        val digits = Regex("""(\d+)""").find(name)?.groupValues?.getOrNull(1) ?: return null
        return digits.toIntOrNull()?.takeIf { it > 0 }
    }
}

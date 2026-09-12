@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package io.kinescope.demo.shorts

import android.content.Context
import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.sdk.api.KinescopeApiConfig
import io.kinescope.sdk.api.KinescopeApiHelper
import io.kinescope.sdk.models.videos.KinescopeVideoApi
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider
import io.kinescope.sdk.shorts.models.DrmInfo
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.models.VideoQualityMapEntry
import io.kinescope.sdk.shorts.models.WidevineInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Shorts feed: Dashboard catalog + playback JSON.
 *
 * Uses catalog `hls_link` when present; otherwise fetches `{id}.json` in parallel.
 * Emits items progressively via [loadVideosProgressive] so the UI can start early.
 */
@OptIn(InternalSerializationApi::class)
class DemoKinescopeVideoProvider(
    context: Context,
    private val apiKey: String = KinescopeDemoConfig.API_KEY,
    private val drmAuthToken: String? = null,
    private val referer: String = "https://kinescope.io/",
) : KinescopeVideoProvider {

    private val apiHelper: KinescopeApiHelper = KinescopeApiConfig.createApiHelper(apiKey)
    private val httpClient = OkHttpClient()
    private val playbackSemaphore = Semaphore(permits = PARALLEL_PLAYBACK_FETCHES)

    // Kept so callers that construct with Context stay source-compatible.
    init {
        context.applicationContext
    }

    override suspend fun loadVideo(videoId: String): VideoData? =
        withContext(Dispatchers.IO) { fetchPlayback(videoId) }

    override suspend fun loadVideos(
        projectId: String?,
        folderId: String?,
        limit: Int,
        offset: Int,
    ): List<VideoData> {
        val catalog = loadCatalog(projectId, folderId, limit, offset) ?: return emptyList()
        return resolveCatalog(catalog)
    }

    /**
     * Loads the feed and invokes [onVideo] in catalog order as soon as each item is ready.
     */
    suspend fun loadVideosProgressive(
        projectId: String?,
        folderId: String?,
        limit: Int,
        onVideo: suspend (VideoData) -> Unit,
    ) {
        val catalog = loadCatalog(projectId, folderId, limit, offset = 0) ?: return

        coroutineScope {
            val deferred = catalog.mapIndexed { index, item ->
                async(Dispatchers.IO) {
                    index to resolveCatalogItem(item)
                }
            }
            // null slot = still pending; resolved[i]=true means slot finished (video may be null).
            val byIndex = arrayOfNulls<VideoData>(catalog.size)
            val resolved = BooleanArray(catalog.size)
            var nextToEmit = 0
            deferred.forEach { job ->
                val (index, video) = job.await()
                byIndex[index] = video
                resolved[index] = true
                while (nextToEmit < byIndex.size && resolved[nextToEmit]) {
                    val ready = byIndex[nextToEmit]
                    nextToEmit++
                    if (ready != null) onVideo(ready)
                }
            }
        }
    }

    private suspend fun loadCatalog(
        projectId: String?,
        folderId: String?,
        limit: Int,
        offset: Int,
    ): List<KinescopeVideoApi>? {
        val page = (offset / limit.coerceAtLeast(1)) + 1
        return withContext(Dispatchers.IO) {
            try {
                apiHelper.getAllVideos(
                    page = page,
                    perPage = limit,
                    projectId = projectId?.takeIf { it.isNotBlank() },
                    folderId = folderId?.takeIf { it.isNotBlank() },
                ).first().data
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun resolveCatalog(items: List<KinescopeVideoApi>): List<VideoData> =
        coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) { resolveCatalogItem(item) }
            }.awaitAll().filterNotNull()
        }

    private suspend fun resolveCatalogItem(item: KinescopeVideoApi): VideoData? {
        val catalogHls = item.hlsLink?.takeIf { it.isNotBlank() }
        if (catalogHls != null) {
            val fromCatalog = VideoData(
                hlsLink = catalogHls,
                drm = null,
                title = item.title,
                subtitle = item.subtitle,
                description = item.description,
                videoId = item.id,
                posterUrl = item.poster?.thumbnailUrl(),
            )
            // Always pull playback JSON for DRM / richer metadata; catalog HLS is fallback only.
            return playbackSemaphore.withPermit { fetchPlayback(item.id) } ?: fromCatalog
        }
        return playbackSemaphore.withPermit { fetchPlayback(item.id) }
    }

    private fun fetchPlayback(videoId: String): VideoData? {
        val url = buildString {
            append("https://kinescope.io/$videoId.json?sdk=android")
            val token = drmAuthToken?.takeIf { it.isNotBlank() }
            if (token != null) append("&drmauthtoken=$token")
        }
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                AppJson.decodeFromString(PlaybackVideo.serializer(), body).toVideoData(apiKey)
            }
        }.getOrNull()
    }

    @Serializable
    private data class PlaybackVideo(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val description: String? = null,
        @SerialName("hls_link") val hlsLink: String? = null,
        val poster: Poster? = null,
        val drm: PlaybackDrm? = null,
        @SerialName("quality_map") val qualityMap: List<PlaybackQuality>? = null,
    ) {
        fun toVideoData(apiKey: String): VideoData? {
            val hls = hlsLink?.takeIf { it.isNotBlank() } ?: return null
            val licenseUrl = drm?.widevine?.licenseUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { url ->
                    when {
                        url.endsWith("token=") -> url + apiKey
                        url.contains("token=") -> url
                        else -> url
                    }
                }
            return VideoData(
                hlsLink = hls,
                drm = licenseUrl?.let { DrmInfo(widevine = WidevineInfo(licenseUrl = it)) },
                title = title,
                subtitle = subtitle,
                description = description,
                videoId = id,
                qualityMap = qualityMap?.mapNotNull { item ->
                    val name = item.name ?: return@mapNotNull null
                    val height = item.height ?: return@mapNotNull null
                    VideoQualityMapEntry(label = item.label, name = name, height = height)
                },
                posterUrl = poster?.url,
            )
        }
    }

    @Serializable
    private data class Poster(val url: String? = null)

    @Serializable
    private data class PlaybackDrm(val widevine: PlaybackWidevine? = null)

    @Serializable
    private data class PlaybackWidevine(
        @SerialName("licenseUrl") val licenseUrl: String? = null,
    )

    @Serializable
    private data class PlaybackQuality(
        val label: String? = null,
        val name: String? = null,
        val height: Int? = null,
    )

    private companion object {
        const val PARALLEL_PLAYBACK_FETCHES = 8
    }
}

package io.kinescope.sdk.shorts.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Warms [VideoCache] for clear HLS without creating an [androidx.media3.exoplayer.ExoPlayer]:
 * master playlist → first media playlist → first few media segments.
 */
@OptIn(UnstableApi::class)
class MediaCachePrefetcher(
    private val maxSegments: Int = 3,
    private val maxBytesPerResource: Long = 1_500_000L,
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun reset() {
        cancelled.set(false)
    }

    fun prefetchHls(manifestUrl: String) {
        if (cancelled.get()) return
        val cache = try {
            VideoCache.getCache()
        } catch (_: Exception) {
            return
        }

        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)

        fun cacheDataSource() = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()

        try {
            cacheUri(cacheDataSource(), manifestUrl)
            if (cancelled.get()) return

            val masterBody = readCachedOrNetwork(cacheDataSource(), manifestUrl) ?: return
            val mediaPlaylistUrl = resolveFirstMediaPlaylist(manifestUrl, masterBody) ?: return
            if (cancelled.get()) return

            cacheUri(cacheDataSource(), mediaPlaylistUrl)
            if (cancelled.get()) return

            val mediaBody = readCachedOrNetwork(cacheDataSource(), mediaPlaylistUrl) ?: return
            val segmentUrls = resolveSegmentUrls(mediaPlaylistUrl, mediaBody).take(maxSegments)
            for (segmentUrl in segmentUrls) {
                if (cancelled.get()) return
                cacheUri(cacheDataSource(), segmentUrl)
            }
        } catch (_: Exception) {
            // Best-effort warm; playback can fetch remaining bytes.
        }
    }

    private fun cacheUri(dataSource: CacheDataSource, url: String) {
        if (cancelled.get()) return
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setLength(maxBytesPerResource)
            .build()
        CacheWriter(dataSource, dataSpec, /* temporaryBuffer= */ null, /* progressListener= */ null)
            .cache()
    }

    private fun readCachedOrNetwork(dataSource: CacheDataSource, url: String): String? {
        val dataSpec = DataSpec(Uri.parse(url))
        return try {
            dataSource.open(dataSpec)
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = dataSource.read(buffer, 0, buffer.size)
                if (read == -1) break
                out.write(buffer, 0, read)
                if (out.size() > maxBytesPerResource) break
            }
            out.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            null
        } finally {
            try {
                dataSource.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveFirstMediaPlaylist(masterUrl: String, body: String): String? {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val next = lines.getOrNull(i + 1) ?: return null
                if (!next.startsWith("#")) {
                    return resolveAgainst(masterUrl, next)
                }
            }
            i++
        }
        // Media playlist (not master): treat URL itself as media playlist.
        if (lines.any { it.startsWith("#EXTINF") }) {
            return masterUrl
        }
        return null
    }

    private fun resolveSegmentUrls(mediaPlaylistUrl: String, body: String): List<String> {
        val result = ArrayList<String>(maxSegments)
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            result.add(resolveAgainst(mediaPlaylistUrl, line))
            if (result.size >= maxSegments) break
        }
        return result
    }

    private fun resolveAgainst(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return try {
            java.net.URI(baseUrl).resolve(ref).toString()
        } catch (_: Exception) {
            ref
        }
    }
}

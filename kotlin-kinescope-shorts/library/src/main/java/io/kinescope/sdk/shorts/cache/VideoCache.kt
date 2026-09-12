package io.kinescope.sdk.shorts.cache

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(androidx.media3.common.util.UnstableApi::class)
object VideoCache {

    @Volatile
    private var simpleCache: SimpleCache? = null

    private fun getMaxCacheSize(): Long {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val processorCount = Runtime.getRuntime().availableProcessors()

        val isWeakDevice = maxMemory < 2048 ||
            processorCount < 4 ||
            (Build.BRAND.equals("honor", ignoreCase = true) && maxMemory < 3072) ||
            (Build.BRAND.equals("huawei", ignoreCase = true) && maxMemory < 3072)

        return if (isWeakDevice) {
            80L * 1024 * 1024
        } else {
            150L * 1024 * 1024
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (simpleCache != null) return
        val cacheDir = File(context.applicationContext.cacheDir, "video_cache")
        try {
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(getMaxCacheSize())
            val databaseProvider = ExoDatabaseProvider(context.applicationContext)
            simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
        } catch (_: Exception) {
            simpleCache = null
        }
    }

    fun getCache(): Cache =
        simpleCache ?: error("VideoCache.initialize(context) must be called first")

    @Synchronized
    fun release() {
        try {
            simpleCache?.release()
        } catch (_: Exception) {
        }
        simpleCache = null
    }

    /** Drops the cache instance so the next [initialize] creates a fresh one. */
    @Synchronized
    fun clearCache() {
        release()
    }
}

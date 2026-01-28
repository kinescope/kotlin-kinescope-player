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
    
    private lateinit var simpleCache: SimpleCache

    fun initialize(context: Context) {
        val cacheDir = File(context.cacheDir, "video_cache")
        if (!::simpleCache.isInitialized) {
            try {
                val maxCacheSize = getMaxCacheSize()
                val cacheEvictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
                val databaseProvider = ExoDatabaseProvider(context)
                simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
            } catch (e: Exception) {
            }
        }
    }

    fun getCache(): Cache = simpleCache

    fun release() {
        if (::simpleCache.isInitialized) {
            simpleCache.release()
        }
    }

    fun clearCache() {
        if (::simpleCache.isInitialized) {
            simpleCache.release()
        }
    }
}






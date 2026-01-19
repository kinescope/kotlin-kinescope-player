package io.kinescope.sdk.shorts.download

import android.content.Context

import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors


@OptIn(UnstableApi::class)
object VideoDownloadManager {
    private const val MAX_CACHE_SIZE = 300L * 1024 * 1024 // 300 MB
    private var downloadManager: DownloadManager? = null
    private var databaseProvider: ExoDatabaseProvider? = null
    private var downloadCache: Cache? = null
    private var downloadIndex: DefaultDownloadIndex? = null
    private var listener: DownloadManager.Listener? = null
    private val listeners = mutableListOf<DownloadManager.Listener>()


    fun initialize(context: Context) {
        if (downloadManager == null) {
            val downloadDirectory = File(context.getExternalFilesDir(null), "downloads")
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs()
            }
            databaseProvider = ExoDatabaseProvider(context)
            downloadCache = SimpleCache(
                downloadDirectory,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE),
                databaseProvider!!
            )
            val upstreamFactory = DefaultDataSource.Factory(context)
            downloadManager = DownloadManager(
                context,
                databaseProvider!!,
                downloadCache!!,
                upstreamFactory,
                Executors.newFixedThreadPool(6)
            )
            downloadIndex = downloadManager!!.downloadIndex as DefaultDownloadIndex
        }

    }
    fun getDownloadManager(context: Context): DownloadManager {
        initialize(context)
        return downloadManager!!
    }


    fun getDownloadCache(context: Context): Cache {
        initialize(context)
        return downloadCache!!
    }

    fun getDownloadIndex(context: Context): DefaultDownloadIndex {
        initialize(context)
        return downloadIndex!!
    }

    fun getDownloadById(contentId: String): Download? {
        return try {
            downloadIndex?.getDownload(contentId)
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "Error getting download by ID: $contentId", e)
            null
        }
    }

    fun getAllCompletedDownloads(context: Context): List<Download> {
        val index = getDownloadIndex(context)
        val downloads = mutableListOf<Download>()
        index.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val download = cursor.download
                if (download.state == Download.STATE_COMPLETED) {
                    downloads.add(download)
                }
            }
        }
        return downloads
    }
    fun getAllDownloadsWithActiveFirst(context: Context): List<Download> {
        val active = getActiveDownloads(context)
        val completed = getAllCompletedDownloads(context)
        return active + completed
    }




    fun getDownloadProgress(download: Download): Pair<Int, Long>{
        val percent = when {
            download.bytesDownloaded > 0 && download.contentLength > 0 -> {
                ((download.bytesDownloaded * 100) / download.contentLength).toInt()

            }
            else -> 0
        }
        return  Pair(percent, download.bytesDownloaded)
    }

    private val downloadSpeeds = mutableMapOf<String, Pair<Long, Long>>()

    fun getRemainingStorage(context: Context): Long{
        val downloadDirectory = File(context.getExternalFilesDir(null),"downloads")
        val availableSpace = downloadDirectory.freeSpace
        val usedSpace = getUsedStorageSpace()
        return MAX_CACHE_SIZE - usedSpace
    }

    fun getUsedStorageSpace(): Long{
        return downloadCache?.cacheSpace ?: 0L
    }

    fun getMaxStorage(): Long {
        return MAX_CACHE_SIZE
    }

    fun getStorageInfo(context: Context): String {
        val used = getUsedStorageSpace()
        val max = getMaxStorage()
        val remaining = getRemainingStorage(context)
        val usedMB = used / (1024 * 1024)
        val maxMB =  max / (1024 *1024)
        val remainingMB = remaining / (1024 * 1024)

        return "Используется: ${usedMB}MB / ${maxMB}MB максимум: ${remainingMB}MB)"
    }


    fun getActiveDownloads(context: Context): List<Download> {
        val index = getDownloadIndex(context)
        val downloads = mutableListOf<Download>()

        index.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val download = cursor.download
                if (download.state == Download.STATE_DOWNLOADING ||
                    download.state == Download.STATE_QUEUED) {
                    downloads.add(download)
                }
            }
        }
        return downloads
    }

    fun addDownloadListener(context: Context, listener: DownloadManager.Listener) {
        initialize(context)
        Log.d("DOWNLOAD_MANAGER", "Adding download listener, total: ${listeners.size}")

        if (!listeners.contains(listener)) {
            listeners.add(listener)
            downloadManager?.addListener(listener)
        }
    }


    fun removeDownloadListener(listener: DownloadManager.Listener) {
        if (listeners.remove(listener)) {
            downloadManager?.removeListener(listener)
            Log.d("DOWNLOAD_MANAGER", "Removed specific listener, remaining: ${listeners.size}")
        }
    }


    fun getUpdatedDownload(downloadId: String): Download? {
        return try {
            downloadIndex?.getDownload(downloadId)
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "Error getting updated download: $downloadId", e)
            null
        }
    }

    fun removeDownload(context: Context, downloadId: String) {
        val manager = getDownloadManager(context)
        manager.removeDownload(downloadId)
    }
}
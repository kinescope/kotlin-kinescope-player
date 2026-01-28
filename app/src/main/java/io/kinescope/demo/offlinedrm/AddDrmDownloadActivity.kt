@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.offlinedrm

import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.demo.R
import io.kinescope.demo.application.KinescopeSDKDemoApplication
import io.kinescope.sdk.api.KinescopeApiHelper
import io.kinescope.sdk.models.videos.KinescopeVideoApi
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.drm.DrmContentProtection
import io.kinescope.sdk.shorts.drm.DrmHelper
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.download.VideoDownloadService
import io.kinescope.sdk.shorts.models.DrmInfo
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.models.WidevineInfo
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.isActive
import kotlin.coroutines.resume
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

@UnstableApi
class AddDrmDownloadActivity : AppCompatActivity() {

    private var recycler: RecyclerView? = null
    private var adapter: Adapter? = null
    private val downloadingIds = mutableSetOf<String>()
    private var currentTempPlayer: ExoPlayer? = null
    private val drmConfigurator = DrmConfigurator(this)
    private lateinit var apiHelper: KinescopeApiHelper
    private lateinit var kinescopeVideoPlayer: KinescopeVideoPlayer
    private var drmTimeoutJob: Job? = null
    private var progressUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_drm_download)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        VideoDownloadManager.initialize(this)
        apiHelper = (application as KinescopeSDKDemoApplication).apiHelper
        kinescopeVideoPlayer = KinescopeVideoPlayer(this.applicationContext)

        recycler = findViewById(R.id.recyclerAddDrm)
        recycler?.layoutManager = LinearLayoutManager(this)
        VideoDownloadManager.addDownloadListener(this, downloadProgressListener)
        startProgressUpdates()
        loadVideosFromApi()
    }
    
    private fun startProgressUpdates() {
        progressUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(500)
                adapter?.notifyProgressUpdate()
            }
        }
    }
    
    private val downloadProgressListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            runOnUiThread {
                // Проверяем, завершена ли загрузка
                if (download.state == Download.STATE_COMPLETED) {
                    // Получаем название видео из данных загрузки
                    try {
                        val videoDataJson = String(download.request.data, StandardCharsets.UTF_8)
                        val videoData = AppJson.decodeFromString(VideoData.serializer(), videoDataJson)
                        Toast.makeText(
                            this@AddDrmDownloadActivity,
                            "Видео \"${videoData.title}\" скачано",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        // Если не удалось получить название, показываем общее сообщение
                        Toast.makeText(
                            this@AddDrmDownloadActivity,
                            "Видео скачано",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                adapter?.notifyItemChangedForDownload(download)
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            runOnUiThread {
                adapter?.notifyItemChangedForDownload(download)
            }
        }
    }

    private fun loadVideosFromApi() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val videoList = mutableListOf<VideoData>()

                apiHelper.getAllVideos().collect { response ->
                    val videos = response.data
                    

                    for (videoApi in videos) {
                        try {
                            val videoData = loadVideoDetails(videoApi.id)
                            if (videoData != null && videoData.hlsLink.isNotBlank()) {
                                videoList.add(videoData)
                            }
                        } catch (e: Exception) {
                        }
                    }

                    adapter = Adapter(videoList, downloadingIds, { videoData -> startDrmDownload(videoData) }, this@AddDrmDownloadActivity)
                    recycler?.adapter = adapter
                    
                    if (videoList.isEmpty()) {
                        Toast.makeText(this@AddDrmDownloadActivity, "Нет видео для скачивания", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddDrmDownloadActivity, "Ошибка загрузки видео", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadVideoDetails(videoId: String): VideoData? {
        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            kinescopeVideoPlayer.loadVideo(videoId,
                onSuccess = { kinescopeVideo ->
                    if (!resumed && kinescopeVideo != null && !kinescopeVideo.hlsLink.isNullOrBlank()) {
                        resumed = true

                        val videoData = VideoData(
                            hlsLink = kinescopeVideo.hlsLink!!,
                            drm = null,
                            title = kinescopeVideo.title,
                            subtitle = kinescopeVideo.subtitle,
                            description = kinescopeVideo.description
                        )
                        continuation.resume(videoData)
                    } else if (!resumed) {
                        resumed = true
                        continuation.resume(null)
                    }
                },
                onFailed = {
                    if (!resumed) {
                        resumed = true
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
    }
    
    override fun onDestroy() {
        drmTimeoutJob?.cancel()
        drmTimeoutJob = null
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        currentTempPlayer?.release()
        currentTempPlayer = null
        VideoDownloadManager.removeDownloadListener(downloadProgressListener)
        super.onDestroy()
    }

    private fun startDrmDownload(videoData: VideoData) {
        val contentId = generateStableContentId(videoData.hlsLink)
        if (currentTempPlayer != null) {
            Toast.makeText(this, "Дождитесь окончания", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = VideoDownloadManager.getDownloadById(contentId)
        if (existing != null && existing.state == Download.STATE_COMPLETED) {
            Toast.makeText(this, "Видео уже скачано", Toast.LENGTH_SHORT).show()
            return
        }
        
        downloadingIds.add(contentId)
        adapter?.notifyDataSetChanged()

        val videoId = generateVideoIdFromUrl(videoData.hlsLink)
        val standardLicenseUrl = "https://license.kinescope.io/v1/vod/$videoId/acquire/widevine?token="
        
        Toast.makeText(this, "Скачиваем... Не покидайте страницу, пока видео не скачается", Toast.LENGTH_LONG).show()

        var psshReceived = false
        val cId = contentId

        val drmHelper = DrmHelper(this, drmConfigurator) { vd, pssh ->
            if (psshReceived) return@DrmHelper
            psshReceived = true
            drmTimeoutJob?.cancel()
            val prot = DrmContentProtection(
                schemeUri = C.WIDEVINE_UUID.toString(),
                licenseUrl = standardLicenseUrl,
                schemeUuid = C.WIDEVINE_UUID
            )
            drmConfigurator.downloadOfflineLicense(
                videoUrl = vd.hlsLink,
                drmContentProtection = prot,
                contentId = cId,
                psshData = pssh
            ) { keySetId ->
                runOnUiThread {
                    currentTempPlayer?.release()
                    currentTempPlayer = null
                    if (keySetId != null) {
                        val videoDataWithDrm = vd.copy(
                            drm = DrmInfo(widevine = WidevineInfo(licenseUrl = standardLicenseUrl))
                        )
                        startOfflineDownload(videoDataWithDrm, keySetId)
                        Toast.makeText(this, "Скачивание с DRM запущено", Toast.LENGTH_SHORT).show()
                    } else {
                        startOfflineDownload(vd, null)
                    }
                    downloadingIds.remove(cId)
                    adapter?.notifyDataSetChanged()
                }
            }
        }

        val player = ExoPlayer.Builder(this).build()
        currentTempPlayer = player

        var errorCount = 0
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorCount++
                if (!psshReceived && errorCount >= 2) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        if (!psshReceived) {
                            psshReceived = true
                            drmTimeoutJob?.cancel()
                            runOnUiThread {
                                currentTempPlayer?.release()
                                currentTempPlayer = null
                                startOfflineDownload(videoData, null)
                                downloadingIds.remove(cId)
                                adapter?.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !psshReceived) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2000)
                        if (psshReceived) return@launch

                        var existingPssh = drmHelper.psshData

                        if (existingPssh == null) {
                            try {
                                player.currentTracks.groups.forEach { group ->
                                    if (group.mediaTrackGroup.length > 0) {
                                        group.mediaTrackGroup.getFormat(0).drmInitData?.let { drmInitData ->
                                            for (i in 0 until drmInitData.schemeDataCount) {
                                                val schemeData = drmInitData.get(i)
                                                if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                                                    existingPssh = schemeData.data

                                                    return@forEach
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        if (existingPssh == null) {
                            try {
                                player.videoFormat?.drmInitData?.let { drmInitData ->
                                    for (i in 0 until drmInitData.schemeDataCount) {
                                        val schemeData = drmInitData.get(i)
                                        if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                                            existingPssh = schemeData.data
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }

                        if (existingPssh != null && !psshReceived) {
                            psshReceived = true
                            drmTimeoutJob?.cancel()
                            val prot = DrmContentProtection(
                                schemeUri = C.WIDEVINE_UUID.toString(),
                                licenseUrl = standardLicenseUrl,
                                schemeUuid = C.WIDEVINE_UUID
                            )
                            drmConfigurator.downloadOfflineLicense(
                                videoUrl = videoData.hlsLink,
                                drmContentProtection = prot,
                                contentId = cId,
                                psshData = existingPssh
                            ) { keySetId ->
                                runOnUiThread {
                                    currentTempPlayer?.release()
                                    currentTempPlayer = null
                                    if (keySetId != null) {
                                        val videoDataWithDrm = videoData.copy(
                                            drm = DrmInfo(widevine = WidevineInfo(licenseUrl = standardLicenseUrl))
                                        )
                                        startOfflineDownload(videoDataWithDrm, keySetId)
                                        Toast.makeText(this@AddDrmDownloadActivity, "Скачивание с DRM запущено", Toast.LENGTH_SHORT).show()
                                    } else {
                                        startOfflineDownload(videoData, null)
                                    }
                                    downloadingIds.remove(cId)
                                    adapter?.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {

                if (!psshReceived) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        if (psshReceived) return@launch

                        var foundPssh: ByteArray? = null
                        try {
                            tracks.groups.forEach { group ->
                                if (group.mediaTrackGroup.length > 0) {
                                    group.mediaTrackGroup.getFormat(0).drmInitData?.let { drmInitData ->
                                        for (i in 0 until drmInitData.schemeDataCount) {
                                            val schemeData = drmInitData.get(i)
                                            if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                                                foundPssh = schemeData.data
                                                return@forEach
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }

                        if (foundPssh != null && !psshReceived) {
                            psshReceived = true
                            drmTimeoutJob?.cancel()
                            val prot = DrmContentProtection(
                                schemeUri = C.WIDEVINE_UUID.toString(),
                                licenseUrl = standardLicenseUrl,
                                schemeUuid = C.WIDEVINE_UUID
                            )
                            drmConfigurator.downloadOfflineLicense(
                                videoUrl = videoData.hlsLink,
                                drmContentProtection = prot,
                                contentId = cId,
                                psshData = foundPssh
                            ) { keySetId ->
                                runOnUiThread {
                                    currentTempPlayer?.release()
                                    currentTempPlayer = null
                                    if (keySetId != null) {
                                        val videoDataWithDrm = videoData.copy(
                                            drm = DrmInfo(widevine = WidevineInfo(licenseUrl = standardLicenseUrl))
                                        )
                                        startOfflineDownload(videoDataWithDrm, keySetId)
                                        Toast.makeText(this@AddDrmDownloadActivity, "Скачивание с DRM запущено", Toast.LENGTH_SHORT).show()
                                    } else {
                                        startOfflineDownload(videoData, null)
                                    }
                                    downloadingIds.remove(cId)
                                    adapter?.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
            }
        })

        drmTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(5000)
            if (!psshReceived) {
                psshReceived = true
                runOnUiThread {
                    currentTempPlayer?.release()
                    currentTempPlayer = null
                    startOfflineDownload(videoData, null)
                    downloadingIds.remove(cId)
                    adapter?.notifyDataSetChanged()
                }
            }
        }

        val testVideoData = videoData.copy(
            drm = DrmInfo(widevine = WidevineInfo(licenseUrl = standardLicenseUrl))
        )
        
        drmHelper.setOfflineDownloadPending(testVideoData)
        val mediaItem = MediaItem.Builder()
            .setUri(videoData.hlsLink)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(standardLicenseUrl)
            .setDrmMultiSession(true)
            .build()
        player.setMediaItem(mediaItem)
        drmHelper.attachToPlayer(player)
        player.prepare()
    }

    private fun startOfflineDownload(videoData: VideoData, keySetId: ByteArray?) {
        if (videoData.hlsLink.isBlank()) return
        val contentId = generateStableContentId(videoData.hlsLink)
        val existing = VideoDownloadManager.getDownloadById(contentId)
        if (existing != null && existing.state == Download.STATE_COMPLETED) return
        try {
            val videoDataJson = try {
                AppJson.encodeToString(VideoData.serializer(), videoData)
            } catch (e: Exception) {
                "{}"
            }
            val reqBuilder = DownloadRequest.Builder(contentId, videoData.hlsLink.toUri())
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setData(videoDataJson.toByteArray(StandardCharsets.UTF_8))
            
            // Добавляем keySetId только если есть DRM
            if (keySetId != null) {
                reqBuilder.setKeySetId(keySetId)
            }
            
            val req = reqBuilder.build()
            VideoDownloadService.startDownload(this.applicationContext, req)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка начала загрузки", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateStableContentId(url: String?): String {
        if (url.isNullOrBlank()) return UUID.randomUUID().toString()
        return try {
            val stable = url.substringBefore("?")
            val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
    
    private fun generateVideoIdFromUrl(url: String): String {
        // Извлекаем video ID из URL, если возможно
        // Формат: https://kinescope.io/{videoId}/master.m3u8
        return try {
            val parts = url.substringBefore("?").split("/")
            parts.getOrNull(parts.size - 2) ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private class Adapter(
        private val items: List<VideoData>,
        private val downloadingIds: Set<String>,
        private val onDownloadClick: (VideoData) -> Unit,
        private val context: android.content.Context
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val drmBadge: TextView = view.findViewById(R.id.drmBadge)
            val btnDownload: Button = view.findViewById(R.id.btnDownload)
            val progressBar: android.widget.ProgressBar = view.findViewById(R.id.progressBar)
            val progressText: TextView = view.findViewById(R.id.progressText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_add_drm_download, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val v = items[position]
            holder.title.text = v.title
            // Показываем DRM badge только если видео действительно имеет DRM
            // Так как мы не знаем заранее, есть ли DRM, не показываем badge
            holder.drmBadge.visibility = View.GONE
            
            val contentId = try {
                val stable = v.hlsLink.substringBefore("?")
                val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
                Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
            } catch (_: Exception) {
                ""
            }
            
            // Проверяем статус скачивания
            val download = VideoDownloadManager.getDownloadById(contentId)
            val isDownloading = contentId in downloadingIds || 
                               (download != null && download.state == Download.STATE_DOWNLOADING)
            val isCompleted = download != null && download.state == Download.STATE_COMPLETED
            
            // Обновляем UI в зависимости от статуса
            if (isDownloading && download != null) {
                // Показываем прогресс скачивания
                val (percent, bytesDownloaded) = VideoDownloadManager.getDownloadProgress(download)
                val totalBytes = download.contentLength
                
                holder.progressBar.visibility = View.VISIBLE
                holder.progressText.visibility = View.VISIBLE
                holder.progressBar.progress = percent
                
                val downloadedMB = bytesDownloaded / (1024 * 1024)
                val totalMB = if (totalBytes > 0) totalBytes / (1024 * 1024) else 0
                
                if (totalMB > 0) {
                    holder.progressText.text = "Скачивается: ${downloadedMB}MB / ${totalMB}MB ($percent%)"
                } else {
                    holder.progressText.text = "Скачивается: ${downloadedMB}MB ($percent%)"
                }
                
                holder.btnDownload.isEnabled = false
                holder.btnDownload.text = "Скачивается..."
            } else if (isCompleted) {
                // Скачивание завершено
                holder.progressBar.visibility = View.GONE
                holder.progressText.visibility = View.GONE
                holder.btnDownload.isEnabled = false
                holder.btnDownload.text = "Скачано"
            } else {
                // Не скачивается
                holder.progressBar.visibility = View.GONE
                holder.progressText.visibility = View.GONE
                holder.btnDownload.isEnabled = true
                holder.btnDownload.text = "Скачать"
            }
            
            holder.btnDownload.setOnClickListener { onDownloadClick(v) }
        }
        
        fun notifyItemChangedForDownload(download: Download) {
            // Находим позицию элемента по contentId
            val contentId = download.request.id
            for (i in items.indices) {
                val item = items[i]
                val itemContentId = try {
                    val stable = item.hlsLink.substringBefore("?")
                    val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
                    Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
                } catch (_: Exception) {
                    ""
                }
                if (itemContentId == contentId) {
                    notifyItemChanged(i)
                    break
                }
            }
        }
        
        fun notifyProgressUpdate() {
            // Обновляем все элементы, которые скачиваются
            for (i in items.indices) {
                val item = items[i]
                val itemContentId = try {
                    val stable = item.hlsLink.substringBefore("?")
                    val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
                    Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
                } catch (_: Exception) {
                    ""
                }
                val download = VideoDownloadManager.getDownloadById(itemContentId)
                if (download != null && download.state == Download.STATE_DOWNLOADING) {
                    notifyItemChanged(i)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}

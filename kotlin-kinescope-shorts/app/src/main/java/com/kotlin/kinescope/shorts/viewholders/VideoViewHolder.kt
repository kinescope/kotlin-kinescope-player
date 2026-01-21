package com.kotlin.kinescope.shorts.viewholders

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.kotlin.kinescope.shorts.R
import com.kotlin.kinescope.shorts.adapters.ViewPager2Adapter
import com.kotlin.kinescope.shorts.databinding.ListVideoBinding
import com.kotlin.kinescope.shorts.drm.DrmConfigurator
import com.kotlin.kinescope.shorts.drm.DrmContentProtection
import com.kotlin.kinescope.shorts.managers.PlayerManager
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.network.InternetConnection
import com.kotlin.kinescope.shorts.view.SeekBarWrap
import com.kotlin.kinescope.shorts.view.SeekPlayerControl
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.recyclerview.widget.LinearLayoutManager
import com.kotlin.kinescope.shorts.AppJson
import com.kotlin.kinescope.shorts.activities.OfflineVideoPlayerActivity
import com.kotlin.kinescope.shorts.activities.OfflineVideoPlayerActivity.Companion.EXTRA_DOWNLOAD_ID
import com.kotlin.kinescope.shorts.activities.OfflineVideoPlayerActivity.Companion.EXTRA_VIDEO_DATA
import com.kotlin.kinescope.shorts.interfaces.ActivityProvider
import com.kotlin.kinescope.shorts.download.VideoDownloadManager
import com.kotlin.kinescope.shorts.download.VideoDownloadService
import com.kotlin.kinescope.shorts.view.DownloadPopupAdapter
import com.kotlin.kinescope.shorts.config.KinescopeUiConfig
import com.kotlin.kinescope.shorts.utils.ThumbnailLoader
import kotlinx.serialization.InternalSerializationApi
import java.security.MessageDigest
import java.util.UUID


@UnstableApi @InternalSerializationApi

@OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoViewHolder(
    val binding: ListVideoBinding,
    private val context: Context,
    private val videoPreparedListener: ViewPager2Adapter.OnVideoPreparedListener,
    val exoPlayer: ExoPlayer,
    private val activityProvider: ActivityProvider? = null
) : RecyclerView.ViewHolder(binding.root) {

    var videoData: VideoData? = null
    private val seekBar: SeekBar =
        binding.playerView.findViewById<SeekBar>(R.id.seekBar).also { KinescopeUiConfig.applyToSeekBar(it) }
    private val seekBarWrap = SeekBarWrap(this)
    private val seekPlayerControl = SeekPlayerControl(null, seekBar)
    private val drmConfigurator = DrmConfigurator(context)
    private val internetConnection = InternetConnection(context)

    private var popupWindow: PopupWindow? = null
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null


    val playerManager = PlayerManager(
        context,
        videoPreparedListener,
        seekPlayerControl,
        seekBarWrap,
        this,

        )
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateDownloadStatus(message: String) {
        showToast(message)
    }
    init {
        binding.playerView.player = exoPlayer
        val layoutParams = binding.root.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        binding.root.layoutParams = layoutParams
        KinescopeUiConfig.applyToVideoItem(binding.root)

        binding.btnOffline.setOnClickListener {
            videoData?.let { data ->
                VideoDownloadManager.initialize(context)
                val contentId = generateStableContentId(data.hlsLink)
                val existingDownload = VideoDownloadManager.getDownloadById(contentId)
                if (existingDownload != null && existingDownload.state == Download.STATE_COMPLETED) {
                    updateDownloadStatus("Видео уже скачено")
                    return@setOnClickListener
                }

                if (!data.drm?.widevine?.licenseUrl.isNullOrBlank()) {
                    updateDownloadStatus("Получение DRM ключей...")

                    val existingLicense = drmConfigurator.loadOfflineLicenseFromStorage(contentId)
                    if (existingLicense != null) {
                        updateDownloadStatus("✅ Ключ получен, начинаем загрузку видео...")
                        startOfflineDownload(data, existingLicense)
                        return@setOnClickListener
                    }

                    val currentPosition = exoPlayer.currentPosition
                    val wasPlaying = exoPlayer.isPlaying

                    playerManager.exposedDrmHelper.callback = { videoData, pssh ->
                        updateDownloadStatus("✅ Ключ получен, начинаем загрузку...")
                        downloadLicense(videoData, pssh) { keySetId ->
                            if (keySetId != null) {
                                val contentId = generateStableContentId(videoData.hlsLink)
                                drmConfigurator.saveOfflineLicenseToStorage(contentId, keySetId)
                                startOfflineDownload(videoData, keySetId)

                                if (wasPlaying && exoPlayer.playbackState != androidx.media3.common.Player.STATE_IDLE) {
                                    exoPlayer.seekTo(currentPosition)
                                    if (wasPlaying) {
                                        exoPlayer.playWhenReady = true
                                    }
                                }
                            } else {
                                updateDownloadStatus("❌ Ошибка загрузки лицензии")
                            }
                        }
                    }

                    var availablePssh: ByteArray? = null

                    availablePssh = playerManager.exposedDrmHelper.psshData

                    if (availablePssh == null) {
                        availablePssh = exoPlayer.videoFormat?.drmInitData?.let { drmInitData ->
                            for (i in 0 until drmInitData.schemeDataCount) {
                                val schemeData = drmInitData.get(i)
                                if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                                    return@let schemeData.data
                                }
                            }
                            null
                        }
                    }

                    if (availablePssh == null) {
                        try {
                            exoPlayer.currentTracks.groups.forEach { group ->
                                if (group.mediaTrackGroup.length > 0) {
                                    group.mediaTrackGroup.getFormat(0).drmInitData?.let { drmInitData ->
                                        for (i in 0 until drmInitData.schemeDataCount) {
                                            val schemeData = drmInitData.get(i)
                                            if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                                                availablePssh = schemeData.data
                                                return@forEach
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }

                    val finalPssh = availablePssh
                    if (finalPssh != null && finalPssh.isNotEmpty()) {
                        playerManager.exposedDrmHelper.psshData = finalPssh
                        playerManager.exposedDrmHelper.callback?.invoke(data, finalPssh)
                    } else {
                        playerManager.exposedDrmHelper.setOfflineDownloadPending(data)
                        playerManager.setupOnlinePlayer(data, absoluteAdapterPosition, binding)
                        playerManager.exoPlayer?.playWhenReady = true
                        Toast.makeText(context, "Получаем ключ...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    updateDownloadStatus("Начинаем загрузку видео...")
                    startClearVideoDownload(data)
                }
            }
        }
        binding.btnShowSavedVideos.setOnClickListener {
            showOfflineVideosPopup()
        }
    }
    fun bindOnlineVideos(videoData: VideoData) {
        binding.playerView.player = null
        exoPlayer.clearVideoSurface()
        this.videoData = videoData
        playerManager.exposedDrmHelper.reset()
        val surfaceView = binding.playerView.videoSurfaceView as? SurfaceView
        surfaceView?.holder?.setFixedSize(1, 1)

        showThumbnail()

        updateDownloadProgressForVideo(videoData)

        playerManager.exoPlayer = exoPlayer
        playerManager.setupOnlinePlayer(videoData, absoluteAdapterPosition, binding)
    }
    
    fun showThumbnail() {
        binding.thumbnailView.visibility = android.view.View.VISIBLE
        val placeholder = ThumbnailLoader.createPlaceholder()
        binding.thumbnailView.setImageBitmap(placeholder)
    }
    
    fun hideThumbnail() {
        binding.thumbnailView.visibility = android.view.View.GONE
    }
    
    private fun updateDownloadProgressForVideo(videoData: VideoData) {
        binding.downloadProgress.visibility = android.view.View.GONE
        stopProgressUpdates()
    }
    
    private fun startProgressUpdates(contentId: String) {
        stopProgressUpdates()

        progressUpdateRunnable = object : Runnable {
            override fun run() {
                videoData?.let { data ->
                    val currentContentId = generateStableContentId(data.hlsLink)
                    if (currentContentId == contentId) {
                        val download = VideoDownloadManager.getDownloadById(contentId)
                        if (download != null && 
                            (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED)) {
                            val (percent, _) = VideoDownloadManager.getDownloadProgress(download)
                            binding.downloadProgress.progress = percent
                            progressUpdateHandler.postDelayed(this, 1000)
                        } else {
                            binding.downloadProgress.visibility = android.view.View.GONE
                            stopProgressUpdates()
                        }
                    } else {
                        stopProgressUpdates()
                    }
                } ?: run {
                    stopProgressUpdates()
                }
            }
        }
        progressUpdateHandler.post(progressUpdateRunnable!!)
    }
    
    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let {
            progressUpdateHandler.removeCallbacks(it)
            progressUpdateRunnable = null
        }
    }
    
    fun onDetached() {
        stopProgressUpdates()
    }

    fun startOfflineLicenseDownload(
        videoData: VideoData,
        psshData: ByteArray?,
        callback: (ByteArray?) -> Unit
    ) {
        if(psshData !=null && psshData.isNotEmpty()){
            downloadLicense(videoData, psshData, callback)
            return
        }
        val tempCallback: (VideoData, ByteArray) -> Unit = { videoData, pssh ->
            downloadLicense(videoData, pssh, callback)
        }
        val originalCallback = playerManager.exposedDrmHelper.callback
        playerManager.exposedDrmHelper.callback = tempCallback
        playerManager.exposedDrmHelper.setOfflineDownloadPending(videoData)
        Handler(Looper.getMainLooper()).postDelayed({
            playerManager.exposedDrmHelper.callback = originalCallback
        }, 1000)
    }

    private fun downloadLicense(videoData: VideoData, psshData: ByteArray, callback: (ByteArray?) -> Unit) {
        val contentId = generateStableContentId(videoData.hlsLink)
        val drmData = DrmContentProtection(
            schemeUri = C.WIDEVINE_UUID.toString(),
            licenseUrl = videoData.drm?.widevine?.licenseUrl,
            schemeUuid = C.WIDEVINE_UUID
        )
        drmConfigurator.downloadOfflineLicense(
            videoUrl = videoData.hlsLink,
            drmContentProtection = drmData,
            contentId = contentId,
            psshData = psshData
        ) { keySetId ->
            (context as? Activity)?.runOnUiThread {
                if (keySetId != null) {
                    drmConfigurator.saveOfflineLicenseToStorage(contentId, keySetId)
                    startOfflineDownload(videoData, keySetId)
                } else {
                    Toast.makeText(context, "Ошибка загрузки лицензии", Toast.LENGTH_SHORT).show()
                }
                callback(keySetId)
            }
        }
    }

    fun startOfflineDownload(videoData: VideoData, keySetId: ByteArray) {
        val contentId = generateStableContentId(videoData.hlsLink)
        val existingDownload = VideoDownloadManager.getDownloadById(contentId)
        if (existingDownload != null && existingDownload.state == Download.STATE_COMPLETED) {
            Toast.makeText(context, "Видео уже скачено", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = videoData.hlsLink.toUri()
            val videoDataJson = try {
                AppJson.encodeToString(VideoData.serializer(), videoData)
            } catch (e: Exception) {
                "{}"
            }

            val builder = DownloadRequest.Builder(contentId, uri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setData(videoDataJson.toByteArray(Charsets.UTF_8))
                .setKeySetId(keySetId)

            val downloadRequest = builder.build()
            VideoDownloadService.startDownload(context, downloadRequest)

            Handler(Looper.getMainLooper()).postDelayed({
                updateDownloadProgressForVideo(videoData)
            }, 500)
        } catch (e: Exception) {
        }
    }
    private fun startClearVideoDownload(videoData: VideoData) {
        val contentId = generateStableContentId(videoData.hlsLink)
        val existingDownload = VideoDownloadManager.getDownloadById(contentId)

        if (existingDownload != null && existingDownload.state == Download.STATE_COMPLETED) {
            Toast.makeText(context, "Видео уже скачено", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = videoData.hlsLink.toUri()
            val downloadRequest = DownloadRequest.Builder(contentId, uri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            VideoDownloadService.startDownload(context, downloadRequest)

            Handler(Looper.getMainLooper()).postDelayed({
                updateDownloadProgressForVideo(videoData)
            }, 500)

        } catch (e: Exception) {
        }
    }

    fun bindOfflineVideos(videoData: VideoData, download: Download?) {
        this.videoData = videoData
        showThumbnail()
        playerManager.exoPlayer = exoPlayer
        playerManager.setupOfflinePlayer(videoData, absoluteAdapterPosition, binding, download)
    }

    fun generateStableContentId(url: String): String {
        return try {
            val stablePart = url.substringBefore("?")
            val digest = MessageDigest.getInstance("SHA-256").digest(stablePart.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun showOfflineVideosPopup() {
        VideoDownloadManager.initialize(context)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_offline_videos, null)
        val recyclerView = popupView.findViewById<RecyclerView>(R.id.recyclerOffline)
        val closeButton = popupView.findViewById<Button>(R.id.btnClose)

        val allDownloads = VideoDownloadManager.getAllDownloadsWithActiveFirst(context).toMutableList()
        val adapter = DownloadPopupAdapter(context, allDownloads)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.startProgressUpdates()

        adapter.setOnItemClicked { download ->
            if (download.state == Download.STATE_COMPLETED) {
                activityProvider?.pauseCurrentPlayer()

                debugDownloadData(download)
                val videoDataJson = try {
                    String(download.request.data, Charsets.UTF_8)
                } catch (e: Exception) {
                    "{}"
                }

                val videoData = try {
                    AppJson.decodeFromString(VideoData.serializer(), videoDataJson)
                } catch (e: Exception) {
                    VideoData(
                        hlsLink = download.request.uri.toString(),
                        drm = null,
                        title = "Загруженное видео"
                    )
                }

                val intent = activityProvider?.getOfflinePlayerActivityIntent(videoData, download.request.id)
                    ?: Intent(context, OfflineVideoPlayerActivity::class.java).apply {
                        putExtra(EXTRA_VIDEO_DATA, videoData)
                        putExtra(EXTRA_DOWNLOAD_ID, download.request.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                if (context is Activity) {
                    context.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }

            } else if (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED) {
                val (percent, bytesDownloaded) = VideoDownloadManager.getDownloadProgress(download)
                val totalSize = download.contentLength
                val downloadedMB = bytesDownloaded / (1024 * 1024)
                val totalMB = if (totalSize > 0) totalSize / (1024 * 1024) else 0

                Toast.makeText(
                    context,
                    "Загрузка: $percent% ($downloadedMB MB / $totalMB MB)",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Статус: ${getDownloadStateText(download.state)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


        adapter.setOnDeleteClicked { download ->
            VideoDownloadManager.removeDownload(context, download.request.id)
            Toast.makeText(context, "Видео удалено", Toast.LENGTH_SHORT).show()
        }

        val downloadListener = object : DownloadManager.Listener {
            private val handler = Handler(Looper.getMainLooper())

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                handler.post {
                    val updatedDownloads = VideoDownloadManager.getAllDownloadsWithActiveFirst(context)
                    adapter.updateDownloadsList(updatedDownloads)

                    videoData?.let { data ->
                        val contentId = generateStableContentId(data.hlsLink)
                        if (download.request.id == contentId) {
                            updateDownloadProgressForVideo(data)
                        }
                    }
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                handler.post {
                    val updatedDownloads = VideoDownloadManager.getAllDownloadsWithActiveFirst(context)
                    adapter.updateDownloadsList(updatedDownloads)

                    videoData?.let { data ->
                        val contentId = generateStableContentId(data.hlsLink)
                        if (download.request.id == contentId) {
                            updateDownloadProgressForVideo(data)
                        }
                    }
                }
            }
        }
        VideoDownloadManager.addDownloadListener(context, downloadListener)
        popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            animationStyle = R.style.PopupAnimation
        }

        popupWindow?.showAtLocation(binding.root, Gravity.END, 0, 0)

        popupWindow?.setOnDismissListener {
            adapter.stopProgressUpdates()
            VideoDownloadManager.removeDownloadListener(downloadListener)
            popupWindow = null
        }

        closeButton.setOnClickListener {
            popupWindow?.dismiss()
        }
    }
    private fun debugDownloadData(download: Download) {
    }

    private fun getDownloadStateText(state: Int): String {
        return when (state) {
            Download.STATE_QUEUED -> "В очереди"
            Download.STATE_DOWNLOADING -> "Скачивается"
            Download.STATE_COMPLETED -> "Завершено"
            Download.STATE_FAILED -> "Ошибка"
            Download.STATE_STOPPED -> "Остановлено"
            else -> "Неизвестно"
        }
    }
}
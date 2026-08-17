@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.offlinedrm

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.demo.R
import io.kinescope.demo.application.KinescopeSDKDemoApplication
import io.kinescope.sdk.api.KinescopeApiHelper
import io.kinescope.sdk.download.DownloadVideoOffline
import io.kinescope.sdk.player.KinescopeSecureDecoderWorkaround
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.download.OfflineDownloadQualityHelper
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.drm.DrmContentProtection
import io.kinescope.sdk.shorts.drm.DrmHelper
import io.kinescope.sdk.shorts.models.DrmInfo
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.models.VideoQualityMapEntry
import io.kinescope.sdk.shorts.models.WidevineInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume

@UnstableApi
class AddDrmDownloadActivity : AppCompatActivity() {

    private var recycler: RecyclerView? = null
    private var adapter: Adapter? = null
    /** contentId → HLS URL key (without query); kept until COMPLETED / FAILED / removed. */
    private val downloadingIds = mutableMapOf<String, String>()
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
            onBackPressedDispatcher.onBackPressed()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    startActivity(
                        Intent(this@AddDrmDownloadActivity, OfflineDrmDemoActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            )
                        },
                    )
                    finish()
                }
            },
        )

        VideoDownloadManager.initialize(this)
        DownloadVideoOffline.initialize(this)
        apiHelper = (application as KinescopeSDKDemoApplication).apiHelper
        kinescopeVideoPlayer = KinescopeVideoPlayer(applicationContext)

        findViewById<Button>(R.id.btnDownloadById).setOnClickListener {
            val videoId = findViewById<EditText>(R.id.etVideoId).text?.toString()?.trim().orEmpty()
            if (videoId.isEmpty()) {
                Toast.makeText(this, "Введите Video ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            downloadByVideoId(videoId)
        }

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
            finalException: Exception?,
        ) {
            runOnUiThread {
                when (download.state) {
                    Download.STATE_COMPLETED -> {
                        downloadingIds.remove(download.request.id)
                        try {
                            val videoDataJson = String(download.request.data, StandardCharsets.UTF_8)
                            val videoData = AppJson.decodeFromString(VideoData.serializer(), videoDataJson)
                            val quality = videoData.selectedQualityLabel?.let { " ($it)" }.orEmpty()
                            Toast.makeText(
                                this@AddDrmDownloadActivity,
                                "Видео \"${videoData.title}\"$quality скачано",
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (_: Exception) {
                            Toast.makeText(this@AddDrmDownloadActivity, "Видео скачано", Toast.LENGTH_LONG).show()
                        }
                    }
                    Download.STATE_FAILED -> {
                        downloadingIds.remove(download.request.id)
                        Toast.makeText(
                            this@AddDrmDownloadActivity,
                            "Ошибка скачивания",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                adapter?.notifyItemChangedForDownload(download)
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            runOnUiThread {
                downloadingIds.remove(download.request.id)
                adapter?.notifyItemChangedForDownload(download)
            }
        }
    }

    private fun loadVideosFromApi() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val videoList = mutableListOf<VideoData>()
                apiHelper.getAllVideos().collect { response ->
                    for (videoApi in response.data) {
                        try {
                            val videoData = loadVideoDetails(videoApi.id)
                            if (videoData != null && videoData.hlsLink.isNotBlank()) {
                                videoList.add(videoData)
                            }
                        } catch (_: Exception) {
                        }
                    }
                    adapter = Adapter(
                        videoList,
                        downloadingIds,
                        { videoData -> promptQualityAndDownload(videoData) },
                        this@AddDrmDownloadActivity,
                    )
                    recycler?.adapter = adapter
                    if (videoList.isEmpty()) {
                        Toast.makeText(this@AddDrmDownloadActivity, "Нет видео для скачивания", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this@AddDrmDownloadActivity, "Ошибка загрузки видео", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadByVideoId(videoId: String) {
        Toast.makeText(this, "Загрузка метаданных…", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val videoData = loadVideoDetails(videoId)
            if (videoData == null || videoData.hlsLink.isBlank()) {
                Toast.makeText(this@AddDrmDownloadActivity, "Не удалось загрузить видео", Toast.LENGTH_SHORT).show()
                return@launch
            }
            promptQualityAndDownload(videoData)
        }
    }

    private suspend fun loadVideoDetails(videoId: String): VideoData? {
        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            kinescopeVideoPlayer.loadVideo(
                videoId,
                onSuccess = { kinescopeVideo ->
                    if (resumed) return@loadVideo
                    resumed = true
                    if (kinescopeVideo == null || kinescopeVideo.hlsLink.isNullOrBlank()) {
                        continuation.resume(null)
                        return@loadVideo
                    }
                    // API catalog videos are usually clear; only attach DRM when embed JSON has it.
                    val licenseFromApi = kinescopeVideo.drm?.widevine?.licenseUrl
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { url ->
                            when {
                                url.endsWith("token=") -> url + KinescopeDemoConfig.API_KEY
                                url.contains("token=") -> url
                                else -> url
                            }
                        }
                    val qualityMap = kinescopeVideo.qualityMap?.map { entry ->
                        VideoQualityMapEntry(
                            label = entry.label,
                            name = entry.name,
                            height = entry.height,
                        )
                    }
                    continuation.resume(
                        VideoData(
                            hlsLink = kinescopeVideo.hlsLink!!,
                            drm = licenseFromApi?.let { DrmInfo(widevine = WidevineInfo(licenseUrl = it)) },
                            title = kinescopeVideo.title,
                            subtitle = kinescopeVideo.subtitle,
                            description = kinescopeVideo.description,
                            videoId = kinescopeVideo.id,
                            qualityMap = qualityMap,
                            posterUrl = kinescopeVideo.poster?.url,
                        ),
                    )
                },
                onFailed = {
                    if (!resumed) {
                        resumed = true
                        continuation.resume(null)
                    }
                },
            )
        }
    }

    private fun promptQualityAndDownload(videoData: VideoData) {
        if (currentTempPlayer != null) {
            Toast.makeText(this, "Дождитесь окончания", Toast.LENGTH_SHORT).show()
            return
        }
        val hints = videoData.qualityMap?.map { entry ->
            OfflineDownloadQualityHelper.QualityMapHint(
                height = entry.height,
                name = entry.name,
                label = entry.label,
            )
        }
        val licenseUrl = videoData.drm?.widevine?.licenseUrl?.takeIf { it.isNotBlank() }

        // DRM: quality_map from embed JSON is enough for the picker (manifest often has no
        // clear tracks until a Widevine session is open).
        val fromMap = OfflineDownloadQualityHelper.qualitiesFromQualityMap(hints)
        if (!licenseUrl.isNullOrBlank() && fromMap.isNotEmpty()) {
            showQualityPicker(videoData, fromMap)
            return
        }

        Toast.makeText(this, "Читаем качества…", Toast.LENGTH_SHORT).show()
        DownloadVideoOffline.listDownloadQualities(
            context = this,
            manifestUri = videoData.hlsLink,
            qualityMap = hints,
            drmLicenseUrl = licenseUrl,
        ) { result ->
            result.onSuccess { qualities ->
                if (qualities.isEmpty()) {
                    Toast.makeText(this, "Нет доступных качеств", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                showQualityPicker(videoData, qualities)
            }.onFailure {
                if (fromMap.isNotEmpty()) {
                    showQualityPicker(videoData, fromMap)
                } else {
                    Toast.makeText(this, "Ошибка чтения качеств", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showQualityPicker(
        videoData: VideoData,
        qualities: List<OfflineDownloadQualityHelper.QualityOption>,
    ) {
        val labels = qualities.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Качество: ${videoData.title}")
            .setItems(labels) { _, which ->
                val selected = qualities[which]
                val withLabel = videoData.copy(selectedQualityLabel = selected.label)
                if (withLabel.drm?.widevine?.licenseUrl.isNullOrBlank()) {
                    startClearDownload(withLabel, selected.height, selected.width)
                } else {
                    startDrmDownload(withLabel, selected.height, selected.width)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        drmTimeoutJob?.cancel()
        drmTimeoutJob = null
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        releaseTempPlayer()
        VideoDownloadManager.removeDownloadListener(downloadProgressListener)
        super.onDestroy()
    }

    private fun releaseTempPlayer() {
        val player = currentTempPlayer ?: return
        currentTempPlayer = null
        try {
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) {
        }
        try {
            player.release()
        } catch (_: Exception) {
        }
    }

    private fun startClearDownload(videoData: VideoData, videoHeightPx: Int, videoWidthPx: Int) {
        val contentId = generateStableContentId(videoData.hlsLink, videoHeightPx)
        if (currentTempPlayer != null) {
            Toast.makeText(this, "Дождитесь окончания", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = VideoDownloadManager.getDownloadById(contentId)
        if (existing != null && existing.state == Download.STATE_COMPLETED) {
            Toast.makeText(
                this,
                "Видео уже скачано (${videoData.selectedQualityLabel ?: "${videoHeightPx}p"})",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        downloadingIds[contentId] = hlsKey(videoData.hlsLink)
        adapter?.notifyDataSetChanged()
        Toast.makeText(this, "Скачиваем ${videoData.selectedQualityLabel ?: ""}…", Toast.LENGTH_SHORT).show()
        startOfflineDownload(videoData, null, videoHeightPx, videoWidthPx)
    }

    private fun startDrmDownload(videoData: VideoData, videoHeightPx: Int, videoWidthPx: Int = C.LENGTH_UNSET) {
        val contentId = generateStableContentId(videoData.hlsLink, videoHeightPx)
        if (currentTempPlayer != null) {
            Toast.makeText(this, "Дождитесь окончания", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = VideoDownloadManager.getDownloadById(contentId)
        if (existing != null && existing.state == Download.STATE_COMPLETED) {
            Toast.makeText(this, "Видео уже скачано (${videoData.selectedQualityLabel ?: "${videoHeightPx}p"})", Toast.LENGTH_SHORT).show()
            return
        }

        downloadingIds[contentId] = hlsKey(videoData.hlsLink)
        adapter?.notifyDataSetChanged()

        val licenseUrl = videoData.drm?.widevine?.licenseUrl?.takeIf { it.isNotBlank() }
        if (licenseUrl.isNullOrBlank()) {
            startClearDownload(videoData, videoHeightPx, videoWidthPx)
            return
        }

        Toast.makeText(this, "Скачиваем… Не покидайте страницу", Toast.LENGTH_LONG).show()

        var psshReceived = false
        val cId = contentId

        fun finishWithKey(keySetId: ByteArray?) {
            releaseTempPlayer()
            if (keySetId == null) {
                downloadingIds.remove(cId)
                adapter?.notifyDataSetChanged()
                Toast.makeText(
                    this,
                    "Не удалось получить DRM-ключ. Скачивание отменено.",
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            val withDrm = videoData.copy(
                drm = DrmInfo(widevine = WidevineInfo(licenseUrl = licenseUrl)),
            )
            startOfflineDownload(withDrm, keySetId, videoHeightPx, videoWidthPx)
            Toast.makeText(
                this,
                "Скачивание с DRM запущено (${videoData.selectedQualityLabel})",
                Toast.LENGTH_SHORT,
            ).show()
        }

        fun finishDrmFailed(message: String) {
            if (psshReceived) return
            psshReceived = true
            drmTimeoutJob?.cancel()
            runOnUiThread {
                releaseTempPlayer()
                downloadingIds.remove(cId)
                adapter?.notifyDataSetChanged()
                Toast.makeText(this@AddDrmDownloadActivity, message, Toast.LENGTH_LONG).show()
            }
        }

        val drmHelper = DrmHelper(this, drmConfigurator) { vd, pssh ->
            if (psshReceived) return@DrmHelper
            psshReceived = true
            drmTimeoutJob?.cancel()
            // Free secure codec before offline license / download continues.
            releaseTempPlayer()
            val prot = DrmContentProtection(
                schemeUri = C.WIDEVINE_UUID.toString(),
                licenseUrl = licenseUrl,
                schemeUuid = C.WIDEVINE_UUID,
            )
            drmConfigurator.downloadOfflineLicense(
                videoUrl = vd.hlsLink,
                drmContentProtection = prot,
                contentId = cId,
                psshData = pssh,
            ) { keySetId ->
                runOnUiThread { finishWithKey(keySetId) }
            }
        }

        // Prefer Shorts-style secure-decoder workaround: probe player often leaves
        // c2.qti.avc.decoder.secure held, so later offline playback fails to re-init.
        val renderersFactory = KinescopeSecureDecoderWorkaround.applyTo(
            DefaultRenderersFactory(this),
        )
        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .build()
        currentTempPlayer = player

        fun tryExtractPssh(tracks: Tracks? = null): ByteArray? {
            drmHelper.psshData?.let { return it }
            try {
                val groups = tracks?.groups ?: player.currentTracks.groups
                groups.forEach { group ->
                    if (group.mediaTrackGroup.length > 0) {
                        group.mediaTrackGroup.getFormat(0).drmInitData?.let { drmInitData ->
                            for (i in 0 until drmInitData.schemeDataCount) {
                                val schemeData = drmInitData.get(i)
                                if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                                    return schemeData.data
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            try {
                player.videoFormat?.drmInitData?.let { drmInitData ->
                    for (i in 0 until drmInitData.schemeDataCount) {
                        val schemeData = drmInitData.get(i)
                        if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                            return schemeData.data
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return null
        }

        fun acquireLicense(pssh: ByteArray) {
            if (psshReceived) return
            psshReceived = true
            drmTimeoutJob?.cancel()
            // Free secure codec before offline license / download continues.
            releaseTempPlayer()
            val prot = DrmContentProtection(
                schemeUri = C.WIDEVINE_UUID.toString(),
                licenseUrl = licenseUrl,
                schemeUuid = C.WIDEVINE_UUID,
            )
            drmConfigurator.downloadOfflineLicense(
                videoUrl = videoData.hlsLink,
                drmContentProtection = prot,
                contentId = cId,
                psshData = pssh,
            ) { keySetId ->
                runOnUiThread { finishWithKey(keySetId) }
            }
        }

        var errorCount = 0
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorCount++
                if (!psshReceived && errorCount >= 2) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        finishDrmFailed("Ошибка DRM при получении ключа")
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !psshReceived) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2000)
                        if (psshReceived) return@launch
                        tryExtractPssh()?.let { acquireLicense(it) }
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (psshReceived) return
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    if (psshReceived) return@launch
                    tryExtractPssh(tracks)?.let { acquireLicense(it) }
                }
            }
        })

        drmTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(15000)
            finishDrmFailed("Таймаут DRM-ключа. Попробуйте ещё раз.")
        }

        val testVideoData = videoData.copy(
            drm = DrmInfo(widevine = WidevineInfo(licenseUrl = licenseUrl)),
        )
        drmHelper.setOfflineDownloadPending(testVideoData)
        val mediaItem = MediaItem.Builder()
            .setUri(videoData.hlsLink)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(licenseUrl)
            .setDrmMultiSession(true)
            .build()
        player.setMediaItem(mediaItem)
        drmHelper.attachToPlayer(player)
        player.prepare()
    }

    private fun startOfflineDownload(
        videoData: VideoData,
        keySetId: ByteArray?,
        videoHeightPx: Int,
        videoWidthPx: Int = C.LENGTH_UNSET,
    ) {
        if (videoData.hlsLink.isBlank()) {
            return
        }
        val contentId = generateStableContentId(videoData.hlsLink, videoHeightPx)
        val existing = VideoDownloadManager.getDownloadById(contentId)
        if (existing != null && existing.state == Download.STATE_COMPLETED) {
            downloadingIds.remove(contentId)
            adapter?.notifyDataSetChanged()
            return
        }
        downloadingIds[contentId] = hlsKey(videoData.hlsLink)
        val videoDataJson = try {
            AppJson.encodeToString(VideoData.serializer(), videoData)
        } catch (_: Exception) {
            "{}"
        }
        DownloadVideoOffline.startDownloadWithQuality(
            context = this,
            contentId = contentId,
            manifestUri = videoData.hlsLink,
            videoHeightPx = videoHeightPx,
            videoWidthPx = videoWidthPx,
            mimeType = MimeTypes.APPLICATION_M3U8,
            data = videoDataJson.toByteArray(StandardCharsets.UTF_8),
            keySetId = keySetId,
            drmLicenseUrl = videoData.drm?.widevine?.licenseUrl,
            qualityHint = videoData.selectedQualityLabel,
            onError = {
                downloadingIds.remove(contentId)
                adapter?.notifyDataSetChanged()
                Toast.makeText(this, "Ошибка начала загрузки", Toast.LENGTH_SHORT).show()
            },
            onStarted = {
                adapter?.notifyDataSetChanged()
            },
        )
    }

    private fun hlsKey(url: String): String = url.substringBefore("?")

    private fun generateStableContentId(url: String?, heightPx: Int): String {
        if (url.isNullOrBlank()) return UUID.randomUUID().toString()
        return try {
            val stable = "${url.substringBefore("?")}#$heightPx"
            val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun generateVideoIdFromUrl(url: String): String {
        return try {
            val parts = url.substringBefore("?").split("/")
            parts.getOrNull(parts.size - 2) ?: UUID.randomUUID().toString()
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private class Adapter(
        private val items: List<VideoData>,
        private val downloadingIds: Map<String, String>,
        private val onDownloadClick: (VideoData) -> Unit,
        private val context: android.content.Context,
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
            holder.drmBadge.visibility =
                if (v.drm?.widevine?.licenseUrl.isNullOrBlank()) View.GONE else View.VISIBLE

            val videoHlsKey = v.hlsLink.substringBefore("?")
            val trackedContentIds = downloadingIds.filterValues { it == videoHlsKey }.keys
            val isTracked = trackedContentIds.isNotEmpty()
            val activeDownload = trackedContentIds
                .asSequence()
                .mapNotNull { id -> VideoDownloadManager.getDownloadById(id) }
                .firstOrNull { VideoDownloadManager.isActiveState(it.state) }
                ?: VideoDownloadManager.getActiveDownloads(context)
                    .firstOrNull { downloadMatchesVideo(it, v) }
            val anyDownloading = isTracked || activeDownload != null

            val completed = !anyDownloading &&
                VideoDownloadManager.getAllCompletedDownloads(context).any { download ->
                    downloadMatchesVideo(download, v)
                }

            when {
                anyDownloading -> {
                    holder.progressBar.visibility = View.VISIBLE
                    holder.progressText.visibility = View.VISIBLE
                    if (activeDownload != null) {
                        val (percent, bytesDownloaded) = VideoDownloadManager.getDownloadProgress(activeDownload)
                        val indeterminate = percent <= 0 && bytesDownloaded <= 0 ||
                            activeDownload.state == Download.STATE_QUEUED ||
                            activeDownload.state == Download.STATE_RESTARTING
                        holder.progressBar.isIndeterminate = indeterminate
                        if (!indeterminate) {
                            holder.progressBar.progress = percent.coerceIn(0, 100)
                        }
                        holder.progressText.text =
                            VideoDownloadManager.formatDownloadProgressLabel(activeDownload)
                    } else {
                        holder.progressBar.isIndeterminate = true
                        holder.progressText.text = "Скачивается…"
                    }
                    holder.btnDownload.isEnabled = false
                    holder.btnDownload.text = "Скачивается…"
                }
                completed -> {
                    holder.progressBar.isIndeterminate = false
                    holder.progressBar.visibility = View.GONE
                    holder.progressText.visibility = View.GONE
                    holder.btnDownload.isEnabled = true
                    holder.btnDownload.text = "Скачать ещё"
                }
                else -> {
                    holder.progressBar.isIndeterminate = false
                    holder.progressBar.visibility = View.GONE
                    holder.progressText.visibility = View.GONE
                    holder.btnDownload.isEnabled = true
                    holder.btnDownload.text = "Скачать"
                }
            }

            holder.btnDownload.setOnClickListener { onDownloadClick(v) }
        }

        fun notifyItemChangedForDownload(download: Download) {
            for (i in items.indices) {
                if (downloadMatchesVideo(download, items[i])) {
                    notifyItemChanged(i)
                    break
                }
            }
        }

        fun notifyProgressUpdate() {
            for (i in items.indices) {
                val video = items[i]
                val videoHlsKey = video.hlsLink.substringBefore("?")
                val tracked = downloadingIds.values.any { it == videoHlsKey }
                val active = VideoDownloadManager.getActiveDownloads(context)
                    .any { downloadMatchesVideo(it, video) } ||
                    downloadingIds.keys.any { id ->
                        VideoDownloadManager.getDownloadById(id)
                            ?.takeIf { VideoDownloadManager.isActiveState(it.state) }
                            ?.let { downloadMatchesVideo(it, video) } == true
                    }
                if (tracked || active) {
                    notifyItemChanged(i)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        private fun downloadMatchesVideo(download: Download, video: VideoData): Boolean {
            return try {
                val json = String(download.request.data, StandardCharsets.UTF_8)
                val data = AppJson.decodeFromString(VideoData.serializer(), json)
                data.hlsLink.substringBefore("?") == video.hlsLink.substringBefore("?")
            } catch (_: Exception) {
                false
            }
        }
    }
}

@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.offlinedrm

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import io.kinescope.demo.R
import io.kinescope.demo.databinding.ActivityOfflineMainPlayerBinding
import io.kinescope.sdk.player.KinescopeContentOrientationController
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.view.KinescopePlayerView
import java.security.MessageDigest
import java.util.UUID

@UnstableApi
class OfflineMainPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineMainPlayerBinding
    private lateinit var kinescopeVideoPlayer: KinescopeVideoPlayer
    private lateinit var orientationController: KinescopeContentOrientationController
    private val drmConfigurator = DrmConfigurator(this)
    private var isVideoFullscreen = false
    private var offlineVideoData: VideoData? = null
    private var playerReleased = false

    companion object {
        private const val TAG = "OfflineMainPlayer"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_VIDEO_DATA_JSON = "video_data_json"
        /** When set, back navigates here instead of relying on a possibly destroyed back stack. */
        const val EXTRA_UP_NAVIGATION = "up_navigation"
        const val UP_NAV_OFFLINE_DRM_LIST = "offline_drm_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineMainPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoDataJson = intent.getStringExtra(EXTRA_VIDEO_DATA_JSON)
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)

        if (videoDataJson == null || downloadId == null) {
            Toast.makeText(this, "Не хватает данных", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val videoData = try {
            AppJson.decodeFromString(VideoData.serializer(), videoDataJson)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки данных видео", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        offlineVideoData = videoData

        val download = VideoDownloadManager.getDownloadIndex(this).getDownload(downloadId)
        if (download?.state != Download.STATE_COMPLETED) {
            Toast.makeText(this, "Видео не загружено", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.findViewById<TextView>(R.id.TitleVideo).text = buildString {
            append(videoData.title)
            videoData.selectedQualityLabel?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
        binding.toolbar.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            navigateUpFromPlayer()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateUpFromPlayer()
                }
            },
        )

        kinescopeVideoPlayer = KinescopeVideoPlayer(applicationContext)
        binding.playerView.setPlayer(kinescopeVideoPlayer)
        binding.playerView.setIsFullscreen(false)
        binding.playerView.onFullscreenButtonCallback = { toggleFullscreen() }

        binding.playerViewFullscreen.setIsFullscreen(true)
        binding.playerViewFullscreen.onFullscreenButtonCallback = { toggleFullscreen() }
        orientationController = KinescopeContentOrientationController(
            activity = this,
            playerViews = { listOf(binding.playerView, binding.playerViewFullscreen) },
        )
        orientationController.attach()

        binding.playerView.post {
            try {
                setupOfflinePlayer(videoData, download)
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка инициализации плеера", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupOfflinePlayer(videoData: VideoData, download: Download) {
        val player = kinescopeVideoPlayer.exoPlayer
            ?: throw IllegalStateException("ExoPlayer is not initialized")

        val contentIdWithHeight = generateContentId(videoData.hlsLink, qualityHeightHint(videoData))
        val contentIdPlain = generateContentId(videoData.hlsLink)
        val keySetId = download.request.keySetId
            ?: drmConfigurator.loadOfflineLicenseFromStorage(contentIdWithHeight)
            ?: drmConfigurator.loadOfflineLicenseFromStorage(contentIdPlain)

        val licenseUrl = videoData.drm?.widevine?.licenseUrl?.takeIf { it.isNotBlank() }
        val needsDrm = !licenseUrl.isNullOrBlank() || download.request.keySetId != null
        if (needsDrm && keySetId == null) {
            Toast.makeText(
                this,
                "Нет DRM-ключа для офлайн-воспроизведения. Скачайте видео заново.",
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return
        }

        // Offline-only: never ignore cache errors — FLAG_IGNORE_CACHE_ON_ERROR would
        // try the (null) upstream and surface Source error when spans are missing.
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoDownloadManager.getDownloadCache(this))
            .setUpstreamDataSourceFactory(null)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())

        applyQualityLabels(videoData, trackHeight = null)

        // Lock settings quality to the downloaded label before tracks arrive so the
        // gear icon does not flicker HD from a transient Auto/videoSize reading.
        val qualityHint = qualityHeightHint(videoData)
        if (qualityHint > 0) {
            binding.playerView.setVideoQualityVariant(qualityHint)
            if (binding.playerViewFullscreen.isVisible) {
                binding.playerViewFullscreen.setVideoQualityVariant(qualityHint)
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@OfflineMainPlayerActivity,
                    "Ошибка воспроизведения: ${error.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            override fun onTracksChanged(tracks: Tracks) {
                pinOfflineQualityLabel(player, videoData)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                pinOfflineQualityLabel(player, videoData)
            }
        })

        // streamKeys from the download + explicit offline Widevine keySetId.
        val mediaItemBuilder = download.request.toMediaItem().buildUpon()
        if (keySetId != null) {
            mediaItemBuilder
                .setDrmUuid(C.WIDEVINE_UUID)
                .setDrmLicenseUri(licenseUrl ?: "https://license.kinescope.io/")
                .setDrmMultiSession(true)
                .setDrmKeySetId(keySetId)
        }
        val mediaItem = mediaItemBuilder.build()

        player.setMediaSource(
            androidx.media3.exoplayer.hls.HlsMediaSource.Factory(cacheDataSourceFactory)
                .createMediaSource(mediaItem),
        )
        player.prepare()
        player.playWhenReady = true
    }

    private fun qualityHeightHint(videoData: VideoData): Int {
        videoData.selectedQualityLabel?.let { label ->
            Regex("""(\d+)""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return 0
    }

    private fun pinOfflineQualityLabel(player: ExoPlayer, videoData: VideoData) {
        val selectedLabel = videoData.selectedQualityLabel?.trim()?.takeIf { it.isNotEmpty() }
        val format = player.videoFormat
        applyQualityLabels(videoData, format?.height, format?.width ?: 0, forceLabel = selectedLabel)

        // Prefer quality_map / label digits for the settings variant so the gear badge
        // follows "480p", not a transient Format.height / videoSize (≥1080 → HD).
        val labelDigits = selectedLabel?.let { label ->
            Regex("""(\d+)""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }?.takeIf { it > 0 }
        val shortSide = qualityShortSide(format?.width ?: 0, format?.height ?: 0)
        val variantId = labelDigits
            ?: shortSide.takeIf { it > 0 }
            ?: format?.height?.takeIf { it > 0 }
            ?: return
        binding.playerView.setVideoQualityVariant(variantId)
        if (binding.playerViewFullscreen.isVisible) {
            binding.playerViewFullscreen.setVideoQualityVariant(variantId)
        }
    }

    private fun applyQualityLabels(
        videoData: VideoData,
        trackHeight: Int?,
        trackWidth: Int = 0,
        forceLabel: String? = null,
    ) {
        val selectedLabel = forceLabel
            ?: videoData.selectedQualityLabel?.trim()?.takeIf { it.isNotEmpty() }
        val names = mutableMapOf<Int, String>()
        videoData.qualityMap?.forEach { entry ->
            val name = entry.name.trim()
            if (name.isEmpty()) return@forEach
            names[entry.height] = name
            Regex("""(\d+)""").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { digits ->
                names[digits] = name
            }
        }
        if (selectedLabel != null) {
            Regex("""(\d+)""").find(selectedLabel)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { digits ->
                names[digits] = selectedLabel
            }
            if (trackHeight != null && trackHeight > 0) {
                names[trackHeight] = selectedLabel
                val shortSide = qualityShortSide(trackWidth, trackHeight)
                if (shortSide > 0) names[shortSide] = selectedLabel
            }
        }
        if (names.isNotEmpty()) {
            kinescopeVideoPlayer.setQualityNamesByHeight(names)
            binding.playerView.setQualityNamesByHeight(names)
            binding.playerViewFullscreen.setQualityNamesByHeight(names)
        }
    }

    private fun qualityShortSide(width: Int, height: Int): Int {
        val w = width.takeIf { it > 0 } ?: 0
        val h = height.takeIf { it > 0 } ?: 0
        return when {
            w > 0 && h > 0 -> minOf(w, h)
            h > 0 -> h
            w > 0 -> w
            else -> 0
        }
    }

    private fun generateContentId(url: String, heightPx: Int = 0): String {
        return try {
            val stablePart = if (heightPx > 0) {
                "${url.substringBefore("?")}#$heightPx"
            } else {
                url.substringBefore("?")
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(stablePart.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun generateVideoIdFromUrl(url: String): String {
        return try {
            val parts = url.substringBefore("?").split("/")
            parts.getOrNull(parts.size - 2) ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    override fun onResume() {
        super.onResume()
        kinescopeVideoPlayer.exoPlayer?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        kinescopeVideoPlayer.exoPlayer?.playWhenReady = false
    }

    override fun onDestroy() {
        releasePlayerSafely()
        super.onDestroy()
    }

    private fun releasePlayerSafely() {
        if (playerReleased) return
        playerReleased = true
        try {
            binding.playerView.setPlayer(null)
        } catch (_: Exception) {
        }
        try {
            binding.playerViewFullscreen.setPlayer(null)
        } catch (_: Exception) {
        }
        if (!::kinescopeVideoPlayer.isInitialized) return
        try {
            kinescopeVideoPlayer.exoPlayer?.stop()
            kinescopeVideoPlayer.exoPlayer?.clearMediaItems()
        } catch (e: Exception) {
            Log.w(TAG, "stop player", e)
        }
        try {
            kinescopeVideoPlayer.release()
        } catch (e: Exception) {
            Log.e(TAG, "release player", e)
        }
    }

    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            setFullscreen(false)
            supportActionBar?.show()
            isVideoFullscreen = false
        } else {
            setFullscreen(true)
            supportActionBar?.hide()
            isVideoFullscreen = true
        }
        binding.playerViewFullscreen.isVisible = isVideoFullscreen
        orientationController.setFullscreen(isVideoFullscreen)
    }

    private fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            binding.toolbar.visibility = View.GONE
            KinescopePlayerView.switchTargetView(
                binding.playerView,
                binding.playerViewFullscreen,
                kinescopeVideoPlayer,
            )
            offlineVideoData?.let { applyQualityLabels(it, null) }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
            } else {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
            }
            binding.toolbar.visibility = View.VISIBLE
            KinescopePlayerView.switchTargetView(
                binding.playerViewFullscreen,
                binding.playerView,
                kinescopeVideoPlayer,
            )
            offlineVideoData?.let { applyQualityLabels(it, null) }
        }
    }

    /**
     * Return to the offline downloads list. Do **not** use [TaskStackBuilder] with
     * [io.kinescope.demo.MainActivity] — it applies CLEAR_TASK and lands on Main when the
     * list intent is dropped. Release DRM player before leaving to avoid process death.
     */
    private fun navigateUpFromPlayer() {
        if (isVideoFullscreen) {
            toggleFullscreen()
            return
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        releasePlayerSafely()

        startActivity(
            Intent(this, OfflineDrmDemoActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
        )
        finish()
    }
}

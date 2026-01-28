@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.offlinedrm

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.offline.Download
import io.kinescope.demo.R
import io.kinescope.demo.databinding.ActivityOfflineMainPlayerBinding
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.view.KinescopePlayerView
import io.kinescope.sdk.shorts.managers.PlayerFactory
import androidx.media3.exoplayer.ExoPlayer
import android.util.Base64
import java.security.MessageDigest
import java.util.UUID

@UnstableApi
class OfflineMainPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineMainPlayerBinding
    private lateinit var kinescopeVideoPlayer: KinescopeVideoPlayer
    private var exoPlayer: ExoPlayer? = null
    private val drmConfigurator = DrmConfigurator(this)
    private var isVideoFullscreen = false

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_VIDEO_DATA_JSON = "video_data_json"
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
            AppJson.decodeFromString(
                VideoData.serializer(),
                videoDataJson
            )
        } catch (e: Exception) {

            Toast.makeText(this, "Ошибка загрузки данных видео", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val download = VideoDownloadManager.getDownloadIndex(this).getDownload(downloadId)
        if (download?.state != Download.STATE_COMPLETED) {
            Toast.makeText(this, "Видео не загружено", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        binding.toolbar.findViewById<TextView>(R.id.TitleVideo).text = videoData.title
        binding.toolbar.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        exoPlayer = PlayerFactory(this).createPlayer()
        kinescopeVideoPlayer = KinescopeVideoPlayer(this.applicationContext)
        kinescopeVideoPlayer.exoPlayer?.release()
        kinescopeVideoPlayer.exoPlayer = exoPlayer

        binding.playerView.setPlayer(kinescopeVideoPlayer)
        binding.playerView.setIsFullscreen(false)
        binding.playerView.onFullscreenButtonCallback = { toggleFullscreen() }

        binding.playerViewFullscreen.setIsFullscreen(true)
        binding.playerViewFullscreen.onFullscreenButtonCallback = { toggleFullscreen() }

        binding.playerView.post {

            binding.playerView.postDelayed({
                try {
                    setupOfflinePlayer(videoData, download)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка инициализации плеера", Toast.LENGTH_LONG).show()
                    finish()
                }
            }, 100)
        }
    }

    private fun setupOfflinePlayer(videoData: VideoData, download: Download) {
        val contentId = generateContentId(videoData.hlsLink)
        val keySetId = download.request.keySetId ?: drmConfigurator.loadOfflineLicenseFromStorage(contentId)

        val hasDrm = keySetId != null
        val licenseUrl = if (hasDrm) {
            videoData.drm?.widevine?.licenseUrl ?: run {
                val videoId = generateVideoIdFromUrl(videoData.hlsLink)
                "https://license.kinescope.io/v1/vod/$videoId/acquire/widevine?token="
            }
        } else null

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoDownloadManager.getDownloadCache(this))
            .setUpstreamDataSourceFactory(null)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(videoData.hlsLink.toUri())

        if (hasDrm && keySetId != null && !licenseUrl.isNullOrBlank()) {
            mediaItemBuilder
                .setDrmUuid(C.WIDEVINE_UUID)
                .setDrmLicenseUri(licenseUrl)
                .setDrmMultiSession(true)
                .setDrmKeySetId(keySetId)
        }


        val mediaItem = mediaItemBuilder.build()
        
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@OfflineMainPlayerActivity, "Ошибка воспроизведения: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    binding.playerView.setPlayer(kinescopeVideoPlayer)
                    exoPlayer?.playWhenReady = true
                }
            }
        })

        exoPlayer?.setMediaSource(HlsMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem))
        exoPlayer?.prepare()

        binding.playerView.invalidate()
        binding.playerView.requestLayout()

    }

    private fun generateContentId(url: String): String {
        return try {
            val stablePart = url.substringBefore("?")
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
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        kinescopeVideoPlayer.stop()
    }
    
    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            setFullscreen(false)
            if (supportActionBar != null) {
                supportActionBar?.show()
            }
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isVideoFullscreen = false
        } else {
            setFullscreen(true)
            if (supportActionBar != null) {
                supportActionBar?.hide()
            }
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            isVideoFullscreen = true
        }
        binding.playerViewFullscreen.isVisible = isVideoFullscreen
    }
    
    private fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            binding.toolbar.visibility = View.GONE
            binding.playerViewFullscreen.setPlayer(kinescopeVideoPlayer)
            KinescopePlayerView.switchTargetView(binding.playerView, binding.playerViewFullscreen, kinescopeVideoPlayer)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            } else {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            }
            binding.toolbar.visibility = View.VISIBLE
            binding.playerView.setPlayer(kinescopeVideoPlayer)
            KinescopePlayerView.switchTargetView(binding.playerViewFullscreen, binding.playerView, kinescopeVideoPlayer)
        }
    }
    
    override fun onBackPressed() {
        if (isVideoFullscreen) {
            toggleFullscreen()
            return
        }
        super.onBackPressed()
    }
}

package io.kinescope.sdk.shorts.player

import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.offline.Download
import io.kinescope.sdk.shorts.databinding.ActivitySaveVideoPlayerBinding
import io.kinescope.sdk.shorts.databinding.ListVideoBinding
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.managers.PlayerFactory
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi
import java.security.MessageDigest
import java.util.UUID

@InternalSerializationApi
@OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflinePlayer(
    private val context: Context,
    private val drmConfigurator: DrmConfigurator
) {
    private val playerFactory = PlayerFactory(context)

    fun setupPlayer(
        videoData: VideoData,
        download: Download?,
        binding: Any,
        onFallback: () -> Unit,
        player: ExoPlayer
    ) {
        if (download?.state != Download.STATE_COMPLETED) {
            Toast.makeText(context, "Видео не загружено", Toast.LENGTH_SHORT).show()
            onFallback()
            return
        }

        val contentId = generateContentId(videoData.hlsLink)
        val hasDrm = !videoData.drm?.widevine?.licenseUrl.isNullOrBlank()

        try {
            val playerView = when (binding) {
                is ListVideoBinding -> binding.playerView
                is ActivitySaveVideoPlayerBinding -> binding.playerView
                else -> throw IllegalArgumentException("Unsupported binding type")
            }

            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(VideoDownloadManager.getDownloadCache(context))
                .setUpstreamDataSourceFactory(null)
                .setCacheReadDataSourceFactory(FileDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(videoData.hlsLink.toUri())

            if (hasDrm) {
                val keySetId = download.request.keySetId ?: drmConfigurator.loadOfflineLicenseFromStorage(contentId)
                if (keySetId == null) {
                    Toast.makeText(context, "Ошибка DRM лицензии", Toast.LENGTH_SHORT).show()
                    onFallback()
                    return
                }
                mediaItemBuilder
                    .setDrmUuid(C.WIDEVINE_UUID)
                    .setDrmLicenseUri(videoData.drm?.widevine?.licenseUrl)
                    .setDrmMultiSession(true)
                    .setDrmKeySetId(keySetId)
            }

            val mediaItem = mediaItemBuilder.build()
            playerView.player = player
            player.setMediaSource(HlsMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem))
            player.playWhenReady = true

            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    player.release()
                    onFallback()
                }
            })
            player.prepare()
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка оффлайна", Toast.LENGTH_SHORT).show()
            onFallback()
        }
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
}
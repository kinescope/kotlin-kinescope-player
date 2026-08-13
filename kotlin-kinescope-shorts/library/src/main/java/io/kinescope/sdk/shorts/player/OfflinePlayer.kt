package io.kinescope.sdk.shorts.player

import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
@OptIn(UnstableApi::class)
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

        val contentIdWithHeight = generateContentId(
            videoData.hlsLink,
            qualityHeightHint(videoData),
        )
        val contentIdPlain = generateContentId(videoData.hlsLink)
        val licenseUrl = videoData.drm?.widevine?.licenseUrl?.takeIf { it.isNotBlank() }
        val hasDrm = !licenseUrl.isNullOrBlank() || download.request.keySetId != null

        try {
            val playerView = when (binding) {
                is ListVideoBinding -> binding.playerView
                is ActivitySaveVideoPlayerBinding -> binding.playerView
                else -> throw IllegalArgumentException("Unsupported binding type")
            }

            val keySetId = if (hasDrm) {
                download.request.keySetId
                    ?: drmConfigurator.loadOfflineLicenseFromStorage(contentIdWithHeight)
                    ?: drmConfigurator.loadOfflineLicenseFromStorage(contentIdPlain)
            } else {
                null
            }
            if (hasDrm && keySetId == null) {
                Toast.makeText(context, "Ошибка DRM лицензии", Toast.LENGTH_SHORT).show()
                onFallback()
                return
            }

            // Offline-only: do not set FLAG_IGNORE_CACHE_ON_ERROR (upstream is null).
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(VideoDownloadManager.getDownloadCache(context))
                .setUpstreamDataSourceFactory(null)
                .setCacheReadDataSourceFactory(FileDataSource.Factory())

            // Prefer DownloadRequest.toMediaItem() so streamKeys + mimeType are preserved.
            val mediaItemBuilder = download.request.toMediaItem().buildUpon()
            if (keySetId != null) {
                mediaItemBuilder
                    .setDrmUuid(C.WIDEVINE_UUID)
                    .setDrmLicenseUri(licenseUrl ?: "https://license.kinescope.io/")
                    .setDrmMultiSession(true)
                    .setDrmKeySetId(keySetId)
            }

            playerView.player = player
            player.setMediaSource(
                HlsMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItemBuilder.build()),
            )
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

    private fun qualityHeightHint(videoData: VideoData): Int {
        videoData.selectedQualityLabel?.let { label ->
            Regex("""(\d+)""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return 0
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
}

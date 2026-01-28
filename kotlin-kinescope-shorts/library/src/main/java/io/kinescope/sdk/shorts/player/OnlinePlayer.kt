package io.kinescope.sdk.shorts.player

import android.content.Context
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import io.kinescope.sdk.shorts.cache.VideoCache
import io.kinescope.sdk.shorts.databinding.ListVideoBinding
import io.kinescope.sdk.shorts.drm.DrmContentProtection
import io.kinescope.sdk.shorts.drm.DrmHelper
import io.kinescope.sdk.shorts.managers.PlayerFactory
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.network.InternetConnection
import io.kinescope.sdk.shorts.utils.NetworkUntil
import kotlinx.serialization.InternalSerializationApi
import java.security.MessageDigest
import java.util.UUID

@InternalSerializationApi
@OptIn(androidx.media3.common.util.UnstableApi::class)

class OnlinePlayer(
    private val context: Context,
    private val internetConnection: InternetConnection,
    private val drmHelper: DrmHelper,
    private val playerFactory: PlayerFactory

) {
    fun setupPlayer(
        player: ExoPlayer,
        videoData: VideoData,
        binding: ListVideoBinding,
        onPrepared: () -> Unit
    ) {

        if (!NetworkUntil(context).isNetworkAvailable()) {
            internetConnection.showNoInternetMessage()
            return
        }
        binding.playerView.player = player

        val hasDrm = !videoData.drm?.widevine?.licenseUrl.isNullOrBlank()
        if (hasDrm) {
            setupDrmPlayer(player, videoData, binding)
        } else {
            setupClearPlayer(player, videoData, binding)
        }
        onPrepared()
        player.prepare()
    }
    private fun setupDrmPlayer(player: ExoPlayer, videoData: VideoData, binding: ListVideoBinding) {
        val contentId = generateContentId(videoData.hlsLink)
        val drmData = DrmContentProtection(
            schemeUri = C.WIDEVINE_UUID.toString(),
            licenseUrl = videoData.drm?.widevine?.licenseUrl,
            schemeUuid = C.WIDEVINE_UUID
        )
        val mediaItem = MediaItem.Builder()
            .setUri(videoData.hlsLink)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(drmData.licenseUrl)
            .setDrmMultiSession(true)

            .build()

        player.setMediaItem(mediaItem)
        drmHelper.attachToPlayer(player)
    }

    private fun setupClearPlayer(player: ExoPlayer, videoData: VideoData, binding: ListVideoBinding) {
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoCache.getCache())
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())

        val mediaItem = MediaItem.fromUri(videoData.hlsLink)
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        player.setMediaSource(hlsSource)
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
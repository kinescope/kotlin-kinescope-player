package io.kinescope.sdk.shorts.managers

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import io.kinescope.sdk.shorts.adapters.ViewPager2Adapter
import io.kinescope.sdk.shorts.databinding.ShortsListVideoBinding
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.drm.DrmHelper
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.network.InternetConnection
import io.kinescope.sdk.shorts.player.OfflinePlayer
import io.kinescope.sdk.shorts.player.OnlinePlayer
import io.kinescope.sdk.shorts.view.SeekBarWrap
import io.kinescope.sdk.shorts.view.SeekPlayerControl
import io.kinescope.sdk.shorts.viewholders.VideoViewHolder
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
@OptIn(UnstableApi::class)
class PlayerManager(
    private val context: Context,
    private val videoPreparedListener: ViewPager2Adapter.OnVideoPreparedListener,
    private val seekPlayerControl: SeekPlayerControl,
    private val seekBarWrap: SeekBarWrap,
    private val videoViewHolder: VideoViewHolder,
) {
    var exoPlayer: ExoPlayer? = null
    var shouldAutoPlay = false
    val playerFactory = PlayerFactory(context)
    val exposedDrmHelper: DrmHelper
        get() = drmHelper

    /** Invoked when the active player enters/leaves rebuffer while wanting to play. */
    var onRebufferingChanged: ((Boolean) -> Unit)? = null

    private var lastVideoData: VideoData? = null
    private var lastBinding: ShortsListVideoBinding? = null
    private var lastPosition: Int = -1
    private var hasSuccessfullyPlayed = false
    private var activeListener: Player.Listener? = null

    private val drmHelper = DrmHelper(context, DrmConfigurator(context)) { videoData, pssh ->
        videoViewHolder.startOfflineLicenseDownload(videoData, pssh) { keySetId ->
            if (keySetId != null) {
                Toast.makeText(context, "Лицензия сохранена", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val onlinePlayer =
        OnlinePlayer(context, InternetConnection(context), drmHelper, playerFactory)
    private val offlinePlayer = OfflinePlayer(context, DrmConfigurator(context))

    fun setupOnlinePlayer(
        videoData: VideoData,
        position: Int,
        binding: ShortsListVideoBinding,
        preloadedPlayer: ExoPlayer? = null,
    ) {
        releasePlayer()

        lastVideoData = videoData
        lastPosition = position
        lastBinding = binding

        val player = if (preloadedPlayer != null) {
            PoolPlayers.get().adoptPlayer(preloadedPlayer)
            preloadedPlayer
        } else {
            PoolPlayers.get().acquirePlayer()
        }
        exoPlayer = player

        if (preloadedPlayer != null) {
            binding.playerView.player = player
            val hasDrm = !videoData.drm?.widevine?.licenseUrl.isNullOrBlank()
            if (hasDrm) {
                drmHelper.attachToPlayer(player)
            }
            player.addListener(createPlayerListener(position).also { activeListener = it })
            seekPlayerControl.startSeekBarUpdates()
            seekPlayerControl.setupPlayer(player)
            seekBarWrap.hidePlayButton()
            videoPreparedListener.onVideoPrepared(PlayerItem(player, position))
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
        } else {
            onlinePlayer.setupPlayer(player, videoData, binding) {
                player.addListener(createPlayerListener(position).also { activeListener = it })
                seekPlayerControl.startSeekBarUpdates()
                seekPlayerControl.setupPlayer(player)
                seekBarWrap.hidePlayButton()
                videoPreparedListener.onVideoPrepared(PlayerItem(player, position))
            }
        }
    }

    fun setupOfflinePlayer(
        videoData: VideoData,
        position: Int,
        binding: ShortsListVideoBinding,
        download: Download?,
    ) {
        releasePlayer()
        val player = PoolPlayers.get().acquirePlayer()
        exoPlayer = player

        offlinePlayer.setupPlayer(videoData, download, binding, {
            setupOnlinePlayer(videoData, position, binding)
        }, player)
        player.addListener(createPlayerListener(position).also { activeListener = it })
        seekPlayerControl.startSeekBarUpdates()
        seekPlayerControl.setupPlayer(player)
        seekBarWrap.hidePlayButton()
        videoPreparedListener.onVideoPrepared(PlayerItem(player, position))
    }

    private fun createPlayerListener(position: Int): Player.Listener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (exoPlayer?.playWhenReady == true) {
                        onRebufferingChanged?.invoke(true)
                    }
                }

                Player.STATE_READY -> {
                    onRebufferingChanged?.invoke(false)
                    videoViewHolder.hideThumbnail()

                    if (shouldAutoPlay) {
                        exoPlayer?.playWhenReady = true
                        shouldAutoPlay = false
                    }

                    val duration = exoPlayer?.duration?.toInt() ?: 0
                    seekBarWrap.updateFullTimes(duration)
                    seekBarWrap.updateSeekBarMax(duration)
                    seekBarWrap.hidePlayButton()

                    seekPlayerControl.startSeekBarUpdates()
                }

                Player.STATE_ENDED -> {
                    onRebufferingChanged?.invoke(false)
                    exoPlayer?.seekTo(0)
                    exoPlayer?.playWhenReady = true
                    seekBarWrap.updateSeekBarProgress(0)
                }

                Player.STATE_IDLE -> {
                    onRebufferingChanged?.invoke(false)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                onRebufferingChanged?.invoke(false)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            onRebufferingChanged?.invoke(false)
            videoViewHolder.showThumbnail()
            val isRuntimeError = error.message?.contains("runtime", ignoreCase = true) == true ||
                error.message?.contains("Unexpected", ignoreCase = true) == true ||
                error.cause?.message?.contains("runtime", ignoreCase = true) == true

            if (isRuntimeError) {
                releasePlayer()

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (lastVideoData != null && lastBinding != null && lastPosition != -1) {
                            setupOnlinePlayer(lastVideoData!!, lastPosition, lastBinding!!)
                        }
                    } catch (_: Exception) {
                        InternetConnection(context).showErrorMessage(error)
                    }
                }, 1000)
            } else {
                InternetConnection(context).showErrorMessage(error)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
            ) {
                hasSuccessfullyPlayed = true
            }
        }
    }

    fun resumePlayback() {
        exoPlayer?.playWhenReady = true
    }

    fun pausePlayback() {
        exoPlayer?.playWhenReady = false
    }

    /**
     * Resume after Activity onPause/onResume. Re-attaches the surface and reloads media
     * if it was cleared (e.g. legacy [PoolPlayers.releaseAll] on background).
     */
    fun resumeAfterBackground() {
        val player = exoPlayer ?: return
        val data = lastVideoData ?: return
        val binding = lastBinding ?: return

        PoolPlayers.get().adoptPlayer(player)
        binding.playerView.player = player

        if (player.mediaItemCount == 0) {
            shouldAutoPlay = true
            onlinePlayer.setupPlayer(player, data, binding) {
                if (activeListener == null) {
                    player.addListener(createPlayerListener(lastPosition).also { activeListener = it })
                }
                seekPlayerControl.startSeekBarUpdates()
                seekPlayerControl.setupPlayer(player)
                seekBarWrap.hidePlayButton()
            }
            player.playWhenReady = true
            return
        }

        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        seekPlayerControl.startSeekBarUpdates()
        player.playWhenReady = true
    }

    fun releasePlayer() {
        exoPlayer?.let { player ->
            activeListener?.let { listener ->
                try {
                    player.removeListener(listener)
                } catch (_: Exception) {
                }
            }
            activeListener = null
            onRebufferingChanged?.invoke(false)
            seekPlayerControl.stopSeekBarUpdates()
            PoolPlayers.get().releasePlayer(player)
            exoPlayer = null
        }
    }
}

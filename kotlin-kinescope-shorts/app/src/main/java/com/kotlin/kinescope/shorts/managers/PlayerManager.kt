package com.kotlin.kinescope.shorts.managers

import android.content.Context
import android.widget.Toast
import com.kotlin.kinescope.shorts.adapters.ViewPager2Adapter
import com.kotlin.kinescope.shorts.databinding.ListVideoBinding
import com.kotlin.kinescope.shorts.drm.DrmConfigurator
import com.kotlin.kinescope.shorts.models.PlayerItem
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.network.InternetConnection
import com.kotlin.kinescope.shorts.view.SeekBarWrap
import com.kotlin.kinescope.shorts.view.SeekPlayerControl
import com.kotlin.kinescope.shorts.viewholders.VideoViewHolder
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import com.kotlin.kinescope.shorts.drm.DrmHelper
import com.kotlin.kinescope.shorts.player.OfflinePlayer
import com.kotlin.kinescope.shorts.player.OnlinePlayer
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
@OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerManager(
    private val context: Context,
    private val videoPreparedListener: ViewPager2Adapter.OnVideoPreparedListener,
    private val seekPlayerControl: SeekPlayerControl,
    private val seekBarWrap: SeekBarWrap,
    private val videoViewHolder: VideoViewHolder
) {
    var exoPlayer: ExoPlayer? = null
    var shouldAutoPlay = false
    val playerFactory = PlayerFactory(context)
    val exposedDrmHelper: DrmHelper
        get() = drmHelper

    private var lastVideoData: VideoData? = null
    private var lastBinding: ListVideoBinding? = null
    private var lastPosition: Int = -1
    private var hasSuccessfullyPlayed = false


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

    fun setupOnlinePlayer(videoData: VideoData, position: Int, binding: ListVideoBinding) {
        releasePlayer()

        lastVideoData = videoData
        lastPosition = position
        lastBinding = binding

        val player = playerFactory.createPlayer()
        exoPlayer = player

        onlinePlayer.setupPlayer(player, videoData, binding) {
            player.addListener(createPlayerListener(position))
            seekPlayerControl.startSeekBarUpdates()
            seekPlayerControl.setupPlayer(player)
            seekBarWrap.hidePlayButton()
            videoPreparedListener.onVideoPrepared(PlayerItem(player, position))
        }
    }

    fun setupOfflinePlayer(
        videoData: VideoData,
        position: Int,
        binding: ListVideoBinding,
        download: Download?
    ) {
        releasePlayer()
        val player = playerFactory.createPlayer()
        exoPlayer = player

        offlinePlayer.setupPlayer(videoData, download, binding, {

            setupOnlinePlayer(videoData, position, binding)
        }, player)
         player.addListener(createPlayerListener(position))
        seekPlayerControl.startSeekBarUpdates()
        seekPlayerControl.setupPlayer(player)
        seekBarWrap.hidePlayButton()
        videoPreparedListener.onVideoPrepared(PlayerItem(player, position))
    }

    private fun createPlayerListener(position: Int): Player.Listener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
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
                    exoPlayer?.seekTo(0)
                    exoPlayer?.playWhenReady = true
                    seekBarWrap.updateSeekBarProgress(0)
                }

            }
        }

        override fun onPlayerError(error: PlaybackException) {
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
                    } catch (e: Exception) {
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
            reason: Int
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

    fun releasePlayer() {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            PoolPlayers.get().releasePlayer(player)
            player.setVideoSurface(null)
            player.release()
            seekPlayerControl.stopSeekBarUpdates()
            exoPlayer = null
        }
    }
}
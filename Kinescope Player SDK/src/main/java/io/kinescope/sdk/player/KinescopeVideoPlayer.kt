package io.kinescope.sdk.player

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.util.MimeTypes
import com.google.common.collect.ImmutableList
import io.kinescope.sdk.logger.KinescopeLogger
import io.kinescope.sdk.logger.KinescopeLoggerLevel
import io.kinescope.sdk.models.videos.KinescopeVideo

class KinescopeVideoPlayer(val context:Context, val kinescopePlayerOptions:KinescopePlayerOptions) {
    constructor(context:Context) :  this(context, KinescopePlayerOptions())

    var exoPlayer: ExoPlayer? = null
    private var currentKinescopeVideo:KinescopeVideo? = null

    init {
        exoPlayer = ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
    }

    fun getVideo():KinescopeVideo? = currentKinescopeVideo

    fun setVideo(kinescopeVideo: KinescopeVideo) {
        val video: MediaItem

        if (getShowSubtitles() && kinescopeVideo.subtitles.isNotEmpty()) {
            val subtitle:MediaItem.SubtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(Uri.parse(kinescopeVideo.subtitles.first().url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            video = MediaItem.Builder()
                .setUri(Uri.parse(kinescopeVideo.assets.first().url))
                .setSubtitleConfigurations(ImmutableList.of(subtitle))
                .build()


        }
        else {
            video = MediaItem.fromUri(Uri.parse(kinescopeVideo.assets.first().url))
        }
        currentKinescopeVideo = kinescopeVideo
        exoPlayer?.setMediaItem(video)
        exoPlayer?.playWhenReady = false
        exoPlayer?.prepare()
    }


    fun play() {
        exoPlayer?.play()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER,"Start playing")
    }

    fun pause() {
        exoPlayer?.pause()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER,"Pause playing")
    }

    fun stop() {
        exoPlayer?.stop()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER,"Stop playing")
    }

    fun seekTo(toMilliSeconds:Long) {
        exoPlayer?.seekTo(exoPlayer!!.contentPosition + toMilliSeconds)
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER,"seek to ${toMilliSeconds / 1000} seconds" )
    }

    fun moveForward() {
        exoPlayer?.seekForward()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER,"Moved forward to ${exoPlayer!!.seekParameters.toleranceAfterUs}")
    }

    fun moveBack() {
        exoPlayer?.seekBack()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER,"Moved back to ${exoPlayer!!.seekParameters.toleranceBeforeUs}")
    }

    fun setPlaybackSpeed(speed:Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER,"Playback speed changed to $speed")
    }

    fun setShowSubtitles(value: Boolean) {
        kinescopePlayerOptions.showSubtitlesButton = value
    }

    fun setShowOptions(value:Boolean) {
        kinescopePlayerOptions.showOptionsButton = value
    }

    fun getShowSubtitles():Boolean {
        return kinescopePlayerOptions.showSubtitlesButton
    }

    fun setShowFullscreen(value: Boolean) {
        kinescopePlayerOptions.showFullscreenButton = value
    }
}
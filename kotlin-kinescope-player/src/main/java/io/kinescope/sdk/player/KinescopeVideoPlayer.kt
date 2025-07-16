package io.kinescope.sdk.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashChunkSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.google.common.collect.ImmutableList
import io.kinescope.sdk.api.KinescopeFetch
import io.kinescope.sdk.logger.KinescopeLogger
import io.kinescope.sdk.logger.KinescopeLoggerLevel
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.network.FetchBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@UnstableApi
class KinescopeVideoPlayer(
    val context: Context,
    val kinescopePlayerOptions: KinescopePlayerOptions
) {
    constructor(context: Context) : this(context, KinescopePlayerOptions())

    var exoPlayer: ExoPlayer? = null
    var onSourceChanged: ((source: String, metricUrl: String?) -> Unit)? = null

    private val USER_AGENT = "KinescopeAndroidVideoKotlin"
    private var currentKinescopeVideo: KinescopeVideo? = null
    private var fetch: KinescopeFetch

    init {
        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(DefaultTrackSelector(context, AdaptiveTrackSelection.Factory()))
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        fetch = FetchBuilder.getKinescopeFetch(kinescopePlayerOptions.referer)
    }

    private fun getDashMediaSource(videoBuilder: MediaItem.Builder): DashMediaSource {
        val headers: MutableMap<String, String> = HashMap()
        headers["Origin"] = "*/*"
        headers["x-drm-type"] = "widevine"
        headers["Referer"] = kinescopePlayerOptions.referer

        val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(headers)
            .setTransferListener(
                DefaultBandwidthMeter.Builder(context)
                    .setResetOnNetworkTypeChange(false)
                    .build()
            )

        val dashChunkSourceFactory: DashChunkSource.Factory = DefaultDashChunkSource.Factory(
            defaultHttpDataSourceFactory
        )

        return DashMediaSource.Factory(dashChunkSourceFactory, defaultHttpDataSourceFactory)
            .setManifestParser(KinescopeDashManifestParser())
            .setLoadErrorHandlingPolicy(KinescopeErrorHandlingPolicy())
            .createMediaSource(
                videoBuilder
                    .setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .build()
                    )
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .setTag(null)
                    .build()
            )
    }

    private fun setVideo(kinescopeVideo: KinescopeVideo) {
        val source: String

        val mediaSource = when {
            kinescopeVideo.dashLink.isNullOrEmpty().not() -> {
                val videoBuilder: MediaItem.Builder = MediaItem.Builder()
                    .setUri(Uri.parse(kinescopeVideo.dashLink))

                if (getShowSubtitles() && kinescopeVideo.subtitles.isNotEmpty()) {
                    val subtitle: MediaItem.SubtitleConfiguration =
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(kinescopeVideo.subtitles.first().url))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()

                    videoBuilder.setSubtitleConfigurations(ImmutableList.of(subtitle))
                }

                source = kinescopeVideo.dashLink.orEmpty()
                getDashMediaSource(videoBuilder)
            }

            kinescopeVideo.hlsLink.isNullOrEmpty().not() -> {
                source = kinescopeVideo.hlsLink.orEmpty()
                HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                    .setLoadErrorHandlingPolicy(KinescopeErrorHandlingPolicy())
                    .createMediaSource(MediaItem.fromUri(kinescopeVideo.hlsLink.orEmpty()))
            }

            else -> return
        }

        currentKinescopeVideo = kinescopeVideo
        onSourceChanged?.invoke(
            source,
            kinescopeVideo.sdk?.metricUrl
        )

        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.playWhenReady = false
        exoPlayer?.prepare()
    }

    private fun fetchUpdate() {
        fetch = FetchBuilder.getKinescopeFetch(kinescopePlayerOptions.referer)
    }

    fun getVideo(): KinescopeVideo? = currentKinescopeVideo

    fun loadVideo(
        videoId: String,
        onSuccess: ((KinescopeVideo?) -> Unit)? = null,
        onFailed: ((t: Throwable?) -> Unit)? = null,
    ) {
        fetch.getVideo(videoId).enqueue(object : Callback<KinescopeVideo> {
            override fun onResponse(
                call: Call<KinescopeVideo>,
                response: Response<KinescopeVideo>
            ) {
                if (response.isSuccessful) {
                    val video = response.body()!!
                    setVideo(video)
                    onSuccess?.invoke(video)

                    if (onSuccess != null) {
                        onSuccess(video)
                    };
                } else {
                    KinescopeLogger.log(
                        KinescopeLoggerLevel.NETWORK,
                        "LoadVideo isSuccessful false"
                    )
                    onFailed?.invoke(null)
                }
            }

            override fun onFailure(call: Call<KinescopeVideo>, t: Throwable) {
                if (onFailed != null) {
                    onFailed(t)
                };
                KinescopeLogger.log(
                    KinescopeLoggerLevel.NETWORK,
                    "LoadVideo failed: $t.message.toString()"
                )
            }
        })
    }

    fun play() {
        exoPlayer?.play()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Start playing")
    }

    fun pause() {
        exoPlayer?.pause()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Pause playing")
    }

    fun stop() {
        exoPlayer?.stop()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Stop playing")
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun seekTo(toMilliSeconds: Long) {
        exoPlayer?.seekTo(exoPlayer!!.contentPosition + toMilliSeconds)
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "seek to ${toMilliSeconds / 1000} seconds")
    }

    fun moveForward() {
        exoPlayer?.seekForward()
        KinescopeLogger.log(
            KinescopeLoggerLevel.PLAYER,
            "Moved forward to ${exoPlayer!!.seekParameters.toleranceAfterUs}"
        )
    }

    fun moveBack() {
        exoPlayer?.seekBack()
        KinescopeLogger.log(
            KinescopeLoggerLevel.PLAYER,
            "Moved back to ${exoPlayer!!.seekParameters.toleranceBeforeUs}"
        )
    }

    fun setReferer(value: String) {
        kinescopePlayerOptions.referer = value
        fetchUpdate();
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Referer $value")
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Playback speed changed to $speed")
    }

    fun setShowSubtitles(value: Boolean) {
        kinescopePlayerOptions.showSubtitlesButton = value
    }

    fun setShowOptions(value: Boolean) {
        kinescopePlayerOptions.showOptionsButton = value
    }

    fun getShowSubtitles(): Boolean {
        return kinescopePlayerOptions.showSubtitlesButton
    }

    fun setShowFullscreen(value: Boolean) {
        kinescopePlayerOptions.showFullscreenButton = value
    }
}
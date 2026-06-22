package io.kinescope.sdk.player

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
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
    private var boundLifecycle: Lifecycle? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var resumeOnStart = false
    private var playerHost: KinescopePlayerHost? = null

    init {
        val toneMapToSdr = KinescopeHdrHelper.shouldToneMapToSdr(context, kinescopePlayerOptions.hdrToneMapping)
        val playerBuilder = ExoPlayer.Builder(context)
            .setTrackSelector(DefaultTrackSelector(context, AdaptiveTrackSelection.Factory()))
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
        if (toneMapToSdr) {
            playerBuilder.setRenderersFactory(
                KinescopeToneMappingRenderersFactory(context, requestOpenGlToneMapping = true),
            )
        }
        exoPlayer = playerBuilder.build()
        playerHost = exoPlayer?.let { KinescopePlayerHost(it) }

        fetch = FetchBuilder.getKinescopeFetch(kinescopePlayerOptions.referer)
        exoPlayer?.let { KinescopeHdrHelper.configure(it, context, kinescopePlayerOptions.hdrToneMapping) }
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
                source = kinescopeVideo.dashLink.orEmpty()
                if (getShowSubtitles() && kinescopeVideo.subtitles.isNotEmpty()) {
                    KinescopeSubtitleMediaSources.createDashWithSideloadedSubtitles(
                        video = kinescopeVideo,
                        referer = kinescopePlayerOptions.referer,
                    )
                } else {
                    getDashMediaSource(
                        MediaItem.Builder().setUri(Uri.parse(kinescopeVideo.dashLink)),
                    )
                }
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
        applyPlaybackOptions()
        exoPlayer?.playWhenReady = kinescopePlayerOptions.autoplay
        exoPlayer?.prepare()
    }

    fun applyPlaybackOptions() {
        exoPlayer?.let { player ->
            player.volume = if (kinescopePlayerOptions.muted) 0f else 1f
            player.repeatMode = if (kinescopePlayerOptions.loop) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun fetchUpdate() {
        fetch = FetchBuilder.getKinescopeFetch(kinescopePlayerOptions.referer)
    }

    fun getVideo(): KinescopeVideo? = currentKinescopeVideo

    /** Active media3 player for UI binding (local ExoPlayer by default, CastPlayer when casting). */
    val playbackPlayer: Player?
        get() = playerHost?.activePlayer

    val isCasting: Boolean
        get() = playerHost?.isCasting == true

    /**
     * Facade for swapping the active player (e.g. local ExoPlayer ↔ CastPlayer).
     */
    fun getOrCreatePlayerHost(): KinescopePlayerHost? {
        val player = exoPlayer ?: return null
        return playerHost ?: KinescopePlayerHost(player).also { playerHost = it }
    }

    fun switchToCastPlayer(castPlayer: Player) {
        playerHost?.switchTo(castPlayer)
    }

    fun switchToLocalPlayer() {
        playerHost?.switchToLocal()
    }

    /**
     * Binds pause/resume/release to the Android lifecycle.
     *
     * @param isPipActive Skip pause on [Lifecycle.Event.ON_STOP] while PiP is active.
     * @param backgroundPlaybackAllowed Keep playback running across stop/start.
     */
    fun bindLifecycle(
        lifecycle: Lifecycle,
        isPipActive: () -> Boolean = { false },
        backgroundPlaybackAllowed: Boolean = kinescopePlayerOptions.backgroundPlaybackAllowed,
        releaseOnDestroy: Boolean = true,
    ) {
        unbindLifecycle()
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (backgroundPlaybackAllowed) {
                    val finishing = (owner as? Activity)?.isFinishing == true
                    if (finishing) {
                        KinescopePlaybackService.disconnect(context)
                        pause()
                        return
                    }
                    KinescopePlaybackService.connect(context, this@KinescopeVideoPlayer)
                    return
                }
                if (isPipActive()) return
                resumeOnStart = playbackPlayer?.playWhenReady == true
                pause()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (backgroundPlaybackAllowed) {
                    KinescopePlaybackService.disconnect(context)
                }
                if (backgroundPlaybackAllowed || !resumeOnStart) return
                resumeOnStart = false
                play()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                KinescopePlaybackService.disconnect(context)
                unbindLifecycle()
                if (releaseOnDestroy) {
                    releasePlayerEngine()
                }
            }
        }
        boundLifecycle = lifecycle
        lifecycleObserver = observer
        lifecycle.addObserver(observer)
    }

    fun unbindLifecycle() {
        val lifecycle = boundLifecycle ?: return
        lifecycleObserver?.let { lifecycle.removeObserver(it) }
        boundLifecycle = null
        lifecycleObserver = null
        resumeOnStart = false
    }

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
                    if (kinescopePlayerOptions.autoplay) {
                        play()
                    }
                    onSuccess?.invoke(video)
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
        playbackPlayer?.play()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Start playing")
    }

    fun pause() {
        playbackPlayer?.pause()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Pause playing")
    }

    fun stop() {
        playbackPlayer?.stop()
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Stop playing")
    }

    fun release() {
        KinescopePlaybackService.disconnect(context)
        unbindLifecycle()
        releasePlayerEngine()
    }

    private fun releasePlayerEngine() {
        exoPlayer?.release()
        exoPlayer = null
        playerHost = null
    }

    /**
     * Swaps the local ExoPlayer instance (e.g. custom offline DRM engine).
     * Re-attach [io.kinescope.sdk.view.KinescopePlayerView] via [setPlayer] after calling this.
     */
    fun replaceExoPlayer(player: ExoPlayer) {
        exoPlayer?.release()
        exoPlayer = player
        playerHost = KinescopePlayerHost(player)
    }

    fun seekTo(toMilliSeconds: Long) {
        val player = playbackPlayer ?: return
        player.seekTo(player.contentPosition + toMilliSeconds)
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "seek to ${toMilliSeconds / 1000} seconds")
    }

    fun moveForward() {
        playbackPlayer?.seekForward()
        KinescopeLogger.log(
            KinescopeLoggerLevel.PLAYER,
            "Moved forward to ${exoPlayer?.seekParameters?.toleranceAfterUs}"
        )
    }

    fun moveBack() {
        playbackPlayer?.seekBack()
        KinescopeLogger.log(
            KinescopeLoggerLevel.PLAYER,
            "Moved back to ${exoPlayer?.seekParameters?.toleranceBeforeUs}"
        )
    }

    fun setReferer(value: String) {
        kinescopePlayerOptions.referer = value
        fetchUpdate();
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Referer $value")
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        if (isCasting) {
            playbackPlayer?.setPlaybackSpeed(speed)
        }
        KinescopeLogger.log(KinescopeLoggerLevel.PLAYER, "Playback speed changed to $speed")
    }

    fun setShowSubtitles(value: Boolean) {
        kinescopePlayerOptions.showSubtitlesButton = value
    }

    fun setShowCast(value: Boolean) {
        kinescopePlayerOptions.showCastButton = value
    }

    fun getShowCast(): Boolean = kinescopePlayerOptions.showCastButton

    fun setShowOptions(value: Boolean) {
        kinescopePlayerOptions.showOptionsButton = value
    }

    fun getShowSubtitles(): Boolean {
        return kinescopePlayerOptions.showSubtitlesButton
    }

    fun setShowFullscreen(value: Boolean) {
        kinescopePlayerOptions.showFullscreenButton = value
    }

    fun setShowPlayPauseButton(value: Boolean) {
        kinescopePlayerOptions.showPlayPauseButton = value
    }

    fun setShowPlaybackSpeedInSettings(value: Boolean) {
        kinescopePlayerOptions.showPlaybackSpeedInSettings = value
    }

    fun setShowAudioOnlyQualityInSettings(value: Boolean) {
        kinescopePlayerOptions.showAudioOnlyQualityInSettings = value
    }

    fun setShowAudioTracksInSettings(value: Boolean) {
        kinescopePlayerOptions.showAudioTracksInSettings = value
    }
}
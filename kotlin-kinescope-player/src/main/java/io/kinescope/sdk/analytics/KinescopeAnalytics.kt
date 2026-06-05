package io.kinescope.sdk.analytics

import android.content.Context
import android.content.res.Resources
import android.os.Build
import com.google.protobuf.ByteString
import io.kinescope.sdk.BuildConfig
import io.kinescope.sdk.analytics.proto.Device
import io.kinescope.sdk.analytics.proto.Native
import io.kinescope.sdk.analytics.proto.Playback
import io.kinescope.sdk.analytics.proto.Player
import io.kinescope.sdk.analytics.proto.Session
import io.kinescope.sdk.analytics.proto.SessionType
import io.kinescope.sdk.analytics.proto.Video
import io.kinescope.sdk.logger.KinescopeLogger
import io.kinescope.sdk.logger.KinescopeLoggerLevel
import io.kinescope.sdk.network.AnalyticsBuilder
import io.kinescope.sdk.utils.currentTimestamp
import io.kinescope.sdk.api.KinescopeApiConfig
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class KinescopeAnalytics(
    context: Context,
) {
    private val analyticsPlayerIdStorage = KinescopeAnalyticsPlayerIdStorage(
        context = context
    )
    private val analyticsApi = AnalyticsBuilder.getAnalyticsApi()

    private val playerId = analyticsPlayerIdStorage.getPlayerId()
    private val viewId = UUID.randomUUID().toString()

    var metricUrl: String? = null
        get() = field.takeIf { !it.isNullOrEmpty() } ?: KinescopeApiConfig.ANALYTICS_BASE_URL

    fun sendEvent(
        event: Event,
        value: Float = 0f,
        source: String,
        watchedDuration: Int,
        args: KinescopeAnalyticsArgs
    ): String {
        val body = Native.newBuilder()
            .setEvent(event.value)
            .setValue(value)
            .setVideo(
                Video.newBuilder()
                    .setSource(source)
                    .setDuration(args.duration)
                    .build()
            )
            .setPlayer(
                Player.newBuilder()
                    .setVersion(PLAYER_VERSION)
                    .setType(PLAYER_TYPE)
                    .build()
            )
            .setDevice(
                Device.newBuilder()
                    .setOS(DEVICE_OS)
                    .setOSVersion(DEVICE_OS_VERSION)
                    .setScreenWidth(DEVICE_SCREEN_WIDTH)
                    .setScreenHeight(DEVICE_SCREEN_HEIGHT)
                    .build()
            )
            .setSession(
                Session.newBuilder()
                    .setID(ByteString.copyFromUtf8(playerId))
                    .setViewID(ByteString.copyFromUtf8(viewId))
                    .setType(SessionType.Online)
                    .setWatchedDuration(watchedDuration)
                    .build()
            )
            .setPlayback(
                Playback.newBuilder()
                    .setRate(args.rate)
                    .setVolume(args.volume)
                    .setQuality(args.quality)
                    .setIsMuted(args.isMuted)
                    .setIsFullscreen(args.isFullScreen)
                    .setPreviewPosition(args.previewPosition)
                    .setCurrentPosition(args.currentPosition)
                    .build()
            )
            .setEventTime(currentTimestamp())
            .build()

        KinescopeLogger.log(
            level = KinescopeLoggerLevel.SDK,
            message = "Analytics event. ${body.toStringData()}"
        )

        analyticsApi.sendEvent(url = metricUrl as String, body = body)
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) =
                    KinescopeLogger.log(
                        level = KinescopeLoggerLevel.NETWORK,
                        message = "Event ${event.value} successfully sent"
                    )

                override fun onFailure(call: Call<Void>, t: Throwable) =
                    KinescopeLogger.log(
                        level = KinescopeLoggerLevel.NETWORK,
                        message = "Event ${event.value} failed to send"
                    )
            })

        return body.toString()
    }

    enum class Event(val value: String) {
        PLAYBACK(EVENT_NAME_PLAYBACK),
        PLAY(EVENT_NAME_PLAY),
        PAUSE(EVENT_NAME_PAUSE),
        END(EVENT_NAME_END),
        REPLAY(EVENT_NAME_REPLAY),
        BUFFERING(EVENT_NAME_BUFFERING),
        SEEK(EVENT_NAME_SEEK),
        RATE(EVENT_NAME_RATE),
        VIEW(EVENT_NAME_VIEW),
        ENTER_FULLSCREEN(EVENT_NAME_ENTER_FULLSCREEN),
        EXIT_FULLSCREEN(EVENT_NAME_EXIT_FULLSCREEN),
        QUALITY_CHANGED(EVENT_NAME_QUALITY_CHANGED),
        AUTO_QUALITY(EVENT_NAME_AUTO_QUALITY),
    }

    companion object {
        private const val PLAYER_VERSION = BuildConfig.VERSION_NAME
        private const val PLAYER_TYPE = "Android SDK"

        private const val DEVICE_OS = "Android"
        private val DEVICE_OS_VERSION = Build.VERSION.INCREMENTAL.orEmpty()
        private val DEVICE_SCREEN_WIDTH = Resources.getSystem().displayMetrics.widthPixels
        private val DEVICE_SCREEN_HEIGHT = Resources.getSystem().displayMetrics.heightPixels

        private const val EVENT_NAME_PLAYBACK = "playback"
        private const val EVENT_NAME_PLAY = "play"
        private const val EVENT_NAME_PAUSE = "pause"
        private const val EVENT_NAME_END = "end"
        private const val EVENT_NAME_REPLAY = "replay"
        private const val EVENT_NAME_BUFFERING = "buffering"
        private const val EVENT_NAME_SEEK = "seek"
        private const val EVENT_NAME_RATE = "rate"
        private const val EVENT_NAME_VIEW = "view"
        private const val EVENT_NAME_ENTER_FULLSCREEN = "enterfullscreen"
        private const val EVENT_NAME_EXIT_FULLSCREEN = "exitfullscreen"
        private const val EVENT_NAME_QUALITY_CHANGED = "qualitychanged"
        private const val EVENT_NAME_AUTO_QUALITY = "autoqualitychanged"
    }
}
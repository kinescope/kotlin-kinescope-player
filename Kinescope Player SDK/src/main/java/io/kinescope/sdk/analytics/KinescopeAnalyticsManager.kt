package io.kinescope.sdk.analytics

import android.content.Context
import io.kinescope.sdk.utils.EMPTY
import io.kinescope.sdk.utils.currentTimestamp
import kotlin.math.roundToInt

class KinescopeAnalyticsManager(
    context: Context
) {
    private val analytics = KinescopeAnalytics(
        context = context
    )

    private var source = String.EMPTY

    private var eventPlaybackLastSentSecondsAgo = 0
    private var eventViewLastSentSecondsAgo = 0
    private var eventViewSent = false

    private var previousPosition = 0
    private var watchedDuration = 0
    private var bufferingStartTime = 0

    private var isBuffering = false
    private var isEnded = false

    fun tick(args: KinescopeAnalyticsArgs) {
        args.currentPosition.let { currentPosition ->
            if (currentPosition != previousPosition) {
                eventPlaybackLastSentSecondsAgo += 1
                eventViewLastSentSecondsAgo += 1
                previousPosition = currentPosition

                setWatchedDuration(
                    position = args.currentPosition
                )
                sendPlaybackEventIfNeed(
                    args = args
                )
                sendViewEventIfNeed(
                    args = args
                )
            }
        }
    }

    fun buffering() {
        isBuffering = true
        bufferingStartTime = currentTimestamp()
    }

    fun ready(args: KinescopeAnalyticsArgs) {
        if (isBuffering) {
            sendEvent(
                event = KinescopeAnalytics.Event.BUFFERING,
                args = args,
                value = (currentTimestamp() - bufferingStartTime).toFloat()
            )
            isBuffering = false
            bufferingStartTime = 0
        }
    }

    fun play(args: KinescopeAnalyticsArgs) {
        if (isEnded) {
            sendEvent(
                event = KinescopeAnalytics.Event.REPLAY,
                args = args,
            )
            isEnded = false
        }

        sendEvent(
            event = KinescopeAnalytics.Event.PLAY,
            args = args,
        )
    }

    fun pause(args: KinescopeAnalyticsArgs) =
        sendEvent(
            event = KinescopeAnalytics.Event.PAUSE,
            args = args,
        )

    fun end(args: KinescopeAnalyticsArgs) {
        isEnded = true
        sendEvent(
            event = KinescopeAnalytics.Event.END,
            args = args,
        )
    }

    fun seek(args: KinescopeAnalyticsArgs) =
        sendEvent(
            event = KinescopeAnalytics.Event.SEEK,
            args = args,
        )

    fun speedChanged(args: KinescopeAnalyticsArgs) =
        sendEvent(
            event = KinescopeAnalytics.Event.RATE,
            args = args,
        )

    fun enterFullscreen(args: KinescopeAnalyticsArgs) =
        sendEvent(
            event = KinescopeAnalytics.Event.ENTER_FULLSCREEN,
            args = args,
        )

    fun exitFullscreen(args: KinescopeAnalyticsArgs) =
        sendEvent(
            event = KinescopeAnalytics.Event.EXIT_FULLSCREEN,
            args = args,
        )

    fun qualityChanged(args: KinescopeAnalyticsArgs) =
        sendEvent(
            event = KinescopeAnalytics.Event.QUALITY_CHANGED,
            args = args,
        )

    fun autoQualityChanged(args: KinescopeAnalyticsArgs) =
        sendEvent(
            event = KinescopeAnalytics.Event.AUTO_QUALITY,
            args = args,
        )

    fun sourceChanged(newSource: String) {
        source = newSource
        eventPlaybackLastSentSecondsAgo = 0
        eventViewLastSentSecondsAgo = 0
        eventViewSent = false
        previousPosition = 0
        watchedDuration = 0
        bufferingStartTime = 0
        isBuffering = false
        isEnded = false
    }

    /**
     * Playback event
     * Executed from the [tick] method
     */
    private fun sendPlaybackEventIfNeed(args: KinescopeAnalyticsArgs) {
        with(args) {
            val percentInNumber = (duration * EVENT_VIEW_INTERVAL_PERCENT / 100f).roundToInt()

            if ((eventPlaybackLastSentSecondsAgo >= percentInNumber
                        && eventPlaybackLastSentSecondsAgo >= EVENT_VIEW_MIN_INTERVAL_SECONDS)
                || eventPlaybackLastSentSecondsAgo >= EVENT_VIEW_MAX_INTERVAL_SECONDS
            ) {
                sendEvent(
                    event = KinescopeAnalytics.Event.PLAYBACK,
                    args = this,
                )
                eventPlaybackLastSentSecondsAgo = 0
            }
        }
    }

    /**
     * View event
     * Executed from the [tick] method
     */
    private fun sendViewEventIfNeed(args: KinescopeAnalyticsArgs) {
        if (eventViewLastSentSecondsAgo == 5 && !eventViewSent) {
            sendEvent(
                event = KinescopeAnalytics.Event.VIEW,
                args = args
            )

            eventViewLastSentSecondsAgo = 0
            eventViewSent = true
        }
    }

    private fun sendEvent(
        event: KinescopeAnalytics.Event,
        args: KinescopeAnalyticsArgs,
        value: Float = 0f
    ) {
        if (source.isNotEmpty()) {
            analytics.sendEvent(
                event = event,
                source = source,
                watchedDuration = watchedDuration,
                args = args,
                value = value,
            )
        }
    }

    private fun setWatchedDuration(position: Int) {
        watchedDuration = position.takeIf { it > watchedDuration } ?: watchedDuration
    }

    companion object {
        private const val EVENT_VIEW_INTERVAL_PERCENT = 2
        private const val EVENT_VIEW_MIN_INTERVAL_SECONDS = 5
        private const val EVENT_VIEW_MAX_INTERVAL_SECONDS = 60
    }
}
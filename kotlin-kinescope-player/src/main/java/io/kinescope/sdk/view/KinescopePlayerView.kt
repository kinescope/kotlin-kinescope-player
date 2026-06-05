package io.kinescope.sdk.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.bumptech.glide.Glide
import io.kinescope.sdk.R
import io.kinescope.sdk.analytics.KinescopeAnalyticsManager
import io.kinescope.sdk.extensions.animateRotation
import io.kinescope.sdk.extensions.currentVolumeInPercent
import io.kinescope.sdk.extensions.getAnalyticsArguments
import io.kinescope.sdk.extensions.playbackSpeed
import io.kinescope.sdk.logger.KinescopeLogger
import io.kinescope.sdk.logger.KinescopeLoggerLevel
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.models.videos.KinescopeVideoAttachments
import io.kinescope.sdk.models.videos.KinescopeVideoSubtitle
import io.kinescope.sdk.models.players.syncLegacyChromeFlags
import io.kinescope.sdk.player.KinescopeGlideListener
import io.kinescope.sdk.player.KinescopePictureInPicture
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.player.quality.KinescopeQualityManager
import io.kinescope.sdk.player.quality.KinescopeQualityVariant
import io.kinescope.sdk.player.quality.getQualityVariantsList
import io.kinescope.sdk.player.speed.KinescopeSpeedVariant
import io.kinescope.sdk.settings.KinescopeSettingsOption
import io.kinescope.sdk.settings.KinescopeSettingsView
import io.kinescope.sdk.utils.formatLiveStartDate


@UnstableApi
class KinescopePlayerView(
    context: Context, attrs:
    AttributeSet?
) : ConstraintLayout(context, attrs) {
    companion object {
        /**
         * Detaches player from current PlayerView and attaches to the new one
         *
         */
        fun switchTargetView(
            oldPlayerView: KinescopePlayerView?,
            newPlayerView: KinescopePlayerView?,
            player: KinescopeVideoPlayer
        ) {
            if (oldPlayerView === newPlayerView || oldPlayerView == null || newPlayerView == null) {
                return
            }

            newPlayerView.let {
                it.setPlayer(player)
                it.qualityManager = oldPlayerView.qualityManager
                it.analyticsManager = oldPlayerView.analyticsManager

                it.posterView?.isVisible = oldPlayerView.posterView?.isVisible ?: false
                it.liveStartDateContainerView?.isVisible =
                    oldPlayerView.liveStartDateContainerView?.isVisible ?: false

                if (oldPlayerView.isLiveState) {
                    it.isLiveState = true
                    it.isLiveSynced = oldPlayerView.isLiveSynced

                    it.positionView?.isVisible = false
                    it.durationView?.isVisible = false
                    it.timeSeparatorView?.isVisible = false
                    it.liveDataView?.isVisible = true
                }

            }

            oldPlayerView.setPlayer(null)
        }

        private const val DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS = 200
        private const val MAX_UPDATE_INTERVAL_MS = 1000
        private const val CONTROL_OVERLAY_FADE_DURATION_MS = 200L
        private const val CONTROL_OVERLAY_AUTO_HIDE_MS = 3000L
        private const val SCRUB_MODE_CONTROL_ELEVATION_DP = 8f
        private const val SETTINGS_MENU_ELEVATION_DP = 24f
        private const val SCRUB_SEEKBAR_SCALE = 1.12f
        private const val SCRUB_SEEKBAR_SCALE_DURATION_MS = 150L
        private const val DOUBLE_TAP_SEEK_SECONDS = 10
        private const val DOUBLE_TAP_SEEK_STREAK_WINDOW_MS = 1500L
        private const val SUBTITLES_OPTION_OFF_ID = -1
    }

    private val gestureDetector: GestureDetectorCompat
    private var gestureListener: KinescopeGestureListener

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private inner class KinescopeGestureListener(private val rootView: View) :
        GestureDetector.SimpleOnGestureListener() {
        private fun isForward(event: MotionEvent): Boolean {
            return event.x > (rootView.width / 2)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            KinescopeLogger.log(KinescopeLoggerLevel.PLAYER_VIEW, "double tap")
            return super.onDoubleTap(e)
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            KinescopeLogger.log(
                KinescopeLoggerLevel.PLAYER_VIEW,
                "double tap event, action=${e.action}, isForward=${isForward(e)}"
            )

            if (e.action != MotionEvent.ACTION_UP) {
                return true
            }

            val isFwd = isForward(e)
            val totalSeconds = registerDoubleTapSeek(isFwd)
            seekView?.showSeekFeedback(forward = isFwd, totalSeconds = totalSeconds)
            kinescopePlayer?.let {
                if (isFwd) it.moveForward() else it.moveBack()
            }
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            KinescopeLogger.log(KinescopeLoggerLevel.PLAYER_VIEW, "tap down")
            return super.onDown(e)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            KinescopeLogger.log(KinescopeLoggerLevel.PLAYER_VIEW, "single tap confirmed")
            toggleControlUI()
            return false;
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            KinescopeLogger.log(KinescopeLoggerLevel.PLAYER_VIEW, "single tap up")
            return super.onSingleTapUp(e)
        }
    }

    var onFullscreenButtonCallback: (() -> Unit)? = null
    var onPictureInPictureButtonCallback: (() -> Unit)? = null
    var onAttachmentSelected: ((KinescopeVideoAttachments) -> Unit)? = null

    private val formatBuilder: StringBuilder = StringBuilder()
    private val formatter = java.util.Formatter(formatBuilder, java.util.Locale.getDefault())

    private var posterView: ImageView? = null

    private var kinescopePlayer: KinescopeVideoPlayer? = null
    private var exoPlayerView: PlayerView? = null
    private var controlView: FrameLayout? = null
    private var seekView: KinesopeSeekView? = null
    private var bufferingView: View? = null
    private var positionView: TextView? = null
    private var durationView: TextView? = null
    private var timeSeparatorView: View? = null
    private var timeBar: KinescopeTimeBar? = null

    private var buttonsContainer: ViewGroup? = null
    private var playPauseButton: ImageView? = null
    private var optionsButton: View? = null
    private var pictureInPictureButton: View? = null
    private var fullscreenButton: View? = null
    private var customButton: ImageButton? = null
    private var selectedSubtitleIndex: Int = SUBTITLES_OPTION_OFF_ID
    private var settingsMenuView: KinescopeSettingsView? = null

    private var titleView: TextView? = null
    private var authorView: TextView? = null

    private var liveDataView: View? = null
    private var liveBadgeCircleView: View? = null
    private var liveBadgeTextView: View? = null
    private var liveTimeOffsetTextView: TextView? = null
    private var liveStartDateContainerView: View? = null
    private var liveStartDateTextView: TextView? = null

    private var isVideoFullscreen = false
        set(value) {
            getAnalyticsArguments().let { args ->
                when (value) {
                    true -> analyticsManager.enterFullscreen(args = args)
                    else -> analyticsManager.exitFullscreen(args = args)
                }
            }
            field = value
        }

    private var scrubbing = false
    private var scrubbingLiveDurationCached = 0L
    private var controlElevationBeforeScrub = 0f
    private var lastDoubleTapSeekForward: Boolean? = null
    private var doubleTapSeekStreakCount = 0
    private var lastDoubleTapSeekTimeMs = 0L

    private val hideControlOverlayRunnable = Runnable {
        if (scrubbing || settingsMenuView?.isVisible == true) {
            return@Runnable
        }
        hideControlOverlay(animated = true)
    }

    private var window = Timeline.Window()
    private val showBuffering = 1

    private var currentWindowOffset: Long = 0
    private val timeBarMinUpdateIntervalMs = DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS

    private var isLiveState = false
    private var isLiveSynced = false
        private set(value) {
            setLiveBadgeState(value)
            field = value
        }

    private var analyticsCallback: ((event: String, data: String) -> Unit)? = null
    private var qualityManager: KinescopeQualityManager? = null
    private var analyticsManager = KinescopeAnalyticsManager(
        context = context,
        onEvent = { event, data -> analyticsCallback?.invoke(event, data) }
    )

    private val updateProgressRunnable = Runnable {
        updateProgress()
        getAnalyticsArguments().let { args ->
            analyticsManager.tick(args = args)
        }
    }

    private val progressUpdateListener =
        PlayerControlView.ProgressUpdateListener { _, _ ->
        }
    private val playbackSpeedVariants by lazy {
        listOf(
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_0_25),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_0_25
            ),
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_0_5),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_0_5
            ),
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_0_75),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_0_75
            ),
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_normal),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_NORMAL,
            ),
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_1_25),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_1_25
            ),
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_1_5),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_1_5
            ),
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_1_75),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_1_75
            ),
            KinescopeSpeedVariant(
                name = context.getString(R.string.settings_playback_speed_2),
                speed = KinescopeSpeedVariant.PLAYBACK_SPEED_VARIANT_2
            )
        )
    }

    private var componentListener = object :
        Player.Listener,
        OnClickListener,
        TimeBar.OnScrubListener,
        PopupWindow.OnDismissListener {
        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
            if (events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED
                )
            ) {
                updateAll()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            getAnalyticsArguments().let { args ->
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        analyticsManager.buffering()

                        if (!isLiveState) {
                            hidePoster()
                        }
                    }

                    Player.STATE_READY -> {
                        analyticsManager.ready(args = args)

                        if (isLiveState) {
                            hidePoster()
                            hideLiveStartDate()
                        }
                    }

                    Player.STATE_ENDED -> {
                        analyticsManager.end(args = args)
                        showControlOverlay(animated = true)
                        cancelControlOverlayAutoHide()
                    }

                    else -> {}
                }
            }

            updateBuffering()
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            kinescopePlayer?.exoPlayer?.let { player ->
                with(player.trackSelector as DefaultTrackSelector) {
                    qualityManager?.updateVariants(
                        variants = getQualityVariantsList()
                    )
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            super.onVideoSizeChanged(videoSize)
            if (videoSize.height != 0) {
                getAnalyticsArguments().let { args ->
                    when (qualityManager?.isAutoQuality) {
                        true -> analyticsManager.autoQualityChanged(args = args)
                        else -> analyticsManager.qualityChanged(args = args)
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (controlView?.isVisible == true) {
                scheduleControlOverlayAutoHide()
            }
            updateBuffering()
            updatePlayPauseButton()
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)

            kinescopePlayer?.exoPlayer?.let { player ->
                if (player.playbackState == Player.STATE_IDLE && player.playWhenReady) {
                    dispatchPlay(player)
                }
            }
        }

        override fun onScrubStart(timeBar: TimeBar, position: Long) {
            scrubbing = true
            enterScrubOverlayMode()
            seekView?.showScrubOverlay()

            if (isLiveState) {
                scrubbingLiveDurationCached = kinescopePlayer?.exoPlayer?.duration ?: 0
                showLiveTimeOffset(
                    isShown = true,
                    position = position
                )
                return
            }

            positionView?.text = Util.getStringForTime(formatBuilder, formatter, position)

            getAnalyticsArguments().let { args ->
                analyticsManager.seek(args = args)
            }
        }

        override fun onScrubMove(timeBar: TimeBar, position: Long) {
            if (isLiveState) {
                isLiveSynced = position == scrubbingLiveDurationCached
                showLiveTimeOffset(
                    isShown = true,
                    position = position
                )
                return
            }

            positionView?.text = Util.getStringForTime(formatBuilder, formatter, position)
        }

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            scrubbing = false
            seekView?.hideScrubOverlay()
            if (!canceled && kinescopePlayer != null) {
                seekToTimeBarPosition(kinescopePlayer!!.exoPlayer!!, position)
            }

            exitScrubOverlayMode()
            updateAll()
            if (controlView?.isVisible == true) {
                scheduleControlOverlayAutoHide()
            } else {
                showControlOverlay(animated = true)
            }

            if (isLiveState) {
                isLiveSynced = position == scrubbingLiveDurationCached
                showLiveTimeOffset(
                    isShown = false,
                    position = position
                )
            }
        }

        override fun onClick(view: View?) {
            val player: Player = kinescopePlayer?.exoPlayer ?: return

            if (playPauseButton === view) {
                dispatchPlayPause(player)
            } else if (fullscreenButton === view) {
                onFullscreenButtonCallback?.invoke()
            } else if (pictureInPictureButton === view) {
                onPictureInPictureButtonCallback?.invoke()
            } else if (optionsButton === view) {
                toggleSettingsMenu()
            }
        }

        override fun onDismiss() {

        }
    }

    init {
        inflate(context, R.layout.view_kinesope_player, this)
        exoPlayerView = findViewById(R.id.view_exoplayer)

        bufferingView = findViewById(R.id.view_buffering)
        bufferingView?.isVisible = false

        gestureListener = KinescopeGestureListener(rootView)
        gestureDetector = GestureDetectorCompat(context, gestureListener)

        posterView = findViewById(R.id.poster_iv)

        controlView = findViewById(R.id.view_control)
        seekView = findViewById(R.id.kinescope_seek_view)

        timeBar = controlView?.findViewById(R.id.kinescope_progress)
        positionView = controlView?.findViewById(R.id.kinescope_position)
        durationView = controlView?.findViewById(R.id.kinescope_duration)
        timeSeparatorView = controlView?.findViewById(R.id.time_separator_view)

        buttonsContainer = controlView?.findViewById(R.id.buttons_container_ll)
        playPauseButton = controlView?.findViewById(R.id.kinescope_play_pause)
        pictureInPictureButton = controlView?.findViewById(R.id.kinescope_picture_in_picture)
        optionsButton = controlView?.findViewById(R.id.kinescope_settings)
        fullscreenButton = controlView?.findViewById(R.id.kinescope_fullscreen)
        customButton = controlView?.findViewById(R.id.custom_btn)

        titleView = controlView?.findViewById(R.id.kinescope_title)
        authorView = controlView?.findViewById(R.id.kinescope_author)

        liveDataView = controlView?.findViewById(R.id.live_data_ll)
        liveBadgeCircleView = controlView?.findViewById(R.id.live_badge_circle_view)
        liveBadgeTextView = controlView?.findViewById(R.id.live_badge_tv)
        liveTimeOffsetTextView = controlView?.findViewById(R.id.live_time_offset)
        liveStartDateContainerView = findViewById(R.id.live_start_date_ll)
        liveStartDateTextView = findViewById(R.id.live_start_date_tv)

        settingsMenuView = findViewById<KinescopeSettingsView?>(R.id.settings_menu)
            .apply {
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.PlaybackSpeed,
                    title = resources.getString(R.string.settings_parameter_playback_speed),
                    icon = R.drawable.ic_playback_speed
                )
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.VideoQuality,
                    title = resources.getString(R.string.settings_parameter_video_quality),
                    icon = R.drawable.ic_quality
                )
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.Subtitles,
                    title = resources.getString(R.string.settings_parameter_subtitles),
                    icon = R.drawable.ic_subtitles,
                )
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.Attachments,
                    title = resources.getString(R.string.settings_parameter_attachments),
                    icon = R.drawable.ic_attachments,
                )
                onOptionSelected = ::onSettingsOptionSelected
            }
        settingsMenuView?.setAnchorView(optionsButton)
        settingsMenuView?.setFullscreenMode(isVideoFullscreen)

        applyKinescopePlayerOptions()
        setSubtitlesStyling()
        setUIListeners()
        updatePlayPauseButton()
    }

    /**
     * Attaches Kinescope player and loads KinescopePlayerOptions
     * to this KinescopePlayerView
     *
     */
    fun setPlayer(kinescopePlayer: KinescopeVideoPlayer?) {
        Assertions.checkState(Looper.myLooper() == Looper.getMainLooper())
        if (this.kinescopePlayer === kinescopePlayer) return
        this.kinescopePlayer?.exoPlayer?.removeListener(componentListener)
        this.kinescopePlayer = kinescopePlayer

        kinescopePlayer?.exoPlayer?.let { player ->
            this.qualityManager =
                KinescopeQualityManager(context, player.trackSelector as DefaultTrackSelector)
        }

        kinescopePlayer?.exoPlayer?.addListener(componentListener)
        kinescopePlayer?.onSourceChanged = { source, metricUrl ->
            applyDefaultQuality()
            analyticsManager.setSource(
                source = source,
                metricUrl = metricUrl,
            )
        }
        exoPlayerView?.player = kinescopePlayer?.exoPlayer
        applyKinescopePlayerOptions()
        applyAccentColor()
        applyExoPlayerVisibility()
        updateAll()
    }

    /**
     * Changes the player colors.
     * @param buttonColor Color of the buttons.
     * @param scrubberColor Color of the progress bar scrubber.
     * @param progressBarColor Color of the progress bar.
     * @param playedColor Color of the played time.
     * @param bufferedColor Color of the buffered part.
     */
    fun setColors(
        @ColorInt buttonColor: Int? = null,
        @ColorInt scrubberColor: Int? = null,
        @ColorInt progressBarColor: Int? = null,
        @ColorInt playedColor: Int? = null,
        @ColorInt bufferedColor: Int? = null,
    ) {
        buttonColor?.let { color ->
            tintControlIcon(pictureInPictureButton, color)
            tintControlIcon(optionsButton, color)
            tintControlIcon(fullscreenButton, color)
            tintControlIcon(customButton, color)
            buttonsContainer?.children?.forEach { child ->
                if (child is ImageButton) {
                    tintControlIcon(child, color)
                }
            }
            settingsMenuView?.applyIconTint(color)
        }
        timeBar?.let {
            scrubberColor?.let { color -> it.setScrubberColor(color) }
            progressBarColor?.let { color -> it.setUnplayedColor(color) }
            playedColor?.let { color -> it.setPlayedColor(color) }
            bufferedColor?.let { color -> it.setBufferedColor(color) }
        }
    }

    /**
     * Enables the live stream mode for the video player,
     * making the progress bar infinitive and adding the Live badge.
     */
    fun setLiveState() {
        isLiveState = true
        isLiveSynced = true

        positionView?.isVisible = false
        durationView?.isVisible = false
        timeSeparatorView?.isVisible = false
        liveDataView?.isVisible = true
    }

    /**
     * Sets the poster image.
     * Poster will be automatically hidden once the buffering started.
     * For the live stream it will be hidden once the video is ready.
     * @param url Image url
     * @param placeholder Will be shown while image is loading.
     * @param errorPlaceholder Will be shown if image loading failed.
     * @param onLoadFinished Fired once image loading finished.
     */
    fun showPoster(
        url: String,
        @DrawableRes placeholder: Int = R.drawable.default_poster,
        @DrawableRes errorPlaceholder: Int = R.drawable.default_poster,
        onLoadFinished: ((isSuccess: Boolean) -> Unit)? = null,
    ) {
        with(kinescopePlayer?.exoPlayer?.playbackState) {
            if ((!isLiveState && this == Player.STATE_BUFFERING) ||
                (isLiveState && this == Player.STATE_READY)
            ) {
                return
            }
        }
        posterView?.let {
            it.isVisible = true
            Glide.with(context)
                .load(url)
                .placeholder(placeholder)
                .error(errorPlaceholder)
                .addListener(KinescopeGlideListener { isSuccess ->
                    onLoadFinished?.invoke(isSuccess)
                })
                .into(it)
        }
    }

    /**
     * Hides the poster image. If video buffering has started, calling this method will do nothing.
     */
    fun hidePoster() {
        posterView?.isVisible = false
    }

    /**
     * Shows the live stream starting date.
     * @param startDate ISO8601 date string
     */
    fun showLiveStartDate(startDate: String) {
        with(kinescopePlayer?.exoPlayer?.playbackState) {
            if ((!isLiveState && this == Player.STATE_BUFFERING) ||
                (isLiveState && this == Player.STATE_READY)
            ) {
                return
            }
        }
        formatLiveStartDate(startDate)
            .takeIf { startDate.isNotEmpty() }
            ?.let { formattedDate ->
                liveStartDateContainerView?.isVisible = true
                liveStartDateTextView?.text = formattedDate
            }
    }

    /**
     * Hides the live stream starting date.
     * If video buffering has started, calling this method will do nothing.
     */
    fun hideLiveStartDate() {
        liveStartDateContainerView?.isVisible = false
    }

    /**
     * Adds the custom button to the player.
     * This method must be called before the [setColors].
     * @param iconRes Icon resource for the button
     * @param onClick On click callback
     */
    fun showCustomButton(
        @DrawableRes iconRes: Int,
        onClick: () -> Unit,
    ) = customButton?.apply {
        isVisible = true
        setImageResource(iconRes)
        setOnClickListener { onClick() }
    }

    /**
     * Hides custom button.
     */
    fun hideCustomButton() {
        customButton?.isVisible = false
    }

    /**
     * Called every time analytics event fired.
     * @param event Analytics event name
     * @param data Analytics event string data
     */
    fun setAnalyticsCallback(
        callback: (event: String, data: String) -> Unit
    ) {
        analyticsCallback = callback
    }

    private fun getVideo(): KinescopeVideo? = kinescopePlayer?.getVideo()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAll()
    }

    fun setIsFullscreen(value: Boolean) {
        isVideoFullscreen = value
        seekView?.setFullscreenMode(value)
        settingsMenuView?.setFullscreenMode(value)
        updateFullscreenButton()
    }

    private fun updateBuffering() {
        if (bufferingView != null) {
            val showBufferingSpinner =
                kinescopePlayer?.exoPlayer != null
                        && kinescopePlayer!!.exoPlayer!!.playbackState == Player.STATE_BUFFERING
                        && (showBuffering == PlayerView.SHOW_BUFFERING_ALWAYS
                        || showBuffering == PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                        && kinescopePlayer!!.exoPlayer!!.playWhenReady)

            bufferingView!!.isVisible = showBufferingSpinner

            val view = bufferingView?.findViewById<ProgressBar>(R.id.kinescope_buffering)
            if (showBufferingSpinner) {
                view?.animateRotation()
            } else {
                view?.clearAnimation()
            }
        }
    }

    private fun updateAll() {
        updatePlayPauseButton()
        updateBuffering()
        updateTimeline()
        updateTitles()
    }

    private fun applyExoPlayerVisibility() {
        if (kinescopePlayer === null) {
            this.exoPlayerView?.visibility = View.GONE;
        } else {
            this.exoPlayerView?.visibility = View.VISIBLE;
        }
    }

    private fun applyKinescopePlayerOptions() {
        val options = kinescopePlayer?.kinescopePlayerOptions
        if (options != null) {
            val showControls = options.controls
            controlView?.isVisible = showControls
            fullscreenButton?.isVisible = showControls && options.fullscreen
            seekView?.isVisible = showControls && options.showSeekBar
            optionsButton?.isVisible = showControls && options.showOptionsButton
            pictureInPictureButton?.isVisible = showControls &&
                options.pictureInPicture &&
                KinescopePictureInPicture.isSupported(context)
            playPauseButton?.isVisible = showControls && options.showPlayPauseButton
            positionView?.isVisible = showControls && options.showDuration && !isLiveState
            durationView?.isVisible = false
            timeSeparatorView?.isVisible = false
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.PlaybackSpeed,
                showControls && options.showPlaybackSpeedInSettings,
            )
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.VideoQuality,
                showControls && options.showAudioOnlyQualityInSettings,
            )
            val video = getVideo()
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.Subtitles,
                showControls && options.showSubtitlesButton && !video?.subtitles.isNullOrEmpty(),
            )
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.Attachments,
                showControls && options.showAttachments && !video?.attachments.isNullOrEmpty(),
            )
        } else {
            playPauseButton?.isVisible = true
            controlView?.isVisible = true
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.PlaybackSpeed,
                true
            )
        }
    }

    /**
     * Re-applies [KinescopeVideoPlayer.kinescopePlayerOptions] to control chrome and settings entries.
     * Call after mutating options on an attached player.
     */
    fun refreshPlayerChrome() {
        kinescopePlayer?.kinescopePlayerOptions?.syncLegacyChromeFlags()
        applyKinescopePlayerOptions()
        applyAccentColor()
        updateAll()
    }

    fun applyTemplateOptions() {
        kinescopePlayer?.applyPlaybackOptions()
        refreshPlayerChrome()
        applyDefaultQuality()
    }

    private fun applyDefaultQuality() {
        val quality = kinescopePlayer?.kinescopePlayerOptions?.quality ?: return
        val variantId = when (quality.lowercase()) {
            "auto" -> KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID
            "audio", "audio_only", "audio-only" -> KinescopeQualityVariant.QUALITY_VARIANT_AUDIO_ONLY_ID
            else -> quality.toIntOrNull() ?: KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID
        }
        qualityManager?.setVariant(variantId)
    }

    private fun applyAccentColor() {
        val white = androidx.core.content.ContextCompat.getColor(context, R.color.white)
        val playedColor = kinescopePlayer?.kinescopePlayerOptions?.accentColor
            ?.let { hex -> runCatching { Color.parseColor(hex) }.getOrNull() }
            ?: androidx.core.content.ContextCompat.getColor(context, R.color.kinescope_primary_color)
        setColors(
            buttonColor = white,
            playedColor = playedColor,
        )
    }

    private fun tintControlIcon(view: View?, @ColorInt color: Int) {
        (view as? android.widget.ImageView)?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun updateTitles() {
        val video = getVideo() ?: return
        titleView?.apply {
            text = video.title
            isVisible = video.title.isNotEmpty()
        }
        authorView?.apply {
            text = video.subtitle
            isVisible = !video.subtitle.isNullOrEmpty()
        }
        if (
            selectedSubtitleIndex == SUBTITLES_OPTION_OFF_ID &&
            kinescopePlayer?.getShowSubtitles() == true &&
            video.subtitles.isNotEmpty()
        ) {
            selectedSubtitleIndex = 0
        }
        applyKinescopePlayerOptions()
    }

    private fun registerDoubleTapSeek(forward: Boolean): Int {
        val now = System.currentTimeMillis()
        doubleTapSeekStreakCount = if (
            lastDoubleTapSeekForward == forward &&
            now - lastDoubleTapSeekTimeMs <= DOUBLE_TAP_SEEK_STREAK_WINDOW_MS
        ) {
            doubleTapSeekStreakCount + 1
        } else {
            1
        }
        lastDoubleTapSeekForward = forward
        lastDoubleTapSeekTimeMs = now
        return doubleTapSeekStreakCount * DOUBLE_TAP_SEEK_SECONDS
    }

    private fun updatePlayPauseButton() {
        val button = playPauseButton as? ImageView ?: return
        val layoutParams = button.layoutParams ?: return
        val size = resources.getDimensionPixelSize(R.dimen.kinescope_play_pause_size)

        layoutParams.width = size
        layoutParams.height = size
        button.layoutParams = layoutParams
        button.clearColorFilter()
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        button.background = null
        button.setPadding(0, 0, 0, 0)

        val iconRes = when {
            shouldShowReplayButton() -> R.drawable.ic_controls_rewind
            shouldShowPauseButton() -> R.drawable.kinescope_controls_pause
            else -> R.drawable.kinescope_controls_play
        }
        button.setImageDrawable(createScaledControlIcon(iconRes, size))
        button.requestLayout()
    }

    private fun createScaledControlIcon(@DrawableRes iconRes: Int, sizePx: Int): Drawable? {
        val source = AppCompatResources.getDrawable(context, iconRes)?.mutate() ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        source.setBounds(0, 0, sizePx, sizePx)
        source.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    private fun enterScrubOverlayMode() {
        cancelControlOverlayAutoHide()
        showControlOverlay(animated = false)
        titleView?.isVisible = false
        authorView?.isVisible = false
        controlView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        seekView?.isVisible = true

        val density = resources.displayMetrics.density
        controlElevationBeforeScrub = controlView?.elevation ?: 0f
        controlView?.elevation = SCRUB_MODE_CONTROL_ELEVATION_DP * density
        controlView?.let { control ->
            (control.parent as? android.view.ViewGroup)?.bringChildToFront(control)
        }

        timeBar?.let { bar ->
            bar.setScrubVisualExpanded(expanded = true)
            bar.animate().cancel()
            bar.scaleX = 1f
            bar.post {
                bar.pivotX = bar.width / 2f
                bar.pivotY = bar.height / 2f
                bar.animate()
                    .scaleY(SCRUB_SEEKBAR_SCALE)
                    .setDuration(SCRUB_SEEKBAR_SCALE_DURATION_MS)
                    .start()
            }
        }
    }

    private fun exitScrubOverlayMode() {
        controlView?.elevation = controlElevationBeforeScrub
        controlView?.setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(
                context,
                R.color.kinescope_control_overlay_scrim,
            ),
        )

        timeBar?.let { bar ->
            bar.setScrubVisualExpanded(expanded = false)
            bar.animate().cancel()
            bar.scaleX = 1f
            bar.animate()
                .scaleY(1f)
                .setDuration(SCRUB_SEEKBAR_SCALE_DURATION_MS)
                .start()
        }
    }

    private fun scheduleControlOverlayAutoHide() {
        if (kinescopePlayer?.kinescopePlayerOptions?.controls != true) {
            return
        }
        if (settingsMenuView?.isVisible == true || scrubbing) {
            return
        }
        removeCallbacks(hideControlOverlayRunnable)
        postDelayed(hideControlOverlayRunnable, CONTROL_OVERLAY_AUTO_HIDE_MS)
    }

    private fun cancelControlOverlayAutoHide() {
        removeCallbacks(hideControlOverlayRunnable)
    }

    private fun showControlOverlay(animated: Boolean) {
        val overlay = controlView ?: return
        if (overlay.isVisible && overlay.alpha >= 1f) {
            updateAll()
            scheduleControlOverlayAutoHide()
            return
        }
        overlay.animate().cancel()
        overlay.isVisible = true
        if (!animated) {
            overlay.alpha = 1f
            updateAll()
            scheduleControlOverlayAutoHide()
            return
        }
        overlay.alpha = 0f
        overlay.animate()
            .alpha(1f)
            .setDuration(CONTROL_OVERLAY_FADE_DURATION_MS)
            .withEndAction {
                updateAll()
                scheduleControlOverlayAutoHide()
            }
            .start()
    }

    private fun hideControlOverlay(animated: Boolean) {
        val overlay = controlView ?: return
        if (!overlay.isVisible) {
            return
        }
        cancelControlOverlayAutoHide()
        overlay.animate().cancel()
        if (!animated) {
            overlay.isVisible = false
            overlay.alpha = 1f
            return
        }
        overlay.animate()
            .alpha(0f)
            .setDuration(CONTROL_OVERLAY_FADE_DURATION_MS)
            .withEndAction {
                overlay.isVisible = false
                overlay.alpha = 1f
            }
            .start()
    }

    private fun updateFullscreenButton() {
        if (fullscreenButton != null) {
            if (isVideoFullscreen) {
                (fullscreenButton as ImageView)
                    .setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_fullscreen_exit
                        )
                    )
            } else {
                (fullscreenButton as ImageView)
                    .setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_fullscreen
                        )
                    )
            }
        }
    }

    private fun toggleSettingsMenu() {
        if (settingsMenuView?.isVisible == true) {
            settingsMenuView?.dismiss()
            if (controlView?.isVisible == true) {
                scheduleControlOverlayAutoHide()
            }
            return
        }
        showSettingsMenu()
    }

    private fun showSettingsMenu() {
        cancelControlOverlayAutoHide()
        val playbackSpeedCurrentValue = playbackSpeedVariants
            .find { variant -> variant.speed == kinescopePlayer?.exoPlayer?.playbackSpeed }
            ?.name
            .orEmpty()

        val qualityCurrentValue = when {
            qualityManager?.isAudioOnlyQuality == true ->
                context.getString(R.string.settings_video_quality_audio_only)

            qualityManager?.isAutoQuality == true ->
                context.getString(
                    R.string.settings_video_quality_variant_auto_caption,
                    kinescopePlayer?.exoPlayer?.videoSize?.height.toString()
                )

            else -> qualityManager?.selectedVariant?.name.orEmpty()
        }

        settingsMenuView?.apply {
            setFullscreenMode(isVideoFullscreen)
            setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.PlaybackSpeed,
                value = playbackSpeedCurrentValue,
            )
            setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.VideoQuality,
                value = qualityCurrentValue
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.PlaybackSpeed,
                options = getSettingsMenuPlaybackSpeedOptions()
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.VideoQuality,
                options = getSettingsMenuVideoQualityOptions()
            )
            setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.Subtitles,
                value = getSubtitlesCurrentValueLabel(),
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.Subtitles,
                options = getSettingsMenuSubtitlesOptions(),
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.Attachments,
                options = getSettingsMenuAttachmentsOptions(),
            )
            show()
        }
        bringSettingsAboveOverlay()
    }

    private fun bringSettingsAboveOverlay() {
        settingsMenuView?.let { settings ->
            val density = resources.displayMetrics.density
            settings.elevation = SETTINGS_MENU_ELEVATION_DP * density
            (settings.parent as? android.view.ViewGroup)?.bringChildToFront(settings)
        }
    }

    private fun getSettingsMenuPlaybackSpeedOptions() =
        playbackSpeedVariants
            .mapIndexed { index, variant ->
                KinescopeSettingsOption(
                    id = index,
                    title = variant.name,
                    isSelected = kinescopePlayer?.exoPlayer?.playbackSpeed == variant.speed,
                )
            }

    private fun getSettingsMenuVideoQualityOptions(): List<KinescopeSettingsOption> {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val includeAudioOnly = options?.showAudioOnlyQualityInSettings != false
        return qualityManager?.variants
            .orEmpty()
            .map { variant ->
                KinescopeSettingsOption(
                    id = variant.id,
                    title = variant.name,
                    isSelected = variant.isSelected
                )
            }
            .toMutableList()
            .apply {
                if (includeAudioOnly) {
                    add(
                        KinescopeSettingsOption(
                            id = KinescopeQualityVariant.QUALITY_VARIANT_AUDIO_ONLY_ID,
                            title = resources.getString(R.string.settings_video_quality_audio_only),
                            isSelected = qualityManager?.isAudioOnlyQuality == true
                        )
                    )
                }
                add(
                    KinescopeSettingsOption(
                        id = KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID,
                        title = resources.getString(R.string.settings_video_quality_variant_auto),
                        isSelected = qualityManager?.isAutoQuality == true
                    )
                )
            }
    }

    private fun onSettingsOptionSelected(
        parameter: KinescopeSettingsView.Parameter,
        optionId: Int
    ) {
        when (parameter) {
            is KinescopeSettingsView.Parameter.PlaybackSpeed -> kinescopePlayer?.setPlaybackSpeed(
                speed = playbackSpeedVariants[optionId].speed
            )

            is KinescopeSettingsView.Parameter.VideoQuality -> qualityManager?.setVariant(
                id = optionId
            )

            is KinescopeSettingsView.Parameter.Subtitles -> applySubtitlesSelection(optionId)

            is KinescopeSettingsView.Parameter.Attachments ->
                getVideo()?.attachments?.getOrNull(optionId)?.let { attachment ->
                    onAttachmentSelected?.invoke(attachment)
                }

            else -> Unit
        }
    }

    private fun getSubtitlesCurrentValueLabel(): String {
        if (selectedSubtitleIndex == SUBTITLES_OPTION_OFF_ID) {
            return context.getString(R.string.settings_subtitles_off)
        }
        return getVideo()?.subtitles?.getOrNull(selectedSubtitleIndex)?.displayLabel().orEmpty()
    }

    private fun getSettingsMenuSubtitlesOptions(): List<KinescopeSettingsOption> {
        val subtitles = getVideo()?.subtitles.orEmpty()
        return buildList {
            add(
                KinescopeSettingsOption(
                    id = SUBTITLES_OPTION_OFF_ID,
                    title = context.getString(R.string.settings_subtitles_off),
                    isSelected = selectedSubtitleIndex == SUBTITLES_OPTION_OFF_ID,
                )
            )
            subtitles.forEachIndexed { index, subtitle ->
                add(
                    KinescopeSettingsOption(
                        id = index,
                        title = subtitle.displayLabel(),
                        isSelected = selectedSubtitleIndex == index,
                    )
                )
            }
        }
    }

    private fun getSettingsMenuAttachmentsOptions(): List<KinescopeSettingsOption> =
        getVideo()?.attachments.orEmpty().mapIndexed { index, attachment ->
            KinescopeSettingsOption(
                id = index,
                title = attachment.title,
                isSelected = false,
            )
        }

    private fun applySubtitlesSelection(optionId: Int) {
        selectedSubtitleIndex = optionId
        val trackSelector = kinescopePlayer?.exoPlayer?.trackSelector as? DefaultTrackSelector
            ?: return
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, optionId == SUBTITLES_OPTION_OFF_ID)
            .build()
    }

    private fun KinescopeVideoSubtitle.displayLabel(): String =
        description.ifBlank { language }.ifBlank { id }

    private fun shouldShowPauseButton(): Boolean {
        return kinescopePlayer?.exoPlayer != null && kinescopePlayer!!.exoPlayer!!.playbackState != Player.STATE_ENDED && kinescopePlayer!!.exoPlayer!!.playbackState != Player.STATE_IDLE && kinescopePlayer!!.exoPlayer!!.playWhenReady
    }

    private fun shouldShowReplayButton(): Boolean {
        return kinescopePlayer?.exoPlayer != null && kinescopePlayer!!.exoPlayer!!.playbackState == Player.STATE_ENDED
    }

    private fun setUIListeners() {
        controlView?.isVisible = false
        this.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        timeBar?.addListener(componentListener)
        playPauseButton?.setOnClickListener(componentListener)
        pictureInPictureButton?.setOnClickListener(componentListener)
        optionsButton?.setOnClickListener(componentListener)
        fullscreenButton?.setOnClickListener(componentListener)

        liveDataView?.setOnClickListener {
            if (!isLiveSynced) {
                kinescopePlayer?.exoPlayer?.let {
                    it.seekTo(it.duration)
                    isLiveSynced = true
                }
            }
        }
    }

    private fun updateTimeline() {
        val player: Player = kinescopePlayer?.exoPlayer ?: return

        currentWindowOffset = 0
        var durationUs: Long = 0
        val timeline = player.currentTimeline
        if (!timeline.isEmpty) {
            val currentWindowIndex = player.currentMediaItemIndex
            val firstWindowIndex = currentWindowIndex
            val lastWindowIndex = currentWindowIndex
            for (i in firstWindowIndex..lastWindowIndex) {
                if (i == currentWindowIndex) {
                    currentWindowOffset = Util.usToMs(durationUs)
                }
                timeline.getWindow(i, window)
                if (window.durationUs == C.TIME_UNSET) {
                    //Assertions.checkState(!multiWindowTimeBar)
                    break
                }
                durationUs += window.durationUs
            }
        }
        val durationMs = Util.usToMs(durationUs)
        durationView?.text = Util.getStringForTime(formatBuilder, formatter, durationMs)
        timeBar?.setDuration(durationMs)
        updateProgress()
    }

    private fun updateProgress() {
        if (!isAttachedToWindow) {
            return
        }
        val player: Player? = kinescopePlayer?.exoPlayer

        if (isLiveState) {
            timeBar?.let { bar ->
                player?.let {
                    when (isLiveSynced) {
                        true -> {
                            bar.setPosition(it.duration)
                            bar.setDuration(it.duration)
                        }

                        else -> {
                            bar.setPosition(it.currentPosition)
                            bar.setDuration(it.duration)
                        }
                    }
                }
            }
            return
        }

        var position: Long = 0
        var bufferedPosition: Long = 0
        var duration: Long = 0
        if (player != null) {
            position = currentWindowOffset + player.contentPosition
            bufferedPosition = currentWindowOffset + player.contentBufferedPosition
            duration = player.duration
        }

        positionView
            ?.takeIf { !scrubbing }
            ?.let {
                it.text = Util.getStringForTime(formatBuilder, formatter, position)
            }

        durationView?.text = Util.getStringForTime(formatBuilder, formatter, duration)

        timeBar?.setPosition(position)
        timeBar?.setBufferedPosition(bufferedPosition)
        if (progressUpdateListener != null) {
            progressUpdateListener.onProgressUpdate(position, bufferedPosition)
        }

        // Cancel any pending updates and schedule a new one if necessary.
        removeCallbacks(updateProgressRunnable)
        val playbackState = player?.playbackState ?: Player.STATE_IDLE
        if (player != null && player.isPlaying) {
            var mediaTimeDelayMs =
                if (timeBar != null) timeBar!!.preferredUpdateDelay else MAX_UPDATE_INTERVAL_MS.toLong()

            // Limit delay to the start of the next full second to ensure position display is smooth.
            val mediaTimeUntilNextFullSecondMs = 1000 - position % 1000
            mediaTimeDelayMs = Math.min(mediaTimeDelayMs, mediaTimeUntilNextFullSecondMs)

            // Calculate the delay until the next update in real time, taking playback speed into account.
            val playbackSpeed = player.playbackParameters.speed
            var delayMs =
                if (playbackSpeed > 0) (mediaTimeDelayMs / playbackSpeed).toLong() else MAX_UPDATE_INTERVAL_MS.toLong()

            // Constrain the delay to avoid too frequent / infrequent updates.
            delayMs = Util.constrainValue(
                delayMs,
                timeBarMinUpdateIntervalMs.toLong(),
                MAX_UPDATE_INTERVAL_MS.toLong()
            )
            postDelayed(updateProgressRunnable, delayMs)
        } else if (playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
            postDelayed(
                updateProgressRunnable,
                MAX_UPDATE_INTERVAL_MS.toLong()
            )
        }
    }

    private fun toggleControlUI() {
        val overlay = controlView ?: return
        if (overlay.isVisible) {
            hideControlOverlay(animated = true)
        } else {
            showControlOverlay(animated = true)
        }
    }

    private fun seekToTimeBarPosition(player: Player, positionMs: Long) {
        var positionMs = positionMs
        var windowIndex: Int
        val timeline = player.currentTimeline
        if (!timeline.isEmpty) {
            val windowCount = timeline.windowCount
            windowIndex = 0
            while (true) {
                val windowDurationMs = timeline.getWindow(windowIndex, window).durationMs
                if (positionMs < windowDurationMs) {
                    break
                } else if (windowIndex == windowCount - 1) {
                    // Seeking past the end of the last window should seek to the end of the timeline.
                    positionMs = windowDurationMs
                    break
                }
                positionMs -= windowDurationMs
                windowIndex++
            }
        } else {
            windowIndex = player.currentMediaItemIndex
        }
        seekTo(player, windowIndex, positionMs)
        updateProgress()
    }

    private fun seekTo(player: Player, windowIndex: Int, positionMs: Long) {
        player.seekTo(windowIndex, positionMs)
    }

    private fun dispatchPlayPause(player: Player) {
        val state = player.playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player.playWhenReady) {
            dispatchPlay(player)
        } else {
            dispatchPause(player)
        }
    }

    private fun dispatchPlay(player: Player) {
        val state = player.playbackState
        if (state == Player.STATE_IDLE) {
            player.prepare()
        } else if (state == Player.STATE_ENDED) {
            seekTo(player, player.currentMediaItemIndex, C.TIME_UNSET)
        }
        player.play()
        updatePlayPauseButton()
        if (controlView?.isVisible == true) {
            scheduleControlOverlayAutoHide()
        }

        getAnalyticsArguments().let { args ->
            analyticsManager.play(args = args)
        }
    }

    private fun dispatchPause(player: Player) {
        player.pause()
        if (controlView?.isVisible == true) {
            scheduleControlOverlayAutoHide()
        }

        if (isLiveState) {
            isLiveSynced = false
        }

        getAnalyticsArguments().let { args ->
            analyticsManager.pause(args = args)
        }
    }


    private fun setSubtitlesStyling() {
        exoPlayerView?.subtitleView?.setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                Color.BLACK,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                Color.WHITE,
                Typeface.SERIF
            )
        )
        //exoPlayerView?.subtitleView?.setFixedTextSize(TypedValue .COMPLEX_UNIT_SP, 24f)
        exoPlayerView?.subtitleView?.setBottomPaddingFraction(96f)
    }

    private fun setLiveBadgeState(isLiveSynced: Boolean) {
        liveBadgeCircleView?.background = ContextCompat.getDrawable(
            context,
            when (isLiveSynced) {
                true -> R.drawable.ic_live_synced
                else -> R.drawable.ic_live_not_synced
            }
        )
    }

    private fun showLiveTimeOffset(isShown: Boolean, position: Long) {
        liveBadgeCircleView?.isVisible = !isShown
        liveBadgeTextView?.isVisible = !isShown

        liveTimeOffsetTextView?.apply {
            isVisible = isShown
            text = resources.getString(
                R.string.live_time_offset,
                Util.getStringForTime(
                    formatBuilder,
                    formatter,
                    scrubbingLiveDurationCached - position
                )
            )
        }
    }

    private fun getAnalyticsArguments() =
        kinescopePlayer?.exoPlayer.getAnalyticsArguments(
            volume = audioManager.currentVolumeInPercent,
            isFullscreen = isVideoFullscreen,
        )

    fun hideControlsExceptPlayPause()  {
        controlView?.children?.forEach { child ->
            child.isVisible = (child == playPauseButton)
        }
    }
    fun hideAllControls(){
        controlView?.isVisible = false
    }

    /**
     * Prepares player chrome for Picture-in-Picture: hides controls and overlays so only video is visible.
     */
    fun prepareForPictureInPicture(preparing: Boolean) {
        if (preparing) {
            settingsMenuView?.isVisible = false
            seekView?.isVisible = false
            posterView?.isVisible = false
            liveStartDateContainerView?.isVisible = false
            hideAllControls()
        } else {
            applyKinescopePlayerOptions()
            updateAll()
        }
    }

    fun showAllControls() {
        controlView?.isVisible = true
        controlView?.children?.forEach { child ->
            child.isVisible = true
        }
        updateAll()
    }

}
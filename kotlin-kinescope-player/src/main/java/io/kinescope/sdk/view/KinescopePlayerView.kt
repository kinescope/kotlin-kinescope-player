package io.kinescope.sdk.view

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.animation.ValueAnimator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
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
import io.kinescope.sdk.models.players.syncLegacyChromeFlags
import io.kinescope.sdk.player.KinescopeGlideListener
import io.kinescope.sdk.player.KinescopePictureInPicture
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.player.quality.KinescopeQualityVariant
import io.kinescope.sdk.player.quality.getQualityVariantsList
import io.kinescope.sdk.player.speed.KinescopeSpeedVariant
import io.kinescope.sdk.player.subtitles.ProgressiveSubtitleCues
import io.kinescope.sdk.player.subtitles.ProgressiveSubtitleOverlay
import io.kinescope.sdk.player.tracks.TrackController
import io.kinescope.sdk.settings.KinescopeSettingsOption
import io.kinescope.sdk.settings.KinescopeSettingsView
import io.kinescope.sdk.settings.SubtitleStyle
import io.kinescope.sdk.settings.qualityBadgeForVariant
import io.kinescope.sdk.cast.KinescopeCastState
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

            val startedPlayback = oldPlayerView.hasStartedPlayback

            newPlayerView.let {
                it.setPlayer(player)
                it.trackController = oldPlayerView.trackController
                it.analyticsManager = oldPlayerView.analyticsManager
                it.restorePlaybackChromeState(startedPlayback = startedPlayback)

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
        private const val SCRUB_SEEKBAR_SCALE = 1.85f
        private const val SCRUB_SEEKBAR_SCALE_DURATION_MS = 150L
        private const val DOUBLE_TAP_SEEK_SECONDS = 10
        private const val DOUBLE_TAP_SEEK_STREAK_WINDOW_MS = 1500L
        private const val MOBILE_TEXT_SHADOW_RADIUS = 2f
        private const val MOBILE_TEXT_SHADOW_DX = 0.5f
        private const val MOBILE_TEXT_SHADOW_DY = 0.5f
        private const val MOBILE_TEXT_SHADOW_COLOR = 0xA3000000.toInt()
        private const val MOBILE_BACKGROUND_GRADIENT_HEIGHT_PX = 120f
        private const val MOBILE_BACKGROUND_REFERENCE_HEIGHT_PX = 432f
        private const val OPTIONS_BAR_ANIMATION_DURATION_MS = 280L
        private const val SUBTITLE_PROGRESS_UPDATE_INTERVAL_MS = 16
        private const val SUBTITLE_SIZE_FRACTION_OF_HEIGHT = 0.062f
        private val optionsBarAnimationInterpolator = DecelerateInterpolator()
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
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            KinescopeLogger.log(KinescopeLoggerLevel.PLAYER_VIEW, "single tap confirmed")
            toggleControlUI()
            return true
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
    private var boundPlaybackPlayer: Player? = null
    private var exoPlayerView: PlayerView? = null
    private var subtitleView: SubtitleView? = null
    private var progressiveSubtitleOverlay: ProgressiveSubtitleOverlay? = null
    private var controlView: FrameLayout? = null
    private var seekView: KinesopeSeekView? = null
    private var bufferingView: View? = null
    private var positionView: TextView? = null
    private var durationView: TextView? = null
    private var timeSeparatorView: View? = null
    private var timeBar: KinescopeTimeBar? = null

    private var buttonsContainer: ViewGroup? = null
    private var playPauseButton: ImageButton? = null
    private var optionsButton: View? = null
    private var optionsDotsButton: View? = null
    private var pictureInPictureButton: View? = null
    private var castButton: View? = null
    private var fullscreenButton: View? = null
    private var customButton: ImageButton? = null
    internal var trackController: TrackController? = null
    private var settingsMenuView: KinescopeSettingsView? = null
    private var subtitleStyle = SubtitleStyle()
    private var pendingCueGroup: CueGroup? = null
    private var learnedCueDurationUs: Long = C.TIME_UNSET

    private var titleView: TextView? = null
    private var authorView: TextView? = null
    private var descriptionBlock: View? = null
    private var timeContainer: View? = null
    private var mobileHeaderGradient: View? = null
    private var mobileFooterGradient: View? = null
    private var controlBar: View? = null
    private var controlBarEndSpacer: View? = null
    private var optionsExpandedGroup: ViewGroup? = null
    private var optionsExpandableStrip: View? = null
    private var optionsExpandableContent: View? = null
    private var optionsExpandableStripAnimator: ValueAnimator? = null
    private var isMobilePlayerChrome = false
    private var isOptionsBarExpanded = false
    private var isPictureInPictureActive = false
    private var timeBarLayoutWeight = 1f
    private var wasCompactOptionsChrome = false

    private var liveDataView: View? = null
    private var liveBadgeCircleView: View? = null
    private var liveBadgeTextView: View? = null
    private var liveTimeOffsetTextView: TextView? = null
    private var liveStartDateContainerView: View? = null
    private var liveStartDateTextView: TextView? = null

    private var castOverlayView: View? = null
    private var castDeviceView: TextView? = null
    private var castPlayPauseView: ImageButton? = null
    private var castPositionView: TextView? = null
    private var castDurationView: TextView? = null
    private var castSeekBar: android.widget.SeekBar? = null
    private var castStopView: View? = null
    private var castSupported = false
    private var castRouteAvailable = false
    private var castOverlaySeekListener: ((Float) -> Unit)? = null
    private var isUpdatingCastSeekBar = false

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

    private var hasStartedPlayback = false
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

    private val subtitleUpdateRunnable = Runnable {
        applyProgressiveSubtitles()
        scheduleSubtitleUpdates()
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

    private val localExoPlayer: ExoPlayer?
        get() = kinescopePlayer?.exoPlayer

    private val activePlaybackPlayer: Player?
        get() = kinescopePlayer?.playbackPlayer

    private fun bindPlaybackPlayer(player: Player?) {
        boundPlaybackPlayer?.removeListener(componentListener)
        boundPlaybackPlayer = player
        player?.addListener(componentListener)
    }

    private fun bindLocalEnginePlayer(player: ExoPlayer?) {
        player?.removeListener(localEngineListener)
        player?.addListener(localEngineListener)
    }

    private fun detachPlayerBindings() {
        bindPlaybackPlayer(null)
        localExoPlayer?.removeListener(localEngineListener)
        kinescopePlayer?.getOrCreatePlayerHost()?.onActivePlayerChanged = null
    }

    private val localEngineListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            localExoPlayer?.let { player ->
                with(player.trackSelector as DefaultTrackSelector) {
                    trackController?.updateQualityVariants(
                        variants = getQualityVariantsList()
                    )
                }
            }
            trackController?.updateTextTracks(tracks)
            trackController?.updateAudioTracks(tracks)
            if (settingsMenuView?.isVisible == true) {
                settingsMenuView?.runBatchUpdate {
                    updateAudioTracksSettingsVisibility()
                    applySettingsMenuCurrentValues()
                }
            } else {
                updateAudioTracksSettingsVisibility()
            }
            val video = getVideo()
            if (video != null && kinescopePlayer?.getShowSubtitles() == true) {
                if (trackController?.selectedSubtitleIndex == TrackController.SUBTITLES_OFF_ID) {
                    trackController?.ensureDefaultSubtitleEnabled(
                        showSubtitles = true,
                        subtitles = video.subtitles,
                    )
                } else {
                    trackController?.applySubtitleSelection(
                        trackController?.selectedSubtitleIndex ?: TrackController.SUBTITLES_OFF_ID,
                    )
                }
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            if (cueGroup.cues.isNotEmpty()) {
                val newStartUs = cueGroup.presentationTimeUs
                val previousGroup = pendingCueGroup
                if (previousGroup != null) {
                    val previousStartUs = previousGroup.presentationTimeUs
                    if (newStartUs > previousStartUs) {
                        learnedCueDurationUs = newStartUs - previousStartUs
                    }
                }
                pendingCueGroup = cueGroup
            } else {
                val player = localExoPlayer
                val activeGroup = pendingCueGroup
                if (player != null && activeGroup != null) {
                    val positionUs = Util.msToUs(player.contentPosition)
                    val cueStartUs = activeGroup.presentationTimeUs
                    if (positionUs > cueStartUs) {
                        learnedCueDurationUs = positionUs - cueStartUs
                    }
                }
                pendingCueGroup = null
            }
            ensureSubtitleUpdatesRunning()
            applyProgressiveSubtitles()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height != 0) {
                getAnalyticsArguments().let { args ->
                    when (trackController?.isAutoQuality) {
                        true -> analyticsManager.autoQualityChanged(args = args)
                        else -> analyticsManager.qualityChanged(args = args)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            localExoPlayer?.let { player ->
                if (player.playbackState == Player.STATE_IDLE && player.playWhenReady) {
                    dispatchPlay(player)
                }
            }
        }
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
                    Player.STATE_IDLE -> {
                        hasStartedPlayback = false
                    }

                    Player.STATE_BUFFERING -> {
                        analyticsManager.buffering()
                        if (!hasStartedPlayback && activePlaybackPlayer?.playWhenReady == true) {
                            hidePoster()
                        }
                    }

                    Player.STATE_READY -> {
                        analyticsManager.ready(args = args)
                        if (isLiveState) {
                            hidePoster()
                            hideLiveStartDate()
                        } else {
                            applyVideoPoster()
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
            updatePlayPauseButton()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            if (isPlaying) {
                hasStartedPlayback = true
                hidePoster()
            }
            progressiveSubtitleOverlay?.setAdvancementEnabled(isPlaying)
            if (isPlaying) {
                ensureSubtitleUpdatesRunning()
            } else {
                scheduleSubtitleUpdates()
            }
            applyProgressiveSubtitles()
            updateBuffering()
            if (isPictureInPictureActive) {
                showPipMinimalControls()
                return
            }
            updatePlayPauseButton()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (isPictureInPictureActive) {
                updateBuffering()
                showPipMinimalControls()
                return
            }
            if (!playWhenReady && !shouldShowReplayButton() && usesGradientChrome()) {
                showControlOverlay(animated = true)
            }
            if (controlView?.isVisible == true) {
                scheduleControlOverlayAutoHide()
            }
            updateMobileBackgroundGradients()
            updateBuffering()
            updatePlayPauseButton()
        }

        override fun onScrubStart(timeBar: TimeBar, position: Long) {
            scrubbing = true
            enterScrubOverlayMode()
            seekView?.showScrubOverlay()

            if (isLiveState) {
                scrubbingLiveDurationCached = activePlaybackPlayer?.duration ?: 0
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
                val player = activePlaybackPlayer ?: return
                seekToTimeBarPosition(player, position)
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
            val player: Player = activePlaybackPlayer ?: return

            if (playPauseButton === view) {
                dispatchPlayPause(player)
            } else if (fullscreenButton === view) {
                onFullscreenButtonCallback?.invoke()
            } else if (pictureInPictureButton === view) {
                onPictureInPictureButtonCallback?.invoke()
            } else if (optionsButton === view) {
                onOptionsButtonClick()
            }
        }

        override fun onDismiss() {

        }
    }

    init {
        inflate(context, R.layout.view_kinesope_player, this)
        bindRootLayoutConstraints()
        exoPlayerView = findViewById(R.id.view_exoplayer)
        subtitleView = findViewById(R.id.kinescope_subtitle_view)
        progressiveSubtitleOverlay = ProgressiveSubtitleOverlay(
            container = findViewById(R.id.kinescope_progressive_subtitle_container),
            linesContainer = findViewById(R.id.kinescope_progressive_subtitle_lines),
            topView = findViewById(R.id.kinescope_progressive_subtitle_top),
            bottomView = findViewById(R.id.kinescope_progressive_subtitle_bottom),
        )
        progressiveSubtitleOverlay?.setOnEnsureUpdatesRunning { ensureSubtitleUpdatesRunning() }
        exoPlayerView?.subtitleView?.isVisible = false

        bufferingView = findViewById(R.id.view_buffering)
        bufferingView?.isVisible = false

        gestureListener = KinescopeGestureListener(this@KinescopePlayerView)
        gestureDetector = GestureDetectorCompat(context, gestureListener)

        posterView = findViewById(R.id.poster_iv)
        mobileHeaderGradient = findViewById(R.id.kinescope_mobile_header_gradient)
        mobileFooterGradient = findViewById(R.id.kinescope_mobile_footer_gradient)

        controlView = findViewById(R.id.view_control)
        seekView = findViewById(R.id.kinescope_seek_view)

        timeBar = controlView?.findViewById(R.id.kinescope_progress)
        positionView = controlView?.findViewById(R.id.kinescope_position)
        durationView = controlView?.findViewById(R.id.kinescope_duration)
        timeSeparatorView = controlView?.findViewById(R.id.time_separator_view)

        buttonsContainer = controlView?.findViewById(R.id.buttons_container_ll)
        optionsExpandedGroup = controlView?.findViewById(R.id.kinescope_options_expanded_group)
        optionsExpandableStrip = controlView?.findViewById(R.id.kinescope_options_expandable_strip)
        optionsExpandableContent = controlView?.findViewById(R.id.kinescope_options_expandable_content)
        playPauseButton = controlView?.findViewById(R.id.kinescope_play_pause)
        pictureInPictureButton = controlView?.findViewById(R.id.kinescope_picture_in_picture)
        castButton = controlView?.findViewById(R.id.kinescope_cast)
        optionsButton = controlView?.findViewById(R.id.kinescope_settings)
        optionsDotsButton = controlView?.findViewById(R.id.kinescope_options_dots)
        fullscreenButton = controlView?.findViewById(R.id.kinescope_fullscreen)
        customButton = controlView?.findViewById(R.id.custom_btn)

        titleView = controlView?.findViewById(R.id.kinescope_title)
        authorView = controlView?.findViewById(R.id.kinescope_author)
        descriptionBlock = controlView?.findViewById(R.id.kinescope_description_block)
        timeContainer = controlView?.findViewById(R.id.kinescope_time_container)
        controlBar = controlView?.findViewById(R.id.kinescope_control_bar)
        controlBarEndSpacer = controlView?.findViewById(R.id.kinescope_control_bar_end_spacer)

        liveDataView = controlView?.findViewById(R.id.live_data_ll)
        liveBadgeCircleView = controlView?.findViewById(R.id.live_badge_circle_view)
        liveBadgeTextView = controlView?.findViewById(R.id.live_badge_tv)
        liveTimeOffsetTextView = controlView?.findViewById(R.id.live_time_offset)
        liveStartDateContainerView = findViewById(R.id.live_start_date_ll)
        liveStartDateTextView = findViewById(R.id.live_start_date_tv)

        castOverlayView = findViewById(R.id.kinescope_cast_overlay)
        castDeviceView = findViewById(R.id.kinescope_cast_device_tv)
        castPlayPauseView = findViewById(R.id.kinescope_cast_play_pause)
        castPositionView = findViewById(R.id.kinescope_cast_position_tv)
        castDurationView = findViewById(R.id.kinescope_cast_duration_tv)
        castSeekBar = findViewById(R.id.kinescope_cast_seek_bar)
        castStopView = findViewById(R.id.kinescope_cast_stop_tv)
        castSeekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingCastSeekBar) {
                    castOverlaySeekListener?.invoke(progress / 1000f)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        settingsMenuView = findViewById<KinescopeSettingsView?>(R.id.settings_menu)
            .apply {
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.Subtitles,
                    title = resources.getString(R.string.settings_parameter_subtitles),
                    icon = R.drawable.ic_menu_cc,
                )
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.AudioTracks,
                    title = resources.getString(R.string.settings_parameter_audio_tracks),
                    icon = R.drawable.ic_menu_audio,
                )
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.PlaybackSpeed,
                    title = resources.getString(R.string.settings_parameter_playback_speed),
                    icon = R.drawable.ic_menu_speed,
                )
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.VideoQuality,
                    title = resources.getString(R.string.settings_parameter_video_quality),
                    icon = R.drawable.ic_menu_quality,
                )
                addParameter(
                    parameter = KinescopeSettingsView.Parameter.Attachments,
                    title = resources.getString(R.string.settings_parameter_attachments),
                    icon = R.drawable.ic_attachments,
                )
                onOptionSelected = ::onSettingsOptionSelected
                onSubtitleStyleChanged = { style ->
                    subtitleStyle = style
                    applySubtitleStyle()
                }
            }
        settingsMenuView?.setAnchorView(optionsButton)
        settingsMenuView?.setFullscreenMode(isVideoFullscreen)

        applyKinescopePlayerOptions()
        applyPlayerChromeLayout()
        applySubtitleStyle()
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = right - left != oldRight - oldLeft
            val heightChanged = bottom - top != oldBottom - oldTop
            if ((widthChanged || heightChanged) && width > 0 && height > 0) {
                applySubtitleStyle()
            }
        }
        setUIListeners()
        updatePlayPauseButton()
    }

    /**
     * Attaches Kinescope player and loads KinescopePlayerOptions
     * to this KinescopePlayerView
     *
     */
    private fun restorePlaybackChromeState(startedPlayback: Boolean) {
        hasStartedPlayback = startedPlayback
        applyExoPlayerVisibility()
        updateBuffering()
        updatePlayPauseButton()
    }

    fun setPlayer(kinescopePlayer: KinescopeVideoPlayer?) {
        Assertions.checkState(Looper.myLooper() == Looper.getMainLooper())
        if (this.kinescopePlayer === kinescopePlayer) return
        detachPlayerBindings()
        hasStartedPlayback = false
        pendingCueGroup = null
        learnedCueDurationUs = C.TIME_UNSET
        stopSubtitleUpdates()
        subtitleView?.setCues(emptyList())
        progressiveSubtitleOverlay?.clear()
        this.kinescopePlayer = kinescopePlayer

        kinescopePlayer?.exoPlayer?.let { player ->
            this.trackController =
                TrackController(context, player.trackSelector as DefaultTrackSelector)
        }

        kinescopePlayer?.onSourceChanged = { source, metricUrl ->
            hasStartedPlayback = false
            pendingCueGroup = null
            learnedCueDurationUs = C.TIME_UNSET
            stopSubtitleUpdates()
            subtitleView?.setCues(emptyList())
            progressiveSubtitleOverlay?.clear()
            applyDefaultQuality()
            analyticsManager.setSource(
                source = source,
                metricUrl = metricUrl,
            )
            postVideoLoadedChromeUpdate()
        }
        exoPlayerView?.player = kinescopePlayer?.exoPlayer
        bindLocalEnginePlayer(kinescopePlayer?.exoPlayer)
        kinescopePlayer?.getOrCreatePlayerHost()?.let { host ->
            host.onActivePlayerChanged = { newPlayer ->
                bindPlaybackPlayer(newPlayer)
                updateAll()
            }
            bindPlaybackPlayer(host.activePlayer)
        }
        applyProgressiveSubtitles()
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
            tintControlIcon(castButton, color)
            tintControlIcon(optionsButton, color)
            tintControlIcon(optionsDotsButton, color)
            tintControlIcon(fullscreenButton, color)
            tintControlIcon(playPauseButton, color)
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
        with(activePlaybackPlayer?.playbackState) {
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
        with(activePlaybackPlayer?.playbackState) {
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
        applyPlayerChromeLayout()
        updateAll()
    }

    fun setIsFullscreen(value: Boolean) {
        isVideoFullscreen = value
        seekView?.setFullscreenMode(value)
        settingsMenuView?.setFullscreenMode(value)
        applyPlayerChromeLayout()
        updateFullscreenButton()
    }

    private fun isBufferingSpinnerVisible(): Boolean {
        val player = localExoPlayer ?: return false
        if (!hasStartedPlayback && player.playbackState == Player.STATE_BUFFERING) {
            return true
        }
        return hasStartedPlayback &&
            player.playbackState == Player.STATE_BUFFERING &&
            (showBuffering == PlayerView.SHOW_BUFFERING_ALWAYS ||
                showBuffering == PlayerView.SHOW_BUFFERING_WHEN_PLAYING && player.playWhenReady)
    }

    private fun isVideoLoaded(): Boolean = getVideo() != null

    private fun postVideoLoadedChromeUpdate() {
        val update = {
            applyVideoPoster()
            updatePlayPauseButton()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update()
        } else {
            post(update)
        }
    }

    private fun applyVideoPoster() {
        if (hasStartedPlayback || !isVideoLoaded()) {
            return
        }
        val player = localExoPlayer
        if (!isLiveState &&
            player?.playbackState == Player.STATE_BUFFERING &&
            player.playWhenReady
        ) {
            return
        }
        getVideo()?.poster?.url?.let(::showPoster)
    }

    private fun bindRootLayoutConstraints() {
        if (childCount == 0) {
            return
        }
        val root = getChildAt(0)
        root.layoutParams = LayoutParams(0, 0).apply {
            topToTop = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
        }
    }

    private fun isControlOverlayVisible(): Boolean {
        val overlay = controlView ?: return false
        return overlay.isVisible && overlay.alpha > 0.01f
    }

    private fun updateBuffering() {
        val player = localExoPlayer
        val waitingForFirstPlayback = player != null && !hasStartedPlayback && player.playWhenReady
        val showBufferingSpinner = isBufferingSpinnerVisible()

        bufferingView?.let { overlay ->
            overlay.isVisible = showBufferingSpinner
            overlay.setBackgroundColor(
                if (waitingForFirstPlayback) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.TRANSPARENT
                },
            )
            val spinner = overlay.findViewById<ProgressBar>(KinescopeSdkViewIds.bufferingSpinner)
            if (showBufferingSpinner) {
                (overlay.parent as? ViewGroup)?.bringChildToFront(overlay)
                settingsMenuView?.let { settings ->
                    (settings.parent as? ViewGroup)?.bringChildToFront(settings)
                }
                spinner?.animateRotation()
            } else {
                spinner?.clearAnimation()
            }
        }

        exoPlayerView?.visibility = when {
            kinescopePlayer == null -> View.GONE
            !hasStartedPlayback -> View.INVISIBLE
            else -> View.VISIBLE
        }
    }

    private fun updateAll() {
        if (isPictureInPictureActive) {
            return
        }
        updatePlayPauseButton()
        updateBuffering()
        updateTimeline()
        updateTitles()
        updateMobileBackgroundGradients()
    }

    private fun applyExoPlayerVisibility() {
        updateBuffering()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            applyPlayerChromeLayout()
        }
    }

    private fun getEffectivePlayerWidthPx(): Int {
        return when {
            width > 0 -> width
            measuredWidth > 0 -> measuredWidth
            else -> resources.displayMetrics.widthPixels
        }
    }

    private fun usesMobilePlayerChrome(): Boolean {
        if (isVideoFullscreen) {
            return false
        }
        val widthDp = getEffectivePlayerWidthPx() / resources.displayMetrics.density
        return widthDp <= resources.getDimension(R.dimen.kinescope_mobile_layout_max_width)
    }

    private fun usesGradientChrome(): Boolean {
        return isMobilePlayerChrome || isVideoFullscreen
    }

    private fun getControlOverlayBackgroundColor(): Int {
        return if (usesGradientChrome()) {
            Color.TRANSPARENT
        } else {
            ContextCompat.getColor(context, R.color.kinescope_control_overlay_scrim)
        }
    }

    private fun shouldShowCenterPlayControl(showControls: Boolean): Boolean {
        if (isPictureInPictureActive) {
            return false
        }
        if (!showControls) {
            return false
        }
        if (isBufferingSpinnerVisible()) {
            return false
        }
        val showPlayPause = kinescopePlayer?.kinescopePlayerOptions?.showPlayPauseButton ?: true
        if (!showPlayPause) {
            return false
        }
        if (!isMobilePlayerChrome) {
            return true
        }
        if (controlView?.isVisible == true && !scrubbing) {
            return true
        }
        return shouldShowReplayButton() || !shouldShowPauseButton()
    }

    private fun usesCompactOptionsChrome(): Boolean {
        return isMobilePlayerChrome || isVideoFullscreen
    }

    private fun applyPlayerChromeLayout() {
        val mobile = usesMobilePlayerChrome()
        val compactOptions = mobile || isVideoFullscreen
        if (isMobilePlayerChrome != mobile || wasCompactOptionsChrome != compactOptions) {
            isOptionsBarExpanded = false
        }
        wasCompactOptionsChrome = compactOptions
        isMobilePlayerChrome = mobile

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.kinescope_mobile_control_margin_horizontal)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.kinescope_mobile_control_margin_bottom)
        val buttonSize = resources.getDimensionPixelSize(
            if (mobile) {
                R.dimen.kinescope_mobile_media_button_size
            } else {
                R.dimen.kinescope_media_button_height
            },
        )

        descriptionBlock?.let { block ->
            (block.layoutParams as? MarginLayoutParams)?.let { params ->
                params.marginStart = horizontalMargin
                params.topMargin = horizontalMargin
                block.layoutParams = params
            }
        }

        controlBar?.setPadding(
            horizontalMargin,
            0,
            horizontalMargin,
            if (mobile) bottomMargin else (4 * resources.displayMetrics.density).toInt(),
        )

        applyControlBarLayout(mobile)

        listOf(fullscreenButton, optionsButton, optionsDotsButton, pictureInPictureButton, castButton, customButton).forEach { button ->
            button?.layoutParams?.let { params ->
                params.width = buttonSize
                params.height = buttonSize
                button.layoutParams = params
            }
        }

        titleView?.apply {
            if (mobile) {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.BOLD)
                setShadowLayer(
                    MOBILE_TEXT_SHADOW_RADIUS,
                    MOBILE_TEXT_SHADOW_DX,
                    MOBILE_TEXT_SHADOW_DY,
                    MOBILE_TEXT_SHADOW_COLOR,
                )
            } else {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.NORMAL)
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        }

        authorView?.apply {
            (layoutParams as? MarginLayoutParams)?.topMargin = if (mobile) {
                resources.getDimensionPixelSize(R.dimen.kinescope_mobile_description_gap)
            } else {
                0
            }
            if (mobile) {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setShadowLayer(
                    MOBILE_TEXT_SHADOW_RADIUS,
                    MOBILE_TEXT_SHADOW_DX,
                    MOBILE_TEXT_SHADOW_DY,
                    MOBILE_TEXT_SHADOW_COLOR,
                )
            } else {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        }

        controlView?.setBackgroundColor(getControlOverlayBackgroundColor())
        seekView?.setMobilePlayerChrome(mobile)
        updateMobileBackgroundGradientHeight()
        if (isPictureInPictureActive) {
            hidePipOverlays()
            return
        }
        updateOptionsBarUi()
        updateMobileBackgroundGradients()
        applyKinescopePlayerOptions()
    }

    private fun applyControlBarLayout(mobile: Boolean) {
        val sectionGap = resources.getDimensionPixelSize(
            if (mobile) {
                R.dimen.kinescope_mobile_control_gap
            } else {
                R.dimen.kinescope_control_section_gap
            },
        )
        val progressLeadingGap = if (mobile) {
            0
        } else {
            resources.getDimensionPixelSize(R.dimen.kinescope_control_progress_leading_gap)
        }
        val mobileRowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_mobile_media_button_size)
        val progressHeight = resources.getDimensionPixelSize(R.dimen.kinescope_progress_control_height)
        val controlRowHeight = if (mobile) mobileRowHeight else progressHeight

        timeContainer?.minimumHeight = if (mobile) controlRowHeight else 0

        (timeContainer?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginEnd = sectionGap
            timeContainer?.layoutParams = params
        }

        (timeBar?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = progressLeadingGap
            params.marginEnd = 0
            params.width = 0
            params.height = controlRowHeight
            if (params is LinearLayout.LayoutParams) {
                params.weight = 1f
                params.gravity = android.view.Gravity.CENTER_VERTICAL
            }
            timeBar?.layoutParams = params
        }

        (buttonsContainer?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = 0
            buttonsContainer?.layoutParams = params
        }

        (optionsButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = if (mobile || isVideoFullscreen) sectionGap else 0
            optionsButton?.layoutParams = params
        }

        (pictureInPictureButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = 0
            params.marginEnd = 0
            pictureInPictureButton?.layoutParams = params
        }

        (castButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = if (mobile || isVideoFullscreen) sectionGap else 0
            castButton?.layoutParams = params
        }

        (optionsDotsButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = if (mobile || isVideoFullscreen) sectionGap else 0
            optionsDotsButton?.layoutParams = params
        }

        (fullscreenButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = if (mobile || isVideoFullscreen) sectionGap else 0
            fullscreenButton?.layoutParams = params
        }
    }

    private fun updateMobileBackgroundGradientHeight() {
        val playerHeight = when {
            height > 0 -> height
            measuredHeight > 0 -> measuredHeight
            else -> return
        }
        val gradientHeight = (
            playerHeight * MOBILE_BACKGROUND_GRADIENT_HEIGHT_PX / MOBILE_BACKGROUND_REFERENCE_HEIGHT_PX
            ).toInt().coerceAtLeast(1)
        listOf(mobileHeaderGradient, mobileFooterGradient).forEach { view ->
            view?.layoutParams = view?.layoutParams?.apply {
                this.height = gradientHeight
            }
        }
    }

    private fun isPlaybackPaused(): Boolean {
        val player = activePlaybackPlayer ?: return false
        return !player.playWhenReady &&
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE
    }

    private fun shouldShowMobileBackgroundGradients(controlsVisible: Boolean? = null): Boolean {
        if (isPictureInPictureActive) {
            return false
        }
        val overlayVisible = controlsVisible ?: (controlView?.isVisible == true)
        return usesGradientChrome() &&
            !scrubbing &&
            kinescopePlayer?.kinescopePlayerOptions?.controls == true &&
            (isPlaybackPaused() || overlayVisible)
    }

    private fun updateMobileBackgroundGradients(
        animated: Boolean = false,
        controlsVisible: Boolean? = null,
    ) {
        setMobileBackgroundGradientsVisible(
            visible = shouldShowMobileBackgroundGradients(controlsVisible),
            animated = animated,
        )
    }

    private fun setMobileBackgroundGradientsVisible(visible: Boolean, animated: Boolean) {
        val gradients = listOfNotNull(mobileHeaderGradient, mobileFooterGradient)
        if (gradients.isEmpty()) {
            return
        }

        if (visible && gradients.all { it.isVisible && it.alpha >= 1f }) {
            return
        }
        if (!visible && gradients.all { !it.isVisible }) {
            return
        }

        gradients.forEach { it.animate().cancel() }

        if (visible) {
            if (!animated) {
                gradients.forEach {
                    it.isVisible = true
                    it.alpha = 1f
                }
                return
            }
            gradients.forEach {
                it.isVisible = true
                it.alpha = 0f
            }
            gradients.forEach { view ->
                view.animate()
                    .alpha(1f)
                    .setDuration(CONTROL_OVERLAY_FADE_DURATION_MS)
                    .start()
            }
            return
        }

        if (!animated) {
            gradients.forEach {
                it.isVisible = false
                it.alpha = 1f
            }
            return
        }

        gradients.filter { it.isVisible }.forEach { view ->
            view.animate()
                .alpha(0f)
                .setDuration(CONTROL_OVERLAY_FADE_DURATION_MS)
                .withEndAction {
                    view.isVisible = false
                    view.alpha = 1f
                }
                .start()
        }
    }

    private fun setOptionsButtonIcon(@DrawableRes iconRes: Int) {
        val button = optionsButton as? ImageView ?: return
        val colorFilter = button.colorFilter
        button.setImageDrawable(AppCompatResources.getDrawable(context, iconRes))
        button.colorFilter = colorFilter
    }

    private fun updateOptionsButtonIcon() {
        if (usesCompactOptionsChrome()) {
            return
        }
        isOptionsBarExpanded = false
        setOptionsButtonIcon(R.drawable.ic_settings)
    }

    private fun updateOptionsButtonsVisibility() {
        val showOptions = kinescopePlayer?.kinescopePlayerOptions?.showOptionsButton != false
        if (usesCompactOptionsChrome()) {
            optionsDotsButton?.isVisible = showOptions
            if (!isOptionsBarExpanded) {
                resetExpandedButtonsTransform()
                optionsExpandableStrip?.isVisible = false
            }
            updateExpandedButtonsChildVisibility()
        } else {
            optionsDotsButton?.isVisible = false
            optionsExpandableStrip?.isVisible = true
            resetExpandedButtonsTransform()
            optionsExpandedGroup?.isVisible = true
            updateExpandedButtonsChildVisibility()
            updateFullscreenButtonVisibility()
        }
    }

    private fun updateExpandedButtonsChildVisibility() {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val showOptions = options?.showOptionsButton != false
        val compactExpanded = usesCompactOptionsChrome() && isOptionsBarExpanded
        optionsButton?.isVisible = showControls &&
            showOptions &&
            (!usesCompactOptionsChrome() || compactExpanded)
        pictureInPictureButton?.isVisible = showControls &&
            options?.pictureInPicture == true &&
            KinescopePictureInPicture.isSupported(context) &&
            (!usesCompactOptionsChrome() || compactExpanded)
        updateCastButtonVisibility()
    }

    private fun updateCastButtonVisibility() {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val compactExpanded = usesCompactOptionsChrome() && isOptionsBarExpanded
        castButton?.isVisible = showControls &&
            castSupported &&
            options?.showCastButton == true &&
            (!usesCompactOptionsChrome() || compactExpanded)
    }

    private fun shouldShowCompactExpandedSettings(): Boolean {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val showOptions = options?.showOptionsButton != false
        return showControls && showOptions
    }

    private fun updateFullscreenButtonVisibility() {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        fullscreenButton?.isVisible = showControls && options?.fullscreen != false
    }

    private fun lockTimeBarWidthForFade() {
        val bar = timeBar ?: return
        val lockedWidth = bar.width
        if (lockedWidth <= 0) {
            return
        }
        (bar.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            timeBarLayoutWeight = params.weight
            params.width = lockedWidth
            params.weight = 0f
            bar.layoutParams = params
        }
    }

    private fun restoreTimeBarFlexibleWidth() {
        val bar = timeBar ?: return
        (bar.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = 0
            params.weight = timeBarLayoutWeight
            bar.layoutParams = params
        }
    }

    private fun resetExpandedButtonsTransform() {
        optionsExpandedGroup?.let { group ->
            group.alpha = 1f
            group.translationX = 0f
            group.pivotX = group.width / 2f
        }
    }

    private fun cancelCompactExpandedChromeAnimations() {
        optionsExpandableStripAnimator?.cancel()
        optionsExpandableStripAnimator = null
    }

    private fun measureOptionsExpandableContentWidth(): Int {
        val content = optionsExpandableContent ?: return 0
        val height = optionsExpandableStrip?.height?.takeIf { it > 0 }
            ?: getDefaultMediaButtonSize().toInt()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        content.measure(widthSpec, heightSpec)
        return content.measuredWidth
    }

    private fun updateExpandableStripLayout(widthPx: Int) {
        val strip = optionsExpandableStrip ?: return
        val params = (strip.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.width = widthPx
        strip.layoutParams = params
    }

    private fun resetCompactExpandedChromeTransforms() {
        cancelCompactExpandedChromeAnimations()
        optionsExpandableContent?.alpha = 1f
        optionsExpandableContent?.translationX = 0f
        optionsExpandableStrip?.let { strip ->
            val params = (strip.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            strip.layoutParams = params
        }
        resetExpandedButtonsTransform()
    }

    private fun hideCompactExpandedChromeForAnimation() {
        cancelCompactExpandedChromeAnimations()
        optionsExpandableStrip?.isVisible = false
        resetCompactExpandedChromeTransforms()
    }

    private fun animateCompactOptionsProgressOut(onEnd: (() -> Unit)? = null) {
        val showProgress = timeBar?.isVisible == true
        val showTimeContainer = timeContainer?.isVisible == true
        if (!showProgress && !showTimeContainer) {
            onEnd?.invoke()
            return
        }

        if (showProgress && (timeBar?.width ?: 0) <= 0) {
            controlBar?.post { animateCompactOptionsProgressOut(onEnd) }
            return
        }

        if (showProgress) {
            lockTimeBarWidthForFade()
        }

        var pending = 0
        fun maybeEnd() {
            pending--
            if (pending <= 0) {
                timeBar?.isVisible = false
                timeBar?.alpha = 1f
                restoreTimeBarFlexibleWidth()
                onEnd?.invoke()
            }
        }

        if (showProgress) {
            pending++
            timeBar?.let { bar ->
                bar.animate().cancel()
                bar.animate()
                    .alpha(0f)
                    .setDuration(OPTIONS_BAR_ANIMATION_DURATION_MS)
                    .setInterpolator(optionsBarAnimationInterpolator)
                    .withEndAction { maybeEnd() }
                    .start()
            } ?: maybeEnd()
        }
        if (showTimeContainer) {
            pending++
            animateControlBarSubviewVisibility(timeContainer, show = false) { maybeEnd() }
        }
        if (pending == 0) {
            onEnd?.invoke()
        }
    }

    private fun animateCompactOptionsProgressIn() {
        controlBarEndSpacer?.isVisible = false
        restoreTimeBarFlexibleWidth()
        animateControlBarSubviewVisibility(timeBar, shouldShowProgressControlsInBar())
        animateControlBarSubviewVisibility(timeContainer, shouldShowTimeContainerInBar())
    }

    private fun animateCompactExpandedChromeIn(animated: Boolean) {
        val strip = optionsExpandableStrip ?: return
        updateExpandedButtonsChildVisibility()
        optionsExpandedGroup?.isVisible = true

        if (!animated) {
            strip.isVisible = true
            resetCompactExpandedChromeTransforms()
            return
        }

        cancelCompactExpandedChromeAnimations()
        strip.isVisible = true
        updateExpandableStripLayout(0)

        (controlBar as? ViewGroup)?.requestLayout()
        controlBar?.post {
            val targetWidth = measureOptionsExpandableContentWidth()
            if (targetWidth <= 0) {
                resetCompactExpandedChromeTransforms()
                return@post
            }
            optionsExpandableStripAnimator = ValueAnimator.ofInt(0, targetWidth).apply {
                duration = OPTIONS_BAR_ANIMATION_DURATION_MS
                interpolator = optionsBarAnimationInterpolator
                addUpdateListener { animator ->
                    updateExpandableStripLayout(animator.animatedValue as Int)
                }
                start()
            }
        }
    }

    private fun animateCompactExpandedChromeOut(animated: Boolean, onEnd: (() -> Unit)? = null) {
        val strip = optionsExpandableStrip ?: run {
            onEnd?.invoke()
            return
        }

        if (!animated) {
            strip.isVisible = false
            resetCompactExpandedChromeTransforms()
            onEnd?.invoke()
            return
        }

        cancelCompactExpandedChromeAnimations()

        val startWidth = strip.width.takeIf { it > 0 } ?: measureOptionsExpandableContentWidth()
        if (startWidth <= 0) {
            strip.isVisible = false
            resetCompactExpandedChromeTransforms()
            onEnd?.invoke()
            return
        }

        updateExpandableStripLayout(startWidth)
        optionsExpandableStripAnimator = ValueAnimator.ofInt(startWidth, 0).apply {
            duration = OPTIONS_BAR_ANIMATION_DURATION_MS
            interpolator = optionsBarAnimationInterpolator
            addUpdateListener { animator ->
                updateExpandableStripLayout(animator.animatedValue as Int)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    strip.isVisible = false
                    resetCompactExpandedChromeTransforms()
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun getDefaultMediaButtonSize(): Float {
        return resources.getDimension(
            if (isMobilePlayerChrome) {
                R.dimen.kinescope_mobile_media_button_size
            } else {
                R.dimen.kinescope_media_button_height
            },
        )
    }

    private fun shouldShowProgressControlsInBar(): Boolean {
        if (usesCompactOptionsChrome() && isOptionsBarExpanded) {
            return false
        }
        return kinescopePlayer?.kinescopePlayerOptions?.showSeekBar != false
    }

    private fun shouldShowTimeContainerInBar(): Boolean {
        if (usesCompactOptionsChrome() && isOptionsBarExpanded) {
            return false
        }
        val options = kinescopePlayer?.kinescopePlayerOptions
        if (options?.controls == false) {
            return false
        }
        if (isLiveState) {
            return true
        }
        return !isLiveState && (isMobilePlayerChrome || options?.showDuration == true)
    }

    private fun updateProgressControlsVisibility(animated: Boolean = false) {
        val showProgress = shouldShowProgressControlsInBar()
        val showTimeContainer = shouldShowTimeContainerInBar()
        val pinButtonsToEnd = usesCompactOptionsChrome() && isOptionsBarExpanded

        if (!animated) {
            timeBar?.animate()?.cancel()
            timeContainer?.animate()?.cancel()
            timeBar?.alpha = 1f
            timeContainer?.alpha = 1f
            timeBar?.isVisible = showProgress
            timeContainer?.isVisible = showTimeContainer
            controlBarEndSpacer?.isVisible = pinButtonsToEnd && !showProgress
            return
        }

        if (pinButtonsToEnd && !showProgress) {
            controlBarEndSpacer?.isVisible = false
            animateControlBarSubviewVisibility(timeBar, show = false) {
                controlBarEndSpacer?.isVisible = true
            }
        } else if (!pinButtonsToEnd && showProgress) {
            controlBarEndSpacer?.isVisible = false
            animateControlBarSubviewVisibility(timeBar, show = true)
        } else {
            controlBarEndSpacer?.isVisible = pinButtonsToEnd && !showProgress
            animateControlBarSubviewVisibility(timeBar, showProgress)
        }
        animateControlBarSubviewVisibility(timeContainer, showTimeContainer)
    }

    private fun animateControlBarSubviewVisibility(
        view: View?,
        show: Boolean,
        onEnd: (() -> Unit)? = null,
    ) {
        view ?: run {
            onEnd?.invoke()
            return
        }
        view.animate().cancel()
        if (show) {
            if (view.isVisible && view.alpha >= 1f) {
                onEnd?.invoke()
                return
            }
            view.isVisible = true
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setDuration(OPTIONS_BAR_ANIMATION_DURATION_MS)
                .setInterpolator(optionsBarAnimationInterpolator)
                .withEndAction { onEnd?.invoke() }
                .start()
            return
        }
        if (!view.isVisible) {
            onEnd?.invoke()
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(OPTIONS_BAR_ANIMATION_DURATION_MS)
            .setInterpolator(optionsBarAnimationInterpolator)
            .withEndAction {
                view.isVisible = false
                view.alpha = 1f
                onEnd?.invoke()
            }
            .start()
    }

    private fun updateOptionsBarUi() {
        updateOptionsButtonIcon()
        updateOptionsButtonsVisibility()
        updateFullscreenButtonVisibility()
        updateProgressControlsVisibility()
    }

    private fun onOptionsButtonClick() {
        if (!usesCompactOptionsChrome()) {
            toggleSettingsMenu()
            return
        }
        if (!isOptionsBarExpanded) {
            return
        }
        toggleSettingsMenu()
    }

    private fun onOptionsDotsButtonClick() {
        if (!usesCompactOptionsChrome()) {
            return
        }
        if (settingsMenuView?.isVisible == true) {
            settingsMenuView?.dismiss()
        }
        cancelControlOverlayAutoHide()
        if (isOptionsBarExpanded) {
            collapseOptionsBar(animated = true)
        } else {
            expandOptionsBar(animated = true)
        }
        scheduleControlOverlayAutoHide()
    }

    private fun expandOptionsBar(animated: Boolean) {
        if (!usesCompactOptionsChrome() || isOptionsBarExpanded) {
            return
        }
        isOptionsBarExpanded = true
        if (!animated) {
            controlBarEndSpacer?.isVisible = true
            updateExpandedButtonsChildVisibility()
            animateCompactExpandedChromeIn(animated = false)
            updateProgressControlsVisibility(animated = false)
            cancelControlOverlayAutoHide()
            return
        }
        hideCompactExpandedChromeForAnimation()
        animateCompactOptionsProgressOut {
            controlBarEndSpacer?.isVisible = true
            animateCompactExpandedChromeIn(animated = true)
        }
        cancelControlOverlayAutoHide()
    }

    private fun collapseOptionsBar(animated: Boolean) {
        if (!isOptionsBarExpanded) {
            return
        }
        isOptionsBarExpanded = false
        if (!animated) {
            animateCompactExpandedChromeOut(animated = false)
            controlBarEndSpacer?.isVisible = false
            updateProgressControlsVisibility(animated = false)
            updateOptionsButtonsVisibility()
            return
        }
        animateCompactExpandedChromeOut(animated = true) {
            updateOptionsButtonsVisibility()
            animateCompactOptionsProgressIn()
        }
    }

    private fun applyKinescopePlayerOptions() {
        if (isPictureInPictureActive) {
            return
        }
        val options = kinescopePlayer?.kinescopePlayerOptions
        if (options != null) {
            val showControls = options.controls
            when {
                !showControls -> controlView?.isVisible = false
                !usesGradientChrome() -> controlView?.isVisible = true
            }
            updateFullscreenButtonVisibility()
            seekView?.isVisible = showControls && options.showSeekBar
            if (usesCompactOptionsChrome()) {
                optionsDotsButton?.isVisible = showControls && options.showOptionsButton
                if (!isOptionsBarExpanded) {
                    optionsExpandableStrip?.isVisible = false
                }
                updateExpandedButtonsChildVisibility()
            } else {
                optionsDotsButton?.isVisible = false
                optionsExpandableStrip?.isVisible = true
                optionsExpandedGroup?.isVisible = true
                updateExpandedButtonsChildVisibility()
            }
            positionView?.isVisible = showControls &&
                !isLiveState &&
                (isMobilePlayerChrome || options.showDuration) &&
                shouldShowTimeContainerInBar()
            durationView?.isVisible = false
            timeSeparatorView?.isVisible = false
            timeBar?.isVisible = showControls && options.showSeekBar && shouldShowProgressControlsInBar()
            timeContainer?.isVisible = showControls && shouldShowTimeContainerInBar()
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
            updateAudioTracksSettingsVisibility(showControls)
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.Attachments,
                showControls && options.showAttachments && !video?.attachments.isNullOrEmpty(),
            )
        } else {
            controlView?.isVisible = true
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.PlaybackSpeed,
                true
            )
        }
        updatePlayPauseButton()
    }

    /**
     * Re-applies [KinescopeVideoPlayer.kinescopePlayerOptions] to control chrome and settings entries.
     * Call after mutating options on an attached player.
     */
    fun refreshPlayerChrome() {
        kinescopePlayer?.kinescopePlayerOptions?.syncLegacyChromeFlags()
        applyPlayerChromeLayout()
        applyAccentColor()
        updateAll()
    }

    /**
     * Re-applies chrome after returning from PiP without flashing the title overlay.
     */
    fun refreshPlayerChromeAfterPictureInPictureExit() {
        kinescopePlayer?.kinescopePlayerOptions?.syncLegacyChromeFlags()
        applyPlayerChromeLayout()
        applyAccentColor()
        dismissControlOverlayForPictureInPictureExit()
        updatePlayPauseButton()
        updateBuffering()
        updateTimeline()
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
        trackController?.setQualityVariant(variantId)
    }

    private fun applyAccentColor() {
        val white = androidx.core.content.ContextCompat.getColor(context, R.color.white)
        val playedColor = kinescopePlayer?.kinescopePlayerOptions?.accentColor
            ?.let { hex -> runCatching { Color.parseColor(hex) }.getOrNull() }
            ?: androidx.core.content.ContextCompat.getColor(context, R.color.kinescope_primary_color)
        setColors(
            buttonColor = white,
            scrubberColor = playedColor,
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
        trackController?.ensureDefaultSubtitleEnabled(
            showSubtitles = kinescopePlayer?.getShowSubtitles() == true,
            subtitles = video.subtitles,
        )
        applyVideoPoster()
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

    private fun centerPlayIconRes(): Int {
        return when {
            shouldShowReplayButton() -> R.drawable.ic_controls_rewind
            shouldShowPauseButton() -> R.drawable.ic_pause
            else -> R.drawable.ic_play
        }
    }

    private fun updatePlayPauseButton() {
        if (isPictureInPictureActive) {
            playPauseButton?.isVisible = false
            return
        }
        val showControls = kinescopePlayer?.kinescopePlayerOptions?.controls ?: true
        val showCenterPlayControl = shouldShowCenterPlayControl(showControls)
        playPauseButton?.isVisible = showCenterPlayControl
        if (showCenterPlayControl) {
            playPauseButton?.setImageResource(centerPlayIconRes())
        }
    }

    private fun enterScrubOverlayMode() {
        cancelControlOverlayAutoHide()
        collapseOptionsBar(animated = false)
        showControlOverlay(animated = false)
        titleView?.isVisible = false
        authorView?.isVisible = false
        setMobileBackgroundGradientsVisible(visible = false, animated = false)
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
        controlView?.setBackgroundColor(getControlOverlayBackgroundColor())
        updateMobileBackgroundGradients()

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
        removeCallbacks(hideControlOverlayRunnable)
        if (kinescopePlayer?.kinescopePlayerOptions?.controls != true) {
            return
        }
        if (settingsMenuView?.isVisible == true || scrubbing) {
            return
        }
        if (usesGradientChrome() && isPlaybackPaused()) {
            return
        }
        if (isOptionsBarExpanded) {
            return
        }
        postDelayed(hideControlOverlayRunnable, CONTROL_OVERLAY_AUTO_HIDE_MS)
    }

    private fun cancelControlOverlayAutoHide() {
        removeCallbacks(hideControlOverlayRunnable)
    }

    private fun showControlOverlay(animated: Boolean) {
        if (isPictureInPictureActive) {
            return
        }
        val overlay = controlView ?: return
        if (overlay.isVisible && overlay.alpha >= 1f) {
            updateAll()
            applySubtitleStyle()
            scheduleControlOverlayAutoHide()
            return
        }
        overlay.animate().cancel()
        overlay.isVisible = true
        updateMobileBackgroundGradients(animated = animated, controlsVisible = true)
        applySubtitleStyle(controlsVisibleOverride = true)
        updateAll()
        if (!animated) {
            overlay.alpha = 1f
            scheduleControlOverlayAutoHide()
            return
        }
        overlay.alpha = 0f
        overlay.animate()
            .alpha(1f)
            .setDuration(CONTROL_OVERLAY_FADE_DURATION_MS)
            .withEndAction {
                applySubtitleStyle(controlsVisibleOverride = true)
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
        updatePlayPauseButton()
        updateMobileBackgroundGradients(animated = animated, controlsVisible = false)
        applySubtitleStyle(controlsVisibleOverride = false)
        if (!animated) {
            overlay.isVisible = false
            overlay.alpha = 1f
            collapseOptionsBar(animated = false)
            updatePlayPauseButton()
            return
        }
        overlay.animate()
            .alpha(0f)
            .setDuration(CONTROL_OVERLAY_FADE_DURATION_MS)
            .withEndAction {
                overlay.isVisible = false
                overlay.alpha = 1f
                collapseOptionsBar(animated = false)
                updatePlayPauseButton()
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
        settingsMenuView?.apply {
            setFullscreenMode(isVideoFullscreen)
            setSubtitleStyle(subtitleStyle)
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.PlaybackSpeed,
                options = getSettingsMenuPlaybackSpeedOptions(),
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.VideoQuality,
                options = getSettingsMenuVideoQualityOptions(),
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.Subtitles,
                options = getSettingsMenuSubtitlesOptions(),
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.AudioTracks,
                options = getSettingsMenuAudioTrackOptions(),
            )
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.Attachments,
                options = getSettingsMenuAttachmentsOptions(),
            )
            updateSettingsMenuCurrentValues()
            show()
        }
        bringSettingsAboveOverlay()
    }

    private fun bringSettingsAboveOverlay() {
        settingsMenuView?.let { settings ->
            (settings.parent as? android.view.ViewGroup)?.bringChildToFront(settings)
        }
    }

    private fun getSettingsMenuPlaybackSpeedOptions() =
        playbackSpeedVariants
            .mapIndexed { index, variant ->
                KinescopeSettingsOption(
                    id = index,
                    title = variant.name,
                    isSelected = kotlin.math.abs(
                        (localExoPlayer?.playbackSpeed ?: 1f) - variant.speed,
                    ) < 0.01f,
                )
            }

    private fun getSettingsMenuVideoQualityOptions(): List<KinescopeSettingsOption> {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val includeAudioOnly = options?.showAudioOnlyQualityInSettings != false
        return trackController?.qualityVariants
            .orEmpty()
            .map { variant ->
                KinescopeSettingsOption(
                    id = variant.id,
                    title = variant.name,
                    isSelected = variant.isSelected,
                    badge = qualityBadgeForVariant(variant.id),
                )
            }
            .toMutableList()
            .apply {
                if (includeAudioOnly) {
                    add(
                        KinescopeSettingsOption(
                            id = KinescopeQualityVariant.QUALITY_VARIANT_AUDIO_ONLY_ID,
                            title = resources.getString(R.string.settings_video_quality_audio_only),
                            isSelected = trackController?.isAudioOnlyQuality == true
                        )
                    )
                }
                add(
                    KinescopeSettingsOption(
                        id = KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID,
                        title = resources.getString(R.string.settings_video_quality_variant_auto),
                        isSelected = trackController?.isAutoQuality == true
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

            is KinescopeSettingsView.Parameter.VideoQuality -> trackController?.setQualityVariant(
                id = optionId
            )

            is KinescopeSettingsView.Parameter.Subtitles -> applySubtitlesSelection(optionId)

            is KinescopeSettingsView.Parameter.AudioTracks -> applyAudioTrackSelection(optionId)

            is KinescopeSettingsView.Parameter.Attachments ->
                getVideo()?.attachments?.getOrNull(optionId)?.let { attachment ->
                    onAttachmentSelected?.invoke(attachment)
                }

            else -> Unit
        }
        updateSettingsMenuCurrentValues()
    }

    private fun updateSettingsMenuCurrentValues() {
        settingsMenuView?.runBatchUpdate {
            applySettingsMenuCurrentValues()
        }
    }

    private fun applySettingsMenuCurrentValues() {
        settingsMenuView?.setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.PlaybackSpeed,
                value = playbackSpeedVariants
                    .find { variant ->
                        kotlin.math.abs(
                            variant.speed - (localExoPlayer?.playbackSpeed ?: 1f),
                        ) < 0.01f
                    }
                    ?.name
                    .orEmpty(),
            )
            settingsMenuView?.setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.VideoQuality,
                value = when {
                    trackController?.isAudioOnlyQuality == true ->
                        context.getString(R.string.settings_video_quality_audio_only)

                    trackController?.isAutoQuality == true ->
                        context.getString(
                            R.string.settings_video_quality_variant_auto_caption,
                            localExoPlayer?.videoSize?.height.toString(),
                        )

                    else -> trackController?.selectedQualityVariant?.name.orEmpty()
                },
            )
            settingsMenuView?.setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.Subtitles,
                value = getSubtitlesCurrentValueLabel(),
            )
            settingsMenuView?.setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.AudioTracks,
                value = trackController?.currentAudioLabel().orEmpty(),
            )
            settingsMenuView?.setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.PlaybackSpeed,
                options = getSettingsMenuPlaybackSpeedOptions(),
            )
            settingsMenuView?.setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.VideoQuality,
                options = getSettingsMenuVideoQualityOptions(),
            )
            settingsMenuView?.setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.Subtitles,
                options = getSettingsMenuSubtitlesOptions(),
            )
            settingsMenuView?.setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.AudioTracks,
                options = getSettingsMenuAudioTrackOptions(),
        )
    }

    private fun updateAudioTracksSettingsVisibility(showControls: Boolean? = null) {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val controlsVisible = showControls ?: (options?.controls != false)
        settingsMenuView?.setParameterVisible(
            KinescopeSettingsView.Parameter.AudioTracks,
            controlsVisible &&
                options?.showAudioTracksInSettings != false &&
                trackController?.hasMultipleAudioTracks == true,
        )
    }

    private fun getSettingsMenuAudioTrackOptions(): List<KinescopeSettingsOption> =
        trackController?.buildAudioOptions().orEmpty()

    private fun applyAudioTrackSelection(optionId: Int) {
        trackController?.applyAudioSelection(optionId)
    }

    private fun getSubtitlesCurrentValueLabel(): String {
        val controller = trackController ?: return context.getString(R.string.settings_subtitles_off)
        return controller.currentSubtitleLabel(
            subtitles = getVideo()?.subtitles.orEmpty(),
            offLabel = context.getString(R.string.settings_subtitles_off),
        )
    }

    private fun getSettingsMenuSubtitlesOptions(): List<KinescopeSettingsOption> {
        val controller = trackController ?: return emptyList()
        return controller.buildSubtitleOptions(
            subtitles = getVideo()?.subtitles.orEmpty(),
            offLabel = context.getString(R.string.settings_subtitles_off),
        )
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
        trackController?.applySubtitleSelection(optionId)
    }

    private fun shouldShowPauseButton(): Boolean {
        val player = activePlaybackPlayer ?: return false
        return player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE &&
            player.playWhenReady
    }

    private fun shouldShowReplayButton(): Boolean {
        return activePlaybackPlayer?.playbackState == Player.STATE_ENDED
    }

    private fun setUIListeners() {
        if (isPictureInPictureActive) {
            return
        }
        controlView?.isVisible = false

        val gestureTouchListener = View.OnTouchListener { _, event ->
            if (!isPictureInPictureActive) {
                gestureDetector.onTouchEvent(event)
            }
            false
        }
        setOnTouchListener { _, event ->
            if (!isPictureInPictureActive) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }
        seekView?.setOnTouchListener(gestureTouchListener)
        controlView?.setOnTouchListener(gestureTouchListener)
        getChildAt(0)?.setOnTouchListener(gestureTouchListener)

        timeBar?.addListener(componentListener)
        playPauseButton?.setOnClickListener(componentListener)
        pictureInPictureButton?.setOnClickListener(componentListener)
        optionsButton?.setOnClickListener(componentListener)
        optionsDotsButton?.setOnClickListener { onOptionsDotsButtonClick() }
        fullscreenButton?.setOnClickListener(componentListener)

        liveDataView?.setOnClickListener {
            if (!isLiveSynced) {
                localExoPlayer?.let {
                    it.seekTo(it.duration)
                    isLiveSynced = true
                }
            }
        }
    }

    private fun updateTimeline() {
        val player: Player = activePlaybackPlayer ?: return

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
        val player: Player? = activePlaybackPlayer

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
            applyProgressiveSubtitles()
            if (shouldApplyProgressiveSubtitles()) {
                scheduleSubtitleUpdates()
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
        applyProgressiveSubtitles()

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
                subtitleUpdateMinIntervalMs(player).toLong(),
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
        if (isControlOverlayVisible()) {
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
        if (isPictureInPictureActive) {
            showPipMinimalControls()
            getAnalyticsArguments().let { args ->
                analyticsManager.play(args = args)
            }
            return
        }
        updatePlayPauseButton()
        updateMobileBackgroundGradients()
        if (controlView?.isVisible == true) {
            scheduleControlOverlayAutoHide()
        }

        getAnalyticsArguments().let { args ->
            analyticsManager.play(args = args)
        }
    }

    private fun dispatchPause(player: Player) {
        player.pause()
        if (isPictureInPictureActive) {
            showPipMinimalControls()
            if (isLiveState) {
                isLiveSynced = false
            }
            getAnalyticsArguments().let { args ->
                analyticsManager.pause(args = args)
            }
            return
        }
        if (usesGradientChrome()) {
            showControlOverlay(animated = true)
        } else if (controlView?.isVisible == true) {
            scheduleControlOverlayAutoHide()
        } else {
            updateMobileBackgroundGradients()
        }

        if (isLiveState) {
            isLiveSynced = false
        }

        getAnalyticsArguments().let { args ->
            analyticsManager.pause(args = args)
        }
    }


    private fun applySubtitleStyle(controlsVisibleOverride: Boolean? = null) {
        val subtitleView = this.subtitleView ?: return
        subtitleView.visibility = View.VISIBLE
        val controlsVisible = controlsVisibleOverride ?: (
            controlView?.isVisible == true && (controlView?.alpha ?: 0f) > 0f
            )
        val bg = (subtitleStyle.bgColor and 0x00FFFFFF) or
            ((subtitleStyle.bgOpacityPercent * 255 / 100) shl 24)
        val roboto = ResourcesCompat.getFont(context, R.font.roboto_regular)

        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setStyle(
            CaptionStyleCompat(
                subtitleStyle.fontColor,
                bg,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                Color.TRANSPARENT,
                roboto,
            ),
        )

        val viewHeight = when {
            subtitleView.height > 1 -> subtitleView.height.toFloat()
            height > 1 -> height.toFloat()
            else -> 0f
        }
        if (viewHeight > 1f) {
            subtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                SUBTITLE_SIZE_FRACTION_OF_HEIGHT * viewHeight * subtitleStyle.fontSizePercent / 100f,
            )
        } else {
            subtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                15f * subtitleStyle.fontSizePercent / 100f,
            )
        }

        val density = resources.displayMetrics.density
        val bottomPx = if (controlsVisible) {
            if (viewHeight > 1f) {
                (0.2f * viewHeight + 4f * density).toInt()
            } else {
                (64f * density).toInt()
            }
        } else {
            (12f * density).toInt()
        }
        if (subtitleView.paddingBottom != bottomPx) {
            subtitleView.setPadding(0, 0, 0, bottomPx)
        }
        subtitleView.setBottomPaddingFraction(0f)

        val textSizePx = if (viewHeight > 1f) {
            SUBTITLE_SIZE_FRACTION_OF_HEIGHT * viewHeight * subtitleStyle.fontSizePercent / 100f
        } else {
            15f * subtitleStyle.fontSizePercent / 100f * resources.displayMetrics.scaledDensity
        }
        progressiveSubtitleOverlay?.applyStyle(
            style = subtitleStyle,
            textSizePx = textSizePx,
            bottomPaddingPx = bottomPx,
        )
    }

    private fun shouldApplyProgressiveSubtitles(): Boolean {
        if (kinescopePlayer?.getShowSubtitles() != true) {
            return false
        }
        if (trackController?.selectedSubtitleIndex == TrackController.SUBTITLES_OFF_ID) {
            return false
        }
        return pendingCueGroup != null ||
            progressiveSubtitleOverlay?.isDisplayComplete() == false ||
            progressiveSubtitleOverlay?.isAnimating() == true
    }

    private fun subtitleUpdateMinIntervalMs(player: Player): Int {
        return if (shouldApplyProgressiveSubtitles() && player.isPlaying) {
            SUBTITLE_PROGRESS_UPDATE_INTERVAL_MS
        } else {
            timeBarMinUpdateIntervalMs
        }
    }

    private fun ensureSubtitleUpdatesRunning() {
        removeCallbacks(subtitleUpdateRunnable)
        scheduleSubtitleUpdates()
    }

    private fun scheduleSubtitleUpdates() {
        removeCallbacks(subtitleUpdateRunnable)
        if (!shouldApplyProgressiveSubtitles()) {
            return
        }
        val player = localExoPlayer ?: return
        val intervalMs = if (player.isPlaying) {
            SUBTITLE_PROGRESS_UPDATE_INTERVAL_MS.toLong()
        } else {
            SUBTITLE_PROGRESS_UPDATE_INTERVAL_MS * 2L
        }
        postDelayed(subtitleUpdateRunnable, intervalMs)
    }

    private fun stopSubtitleUpdates() {
        removeCallbacks(subtitleUpdateRunnable)
    }

    private fun resolveCueEndUs(cueStartUs: Long): Long {
        if (learnedCueDurationUs == C.TIME_UNSET) {
            return C.TIME_UNSET
        }
        return cueStartUs + learnedCueDurationUs
    }

    private fun applyProgressiveSubtitles() {
        val player = localExoPlayer
        if (player == null) {
            subtitleView?.setCues(emptyList())
            pendingCueGroup = null
            progressiveSubtitleOverlay?.clear()
            return
        }

        if (!shouldApplyProgressiveSubtitles()) {
            subtitleView?.setCues(emptyList())
            pendingCueGroup = null
            progressiveSubtitleOverlay?.clear()
            stopSubtitleUpdates()
            return
        }

        subtitleView?.setCues(emptyList())

        val cueGroup = pendingCueGroup
        if (cueGroup == null) {
            progressiveSubtitleOverlay?.ensureWordPumpRunning()
            return
        }

        val positionUs = Util.msToUs(player.contentPosition)
        val cueStartUs = cueGroup.presentationTimeUs
        val words = ProgressiveSubtitleCues.extractWords(cueGroup.cues)

        if (words.isEmpty()) {
            if (progressiveSubtitleOverlay?.hasVisibleContent() == true) {
                progressiveSubtitleOverlay?.clear()
            }
            return
        }

        if (positionUs < cueStartUs) {
            val overlay = progressiveSubtitleOverlay
            if (overlay?.hasVisibleContent() == true && overlay.shouldKeepVisible(positionUs) != true) {
                overlay.clear()
            }
            return
        }

        if (!shouldKeepCueVisible(positionUs, cueStartUs, words.size)) {
            pendingCueGroup = null
            if (progressiveSubtitleOverlay?.hasVisibleContent() == true) {
                progressiveSubtitleOverlay?.clear()
            }
            return
        }

        val state = ProgressiveSubtitleCues.buildState(
            cues = cueGroup.cues,
            positionUs = positionUs,
            cueStartUs = cueStartUs,
            cueId = ProgressiveSubtitleCues.stableCueId(cueStartUs, words),
            cueEndUs = resolveCueEndUs(cueStartUs),
        ) ?: run {
            progressiveSubtitleOverlay?.ensureWordPumpRunning()
            return
        }

        progressiveSubtitleOverlay?.setAdvancementEnabled(player.isPlaying)
        progressiveSubtitleOverlay?.update(state, cueStartUs)
    }

    private fun shouldKeepCueVisible(positionUs: Long, cueStartUs: Long, wordCount: Int): Boolean {
        val overlay = progressiveSubtitleOverlay
        if (overlay?.isAnimating() == true) {
            return true
        }
        if (overlay?.isDisplayComplete() == false) {
            return true
        }
        if (overlay?.shouldKeepVisible(positionUs) == true) {
            return true
        }
        return positionUs <= ProgressiveSubtitleCues.cueVisibleUntilUs(
            cueStartUs = cueStartUs,
            wordCount = wordCount,
            cueEndUs = resolveCueEndUs(cueStartUs),
        )
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
        localExoPlayer.getAnalyticsArguments(
            volume = audioManager.currentVolumeInPercent,
            isFullscreen = isVideoFullscreen,
        )

    fun hideControlsExceptPlayPause() {
        controlView?.children?.forEach { child ->
            child.isVisible = false
        }
    }
    fun hideAllControls(){
        controlView?.isVisible = false
    }

    /** Hides control chrome after returning from PiP so the title bar does not flash on expand. */
    fun dismissControlOverlayForPictureInPictureExit() {
        cancelControlOverlayAutoHide()
        controlView?.animate()?.cancel()
        controlView?.isVisible = false
        titleView?.isVisible = false
        authorView?.isVisible = false
        setMobileBackgroundGradientsVisible(visible = false, animated = false)
    }

    /** Поднимает/опускает progressive-субтитры при показе Compose-контролов. */
    fun syncSubtitleChromeForControls(controlsVisible: Boolean) {
        applySubtitleStyle(controlsVisibleOverride = controlsVisible)
    }

    private fun hidePipOverlays() {
        settingsMenuView?.isVisible = false
        seekView?.isVisible = false
        seekView?.isEnabled = false
        posterView?.isVisible = false
        liveStartDateContainerView?.isVisible = false
        bufferingView?.isVisible = false
        mobileHeaderGradient?.animate()?.cancel()
        mobileFooterGradient?.animate()?.cancel()
        mobileHeaderGradient?.isVisible = false
        mobileFooterGradient?.isVisible = false
        mobileHeaderGradient?.alpha = 0f
        mobileFooterGradient?.alpha = 0f
        controlView?.animate()?.cancel()
        controlView?.alpha = 1f
        setMobileBackgroundGradientsVisible(visible = false, animated = false)
        showPipMinimalControls()
    }

    private fun showPipMinimalControls() {
        fullscreenButton?.isVisible = false
        controlBar?.isVisible = false
        hideAllControls()
    }

    private fun restorePlayerChromeAfterPipExit() {
        isOptionsBarExpanded = false
        controlBarEndSpacer?.isVisible = false
        resetCompactExpandedChromeTransforms()
        optionsExpandableStrip?.isVisible = false
        controlBar?.isVisible = true
    }

    /**
     * View used for PiP transition bounds — the ExoPlayer surface, not the full chrome.
     */
    fun getPipAnchorView(): View = exoPlayerView ?: this

    /**
     * Prepares player chrome for Picture-in-Picture: hides controls and overlays so only video is visible.
     */
    fun prepareForPictureInPicture(preparing: Boolean) {
        isPictureInPictureActive = preparing
        if (preparing) {
            hidePipOverlays()
            isClickable = false
            isFocusable = false
            post { hidePipOverlays() }
        } else {
            seekView?.isEnabled = true
            isClickable = true
            isFocusable = true
            restorePlayerChromeAfterPipExit()
            setUIListeners()
            applyKinescopePlayerOptions()
            dismissControlOverlayForPictureInPictureExit()
            updatePlayPauseButton()
            updateBuffering()
            updateTimeline()
        }
    }

    fun showAllControls() {
        controlView?.isVisible = true
        controlView?.children?.forEach { child ->
            child.isVisible = true
        }
        updateAll()
    }

    private var castButtonClickListener: (() -> Unit)? = null

    fun setCastSupported(supported: Boolean) {
        castSupported = supported
        updateCastButtonVisibility()
    }

    fun setCastRouteAvailable(available: Boolean) {
        castRouteAvailable = available
        updateCastButtonVisibility()
    }

    fun setCastButtonClickListener(listener: (() -> Unit)?) {
        castButtonClickListener = listener
        castButton?.setOnClickListener(
            if (listener != null) {
                OnClickListener { listener() }
            } else {
                null
            },
        )
    }

    fun showCastOverlay(
        state: KinescopeCastState,
        onPlayPause: () -> Unit,
        onSeek: (Float) -> Unit,
        onStop: () -> Unit,
    ) {
        castOverlayView?.isVisible = true
        castOverlaySeekListener = onSeek

        val deviceLabel = state.deviceName ?: context.getString(R.string.player_cast_device_unknown)
        castDeviceView?.text = context.getString(R.string.player_cast_device, deviceLabel)

        castPlayPauseView?.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )
        castPlayPauseView?.setOnClickListener { onPlayPause() }
        castStopView?.setOnClickListener { onStop() }

        val duration = state.durationMs
        val position = state.positionMs
        val showSeek = duration > 0
        castSeekBar?.isVisible = showSeek
        castPositionView?.isVisible = showSeek
        castDurationView?.isVisible = showSeek

        if (showSeek) {
            castPositionView?.text = Util.getStringForTime(formatBuilder, formatter, position)
            castDurationView?.text = Util.getStringForTime(formatBuilder, formatter, duration)
            isUpdatingCastSeekBar = true
            castSeekBar?.progress = ((position.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000)
            isUpdatingCastSeekBar = false
        }
    }

    fun hideCastOverlay() {
        castOverlayView?.isVisible = false
        castOverlaySeekListener = null
        castPlayPauseView?.setOnClickListener(null)
        castStopView?.setOnClickListener(null)
    }

}
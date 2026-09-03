package io.kinescope.sdk.view

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
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
import io.kinescope.sdk.models.videos.KinescopeVideoChapterItem
import io.kinescope.sdk.models.videos.availableChapters
import io.kinescope.sdk.models.videos.chapterAt
import io.kinescope.sdk.models.videos.startTimeMs
import io.kinescope.sdk.models.players.syncLegacyChromeFlags
import io.kinescope.sdk.playlist.KinescopePlaylistItem
import io.kinescope.sdk.playlist.KinescopePlaylistMenuView
import io.kinescope.sdk.player.KinescopeContentOrientation
import io.kinescope.sdk.player.KinescopeGlideListener
import io.kinescope.sdk.player.KinescopeChromeButton
import io.kinescope.sdk.player.KinescopePictureInPicture
import io.kinescope.sdk.player.KinescopePlayerChromeCustomization
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.player.quality.KinescopeQualityVariant
import io.kinescope.sdk.player.quality.digitsFromQualityName
import io.kinescope.sdk.player.quality.getQualityVariantsList
import io.kinescope.sdk.player.quality.qualityDisplayHeightPx
import io.kinescope.sdk.player.quality.resolveQualityDisplayHeightPx
import io.kinescope.sdk.player.quality.resolveQualityMapName
import io.kinescope.sdk.player.speed.KinescopeSpeedVariant
import io.kinescope.sdk.player.subtitles.ProgressiveSubtitleCues
import io.kinescope.sdk.player.subtitles.ProgressiveSubtitleOverlay
import io.kinescope.sdk.player.tracks.TrackController
import io.kinescope.sdk.settings.KinescopeSettingsOption
import io.kinescope.sdk.chapters.KinescopeChaptersView
import io.kinescope.sdk.settings.KinescopeSettingsView
import io.kinescope.sdk.settings.SubtitleStyle
import io.kinescope.sdk.settings.qualityBadgeForVariant
import io.kinescope.sdk.settings.qualitySettingsIconRes
import io.kinescope.sdk.player.state.PlaybackBufferingWatchdog
import io.kinescope.sdk.utils.LiveInformerFormatter
import io.kinescope.sdk.utils.formatPlayerTime
import kotlin.math.roundToInt


/**
 * @param useTextureSurface When `true`, renders video via [android.view.TextureView] instead of
 * [android.view.SurfaceView]. Enables smooth PiP transitions on some OEMs but
 * cannot display Widevine L1 protected content — use only for non-DRM playback.
 */
@UnstableApi
class KinescopePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    useTextureSurface: Boolean = false,
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
            val playbackPlayer = player.exoPlayer ?: player.playbackPlayer
            val showReplay = playbackPlayer?.playbackState == Player.STATE_ENDED
            val showPauseIcon = if (showReplay) {
                null
            } else {
                resolveShowPauseIconForSwitch(playbackPlayer, startedPlayback)
            }

            newPlayerView.suppressPlayPauseButtonUpdate = true
            newPlayerView.beginViewSwitchPlayPauseOverride(
                showPauseIcon = showPauseIcon,
                showReplay = showReplay,
            )

            newPlayerView.let {
                it.setPlayer(player)
                it.trackController = oldPlayerView.trackController
                it.analyticsManager = oldPlayerView.analyticsManager
                it.adoptContentOrientationFrom(oldPlayerView)
                it.hasStartedPlayback = startedPlayback
                it.applyExoPlayerVisibility()
                it.updateBuffering()

                it.posterView?.isVisible = oldPlayerView.posterView?.isVisible ?: false
                it.scheduledLiveStartDate = oldPlayerView.scheduledLiveStartDate

                if (oldPlayerView.isLiveState) {
                    it.isLiveState = true
                    it.isLiveStateExplicit = oldPlayerView.isLiveStateExplicit
                    it.isLiveSynced = oldPlayerView.isLiveSynced
                    it.isLiveBroadcastStarted = oldPlayerView.isLiveBroadcastStarted
                    it.syncLiveTimeChrome()
                }
                it.syncLiveInformer()
                it.applySubtitleStyle()
            }

            oldPlayerView.detachForViewSwitch()
            newPlayerView.suppressPlayPauseButtonUpdate = false
            newPlayerView.applyCapturedPlayPauseIcon(
                showPauseIcon = showPauseIcon,
                showReplay = showReplay,
            )
            newPlayerView.rebindPlayerHost()
            newPlayerView.ensureControlBarProgressChromeVisible()
        }

        private fun resolveShowPauseIconForSwitch(
            playbackPlayer: Player?,
            startedPlayback: Boolean,
        ): Boolean {
            if (playbackPlayer == null) {
                return startedPlayback
            }
            if (playbackPlayer.playbackState == Player.STATE_ENDED) {
                return false
            }
            if (playbackPlayer.isPlaying) {
                return true
            }
            return playbackPlayer.playWhenReady || startedPlayback
        }

        private const val DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS = 200
        private const val MAX_UPDATE_INTERVAL_MS = 1000
        private const val CONTROL_OVERLAY_FADE_DURATION_MS = 200L
        private const val CONTROL_OVERLAY_AUTO_HIDE_MS = 3000L
        private const val TIME_DURATION_TOGGLE_ANIMATION_MS = 100L
        private const val TIME_DURATION_LABEL_FADE_START = 0.4f
        private const val TIME_LABEL_SEPARATOR = " / "
        private const val SCRUB_MODE_CONTROL_ELEVATION_DP = 8f
        private const val SETTINGS_MENU_ELEVATION_DP = 24f
        private const val SCRUB_SEEKBAR_SCALE = 1.85f
        private const val SCRUB_SEEKBAR_SCALE_DURATION_MS = 150L
        private const val SUBTITLE_SCRUB_FADE_DURATION_MS = 160L
        private const val LIVE_INFORMER_COUNTDOWN_INTERVAL_MS = 1_000L
        private const val DOUBLE_TAP_SEEK_SECONDS = 10
        private const val DOUBLE_TAP_SEEK_STREAK_WINDOW_MS = 1500L
        private const val MOBILE_TEXT_SHADOW_RADIUS = 2f
        private const val MOBILE_TEXT_SHADOW_DX = 0.5f
        private const val MOBILE_TEXT_SHADOW_DY = 0.5f
        private const val MOBILE_TEXT_SHADOW_COLOR = 0xA3000000.toInt()
        private const val MOBILE_BACKGROUND_GRADIENT_HEIGHT_PX = 120f
        private const val MOBILE_BACKGROUND_REFERENCE_HEIGHT_PX = 432f
        private const val OPTIONS_BAR_ANIMATION_DURATION_MS = 150L
        private const val LIVE_BADGE_PULSE_DURATION_MS = 800L
        private const val LIVE_BADGE_PULSE_MIN_ALPHA = 0f
        private const val VIEW_SWITCH_PLAY_PAUSE_OVERRIDE_MS = 500L
        private const val SUBTITLE_PROGRESS_UPDATE_INTERVAL_MS = 16
        /**
         * Fraction of the **shorter** player side. Using height alone makes captions
         * enormous on portrait / vertical videos (tall frame).
         */
        private const val SUBTITLE_SIZE_FRACTION_OF_SHORTER_SIDE = 0.045f
        private const val SUBTITLE_SIZE_FRACTION_OF_SHORTER_SIDE_FULLSCREEN = 0.036f
        /** Hard cap so tall phone screens never produce billboard-sized captions. */
        private const val SUBTITLE_MAX_TEXT_SIZE_SP = 18f
        private const val SUBTITLE_MAX_TEXT_SIZE_FULLSCREEN_SP = 17f
        private const val TABLET_SMALLEST_WIDTH_DP = 600
        private const val LEGACY_CUSTOM_BUTTON_ID = "legacy_custom"
        private val optionsBarAnimationInterpolator = DecelerateInterpolator()
        private val timeDurationToggleInterpolator = FastOutSlowInInterpolator()
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
            // Some OEMs deliver touches into the PiP window: a double tap then
            // raised the seek feedback + control chrome over the SYSTEM PiP
            // controls. In PiP the system owns all gestures — swallow ours.
            // Frame preview likewise: the host owns the whole chrome.
            if (isPictureInPictureActive || framePreviewActive) {
                return true
            }
            KinescopeLogger.log(
                KinescopeLoggerLevel.PLAYER_VIEW,
                "double tap event, action=${e.action}, isForward=${isForward(e)}"
            )

            if (e.action != MotionEvent.ACTION_UP) {
                return true
            }

            val isFwd = isForward(e)
            val totalSeconds = registerDoubleTapSeek(isFwd)
            val showControls = kinescopePlayer?.kinescopePlayerOptions?.controls == true
            if (showControls) {
                showSeekFeedbackChrome()
                seekView?.showSeekFeedback(
                    forward = isFwd,
                    totalSeconds = totalSeconds,
                    onHidden = ::hideControlOverlayAfterSeekFeedback,
                )
            }
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
            if (isPictureInPictureActive || framePreviewActive) {
                return true
            }
            KinescopeLogger.log(KinescopeLoggerLevel.PLAYER_VIEW, "single tap confirmed")
            if (tryOpenCaptionsSearchAt(e.x, e.y)) {
                return true
            }
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
    /**
     * Invoked when the playing video aspect changes.
     * `true` = portrait / vertical (height > width). Use with [KinescopeContentOrientation].
     */
    var onContentOrientationChanged: ((isPortrait: Boolean) -> Unit)? = null

    /** Latest known content aspect: taller than wide. */
    var isPortraitContent: Boolean = false
        private set
    var onAttachmentSelected: ((KinescopeVideoAttachments) -> Unit)? = null

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
    private var timeDurationSuffixClip: View? = null
    private var timeBar: KinescopeTimeBar? = null
    private var progressContainer: View? = null
    private var scrubChapterTitleView: TextView? = null

    private var buttonsContainer: ViewGroup? = null
    private var playPauseButton: KinescopePlayPauseMorphView? = null
    private var lastCenterPauseShown: Boolean? = null
    private var lastCenterPlayControlVisible: Boolean? = null
    private var viewSwitchPauseOverride: Boolean? = null
    private var viewSwitchReplayOverride = false
    private var suppressPlayPauseButtonUpdate = false
    private var optionsButton: View? = null
    private var optionsDotsButton: View? = null
    private var pictureInPictureButton: View? = null
    private var castButton: View? = null
    private var chaptersButton: View? = null
    private var playlistButton: View? = null
    private var subtitlesButton: View? = null
    private var fullscreenButton: View? = null
    private var customButtonsContainer: ViewGroup? = null
    private var chromeCustomization: KinescopePlayerChromeCustomization? = null
    private var legacyCustomButton: KinescopeChromeButton? = null
    internal var trackController: TrackController? = null
    private var settingsMenuView: KinescopeSettingsView? = null
    private var chaptersMenuView: KinescopeChaptersView? = null
    private var playlistMenuView: KinescopePlaylistMenuView? = null
    private var playlistItems: List<KinescopePlaylistItem> = emptyList()
    private var captionsSearchView: KinescopeCaptionsSearchView? = null
    private var videoTransformContainer: View? = null
    private var videoScaleBadge: TextView? = null
    private var videoScaleController: KinescopeVideoScaleController? = null
    private var videoSubtitlesHiddenForCaptionsSearch = false
    private var savedFooterGradientBackground: Drawable? = null
    private var savedControlBarBackground: Drawable? = null
    private var subtitleStyle = SubtitleStyle()
    private var pendingCueGroup: CueGroup? = null
    private var learnedCueDurationUs: Long = C.TIME_UNSET

    private var titleView: TextView? = null
    private var authorView: TextView? = null
    private var descriptionBlock: View? = null

    /**
     * How far the status bar overlaps this view's top edge, in px. Non-zero
     * only when the view is laid out under the status bar (edge-to-edge
     * embedding); the title/author block is pushed down by this amount so a
     * long, wrapping title never runs into the system chrome.
     */
    private var chromeTopOverlapPx = 0

    /**
     * Like [chromeTopOverlapPx] but for the whole system safe area at the top
     * (status bar and display cutout): what a panel docked to the top edge has
     * to clear. Fed by the same insets and location.
     */
    private var chromeTopSafeInsetPx = 0

    /**
     * Insets last dispatched to this view — the fallback source, see
     * [currentWindowInsets] — and the screen Y the overlap was resolved against.
     */
    private var lastWindowInsets: WindowInsetsCompat? = null
    private var chromeTopScreenY = Int.MIN_VALUE


    /**
     * Whether this view draws the video title/author block. Hosts that render
     * their own chrome over the video (a back button, a menu) can turn it off
     * for the embedded view while a fullscreen view of the same player keeps
     * it. View-level on purpose: the player options object is shared by every
     * view attached to the engine.
     */
    /**
     * Frame-preview mode: the host scrubs the engine to pick a poster frame
     * BEFORE playback ever started. Pre-start the video surface is
     * deliberately INVISIBLE (poster + play own the band), so seeks render
     * nowhere — this flips the surface on and the poster off without starting
     * playback, and restores the pre-start chrome on exit.
     */
    fun setFramePreviewActive(active: Boolean) {
        if (framePreviewActive == active) return
        framePreviewActive = active
        if (active) {
            posterView?.isVisible = false
            exoPlayerView?.visibility = View.VISIBLE
            // The host draws the entire frame-pick chrome itself (trim-style
            // header, centre play, timeline) — every piece of ours goes quiet:
            // overlay, centre play, subtitles; taps are swallowed below.
            controlView?.animate()?.cancel()
            controlView?.isVisible = false
            subtitleView?.isVisible = false
            findViewById<View?>(R.id.kinescope_progressive_subtitle_container)?.isVisible = false
            updatePlayPauseButton()
        } else {
            findViewById<View?>(R.id.kinescope_progressive_subtitle_container)?.isVisible = true
            subtitleView?.isVisible = false
            showControlOverlay(animated = false)
            updatePlayPauseButton()
            updateBuffering()
            applyVideoPoster()
        }
    }

    private var framePreviewActive = false

    var titleChromeEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            updateTitles()
        }

    /**
     * Where the captions search panel sits while this view is inline (not
     * fullscreen). [KinescopeCaptionsSearchPlacement.BOTTOM] (default) docks
     * a fixed-height panel above the control bar;
     * [KinescopeCaptionsSearchPlacement.TOP] docks it to the top edge and lets
     * the list fill down to the control bar — for hosts whose player band
     * changes height (a draggable sheet), so the panel stays put instead of
     * following the bottom edge. The fullscreen layout is unaffected. Honoured
     * by every re-sync of the panel: fullscreen toggles, content orientation
     * changes, view switches, resizes. View-level, like [titleChromeEnabled].
     *
     * A top-docked panel clears the system safe area at the top (status bar,
     * display cutout) on its own; [captionsSearchTopInset] adds the host's own
     * chrome on top of that. While it is up, scrubbing keeps the scrub hint
     * header and the control overlay under it — they would draw over the
     * search field otherwise; both come back if the panel closes or the
     * placement changes mid-scrub.
     */
    var captionsSearchPlacement: KinescopeCaptionsSearchPlacement = KinescopeCaptionsSearchPlacement.BOTTOM
        set(value) {
            if (field == value) return
            field = value
            syncCaptionsSearchFullscreenMode()
            syncScrubChromePresentation()
        }

    /**
     * Extra top inset, in px, for a panel docked to the top
     * ([KinescopeCaptionsSearchPlacement.TOP]) — the host's own header drawn
     * over the top of the player band. Added on top of the system safe area,
     * which the panel clears on its own. Ignored for the bottom placement and
     * in fullscreen. Negative values are clamped to 0.
     */
    var captionsSearchTopInset: Int = 0
        set(value) {
            val clamped = value.coerceAtLeast(0)
            if (field == clamped) return
            field = clamped
            updateCaptionsSearchInsets()
        }
    private var timeContainer: View? = null
    private var mobileHeaderGradient: View? = null
    private var mobileFooterGradient: View? = null
    private var controlBar: View? = null
    private var controlBarEndSpacer: View? = null
    private var optionsExpandedGroup: ViewGroup? = null
    private var optionsExpandableStrip: View? = null
    private var optionsExpandableContent: View? = null
    private var optionsExpandableStripAnimator: ValueAnimator? = null
    private var compactOptionsBarAnimator: ValueAnimator? = null
    private var timeDurationSuffixAnimator: Animator? = null
    private var isMobilePlayerChrome = false
    private var isOptionsBarExpanded = false
    private var isCompactOptionsBarAnimating = false
    private var showTotalDuration = false
    private var isPictureInPictureActive = false
    private var timeBarLayoutWeight = 1f
    private var lastCompactProgressBarWidth = 0
    private var wasCompactOptionsChrome = false

    private var liveDataView: View? = null
    private var liveBadgeCircleView: View? = null
    private var liveBadgeTextView: View? = null
    private var liveBadgePulseAnimator: ValueAnimator? = null
    private var liveStartDateContainerView: View? = null
    private var liveInformerTitleTextView: TextView? = null
    private var liveStartDateTextView: TextView? = null
    private var scheduledLiveStartDate: String? = null
    private val liveInformerUpdateRunnable = object : Runnable {
        override fun run() {
            syncLiveInformerContent()
            if (shouldShowLiveInformer() &&
                LiveInformerFormatter.needsCountdownUpdates(scheduledLiveStartDate.orEmpty())
            ) {
                postDelayed(this, LIVE_INFORMER_COUNTDOWN_INTERVAL_MS)
            }
        }
    }

    private var castOverlayView: View? = null
    private var castDeviceView: TextView? = null
    private var castPlayPauseView: ImageButton? = null
    private var castPositionView: TextView? = null
    private var castDurationView: TextView? = null
    private var castSeekBar: android.widget.SeekBar? = null
    private var castStopView: View? = null
    private var castSupported = false
    private var castRouteAvailable = false
    private var isCastOverlayVisible = false
    private var isUpdatingCastSeekBar = false
    private val playbackBufferingWatchdog = PlaybackBufferingWatchdog()
    private var playbackStallDispatched = false

    /** Optional callback when buffering exceeds [PlaybackBufferingWatchdog.TIMEOUT_MS]. */
    var onPlaybackStallListener: ((String) -> Unit)? = null

    private val bufferingWatchdogRunnable: Runnable = object : Runnable {
        override fun run() {
            evaluatePlaybackBufferingWatchdog()
            if (activePlaybackPlayer?.playbackState == Player.STATE_BUFFERING) {
                postDelayed(this, PlaybackBufferingWatchdog.POLL_MS)
            }
        }
    }

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
    private var initialSubtitlesConfigured = false
    /** Ensures the control overlay is shown once per player attach when [controls] is enabled. */
    private var controlOverlayPresentedForCurrentPlayer = false
    private var scrubbing = false
    private var scrubOverlayHiding = false
    private var controlOverlayHiding = false
    private var seekFeedbackActive = false
    private var scrubbingLiveDurationCached = 0L
    private var controlElevationBeforeScrub = 0f
    private var lastDoubleTapSeekForward: Boolean? = null
    private var doubleTapSeekStreakCount = 0
    private var lastDoubleTapSeekTimeMs = 0L

    private val hideControlOverlayRunnable = Runnable {
        if (scrubbing || settingsMenuView?.isVisible == true || isCaptionsSearchActive()) {
            return@Runnable
        }
        // Armed before start (or start rolled back to IDLE meanwhile): keep
        // the chrome — see scheduleControlOverlayAutoHide.
        if (!hasStartedPlayback) {
            return@Runnable
        }
        hideControlOverlay(animated = true)
    }

    private var window = Timeline.Window()
    private val showBuffering = 1

    private var currentWindowOffset: Long = 0
    private val timeBarMinUpdateIntervalMs = DEFAULT_TIME_BAR_MIN_UPDATE_INTERVAL_MS

    private var isLiveState = false
    private var isLiveStateExplicit = false
    private var isLiveBroadcastStarted = false
    private var isLiveSynced = false
        private set(value) {
            field = value
            updateLiveBadgeVisuals()
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

    private fun detachPlayerBindings(clearHostCallback: Boolean = true) {
        bindPlaybackPlayer(null)
        localExoPlayer?.removeListener(localEngineListener)
        if (clearHostCallback) {
            kinescopePlayer?.getOrCreatePlayerHost()?.onActivePlayerChanged = null
        }
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
            syncQualityNamesFromVideo()
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
            updateOptionsButtonIcon()
            val video = getVideo()
            if (video != null) {
                trackController?.applySubtitleSelection(
                    trackController?.selectedSubtitleIndex ?: TrackController.SUBTITLES_OFF_ID,
                )
                updateSubtitlesButtonIcon()
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            if (trackController?.selectedSubtitleIndex == TrackController.SUBTITLES_OFF_ID) {
                pendingCueGroup = null
                subtitleView?.setCues(emptyList())
                progressiveSubtitleOverlay?.clear()
                stopSubtitleUpdates()
                return
            }
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
                updateOptionsButtonIcon()
                applySubtitleStyle()
                updateContentOrientation(videoSize.width, videoSize.height)
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
                        if (viewSwitchPauseOverride == null) {
                            hasStartedPlayback = false
                        }
                    }

                    Player.STATE_BUFFERING -> {
                        analyticsManager.buffering()
                        if (!hasStartedPlayback &&
                            activePlaybackPlayer?.playWhenReady == true &&
                            !isLiveState
                        ) {
                            hidePoster()
                        }
                    }

                    Player.STATE_READY -> {
                        analyticsManager.ready(args = args)
                        if (isLiveState) {
                            isLiveBroadcastStarted = true
                            hidePoster()
                            hideLiveStartDate()
                            syncLiveTimeChrome()
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
            syncPlaybackBufferingWatchdog(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            if (isPlaying) {
                hasStartedPlayback = true
                if (isLiveState) {
                    isLiveBroadcastStarted = true
                }
                if (!isLiveState || isLiveOnAir()) {
                    hidePoster()
                }
                syncLiveTimeChrome()
            }
            if (isCastOverlayVisible) {
                refreshCastOverlay()
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
            seekView?.hideSeekFeedback()
            hideVideoSubtitlesForScrub()
            enterScrubOverlayMode()
            syncScrubChromePresentation()

            if (isLiveState) {
                scrubbingLiveDurationCached = activePlaybackPlayer?.duration ?: 0
                isLiveSynced = position == scrubbingLiveDurationCached
                showLiveScrubBadge()
                return
            }

            positionView?.text = formatPlayerTime(position)
            updateScrubChapterTitle(position)

            getAnalyticsArguments().let { args ->
                analyticsManager.seek(args = args)
            }
        }

        override fun onScrubMove(timeBar: TimeBar, position: Long) {
            if (isLiveState) {
                isLiveSynced = position == scrubbingLiveDurationCached
                showLiveScrubBadge()
                return
            }

            positionView?.text = formatPlayerTime(position)
            updateScrubChapterTitle(position)
        }

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            scrubbing = false
            scrubOverlayHiding = true
            hideScrubChapterTitle()
            if (!canceled && kinescopePlayer != null) {
                activePlaybackPlayer?.let { player ->
                    seekToTimeBarPosition(player, position)
                }
            }

            val controlWasVisible = controlView?.isVisible == true
            seekView?.hideScrubOverlay {
                scrubOverlayHiding = false
                restoreScrubTimeBarChrome()
                restoreScrubControlChrome()
                updateMobileBackgroundGradients()
                updatePlayPauseButton()
                updateBuffering()
                updateTimeline()
                restoreVideoSubtitlesAfterScrub()
                if (controlWasVisible) {
                    scheduleControlOverlayAutoHide()
                } else {
                    showControlOverlay(animated = true)
                }
            }

            if (isLiveState) {
                isLiveSynced = position == scrubbingLiveDurationCached
                updateLiveBadgeVisuals()
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
            } else if (chaptersButton === view) {
                onChaptersButtonClick()
            } else if (playlistButton === view) {
                onPlaylistButtonClick()
            } else if (subtitlesButton === view) {
                onSubtitlesButtonClick()
            }
        }

        override fun onDismiss() {

        }
    }

    init {
        val playerLayoutRes = if (useTextureSurface) {
            R.layout.view_kinesope_player_texture
        } else {
            R.layout.view_kinesope_player
        }
        inflate(context, playerLayoutRes, this)
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
        videoTransformContainer = findViewById(R.id.kinescope_video_transform_container)
        videoScaleBadge = findViewById(R.id.kinescope_video_scale_badge)
        videoTransformContainer?.let { transformContainer ->
            videoScaleBadge?.let { badge ->
                videoScaleController = KinescopeVideoScaleController(transformContainer, badge).apply {
                    onScaleChanged = { updateVideoScaleSettingsValue() }
                }
                badge.setOnClickListener {
                    videoScaleController?.reset(animated = true)
                }
            }
        }
        mobileHeaderGradient = findViewById(R.id.kinescope_mobile_header_gradient)
        mobileFooterGradient = findViewById(R.id.kinescope_mobile_footer_gradient)

        controlView = findViewById(R.id.view_control)
        seekView = findViewById(R.id.kinescope_seek_view)
        controlView?.findViewById<ViewGroup>(R.id.scrub_top_bar)?.let { scrubTopBar ->
            seekView?.attachScrubHintBar(scrubTopBar)
        }
        seekView?.setPortraitContent(isPortraitContent)

        progressContainer = controlView?.findViewById(R.id.kinescope_progress_container)
        timeBar = controlView?.findViewById(R.id.kinescope_progress)
        scrubChapterTitleView = controlView?.findViewById(R.id.scrub_chapter_title)
        applyScrubChapterTitleStyle()
        positionView = controlView?.findViewById(R.id.kinescope_position)
        durationView = controlView?.findViewById(R.id.kinescope_duration)
        timeDurationSuffixClip = controlView?.findViewById(R.id.kinescope_time_duration_suffix_clip)

        buttonsContainer = controlView?.findViewById(R.id.buttons_container_ll)
        optionsExpandedGroup = controlView?.findViewById(R.id.kinescope_options_expanded_group)
        optionsExpandableStrip = controlView?.findViewById(R.id.kinescope_options_expandable_strip)
        optionsExpandableContent = controlView?.findViewById(R.id.kinescope_options_expandable_content)
        playPauseButton = controlView?.findViewById(R.id.kinescope_play_pause)
        pictureInPictureButton = controlView?.findViewById(R.id.kinescope_picture_in_picture)
        castButton = controlView?.findViewById(R.id.kinescope_cast)
        chaptersButton = controlView?.findViewById(R.id.kinescope_chapters)
        playlistButton = controlView?.findViewById(R.id.kinescope_playlist)
        subtitlesButton = controlView?.findViewById(R.id.kinescope_subtitles)
        optionsButton = controlView?.findViewById(R.id.kinescope_settings)
        optionsDotsButton = controlView?.findViewById(R.id.kinescope_options_dots)
        fullscreenButton = controlView?.findViewById(R.id.kinescope_fullscreen)
        customButtonsContainer = controlView?.findViewById(R.id.kinescope_custom_buttons_container)

        titleView = controlView?.findViewById(R.id.kinescope_title)
        titleView?.apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        authorView = controlView?.findViewById(R.id.kinescope_author)
        descriptionBlock = controlView?.findViewById(R.id.kinescope_description_block)
        timeContainer = controlView?.findViewById(R.id.kinescope_time_container)
        controlBar = controlView?.findViewById(R.id.kinescope_control_bar)
        controlBarEndSpacer = controlView?.findViewById(R.id.kinescope_control_bar_end_spacer)

        liveDataView = controlView?.findViewById(R.id.live_data_ll)
        liveBadgeCircleView = controlView?.findViewById(R.id.live_badge_circle_view)
        liveBadgeTextView = controlView?.findViewById(R.id.live_badge_tv)
        liveStartDateContainerView = findViewById(R.id.live_start_date_ll)
        liveInformerTitleTextView = liveStartDateContainerView?.findViewById(R.id.live_informer_title_tv)
            ?: findViewById(R.id.live_informer_title_tv)
        liveStartDateTextView = liveStartDateContainerView?.findViewById(R.id.live_start_date_tv)
            ?: findViewById(R.id.live_start_date_tv)

        castOverlayView = findViewById(R.id.kinescope_cast_overlay)
        castDeviceView = findViewById(R.id.kinescope_cast_device_tv)
        castPlayPauseView = findViewById(R.id.kinescope_cast_play_pause)
        castPositionView = findViewById(R.id.kinescope_cast_position_tv)
        castDurationView = findViewById(R.id.kinescope_cast_duration_tv)
        castSeekBar = findViewById(R.id.kinescope_cast_seek_bar)
        castStopView = findViewById(R.id.kinescope_cast_stop_tv)
        castSeekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isUpdatingCastSeekBar || !isCastOverlayVisible) return
                val duration = activePlaybackPlayer?.duration ?: return
                if (duration > 0) {
                    kinescopePlayer?.seekToPosition((progress / 1000f * duration).toLong())
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
                    parameter = KinescopeSettingsView.Parameter.Scale,
                    title = resources.getString(R.string.settings_parameter_scale),
                    icon = R.drawable.ic_menu_scale,
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
                onCaptionsSearchClick = ::openCaptionsSearch
                videoScalePercentProvider = { videoScaleController?.percentLabel().orEmpty() }
                onVideoScaleStep = { factor -> videoScaleController?.multiplyScale(factor) }
                onVideoScaleReset = { videoScaleController?.reset(animated = true) }
                onNavigationChanged = {
                    updateVideoScaleBadgeVisibility()
                    if (settingsMenuView?.isVisible != true) {
                        restoreChromeAfterSettingsDismiss()
                    }
                }
            }
        settingsMenuView?.setAnchorView(optionsButton)
        settingsMenuView?.setFullscreenMode(isVideoFullscreen)

        captionsSearchView = findViewById(R.id.captions_search_overlay)
        syncCaptionsSearchFullscreenMode()
        captionsSearchView?.onSeekToMs = { positionMs ->
            kinescopePlayer?.seekToPosition(positionMs)
            if (isCaptionsSearchActive()) {
                applyCaptionsSearchControlChrome(active = true)
            }
        }
        captionsSearchView?.onVisibilityChanged = { visible ->
            setVideoSubtitlesHiddenForCaptionsSearch(visible)
            applyCaptionsSearchControlChrome(visible)
            if (visible) {
                descriptionBlock?.isVisible = false
                playPauseButton?.isVisible = false
                updateCaptionsSearchInsets()
                updateProgressControlsVisibility()
                refreshCaptionsSearchChrome()
            } else {
                restoreControlOverlayAfterCaptionsSearch()
            }
            syncScrubChromePresentation()
        }

        chaptersMenuView = findViewById(R.id.chapters_menu)
        chaptersMenuView?.setAnchorView(chaptersButton)
        chaptersMenuView?.setFullscreenMode(isVideoFullscreen)
        chaptersMenuView?.onChapterSelected = { chapter ->
            kinescopePlayer?.seekToPosition(chapter.startTimeMs())
            if (isOptionsBarExpanded) {
                collapseOptionsBar(animated = false)
            } else {
                updateProgressControlsVisibility(animated = false)
            }
            if (controlView?.isVisible == true) {
                scheduleControlOverlayAutoHide()
            }
        }

        playlistMenuView = findViewById(R.id.playlist_menu)
        playlistMenuView?.onItemSelected = { item ->
            onPlaylistItemSelected?.invoke(item)
            scheduleControlOverlayAutoHide()
        }
        playlistMenuView?.onCopyLinkClick = { item ->
            onPlaylistCopyLinkClick?.invoke(item)
        }
        playlistMenuView?.onDismiss = {
            scheduleControlOverlayAutoHide()
        }

        applyKinescopePlayerOptions()
        applyPlayerChromeLayout()
        applySubtitleStyle()
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = right - left != oldRight - oldLeft
            val heightChanged = bottom - top != oldBottom - oldTop
            if ((widthChanged || heightChanged) && width > 0 && height > 0) {
                syncCaptionsSearchFullscreenMode()
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
    private fun applyCapturedPlayPauseIcon(
        showPauseIcon: Boolean?,
        showReplay: Boolean,
    ) {
        resetPlayPauseButtonStateCache()
        syncPlayPauseButtonForViewSwitch(
            showPauseIcon = showPauseIcon,
            showReplay = showReplay,
        )
        post {
            syncPlayPauseButtonForViewSwitch(
                showPauseIcon = showPauseIcon,
                showReplay = showReplay,
            )
        }
    }

    private fun beginViewSwitchPlayPauseOverride(
        showPauseIcon: Boolean?,
        showReplay: Boolean,
    ) {
        removeCallbacks(endViewSwitchPlayPauseOverrideRunnable)
        viewSwitchReplayOverride = showReplay
        viewSwitchPauseOverride = showPauseIcon
        postDelayed(endViewSwitchPlayPauseOverrideRunnable, VIEW_SWITCH_PLAY_PAUSE_OVERRIDE_MS)
    }

    private fun endViewSwitchPlayPauseOverride() {
        val expectedPause = viewSwitchPauseOverride
        if (expectedPause != null && expectedPause != shouldShowPauseButton()) {
            postDelayed(endViewSwitchPlayPauseOverrideRunnable, 100L)
            return
        }
        viewSwitchPauseOverride = null
        viewSwitchReplayOverride = false
        updatePlayPauseButton()
    }

    private val endViewSwitchPlayPauseOverrideRunnable = Runnable {
        endViewSwitchPlayPauseOverride()
    }

    internal fun detachForViewSwitch() {
        if (kinescopePlayer == null) {
            return
        }
        settingsMenuView?.takeIf { it.isVisible }?.dismiss()
        isOptionsBarExpanded = false
        ensureControlBarProgressChromeVisible()
        removeCallbacks(bufferingWatchdogRunnable)
        removeCallbacks(liveInformerUpdateRunnable)
        detachPlayerBindings(clearHostCallback = false)
        exoPlayerView?.player = null
        kinescopePlayer = null
        hasStartedPlayback = false
    }

    private fun resolvePauseForPlayPauseButton(): Boolean {
        viewSwitchPauseOverride?.let { return it }
        return shouldShowPauseButton()
    }

    private fun resolveReplayForPlayPauseButton(): Boolean {
        if (viewSwitchReplayOverride) {
            return true
        }
        return shouldShowReplayButton()
    }

    private fun syncPlayPauseButtonForViewSwitch(
        showPauseIcon: Boolean?,
        showReplay: Boolean,
    ) {
        if (isPictureInPictureActive || isCaptionsSearchActive()) {
            playPauseButton?.isVisible = false
            lastCenterPlayControlVisible = false
            return
        }
        val showControls = kinescopePlayer?.kinescopePlayerOptions?.controls ?: true
        val showCenterPlayControl = shouldShowCenterPlayControl(showControls)
        lastCenterPlayControlVisible = showCenterPlayControl
        playPauseButton?.isVisible = showCenterPlayControl
        if (!showCenterPlayControl) {
            return
        }
        val button = playPauseButton ?: return
        when {
            showReplay || resolveReplayForPlayPauseButton() -> {
                if (!button.isShowingReplay()) {
                    button.showReplay()
                }
                lastCenterPauseShown = null
            }

            else -> {
                val pause = showPauseIcon ?: resolvePauseForPlayPauseButton()
                button.setPlaying(pause, animated = false)
                lastCenterPauseShown = pause
            }
        }
    }

    private fun rebindPlayerHost() {
        kinescopePlayer?.getOrCreatePlayerHost()?.let { host ->
            host.onActivePlayerChanged = { newPlayer ->
                bindPlaybackPlayer(newPlayer)
                updateAll()
            }
            bindPlaybackPlayer(host.activePlayer)
        }
    }

    private fun resetPlayPauseButtonStateCache() {
        lastCenterPauseShown = null
        lastCenterPlayControlVisible = null
    }

    private fun playbackPlayerForChrome(): Player? {
        return localExoPlayer ?: activePlaybackPlayer
    }

    fun setPlayer(kinescopePlayer: KinescopeVideoPlayer?) {
        Assertions.checkState(Looper.myLooper() == Looper.getMainLooper())
        if (this.kinescopePlayer === kinescopePlayer) return
        resetLiveModeState()
        detachPlayerBindings()
        removeCallbacks(bufferingWatchdogRunnable)
        playbackBufferingWatchdog.reset()
        hasStartedPlayback = false
        initialSubtitlesConfigured = false
        controlOverlayPresentedForCurrentPlayer = false
        hidePoster()
        pendingCueGroup = null
        learnedCueDurationUs = C.TIME_UNSET
        stopSubtitleUpdates()
        subtitleView?.setCues(emptyList())
        progressiveSubtitleOverlay?.clear()
        playbackBufferingWatchdog.reset()
        playbackStallDispatched = false
        removeCallbacks(bufferingWatchdogRunnable)
        this.kinescopePlayer = kinescopePlayer

        kinescopePlayer?.exoPlayer?.let { player ->
            this.trackController =
                TrackController(context, player.trackSelector as DefaultTrackSelector)
        }

        kinescopePlayer?.onSourceChanged = { source, metricUrl ->
            hasStartedPlayback = false
            initialSubtitlesConfigured = false
            pendingCueGroup = null
            learnedCueDurationUs = C.TIME_UNSET
            stopSubtitleUpdates()
            subtitleView?.setCues(emptyList())
            progressiveSubtitleOverlay?.clear()
            applyDefaultQuality()
            syncQualityNamesFromVideo()
            analyticsManager.setSource(
                source = source,
                metricUrl = metricUrl,
            )
            resetOptionsChromeForSourceChange()
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
        ensureControlBarProgressChromeVisible()
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
            tintControlIcon(chaptersButton, color)
            buttonsContainer?.children?.forEach { child ->
                if (child is ImageButton && child !== subtitlesButton) {
                    tintControlIcon(child, color)
                }
            }
            subtitlesButton?.let { button ->
                if (button is android.widget.ImageView) {
                    button.imageTintList = null
                }
            }
            updateSubtitlesButtonIcon()
            tintControlIcon(playPauseButton, color)
            customButtonsContainer?.children?.forEach { child ->
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

    private fun resetLiveModeState() {
        stopLiveBadgePulse()
        isLiveState = false
        isLiveStateExplicit = false
        isLiveBroadcastStarted = false
        isLiveSynced = false
    }

    private fun applyLiveModeFromVideo() {
        val video = getVideo()
        if (video != null) {
            val isLiveVideo = video.isLive || video.live != null
            if (isLiveVideo) {
                if (!isLiveState) {
                    isLiveState = true
                    isLiveSynced = true
                }
            } else if (!isLiveStateExplicit) {
                isLiveState = false
                isLiveBroadcastStarted = false
                isLiveSynced = false
            }
        }

        if (!isLiveState) {
            return
        }

        val player = activePlaybackPlayer
        if (player?.playbackState == Player.STATE_READY ||
            player?.isPlaying == true ||
            hasStartedPlayback
        ) {
            isLiveBroadcastStarted = true
        }
    }

    /**
     * Enables the live stream mode for the video player,
     * making the progress bar infinitive and adding the Live badge.
     */
    fun setLiveState() {
        isLiveStateExplicit = true
        isLiveState = true
        isLiveBroadcastStarted = activePlaybackPlayer?.playbackState == Player.STATE_READY ||
            activePlaybackPlayer?.isPlaying == true ||
            hasStartedPlayback
        isLiveSynced = true
        resetPlaybackStallWatchdog()
        syncLiveTimeChrome()
        syncLiveInformer()
    }

    /**
     * Shows the default cover while a live broadcast has not started yet.
     * Respects [KinescopePlayerOptions.showLiveAwaitingCover]; no-op when disabled.
     */
    fun showLiveAwaitingCover(
        @DrawableRes coverRes: Int = R.drawable.live_awaiting_cover,
    ) {
        if (kinescopePlayer?.kinescopePlayerOptions?.showLiveAwaitingCover == false) {
            return
        }
        showDefaultPoster(drawableRes = coverRes)
    }

    private fun isLiveOnAir(): Boolean {
        return isLiveState && isLiveBroadcastStarted
    }

    private fun shouldKeepLiveAwaitingCover(): Boolean {
        return isLiveState &&
            kinescopePlayer?.kinescopePlayerOptions?.showLiveAwaitingCover == true &&
            !isLiveBroadcastStarted
    }

    private fun shouldShowLiveBadge(): Boolean {
        return isLiveState && !shouldKeepLiveAwaitingCover()
    }

    private fun syncLiveTimeChrome() {
        if (!isLiveState) {
            return
        }
        val showControls = kinescopePlayer?.kinescopePlayerOptions?.controls != false
        val showTimeContainer = showControls && shouldShowTimeContainerInBar()
        val showBadge = shouldShowLiveBadge()

        positionView?.isVisible = false
        durationView?.isVisible = false
        timeDurationSuffixClip?.isVisible = false
        liveDataView?.isVisible = showBadge && showTimeContainer
        if (showBadge && showTimeContainer) {
            liveBadgeTextView?.isVisible = true
            liveBadgeCircleView?.isVisible = true
            updateLiveBadgeVisuals()
        }
        applyControlBarLayout(usesMobilePlayerChrome())
        restoreTimeBarFlexibleWidth()
        progressContainer?.requestLayout()
    }

    private fun enforceLiveTimeChromeIfNeeded() {
        applyLiveModeFromVideo()
        if (isLiveStateExplicit && !isLiveState) {
            isLiveState = true
            isLiveSynced = true
        }
        if (isLiveState) {
            syncLiveTimeChrome()
        }
    }

    /**
     * Sets the poster image.
     * Poster stays visible while the video is loading until playback starts.
     * For live streams it is hidden once the video is ready.
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
        if (shouldSuppressPosterDisplay()) {
            return
        }
        // showDefaultPoster=false suppresses the branded stand-in on EVERY
        // path: not just the no-URL case, but also the load placeholder and
        // the error fallback here — otherwise a slow network flashes the
        // default art while the real poster downloads. The view stays empty
        // (host background shows through) until the actual image lands.
        val suppressDefault = kinescopePlayer?.kinescopePlayerOptions?.showDefaultPoster == false
        posterView?.let {
            it.isVisible = true
            if (suppressDefault && placeholder == R.drawable.default_poster) {
                Glide.with(context).clear(it)
                it.setImageDrawable(null)
                Glide.with(context)
                    .load(url)
                    .fitCenter()
                    .apply { if (errorPlaceholder != R.drawable.default_poster) error(errorPlaceholder) }
                    .addListener(KinescopeGlideListener { isSuccess ->
                        onLoadFinished?.invoke(isSuccess)
                    })
                    .into(it)
            } else {
                it.setImageResource(placeholder)
                Glide.with(context)
                    .load(url)
                    .fitCenter()
                    .placeholder(placeholder)
                    .error(errorPlaceholder)
                    .addListener(KinescopeGlideListener { isSuccess ->
                        onLoadFinished?.invoke(isSuccess)
                    })
                    .into(it)
            }
        }
    }

    private fun showDefaultPoster(
        @DrawableRes drawableRes: Int = R.drawable.default_poster,
    ) {
        if (shouldSuppressPosterDisplay()) {
            return
        }
        if (drawableRes == R.drawable.default_poster &&
            kinescopePlayer?.kinescopePlayerOptions?.showDefaultPoster == false
        ) {
            return
        }
        posterView?.let {
            it.isVisible = true
            Glide.with(context).clear(it)
            it.setImageResource(drawableRes)
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
        scheduledLiveStartDate = startDate.takeIf { it.isNotEmpty() }
        syncLiveInformer()
    }

    /**
     * Hides the live stream starting date.
     */
    fun hideLiveStartDate() {
        scheduledLiveStartDate = null
        removeCallbacks(liveInformerUpdateRunnable)
        liveStartDateContainerView?.isVisible = false
    }

    private fun shouldShowLiveInformer(): Boolean {
        return !scheduledLiveStartDate.isNullOrBlank() &&
            !isLiveBroadcastStarted &&
            !isPictureInPictureActive
    }

    private fun isLiveAwaitingBroadcast(): Boolean {
        return isLiveState && !isLiveBroadcastStarted
    }

    private fun shouldSuppressPlaybackStallWatchdog(): Boolean {
        return isLiveAwaitingBroadcast()
    }

    private fun resetPlaybackStallWatchdog() {
        playbackBufferingWatchdog.reset()
        playbackStallDispatched = false
        removeCallbacks(bufferingWatchdogRunnable)
    }

    private fun syncLiveInformerContent() {
        val startDate = scheduledLiveStartDate ?: return
        var title = LiveInformerFormatter.formatTitle(resources, startDate)
        var subtitle = LiveInformerFormatter.formatSubtitle(startDate)
        if (title.isEmpty()) {
            title = resources.getString(R.string.live_its_starting_soon)
        }
        if (subtitle.isEmpty()) {
            subtitle = startDate
        }
        liveInformerTitleTextView?.text = title
        liveInformerTitleTextView?.isVisible = true
        liveStartDateTextView?.text = subtitle
        liveStartDateTextView?.isVisible = true
    }

    private fun syncLiveInformer() {
        removeCallbacks(liveInformerUpdateRunnable)
        val show = shouldShowLiveInformer()
        liveStartDateContainerView?.isVisible = show
        if (!show) {
            return
        }
        resetPlaybackStallWatchdog()
        syncLiveInformerContent()
        liveStartDateContainerView?.let { informer ->
            (informer.parent as? ViewGroup)?.bringChildToFront(informer)
        }
        if (LiveInformerFormatter.needsCountdownUpdates(scheduledLiveStartDate.orEmpty())) {
            postDelayed(liveInformerUpdateRunnable, LIVE_INFORMER_COUNTDOWN_INTERVAL_MS)
        }
    }

    private fun maybeShowLiveInformerFromVideo() {
        if (!scheduledLiveStartDate.isNullOrBlank()) {
            return
        }
        val video = getVideo() ?: return
        if (!video.isLive && video.live == null) {
            return
        }
        video.live?.startsAt?.takeIf { it.isNotEmpty() }?.let { startsAt ->
            scheduledLiveStartDate = startsAt
            syncLiveInformer()
        }
    }

    /**
     * Built-in settings popup. Use [configureChrome] or [configureSettingsMenu] to add custom rows.
     */
    val settingsMenu: KinescopeSettingsView?
        get() = settingsMenuView

    /**
     * Applies optional chrome customization: extra control-bar buttons and settings-menu tweaks.
     */
    fun configureChrome(customization: KinescopePlayerChromeCustomization?) {
        chromeCustomization = customization
        applyChromeCustomization()
    }

    /**
     * Builds and applies [KinescopePlayerChromeCustomization].
     */
    fun configureChrome(block: KinescopePlayerChromeCustomization.() -> Unit) {
        configureChrome(KinescopePlayerChromeCustomization().apply(block))
    }

    /**
     * Mutates the built-in settings menu after default parameters are registered.
     */
    fun configureSettingsMenu(block: KinescopeSettingsView.() -> Unit) {
        settingsMenuView?.apply(block)
    }

    var onPlaylistItemSelected: ((KinescopePlaylistItem) -> Unit)? = null
    var onPlaylistCopyLinkClick: ((KinescopePlaylistItem) -> Unit)? = null

    /**
     * Supplies playlist rows for the built-in playlist menu opened from the control bar.
     */
    fun setPlaylistItems(items: List<KinescopePlaylistItem>, selectedId: String? = null) {
        playlistItems = items
        playlistMenuView?.setItems(items, selectedId)
        updatePlaylistButtonVisibility()
    }

    fun setSelectedPlaylistItemId(id: String?) {
        playlistMenuView?.setSelectedId(id)
    }

    fun dismissPlaylistMenu() {
        playlistMenuView?.dismiss()
    }

    private fun applyChromeCustomization() {
        chromeCustomization?.settingsMenuConfigurator?.let { configurator ->
            settingsMenuView?.configurator()
        }
        rebuildCustomChromeButtons()
        refreshPlayerChrome()
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
    ) {
        legacyCustomButton = KinescopeChromeButton(
            id = LEGACY_CUSTOM_BUTTON_ID,
            iconRes = iconRes,
            contentDescription = null,
            onClick = onClick,
        )
        rebuildCustomChromeButtons()
    }

    /**
     * Hides custom button added via [showCustomButton].
     */
    fun hideCustomButton() {
        legacyCustomButton = null
        rebuildCustomChromeButtons()
    }

    private fun rebuildCustomChromeButtons() {
        val container = customButtonsContainer ?: return
        container.removeAllViews()
        val buttons = buildList {
            legacyCustomButton?.let(::add)
            addAll(chromeCustomization?.customButtons.orEmpty())
        }
        if (buttons.isEmpty()) {
            container.isVisible = false
            return
        }
        val sectionGap = resources.getDimensionPixelSize(R.dimen.kinescope_control_section_gap)
        buttons.forEachIndexed { index, spec ->
            val button = ImageButton(context, null, 0, R.style.KinescopeMediaButton).apply {
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = spec.contentDescription
                setImageResource(spec.iconRes)
                setOnClickListener { spec.onClick() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { params ->
                    if (index > 0) {
                        params.marginStart = sectionGap
                    }
                }
            }
            container.addView(button)
        }
        updateCustomButtonsVisibility()
    }

    private fun updateCustomButtonsVisibility() {
        val container = customButtonsContainer ?: return
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val compactExpanded = usesCompactOptionsChrome() && isOptionsBarExpanded
        val hasButtons = container.childCount > 0
        container.isVisible = showControls &&
            hasButtons &&
            (!usesCompactOptionsChrome() || compactExpanded)
        optionsExpandedGroup?.isVisible = container.isVisible
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

    private fun syncQualityNamesFromVideo() {
        val fromPlayer = kinescopePlayer?.qualityNamesByHeight().orEmpty()
        if (fromPlayer.isNotEmpty()) {
            trackController?.setQualityNamesByHeight(fromPlayer)
            return
        }
        val qualityMap = getVideo()?.qualityMap
        if (qualityMap.isNullOrEmpty()) {
            trackController?.setQualityNamesByHeight(emptyMap())
            return
        }
        val names = mutableMapOf<Int, String>()
        localExoPlayer?.let { player ->
            val selector = player.trackSelector as? DefaultTrackSelector ?: return@let
            selector.getQualityVariantsList().forEach { variant ->
                val override = variant.override
                val format = override?.let { o ->
                    val idx = o.trackIndices.firstOrNull() ?: return@let null
                    o.mediaTrackGroup.getFormat(idx)
                }
                val name = if (format != null) {
                    resolveQualityMapName(qualityMap, format)
                } else {
                    resolveQualityMapName(qualityMap, C.LENGTH_UNSET, variant.id)
                }
                if (!name.isNullOrBlank()) {
                    names[variant.id] = name
                }
            }
        }
        trackController?.setQualityNamesByHeight(names)
    }

    /**
     * Override quality labels (track height → display name from embed `quality_map.name`).
     * Useful for offline playback when [KinescopeVideo] is not loaded.
     */
    fun setQualityNamesByHeight(namesByHeight: Map<Int, String>) {
        trackController?.setQualityNamesByHeight(namesByHeight)
        if (settingsMenuView?.isVisible == true) {
            updateSettingsMenuCurrentValues()
        }
    }

    /**
     * Forces a fixed video quality variant (by track height id). Used for offline
     * single-quality playback so settings do not stay on Auto with a wrong caption.
     */
    fun setVideoQualityVariant(heightPx: Int) {
        trackController?.setQualityVariant(heightPx)
        updateOptionsButtonIcon()
        if (settingsMenuView?.isVisible == true) {
            updateSettingsMenuCurrentValues()
        }
    }

    private val chromeTopOverlapLayoutListener =
        OnLayoutChangeListener { _, _, top, _, _, _, oldTop, _, _ ->
            if (top != oldTop) {
                updateChromeTopOverlap(currentWindowInsets())
            }
        }

    /**
     * A translated view, or one inside a scrolled ancestor, moves on screen
     * without a layout of its own (the player band riding a sheet). The
     * overlap is re-read when the screen position changes — one location
     * read per frame, nothing more while the view stays put.
     */
    private val screenPositionPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (screenY() != chromeTopScreenY) {
            updateChromeTopOverlap(currentWindowInsets())
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addOnLayoutChangeListener(chromeTopOverlapLayoutListener)
        viewTreeObserver.addOnPreDrawListener(screenPositionPreDrawListener)
        updateChromeTopOverlap(currentWindowInsets())
        applyPlayerChromeLayout()
        updateAll()
        refreshCaptionsSearchChrome()
    }

    override fun onDetachedFromWindow() {
        removeOnLayoutChangeListener(chromeTopOverlapLayoutListener)
        viewTreeObserver.removeOnPreDrawListener(screenPositionPreDrawListener)
        super.onDetachedFromWindow()
    }

    /**
     * The insets the overlap is read from: the root's, raw. The formula in
     * [updateChromeTopOverlap] is a screen one, and the status bar sits where
     * it sits no matter what an ancestor consumed on the way down — a host
     * padding its own toolbar and passing the rest on with the bar zeroed
     * would otherwise leave the band blind to the bar it slides under with
     * the sheet. The insets last dispatched to this view stand in where there
     * are no root insets to read (before API 23, or detached).
     */
    private fun currentWindowInsets(): WindowInsetsCompat? {
        return ViewCompat.getRootWindowInsets(this) ?: lastWindowInsets
    }

    /** Reused across pre-draw checks: they run every frame. */
    private val screenLocation = IntArray(2)

    private fun screenY(): Int {
        getLocationOnScreen(screenLocation)
        return screenLocation[1]
    }

    /**
     * The overlap is read on the dispatch path rather than through a listener
     * on this view: that listener slot is the host's (a host setting its own
     * would silently replace ours, or we theirs). super still routes the
     * insets to the host's listener, if any, and down to the children. What
     * arrives here is the signal that the insets changed, and the fallback
     * source; the overlap itself comes from [currentWindowInsets].
     */
    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        lastWindowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        updateChromeTopOverlap(currentWindowInsets())
        return super.dispatchApplyWindowInsets(insets)
    }

    private fun updateChromeTopOverlap(insets: WindowInsetsCompat?) {
        insets ?: return
        val statusBarBottom = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val safeAreaBottom = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
        ).top
        // Screen coordinates, not window coordinates: in a decor-fitted (non
        // edge-to-edge) window the content already starts below the status bar
        // yet sits at window Y=0, which would fake a full-bar overlap. The
        // status bar itself is always anchored to screen Y=0.
        val screenY = screenY()
        chromeTopScreenY = screenY
        val overlap = (statusBarBottom - screenY).coerceAtLeast(0)
        val safeInset = (safeAreaBottom - screenY).coerceAtLeast(0)
        if (overlap != chromeTopOverlapPx || safeInset != chromeTopSafeInsetPx) {
            chromeTopOverlapPx = overlap
            chromeTopSafeInsetPx = safeInset
            applyPlayerChromeLayout()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            refreshCaptionsSearchChrome()
        }
    }

    private fun refreshCaptionsSearchChrome() {
        if (!isCaptionsSearchActive()) {
            return
        }
        playPauseButton?.isVisible = false
        captionsSearchView?.let { search ->
            (search.parent as? ViewGroup)?.bringChildToFront(search)
        }
        updateCaptionsSearchInsets()
        applyCaptionsSearchControlChrome(active = true)
    }

    fun setIsFullscreen(value: Boolean) {
        isVideoFullscreen = value
        seekView?.setFullscreenMode(value)
        settingsMenuView?.setFullscreenMode(value)
        chaptersMenuView?.setFullscreenMode(value)
        syncCaptionsSearchFullscreenMode()
        applyPlayerChromeLayout()
        updateFullscreenButton()
        updatePlayPauseButton()
        applySubtitleStyle()
        syncScrubChromePresentation()
    }

    private fun updateContentOrientation(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val portrait = KinescopeContentOrientation.isPortrait(width, height)
        if (portrait == isPortraitContent) return
        isPortraitContent = portrait
        seekView?.setPortraitContent(portrait)
        syncCaptionsSearchFullscreenMode()
        applySubtitleStyle()
        onContentOrientationChanged?.invoke(portrait)
    }

    /** Copies portrait/landscape content flag when switching inline ↔ fullscreen views. */
    fun adoptContentOrientationFrom(other: KinescopePlayerView) {
        if (other.isPortraitContent == isPortraitContent) return
        isPortraitContent = other.isPortraitContent
        seekView?.setPortraitContent(isPortraitContent)
        syncCaptionsSearchFullscreenMode()
        applySubtitleStyle()
        onContentOrientationChanged?.invoke(isPortraitContent)
    }

    private fun isBufferingSpinnerVisible(): Boolean {
        // The PiP window shows only the system's own UI. hidePipOverlays()
        // hides the spinner on entry, but the first rebuffer used to bring it
        // straight back through updateBuffering() — together with its opaque
        // black backdrop on the first-playback path. prepareForPictureInPicture
        // (false) re-runs updateBuffering() on exit, so visibility recovers.
        if (isPictureInPictureActive) {
            return false
        }
        if (shouldShowLiveInformer()) {
            return false
        }
        val player = localExoPlayer ?: return false
        if (!hasStartedPlayback && !isLiveState) {
            // Vimeo's loading pattern: the spinner doubles as the loading
            // indicator over whatever the band shows (black, then the poster)
            // until the source is actually ready to start; the play button
            // takes over at READY. A terminal error stops it — the host's
            // error surface owns the band from there. Live previews draw
            // their own chrome (informer / start date) and keep the old rule.
            if (player.playerError != null) {
                return false
            }
            return player.playbackState != Player.STATE_READY
        }
        return hasStartedPlayback &&
            player.playbackState == Player.STATE_BUFFERING &&
            (showBuffering == PlayerView.SHOW_BUFFERING_ALWAYS ||
                showBuffering == PlayerView.SHOW_BUFFERING_WHEN_PLAYING && player.playWhenReady)
    }

    private fun isVideoLoaded(): Boolean = getVideo() != null

    private fun postVideoLoadedChromeUpdate() {
        val update = {
            enforceLiveTimeChromeIfNeeded()
            maybeShowLiveInformerFromVideo()
            syncLiveInformer()
            applyVideoPoster()
            ensureInitialSubtitlesIfNeeded()
            updateChaptersMenuContent()
            updatePlayPauseButton()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update()
        } else {
            post(update)
        }
    }

    private fun shouldSuppressPosterDisplay(): Boolean {
        if (hasStartedPlayback && !shouldKeepLiveAwaitingCover()) {
            return true
        }
        val player = activePlaybackPlayer ?: return false
        return if (!isLiveState) {
            player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        } else {
            isLiveOnAir()
        }
    }

    private fun applyVideoPoster() {
        if (hasStartedPlayback || framePreviewActive || shouldSuppressPosterDisplay()) {
            return
        }
        val posterUrl = getVideo()?.poster?.url
        if (posterUrl.isNullOrBlank()) {
            if (kinescopePlayer?.kinescopePlayerOptions?.showDefaultPoster == false) {
                hidePoster()
                return
            }
            showDefaultPoster()
        } else {
            showPoster(posterUrl)
        }
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
            !hasStartedPlayback && !framePreviewActive -> View.INVISIBLE
            else -> View.VISIBLE
        }

        if (!hasStartedPlayback && player != null && !player.playWhenReady) {
            applyVideoPoster()
        } else if (kinescopePlayer == null) {
            hidePoster()
        }
        syncLiveInformer()
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
        if (isPictureInPictureActive || framePreviewActive) {
            return false
        }
        if (isCaptionsSearchActive()) {
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
        // Gradient chrome (mobile inline + fullscreen): show with overlay, or when paused/ended.
        if (usesGradientChrome()) {
            if (controlView?.isVisible == true && !scrubbing) {
                return true
            }
            return shouldShowReplayButton() || !shouldShowPauseButton()
        }
        return true
    }

    private fun usesCompactOptionsChrome(): Boolean {
        return isMobilePlayerChrome || isVideoFullscreen
    }

    private fun applyPlayerChromeLayout() {
        val previousMobileChrome = isMobilePlayerChrome
        val previousCompactOptionsChrome = wasCompactOptionsChrome
        val mobile = usesMobilePlayerChrome()
        val compactOptions = mobile || isVideoFullscreen
        val chromeModeChanged = previousMobileChrome != mobile ||
            previousCompactOptionsChrome != compactOptions
        if (chromeModeChanged) {
            isOptionsBarExpanded = false
            ensureControlBarProgressChromeVisible()
        }
        wasCompactOptionsChrome = compactOptions
        isMobilePlayerChrome = mobile

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.kinescope_mobile_control_margin_horizontal)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.kinescope_mobile_control_margin_bottom)
        val buttonSize = resources.getDimensionPixelSize(R.dimen.kinescope_media_button_height)

        descriptionBlock?.let { block ->
            (block.layoutParams as? MarginLayoutParams)?.let { params ->
                params.marginStart = horizontalMargin
                params.topMargin = horizontalMargin + chromeTopOverlapPx
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

        listOf(
            fullscreenButton,
            optionsButton,
            optionsDotsButton,
            pictureInPictureButton,
            playlistButton,
            chaptersButton,
            subtitlesButton,
            castButton,
        ).forEach { button ->
            button?.layoutParams?.let { params ->
                params.width = buttonSize
                params.height = buttonSize
                button.layoutParams = params
            }
            button?.setPadding(0, 0, 0, 0)
        }
        customButtonsContainer?.children?.forEach { button ->
            button.layoutParams?.let { params ->
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
        updateFullscreenButton()
        updateMobileBackgroundGradients()
        applyKinescopePlayerOptions()
        updateCaptionsSearchInsets()
        if (chromeModeChanged) {
            resetPlayPauseButtonStateCache()
            updatePlayPauseButton()
        }
    }

    private fun applyControlBarLayout(mobile: Boolean) {
        val sectionGap = resources.getDimensionPixelSize(
            if (mobile) {
                R.dimen.kinescope_mobile_control_gap
            } else {
                R.dimen.kinescope_control_section_gap
            },
        )
        val showLiveBadge = shouldShowLiveBadge()
        val liveProgressOverlap = resources.getDimensionPixelSize(
            R.dimen.kinescope_live_progress_text_overlap,
        )
        val progressLeadingGap = when {
            showLiveBadge -> 0
            mobile -> resources.getDimensionPixelSize(R.dimen.kinescope_mobile_progress_leading_gap)
            else -> resources.getDimensionPixelSize(R.dimen.kinescope_control_progress_leading_gap)
        }
        val mobileRowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_mobile_media_button_size)
        val progressHeight = resources.getDimensionPixelSize(R.dimen.kinescope_progress_control_height)
        val controlRowHeight = if (mobile) mobileRowHeight else progressHeight

        timeContainer?.minimumHeight = if (mobile) controlRowHeight else 0

        (timeContainer?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginEnd = if (showLiveBadge) {
                resources.getDimensionPixelSize(R.dimen.kinescope_live_badge_progress_gap)
            } else {
                sectionGap
            }
            if (params is LinearLayout.LayoutParams) {
                params.gravity = android.view.Gravity.CENTER_VERTICAL
            }
            timeContainer?.layoutParams = params
        }

        (controlBar as? android.widget.LinearLayout)?.gravity = android.view.Gravity.CENTER_VERTICAL

        (progressContainer?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = progressLeadingGap
            params.marginEnd = 0
            params.width = 0
            params.height = controlRowHeight
            if (params is LinearLayout.LayoutParams) {
                params.weight = 1f
                params.gravity = android.view.Gravity.CENTER_VERTICAL
            }
            progressContainer?.layoutParams = params
        }
        progressContainer?.translationX = if (showLiveBadge) {
            -liveProgressOverlap.toFloat()
        } else {
            0f
        }
        timeBar?.translationY = 0f

        (buttonsContainer?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = 0
            buttonsContainer?.layoutParams = params
        }

        (optionsExpandableStrip?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (!mobile && !isVideoFullscreen) {
                params.width = LinearLayout.LayoutParams.WRAP_CONTENT
                params.weight = 0f
            }
            optionsExpandableStrip?.layoutParams = params
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

        (subtitlesButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = if (pictureInPictureButton?.isVisible == true) {
                sectionGap
            } else if (!mobile && !isVideoFullscreen) {
                0
            } else {
                sectionGap
            }
            params.marginEnd = 0
            subtitlesButton?.layoutParams = params
        }

        (castButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = if (mobile || isVideoFullscreen) sectionGap else 0
            castButton?.layoutParams = params
        }

        val trailingGap = resources.getDimensionPixelSize(R.dimen.kinescope_control_trailing_gap)

        (optionsDotsButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = if (mobile || isVideoFullscreen) sectionGap else 0
            optionsDotsButton?.layoutParams = params
        }

        (fullscreenButton?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = when {
                mobile || isVideoFullscreen -> trailingGap
                !usesCompactOptionsChrome() && optionsButton?.isVisible == true -> trailingGap
                else -> 0
            }
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

    private fun shouldShowMobileBackgroundGradients(controlsVisible: Boolean? = null): Boolean {
        if (isPictureInPictureActive) {
            return false
        }
        if (isCaptionsSearchActive()) {
            return usesGradientChrome() &&
                kinescopePlayer?.kinescopePlayerOptions?.controls == true
        }
        if (seekFeedbackActive) {
            return usesGradientChrome() &&
                kinescopePlayer?.kinescopePlayerOptions?.controls == true
        }
        val overlayVisible = when {
            controlsVisible != null -> controlsVisible
            controlOverlayHiding -> false
            else -> isControlOverlayVisible()
        }
        return usesGradientChrome() &&
            !scrubbing &&
            !scrubOverlayHiding &&
            kinescopePlayer?.kinescopePlayerOptions?.controls == true &&
            overlayVisible
    }

    private fun updateMobileBackgroundGradients(
        animated: Boolean = false,
        controlsVisible: Boolean? = null,
    ) {
        setMobileBackgroundGradientsVisible(
            visible = shouldShowMobileBackgroundGradients(controlsVisible),
            animated = animated,
        )
        if (isCaptionsSearchActive()) {
            applyCaptionsSearchControlChrome(active = true)
        }
    }

    private fun setMobileBackgroundGradientsVisible(visible: Boolean, animated: Boolean) {
        if (!visible && isCaptionsSearchActive()) {
            mobileHeaderGradient?.animate()?.cancel()
            mobileHeaderGradient?.isVisible = false
            mobileHeaderGradient?.alpha = 1f
            applyCaptionsSearchControlChrome(active = true)
            return
        }
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
        val controller = trackController
        val iconRes = qualitySettingsIconRes(
            isAudioOnlyQuality = controller?.isAudioOnlyQuality == true,
            playbackHeightPx = resolvePlaybackHeightForSettingsIcon(),
        )
        setOptionsButtonIcon(iconRes)
    }

    private fun resolvePlaybackHeightForSettingsIcon(): Int {
        val controller = trackController
        // Locked quality: use the selected variant (name digits / id), not live videoSize.
        // videoSize can briefly report another resolution from the shared cache and flicker HD.
        if (controller != null && !controller.isAutoQuality && !controller.isAudioOnlyQuality) {
            val selected = controller.selectedQualityVariant
            digitsFromQualityName(selected.name)?.let { return it }
            return selected.id.coerceAtLeast(0)
        }

        val vs = localExoPlayer?.videoSize
        val w = vs?.width ?: 0
        val h = vs?.height ?: 0
        if (h > 0 || w > 0) {
            val fromMap = resolveQualityDisplayHeightPx(getVideo()?.qualityMap, w, h)
            if (fromMap > 0) return fromMap
            return qualityDisplayHeightPx(w, h).takeIf { it > 0 } ?: h
        }
        return 0
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
        updateChaptersButtonVisibility()
        updatePlaylistButtonVisibility()
        updateSubtitlesButtonVisibility()
        updateCustomButtonsVisibility()
    }

    private fun updateSubtitlesButtonVisibility() {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val compactExpanded = usesCompactOptionsChrome() && isOptionsBarExpanded
        val hasSubtitles = !getVideo()?.subtitles.isNullOrEmpty()
        subtitlesButton?.isVisible = showControls &&
            options?.showSubtitlesButton != false &&
            hasSubtitles &&
            !isLiveState &&
            (!usesCompactOptionsChrome() || compactExpanded)
        updateSubtitlesButtonIcon()
    }

    private fun updateSubtitlesButtonIcon() {
        val button = subtitlesButton as? android.widget.ImageView ?: return
        val subtitlesOn = trackController?.selectedSubtitleIndex != TrackController.SUBTITLES_OFF_ID
        button.setImageResource(
            if (subtitlesOn) {
                R.drawable.ic_cc_on
            } else {
                R.drawable.ic_cc_off
            },
        )
        button.imageTintList = null
    }

    private fun updateChaptersButtonVisibility() {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val compactExpanded = usesCompactOptionsChrome() && isOptionsBarExpanded
        val hasChapters = getVideo()?.availableChapters().orEmpty().isNotEmpty()
        chaptersButton?.isVisible = showControls &&
            options?.showChaptersButton != false &&
            hasChapters &&
            !isLiveState &&
            (!usesCompactOptionsChrome() || compactExpanded)
    }

    private fun updatePlaylistButtonVisibility() {
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val compactExpanded = usesCompactOptionsChrome() && isOptionsBarExpanded
        val hasPlaylist = playlistItems.isNotEmpty()
        playlistButton?.isVisible = showControls &&
            options?.showPlaylistButton == true &&
            hasPlaylist &&
            !isLiveState &&
            (!usesCompactOptionsChrome() || compactExpanded)
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
        val container = progressContainer ?: return
        val lockedWidth = container.width
        if (lockedWidth <= 0) {
            return
        }
        (container.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.weight > 0f) {
                timeBarLayoutWeight = params.weight
            }
            params.width = lockedWidth
            params.weight = 0f
            container.layoutParams = params
        }
    }

    private fun restoreTimeBarFlexibleWidth() {
        val container = progressContainer ?: return
        (container.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = 0
            if (timeBarLayoutWeight <= 0f) {
                timeBarLayoutWeight = 1f
            }
            params.weight = timeBarLayoutWeight
            container.layoutParams = params
        }
        container.requestLayout()
    }

    private fun recoverCompactOptionsBarLayout() {
        cancelCompactOptionsBarTransition()
        isCompactOptionsBarAnimating = false
        if (!isOptionsBarExpanded) {
            controlBarEndSpacer?.isVisible = false
            optionsExpandableStrip?.isVisible = false
            resetCompactExpandedChromeTransforms()
            restoreTimeBarFlexibleWidth()
            progressContainer?.alpha = 1f
            timeContainer?.alpha = 1f
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

    private fun updateProgressContainerWidth(widthPx: Int) {
        val container = progressContainer ?: return
        val params = (container.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.width = widthPx.coerceAtLeast(0)
        container.layoutParams = params
    }

    private fun prepareProgressContainerForWidthAnimation(initialWidthPx: Int = 0) {
        val container = progressContainer ?: return
        (container.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.weight != 0f) {
                timeBarLayoutWeight = params.weight
            }
            params.width = initialWidthPx.coerceAtLeast(0)
            params.weight = 0f
            container.layoutParams = params
        }
        container.isVisible = true
        container.alpha = 1f
    }

    private fun cancelCompactOptionsBarTransition() {
        compactOptionsBarAnimator?.cancel()
        compactOptionsBarAnimator = null
        isCompactOptionsBarAnimating = false
        cancelCompactExpandedChromeAnimations()
        progressContainer?.animate()?.cancel()
        timeContainer?.animate()?.cancel()
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

    private fun finishCompactOptionsBarExpand() {
        isCompactOptionsBarAnimating = false
        progressContainer?.isVisible = false
        progressContainer?.alpha = 1f
        restoreTimeBarFlexibleWidth()
        if (timeContainer?.isVisible == true) {
            timeContainer?.isVisible = false
            timeContainer?.alpha = 1f
        }
        positionView?.isVisible = false
        controlBarEndSpacer?.isVisible = true
        resetCompactExpandedChromeTransforms()
        applyControlBarLayout(usesMobilePlayerChrome())
    }

    private fun animateCompactOptionsBarExpand() {
        cancelCompactOptionsBarTransition()
        updateExpandedButtonsChildVisibility()

        val strip = optionsExpandableStrip ?: return
        strip.isVisible = true
        updateExpandableStripLayout(0)
        controlBarEndSpacer?.isVisible = false
        restoreTimeBarFlexibleWidth()

        val progressVisible = progressContainer?.isVisible == true
        val showTime = timeContainer?.isVisible == true

        if (progressVisible && (progressContainer?.width ?: 0) <= 0) {
            controlBar?.post { animateCompactOptionsBarExpand() }
            return
        }

        (controlBar as? ViewGroup)?.requestLayout()
        controlBar?.post {
            val progressStartWidth = if (progressContainer?.isVisible == true) {
                progressContainer?.width ?: 0
            } else {
                0
            }
            val stripTargetWidth = measureOptionsExpandableContentWidth()
            if (stripTargetWidth <= 0 && progressStartWidth <= 0) {
                controlBarEndSpacer?.isVisible = true
                finishCompactOptionsBarExpand()
                return@post
            }

            if (progressStartWidth > 0) {
                lastCompactProgressBarWidth = progressStartWidth
                lockTimeBarWidthForFade()
            }
            controlBarEndSpacer?.isVisible = true
            (controlBar as? ViewGroup)?.requestLayout()

            isCompactOptionsBarAnimating = true
            compactOptionsBarAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = OPTIONS_BAR_ANIMATION_DURATION_MS
                interpolator = optionsBarAnimationInterpolator
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    if (progressStartWidth > 0) {
                        updateProgressContainerWidth(
                            (progressStartWidth * (1f - fraction)).roundToInt(),
                        )
                    }
                    if (stripTargetWidth > 0) {
                        updateExpandableStripLayout(
                            (stripTargetWidth * fraction).roundToInt(),
                        )
                    }
                    if (showTime) {
                        timeContainer?.alpha = 1f - fraction
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        compactOptionsBarAnimator = null
                        finishCompactOptionsBarExpand()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        compactOptionsBarAnimator = null
                        if (isOptionsBarExpanded) {
                            finishCompactOptionsBarExpand()
                        } else {
                            finishCompactOptionsBarCollapse()
                        }
                    }
                })
                start()
            }
        }
    }

    private fun finishCompactOptionsBarCollapse() {
        isCompactOptionsBarAnimating = false
        progressContainer?.alpha = 1f
        restoreTimeBarFlexibleWidth()
        timeContainer?.alpha = 1f
        controlBarEndSpacer?.isVisible = false
        updateProgressControlsVisibility(animated = false)
        progressContainer?.isVisible = shouldShowProgressControlsInBar()
        timeContainer?.isVisible = shouldShowTimeContainerInBar()
        syncPositionViewWithTimeChrome()
        updateOptionsButtonsVisibility()
        applyControlBarLayout(usesMobilePlayerChrome())
        controlBar?.post {
            restoreTimeBarFlexibleWidth()
            controlBar?.requestLayout()
        }
    }

    private fun animateCompactOptionsBarCollapse() {
        val strip = optionsExpandableStrip
        cancelCompactOptionsBarTransition()

        val showProgress = shouldShowProgressControlsInBar()
        val showTime = shouldShowTimeContainerInBar()
        val progressTargetWidth = lastCompactProgressBarWidth.takeIf { showProgress && it > 0 } ?: 0

        val stripStartWidth = strip?.width?.takeIf { it > 0 }
            ?: measureOptionsExpandableContentWidth().takeIf { it > 0 }
            ?: 0

        if (stripStartWidth <= 0 && progressTargetWidth <= 0) {
            strip?.isVisible = false
            resetCompactExpandedChromeTransforms()
            finishCompactOptionsBarCollapse()
            if (showProgress || showTime) {
                animateCompactOptionsProgressIn()
            }
            return
        }

        strip?.let {
            it.isVisible = true
            updateExpandableStripLayout(stripStartWidth)
        }
        if (progressTargetWidth > 0) {
            prepareProgressContainerForWidthAnimation(initialWidthPx = 0)
        }
        if (showTime) {
            syncPositionViewWithTimeChrome()
            timeContainer?.isVisible = true
            timeContainer?.alpha = 0f
        }
        controlBarEndSpacer?.isVisible = true
        (controlBar as? ViewGroup)?.requestLayout()

        isCompactOptionsBarAnimating = true
        compactOptionsBarAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = OPTIONS_BAR_ANIMATION_DURATION_MS
            interpolator = optionsBarAnimationInterpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                if (stripStartWidth > 0) {
                    updateExpandableStripLayout((stripStartWidth * fraction).roundToInt())
                }
                if (progressTargetWidth > 0) {
                    updateProgressContainerWidth(
                        (progressTargetWidth * (1f - fraction)).roundToInt(),
                    )
                }
                if (showTime) {
                    timeContainer?.alpha = 1f - fraction
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    compactOptionsBarAnimator = null
                    strip?.isVisible = false
                    resetCompactExpandedChromeTransforms()
                    finishCompactOptionsBarCollapse()
                    val showProgress = shouldShowProgressControlsInBar()
                    val showTime = shouldShowTimeContainerInBar()
                    if (showProgress || showTime) {
                        animateCompactOptionsProgressIn()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    compactOptionsBarAnimator = null
                    strip?.isVisible = false
                    resetCompactExpandedChromeTransforms()
                    finishCompactOptionsBarCollapse()
                }
            })
            start()
        }
    }

    private fun animateCompactOptionsProgressOut(onEnd: (() -> Unit)? = null) {
        val showProgress = progressContainer?.isVisible == true
        val showTimeContainer = timeContainer?.isVisible == true
        if (!showProgress && !showTimeContainer) {
            onEnd?.invoke()
            return
        }

        if (showProgress && (progressContainer?.width ?: 0) <= 0) {
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
                progressContainer?.isVisible = false
                progressContainer?.alpha = 1f
                restoreTimeBarFlexibleWidth()
                onEnd?.invoke()
            }
        }

        if (showProgress) {
            pending++
            progressContainer?.let { container ->
                container.animate().cancel()
                container.animate()
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
        restoreTimeBarFlexibleWidth()
        val showProgress = shouldShowProgressControlsInBar()
        val showTime = shouldShowTimeContainerInBar()
        if (!showProgress && !showTime) {
            controlBarEndSpacer?.isVisible = false
            return
        }

        var pending = 0
        fun maybeEnd() {
            pending--
            if (pending <= 0) {
                controlBarEndSpacer?.isVisible = false
            }
        }

        if (showProgress) {
            pending++
            animateControlBarSubviewVisibility(progressContainer, show = true) { maybeEnd() }
        }
        if (showTime) {
            syncPositionViewWithTimeChrome()
            pending++
            animateControlBarSubviewVisibility(timeContainer, show = true) { maybeEnd() }
        }
        if (pending == 0) {
            controlBarEndSpacer?.isVisible = false
        }
    }

    private fun animateCompactExpandedChromeIn(animated: Boolean) {
        val strip = optionsExpandableStrip ?: return
        updateExpandedButtonsChildVisibility()

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
        return resources.getDimension(R.dimen.kinescope_media_button_height)
    }

    private fun isCaptionsSearchActive(): Boolean = captionsSearchView?.isVisible == true

    /** Inline, top-docked: the panel owns the top edge — where the scrub hint header draws. */
    private fun isCaptionsSearchPinnedToTop(): Boolean {
        return !isVideoFullscreen && captionsSearchPlacement == KinescopeCaptionsSearchPlacement.TOP
    }

    /**
     * Scrub chrome: the hint header along the top edge and the overlay lifted
     * above the captions search panel (so the scaled seek bar stays visible
     * over a bottom panel). Off while a top-docked panel is up — the header
     * would draw over the search field and the overlay, opaque in the wide
     * chrome, would cover the panel. Decided on scrub start and again
     * whenever that changes mid-scrub: panel shown or closed, placement or
     * fullscreen switched.
     */
    private fun syncScrubChromePresentation() {
        if (!scrubbing) {
            return
        }
        if (isCaptionsSearchActive() && isCaptionsSearchPinnedToTop()) {
            seekView?.hideScrubOverlay()
            controlView?.elevation = controlElevationBeforeScrub
        } else {
            seekView?.showScrubOverlay()
            controlView?.elevation = SCRUB_MODE_CONTROL_ELEVATION_DP * resources.displayMetrics.density
        }
    }

    private fun pinsExpandedOptionsToBarEnd(): Boolean {
        return usesCompactOptionsChrome() && isOptionsBarExpanded
    }

    private fun shouldShowProgressControlsInBar(): Boolean {
        if (pinsExpandedOptionsToBarEnd() && !isCaptionsSearchActive()) {
            return false
        }
        return kinescopePlayer?.kinescopePlayerOptions?.showSeekBar != false
    }

    private fun shouldShowTimeContainerInBar(): Boolean {
        if (pinsExpandedOptionsToBarEnd() && !isCaptionsSearchActive()) {
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

    private fun shouldShowTotalDurationInBar(): Boolean {
        return showTotalDuration && !isLiveState && shouldShowTimeContainerInBar()
    }

    private fun syncPositionViewWithTimeChrome() {
        if (isLiveState) {
            syncLiveTimeChrome()
            return
        }
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        val showTime = shouldShowTimeContainerInBar()
        positionView?.isVisible = showControls &&
            !isLiveState &&
            (isMobilePlayerChrome || options?.showDuration == true) &&
            showTime
    }

    private fun toggleTotalDurationVisibility() {
        if (isLiveState || !shouldShowTimeContainerInBar()) {
            return
        }
        showTotalDuration = !showTotalDuration
        updateTotalDurationVisibility(animated = true)
        cancelControlOverlayAutoHide()
        scheduleControlOverlayAutoHide()
    }

    private fun durationLabelText(durationMs: Long): String {
        return TIME_LABEL_SEPARATOR + formatPlayerTime(durationMs)
    }

    private fun updateTotalDurationVisibility(animated: Boolean = false) {
        if (isLiveState) {
            cancelTimeDurationSuffixAnimation()
            durationView?.isVisible = false
            timeDurationSuffixClip?.isVisible = false
            updateTimeDurationSuffixClipWidth(0)
            return
        }
        val show = shouldShowTotalDurationInBar()
        val label = durationView ?: return
        val clip = timeDurationSuffixClip ?: return
        val secondaryColor = ContextCompat.getColor(context, R.color.white_secondary)
        label.setTextColor(secondaryColor)
        if (!animated) {
            cancelTimeDurationSuffixAnimation()
            if (show) {
                val targetWidth = measureTimeDurationSuffixWidth()
                lockTimeDurationLabelWidth(targetWidth)
                updateTimeDurationSuffixClipWidth(targetWidth)
                label.alpha = 1f
                clip.isVisible = true
            } else {
                updateTimeDurationSuffixClipWidth(0)
                label.alpha = 1f
                clip.isVisible = false
            }
            return
        }
        if (show) {
            animateTimeDurationSuffixIn()
        } else {
            animateTimeDurationSuffixOut()
        }
    }

    private fun cancelTimeDurationSuffixAnimation() {
        timeDurationSuffixAnimator?.cancel()
        timeDurationSuffixAnimator = null
        durationView?.animate()?.cancel()
    }

    private fun timeDurationRowHeightPx(): Int {
        return resources.getDimensionPixelSize(R.dimen.kinescope_mobile_control_time_height)
    }

    private fun timeDurationWidthFraction(progress: Float): Float = progress

    private fun timeDurationLabelAlpha(progress: Float): Float {
        return ((progress - TIME_DURATION_LABEL_FADE_START) / (1f - TIME_DURATION_LABEL_FADE_START))
            .coerceIn(0f, 1f)
    }

    private fun measureTimeDurationSuffixWidth(): Int {
        val label = durationView ?: return 0
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(timeDurationRowHeightPx(), View.MeasureSpec.EXACTLY)
        label.measure(widthSpec, heightSpec)
        return label.measuredWidth
    }

    private fun lockTimeDurationLabelWidth(widthPx: Int) {
        val label = durationView ?: return
        val params = (label.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(widthPx, timeDurationRowHeightPx())
        params.width = widthPx
        params.height = timeDurationRowHeightPx()
        params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        label.layoutParams = params
    }

    private fun updateTimeDurationSuffixClipWidth(widthPx: Int) {
        val clip = timeDurationSuffixClip ?: return
        val params = (clip.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(widthPx, timeDurationRowHeightPx())
        params.width = widthPx.coerceAtLeast(0)
        params.height = timeDurationRowHeightPx()
        params.gravity = Gravity.CENTER_VERTICAL
        clip.layoutParams = params
        (clip.parent as? View)?.requestLayout()
    }

    private fun syncTimeDurationSuffixLayoutIfExpanded() {
        if (!shouldShowTotalDurationInBar() || timeDurationSuffixAnimator != null) {
            return
        }
        val targetWidth = measureTimeDurationSuffixWidth()
        if (targetWidth <= 0) {
            return
        }
        lockTimeDurationLabelWidth(targetWidth)
        updateTimeDurationSuffixClipWidth(targetWidth)
    }

    private fun animateTimeDurationSuffixIn() {
        val label = durationView ?: return
        val clip = timeDurationSuffixClip ?: return
        cancelTimeDurationSuffixAnimation()
        clip.isVisible = true
        label.alpha = 0f
        updateTimeDurationSuffixClipWidth(0)
        clip.post {
            val targetWidth = measureTimeDurationSuffixWidth()
            if (targetWidth <= 0) {
                clip.isVisible = false
                label.alpha = 1f
                return@post
            }
            lockTimeDurationLabelWidth(targetWidth)
            timeDurationSuffixAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TIME_DURATION_TOGGLE_ANIMATION_MS
                interpolator = timeDurationToggleInterpolator
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    updateTimeDurationSuffixClipWidth(
                        (targetWidth * timeDurationWidthFraction(progress)).roundToInt(),
                    )
                    label.alpha = timeDurationLabelAlpha(progress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        timeDurationSuffixAnimator = null
                        lockTimeDurationLabelWidth(targetWidth)
                        updateTimeDurationSuffixClipWidth(targetWidth)
                        label.alpha = 1f
                        clip.isVisible = true
                    }
                })
                start()
            }
        }
    }

    private fun animateTimeDurationSuffixOut() {
        val label = durationView ?: return
        val clip = timeDurationSuffixClip ?: return
        if (!clip.isVisible) {
            return
        }
        cancelTimeDurationSuffixAnimation()
        val startWidth = clip.width.takeIf { it > 0 } ?: measureTimeDurationSuffixWidth()
        if (startWidth <= 0) {
            clip.isVisible = false
            label.alpha = 1f
            updateTimeDurationSuffixClipWidth(0)
            return
        }
        lockTimeDurationLabelWidth(startWidth)
        updateTimeDurationSuffixClipWidth(startWidth)
        timeDurationSuffixAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = TIME_DURATION_TOGGLE_ANIMATION_MS
            interpolator = timeDurationToggleInterpolator
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                updateTimeDurationSuffixClipWidth(
                    (startWidth * timeDurationWidthFraction(progress)).roundToInt(),
                )
                label.alpha = timeDurationLabelAlpha(progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    timeDurationSuffixAnimator = null
                    clip.isVisible = false
                    label.alpha = 1f
                    updateTimeDurationSuffixClipWidth(0)
                }
            })
            start()
        }
    }

    private fun updateProgressControlsVisibility(animated: Boolean = false) {
        if (isCompactOptionsBarAnimating) {
            return
        }
        val showProgress = shouldShowProgressControlsInBar()
        val showTimeContainer = shouldShowTimeContainerInBar()
        val pinButtonsToEnd = pinsExpandedOptionsToBarEnd()

        if (!animated) {
            progressContainer?.animate()?.cancel()
            timeContainer?.animate()?.cancel()
            progressContainer?.alpha = 1f
            timeContainer?.alpha = 1f
            if (!pinButtonsToEnd) {
                controlBarEndSpacer?.isVisible = false
                if (showProgress) {
                    restoreTimeBarFlexibleWidth()
                }
            }
            progressContainer?.isVisible = showProgress
            timeContainer?.isVisible = showTimeContainer
            syncPositionViewWithTimeChrome()
            syncLiveTimeChrome()
            controlBarEndSpacer?.isVisible = pinButtonsToEnd && !showProgress
            updateTotalDurationVisibility(animated = false)
            return
        }

        if (pinButtonsToEnd && !showProgress) {
            controlBarEndSpacer?.isVisible = true
            animateControlBarSubviewVisibility(progressContainer, show = false)
        } else if (!pinButtonsToEnd && showProgress) {
            animateControlBarSubviewVisibility(progressContainer, show = true) {
                controlBarEndSpacer?.isVisible = false
            }
        } else {
            controlBarEndSpacer?.isVisible = pinButtonsToEnd && !showProgress
            animateControlBarSubviewVisibility(progressContainer, showProgress)
        }
        if (showTimeContainer) {
            syncPositionViewWithTimeChrome()
        }
        animateControlBarSubviewVisibility(timeContainer, showTimeContainer) {
            if (!showTimeContainer) {
                syncPositionViewWithTimeChrome()
            }
        }
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
        if (!usesCompactOptionsChrome()) {
            isOptionsBarExpanded = false
        }
        updateOptionsButtonIcon()
        updateOptionsButtonsVisibility()
        updateFullscreenButtonVisibility()
        updateProgressControlsVisibility()
    }

    private fun onChaptersButtonClick() {
        if (!usesCompactOptionsChrome()) {
            toggleChaptersMenu()
            return
        }
        if (!isOptionsBarExpanded) {
            return
        }
        toggleChaptersMenu()
    }

    private fun onPlaylistButtonClick() {
        if (!usesCompactOptionsChrome()) {
            togglePlaylistMenu()
            return
        }
        if (!isOptionsBarExpanded) {
            return
        }
        togglePlaylistMenu()
    }

    private fun onSubtitlesButtonClick() {
        if (!usesCompactOptionsChrome()) {
            showSubtitlesSettingsMenu()
            return
        }
        if (!isOptionsBarExpanded) {
            return
        }
        showSubtitlesSettingsMenu()
    }

    private fun onOptionsButtonClick() {
        if (!usesCompactOptionsChrome()) {
            toggleSettingsMenu()
            return
        }
        if (!isOptionsBarExpanded && !isCaptionsSearchActive()) {
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
        if (chaptersMenuView?.isShowing() == true) {
            chaptersMenuView?.dismiss()
        }
        if (playlistMenuView?.isShowing() == true) {
            playlistMenuView?.dismiss()
        }
        if (isCompactOptionsBarAnimating) {
            return
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
        if (!usesCompactOptionsChrome() || isOptionsBarExpanded || isCompactOptionsBarAnimating) {
            return
        }
        showTotalDuration = false
        updateTotalDurationVisibility(animated = true)
        isOptionsBarExpanded = true
        applyControlBarLayout(usesMobilePlayerChrome())
        if (!animated) {
            controlBarEndSpacer?.isVisible = true
            updateExpandedButtonsChildVisibility()
            animateCompactExpandedChromeIn(animated = false)
            updateProgressControlsVisibility(animated = false)
            cancelControlOverlayAutoHide()
            return
        }
        cancelControlOverlayAutoHide()
        animateCompactOptionsBarExpand()
    }

    private fun resetOptionsChromeForSourceChange() {
        if (isPictureInPictureActive) {
            return
        }
        settingsMenuView?.dismiss()
        chaptersMenuView?.dismiss()
        playlistMenuView?.dismiss()
        cancelCompactOptionsBarTransition()
        isOptionsBarExpanded = false
        controlBarEndSpacer?.isVisible = false
        resetCompactExpandedChromeTransforms()
        optionsExpandableStrip?.isVisible = false
        progressContainer?.alpha = 1f
        timeContainer?.alpha = 1f
        restoreTimeBarFlexibleWidth()
        updateProgressControlsVisibility(animated = false)
        updateOptionsButtonsVisibility()
    }

    private fun collapseOptionsBar(animated: Boolean) {
        if (isCompactOptionsBarAnimating) {
            cancelCompactOptionsBarTransition()
            isCompactOptionsBarAnimating = false
        }
        if (!isOptionsBarExpanded) {
            ensureControlBarProgressChromeVisible()
            return
        }
        isOptionsBarExpanded = false
        if (!animated) {
            cancelCompactOptionsBarTransition()
            animateCompactExpandedChromeOut(animated = false)
            controlBarEndSpacer?.isVisible = false
            finishCompactOptionsBarCollapse()
            return
        }
        animateCompactOptionsBarCollapse()
    }

    private fun resetOptionsBarForControlsDisabled() {
        settingsMenuView?.dismiss()
        chaptersMenuView?.dismiss()
        playlistMenuView?.dismiss()
        cancelCompactOptionsBarTransition()
        isOptionsBarExpanded = false
        isCompactOptionsBarAnimating = false
        controlBarEndSpacer?.isVisible = false
        resetCompactExpandedChromeTransforms()
        optionsExpandableStrip?.isVisible = false
        progressContainer?.alpha = 1f
        timeContainer?.alpha = 1f
        restoreTimeBarFlexibleWidth()
    }

    private fun applyKinescopePlayerOptions() {
        if (isPictureInPictureActive) {
            return
        }
        enforceLiveTimeChromeIfNeeded()
        val options = kinescopePlayer?.kinescopePlayerOptions
        if (options != null) {
            val showControls = options.controls
            if (!showControls) {
                resetOptionsBarForControlsDisabled()
                controlOverlayPresentedForCurrentPlayer = false
            }
            when {
                !showControls -> {
                    cancelControlOverlayAutoHide()
                    controlView?.isVisible = false
                }
                !controlOverlayPresentedForCurrentPlayer -> {
                    controlOverlayPresentedForCurrentPlayer = true
                    showControlOverlay(animated = false)
                }
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
                updateExpandedButtonsChildVisibility()
            }
            positionView?.isVisible = showControls &&
                !isLiveState &&
                (isMobilePlayerChrome || options.showDuration) &&
                shouldShowTimeContainerInBar()
            syncLiveTimeChrome()
            updateTotalDurationVisibility(animated = false)
            progressContainer?.isVisible = showControls && options.showSeekBar && shouldShowProgressControlsInBar()
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
            settingsMenuView?.setParameterVisible(
                KinescopeSettingsView.Parameter.Scale,
                showControls && options.videoScale,
            )
            if (options.videoScale) {
                videoScaleController?.setEnabled(true)
            } else {
                videoScaleController?.reset(animated = false)
                videoScaleController?.setEnabled(false)
            }
            if (showControls) {
                ensureControlBarProgressChromeVisible()
            }
        } else {
            if (!controlOverlayPresentedForCurrentPlayer) {
                controlOverlayPresentedForCurrentPlayer = true
                showControlOverlay(animated = false)
            } else {
                controlView?.isVisible = true
            }
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
        restoreProgressChromeAfterPipExit()
        dismissControlOverlayForPictureInPictureExit()
        applyKinescopePlayerOptions()
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
        when (view) {
            is KinescopePlayPauseMorphView -> view.applyIconTint(color)
            is android.widget.ImageView -> {
                view.imageTintList = android.content.res.ColorStateList.valueOf(color)
            }
        }
    }

    private fun updateTitles() {
        val video = getVideo()
        if (video != null) {
            titleView?.apply {
                text = video.title
                isVisible = titleChromeEnabled && video.title.isNotEmpty()
            }
            authorView?.apply {
                text = video.subtitle
                isVisible = titleChromeEnabled && !video.subtitle.isNullOrEmpty()
            }
            enforceLiveTimeChromeIfNeeded()
            applyVideoPoster()
            updateChaptersMenuContent()
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

    private fun hideControlOverlayAfterSeekFeedback() {
        seekFeedbackActive = false
        if (scrubbing || settingsMenuView?.isVisible == true || isCaptionsSearchActive()) {
            seekView?.hideSeekFeedback()
            return
        }
        hideControlOverlay(animated = true)
    }

    private fun showSeekFeedbackChrome() {
        if (isPictureInPictureActive) {
            return
        }
        seekFeedbackActive = true
        controlOverlayHiding = false
        cancelControlOverlayAutoHide()
        if (!isControlOverlayVisible()) {
            showControlOverlay(animated = true)
            cancelControlOverlayAutoHide()
        }
        updateMobileBackgroundGradients(animated = true, controlsVisible = true)
    }

    private fun updatePlayPauseButton() {
        if (suppressPlayPauseButtonUpdate) {
            return
        }
        if (isPictureInPictureActive || isCaptionsSearchActive()) {
            playPauseButton?.isVisible = false
            lastCenterPlayControlVisible = false
            return
        }
        val showControls = kinescopePlayer?.kinescopePlayerOptions?.controls ?: true
        val showCenterPlayControl = shouldShowCenterPlayControl(showControls)
        val centerControlVisibilityChanged = lastCenterPlayControlVisible != showCenterPlayControl
        lastCenterPlayControlVisible = showCenterPlayControl
        val button = playPauseButton
        button?.isVisible = showCenterPlayControl
        if (!showCenterPlayControl || button == null) {
            return
        }
        when {
            resolveReplayForPlayPauseButton() -> {
                if (!button.isShowingReplay()) {
                    button.showReplay()
                }
                lastCenterPauseShown = null
            }

            else -> {
                val pause = resolvePauseForPlayPauseButton()
                val leavingReplay = button.isShowingReplay()
                if (leavingReplay || lastCenterPauseShown != pause || centerControlVisibilityChanged) {
                    val animate = !leavingReplay &&
                        lastCenterPauseShown != null &&
                        !centerControlVisibilityChanged
                    button.setPlaying(pause, animated = animate)
                    lastCenterPauseShown = pause
                }
            }
        }
    }

    private fun enterScrubOverlayMode() {
        cancelControlOverlayAutoHide()
        collapseOptionsBar(animated = false)
        showControlOverlay(animated = false)
        val captionsSearchActive = isCaptionsSearchActive()
        if (captionsSearchActive) {
            mobileHeaderGradient?.animate()?.cancel()
            mobileHeaderGradient?.isVisible = false
            mobileHeaderGradient?.alpha = 1f
            applyCaptionsSearchControlChrome(active = true)
        } else {
            setMobileBackgroundGradientsVisible(visible = false, animated = false)
            controlView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        seekView?.isVisible = true

        controlElevationBeforeScrub = controlView?.elevation ?: 0f

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

    private fun restoreScrubTimeBarChrome() {
        hideScrubChapterTitle()
        controlView?.elevation = controlElevationBeforeScrub

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

    private fun restoreScrubControlChrome() {
        if (isCaptionsSearchActive()) {
            applyCaptionsSearchControlChrome(active = true)
        } else {
            controlView?.setBackgroundColor(getControlOverlayBackgroundColor())
        }
    }

    private fun exitScrubOverlayMode() {
        restoreScrubTimeBarChrome()
        restoreScrubControlChrome()
        updateMobileBackgroundGradients()
    }

    private fun scheduleControlOverlayAutoHide() {
        removeCallbacks(hideControlOverlayRunnable)
        if (kinescopePlayer?.kinescopePlayerOptions?.controls != true) {
            return
        }
        // Before playback ever starts there is nothing to declutter: hiding
        // the overlay also hides the centre play button (its child), leaving
        // a poster with no affordance at all. The countdown starts with
        // playback.
        if (!hasStartedPlayback) {
            return
        }
        if (settingsMenuView?.isVisible == true || scrubbing) {
            return
        }
        if (isCaptionsSearchActive()) {
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
        // The PiP window shows only the system's own controls; every path that
        // could raise our overlay while minimised is cut off here. Frame
        // preview owns its chrome the same way.
        if (isPictureInPictureActive || framePreviewActive) {
            return
        }
        if (isPictureInPictureActive) {
            return
        }
        val overlay = controlView ?: return
        controlOverlayHiding = false
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
        // Re-apply after control bar has a real height so bottom offset can move.
        controlBar?.post { applySubtitleStyle(controlsVisibleOverride = true) }
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
        if (isCaptionsSearchActive()) {
            return
        }
        val overlay = controlView ?: return
        seekView?.hideSeekFeedback()
        cancelControlOverlayAutoHide()
        seekFeedbackActive = false
        controlOverlayHiding = true
        updateMobileBackgroundGradients(animated = animated, controlsVisible = false)
        applySubtitleStyle(controlsVisibleOverride = false)
        if (!isControlOverlayVisible()) {
            overlay.animate().cancel()
            overlay.isVisible = false
            overlay.alpha = 1f
            controlOverlayHiding = false
            updatePlayPauseButton()
            return
        }
        overlay.animate().cancel()
        updatePlayPauseButton()
        if (!animated) {
            overlay.isVisible = false
            overlay.alpha = 1f
            controlOverlayHiding = false
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
                controlOverlayHiding = false
                collapseOptionsBar(animated = false)
                updatePlayPauseButton()
            }
            .start()
    }

    private fun updateFullscreenButton() {
        val button = fullscreenButton as? ImageButton ?: return
        val drawableRes = if (isVideoFullscreen) {
            R.drawable.ic_fullscreen_exit
        } else {
            R.drawable.ic_fullscreen
        }
        button.setImageDrawable(AppCompatResources.getDrawable(context, drawableRes))
        button.setPadding(0, 0, 0, 0)
    }

    private fun toggleChaptersMenu() {
        if (chaptersMenuView?.isShowing() == true) {
            chaptersMenuView?.dismiss()
            if (controlView?.isVisible == true) {
                scheduleControlOverlayAutoHide()
            }
            return
        }
        showChaptersMenu()
    }

    private fun togglePlaylistMenu() {
        if (playlistMenuView?.isShowing() == true) {
            playlistMenuView?.dismiss()
            if (controlView?.isVisible == true) {
                scheduleControlOverlayAutoHide()
            }
            return
        }
        showPlaylistMenu()
    }

    private fun showPlaylistMenu() {
        if (playlistItems.isEmpty()) {
            return
        }
        cancelControlOverlayAutoHide()
        settingsMenuView?.dismiss()
        chaptersMenuView?.dismiss()
        playlistMenuView?.show()
        playlistMenuView?.let { menu ->
            (menu.parent as? ViewGroup)?.bringChildToFront(menu)
        }
    }

    private fun showChaptersMenu() {
        val chapters = getVideo()?.availableChapters().orEmpty()
        if (chapters.isEmpty()) {
            return
        }
        cancelControlOverlayAutoHide()
        settingsMenuView?.dismiss()
        playlistMenuView?.dismiss()
        chaptersMenuView?.apply {
            setFullscreenMode(isVideoFullscreen)
            setChapters(chapters)
            show()
        }
        bringChaptersAboveOverlay()
    }

    private fun bringChaptersAboveOverlay() {
        chaptersMenuView?.let { chapters ->
            (chapters.parent as? android.view.ViewGroup)?.bringChildToFront(chapters)
        }
    }

    private fun updateChaptersMenuContent() {
        val chapters = getVideo()?.availableChapters().orEmpty()
        chaptersMenuView?.setChapters(chapters)
        updateTimeBarChapters(chapters)
        if (chapters.isEmpty()) {
            chaptersMenuView?.dismiss()
        }
        updateChaptersButtonVisibility()
    }

    private fun updateTimeBarChapters(chapters: List<KinescopeVideoChapterItem>) {
        timeBar?.setChapterStartTimesMs(chapters.map { it.startTimeMs() })
    }

    private fun updateScrubChapterTitle(positionMs: Long) {
        val titleView = scrubChapterTitleView ?: return
        val bar = timeBar ?: return
        val control = controlView ?: return
        val chapter = getVideo()?.availableChapters().orEmpty().chapterAt(positionMs)
        if (chapter == null) {
            titleView.isVisible = false
            return
        }

        titleView.text = chapter.title
        titleView.isVisible = true
        titleView.post {
            if (!titleView.isVisible) {
                return@post
            }
            val durationMs = activePlaybackPlayer?.duration?.takeIf { it > 0 } ?: return@post
            val fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val controlLocation = IntArray(2)
            val barLocation = IntArray(2)
            control.getLocationInWindow(controlLocation)
            bar.getLocationInWindow(barLocation)

            val barLeft = barLocation[0] - controlLocation[0]
            val barWidth = bar.width
            val barTop = barLocation[1] - controlLocation[1]
            val anchorX = barLeft + barWidth * fraction
            val gap = resources.getDimensionPixelSize(R.dimen.kinescope_scrub_chapter_title_gap)
            val offsetDown = resources.getDimensionPixelSize(R.dimen.kinescope_scrub_chapter_title_offset_down)
            val desiredTop = barTop - gap - titleView.height + offsetDown
            titleView.translationY = (desiredTop - titleView.top).toFloat()

            val minX = barLeft.toFloat()
            val maxX = (barLeft + barWidth - titleView.width).toFloat().coerceAtLeast(minX)
            titleView.translationX = if (titleView.width > barWidth) {
                minX
            } else {
                (anchorX - titleView.width / 2f).coerceIn(minX, maxX)
            }
        }
    }

    private fun hideScrubChapterTitle() {
        scrubChapterTitleView?.isVisible = false
        scrubChapterTitleView?.translationX = 0f
        scrubChapterTitleView?.translationY = 0f
    }

    private fun ensureInitialSubtitlesIfNeeded() {
        if (initialSubtitlesConfigured) {
            return
        }
        val video = getVideo() ?: return
        trackController?.ensureDefaultSubtitleEnabled(
            showSubtitles = kinescopePlayer?.getShowSubtitles() == true,
            subtitles = video.subtitles,
        )
        initialSubtitlesConfigured = true
        updateSubtitlesButtonIcon()
    }

    private fun showSubtitlesSettingsMenu() {
        cancelControlOverlayAutoHide()
        chaptersMenuView?.dismiss()
        playlistMenuView?.dismiss()
        settingsMenuView?.setAnchorView(subtitlesButton ?: optionsButton)
        settingsMenuView?.apply {
            setFullscreenMode(isVideoFullscreen)
            setSubtitleStyle(subtitleStyle)
            setParameterOptions(
                parameter = KinescopeSettingsView.Parameter.Subtitles,
                options = getSettingsMenuSubtitlesOptions(),
            )
            updateSettingsMenuCurrentValues()
            showParameterOptions(KinescopeSettingsView.Parameter.Subtitles)
        }
        bringSettingsAboveOverlay()
        if (isCaptionsSearchActive()) {
            updateProgressControlsVisibility()
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

    private fun restoreChromeAfterSettingsDismiss() {
        if (isCaptionsSearchActive()) {
            return
        }
        if (settingsMenuView?.isVisible == true) {
            return
        }
        if (usesCompactOptionsChrome() && isOptionsBarExpanded) {
            optionsExpandableStrip?.isVisible = true
            val stripWidth = measureOptionsExpandableContentWidth()
            if (stripWidth > 0) {
                updateExpandableStripLayout(stripWidth)
            }
            controlBarEndSpacer?.isVisible = true
            progressContainer?.isVisible = false
            timeContainer?.isVisible = false
            positionView?.isVisible = false
            updateExpandedButtonsChildVisibility()
        }
        ensureControlBarProgressChromeVisible()
        updateOptionsButtonsVisibility()
        applyControlBarLayout(usesMobilePlayerChrome())
        updateProgress()
    }

    private fun ensureControlBarProgressChromeVisible() {
        cancelCompactOptionsBarTransition()
        isCompactOptionsBarAnimating = false
        if (usesCompactOptionsChrome()) {
            if (!isOptionsBarExpanded) {
                optionsExpandableStrip?.isVisible = false
                controlBarEndSpacer?.isVisible = false
            }
        } else {
            optionsExpandableStrip?.isVisible = true
            controlBarEndSpacer?.isVisible = false
        }
        resetCompactExpandedChromeTransforms()
        restoreTimeBarFlexibleWidth()
        progressContainer?.alpha = 1f
        timeContainer?.alpha = 1f
        updateProgressControlsVisibility(animated = false)
        val options = kinescopePlayer?.kinescopePlayerOptions
        val showControls = options?.controls != false
        progressContainer?.isVisible = showControls && shouldShowProgressControlsInBar()
        timeContainer?.isVisible = showControls && shouldShowTimeContainerInBar()
        positionView?.isVisible = showControls &&
            !isLiveState &&
            (isMobilePlayerChrome || options?.showDuration == true) &&
            shouldShowTimeContainerInBar()
        syncLiveTimeChrome()
        updateTotalDurationVisibility(animated = false)
    }

    private fun showSettingsMenu() {
        cancelControlOverlayAutoHide()
        chaptersMenuView?.dismiss()
        playlistMenuView?.dismiss()
        settingsMenuView?.setAnchorView(optionsButton)
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
        if (isCaptionsSearchActive()) {
            updateProgressControlsVisibility()
        }
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
        if (parameter is KinescopeSettingsView.Parameter.VideoQuality) {
            updateOptionsButtonIcon()
        }
    }

    private fun openCaptionsSearch() {
        val subtitleUrl = resolveCaptionsSearchSubtitleUrl() ?: return
        settingsMenuView?.dismiss()
        if (isOptionsBarExpanded) {
            collapseOptionsBar(animated = false)
        }
        cancelControlOverlayAutoHide()
        showControlOverlay(animated = false)
        syncCaptionsSearchFullscreenMode()
        captionsSearchView?.show(subtitleUrl)
        updateCaptionsSearchPlayback()
        updateCaptionsSearchInsets()
        updateProgressControlsVisibility()
    }

    private fun tryOpenCaptionsSearchAt(x: Float, y: Float): Boolean {
        if (isCaptionsSearchActive() || isPictureInPictureActive) {
            return false
        }
        if (progressiveSubtitleOverlay?.containsTouch(this, x, y) != true) {
            return false
        }
        if (resolveCaptionsSearchSubtitleUrl() == null) {
            return false
        }
        openCaptionsSearch()
        return true
    }

    private fun subtitleSizeFractionOfShorterSide(): Float =
        if (isVideoFullscreen) {
            SUBTITLE_SIZE_FRACTION_OF_SHORTER_SIDE_FULLSCREEN
        } else {
            SUBTITLE_SIZE_FRACTION_OF_SHORTER_SIDE
        }

    private fun restoreControlOverlayAfterCaptionsSearch() {
        descriptionBlock?.isVisible = titleChromeEnabled
        updateTitles()
        ensureControlBarProgressChromeVisible()
        updatePlayPauseButton()
        showControlOverlay(animated = false)
    }

    private fun updateCaptionsSearchInsets() {
        val searchView = captionsSearchView ?: return
        if (!searchView.isVisible) {
            return
        }
        val bar = controlBar ?: return
        searchView.post {
            val barHeight = bar.height
            if (barHeight <= 0) {
                bar.post { updateCaptionsSearchInsets() }
                return@post
            }
            val layoutParams = searchView.layoutParams as? FrameLayout.LayoutParams ?: return@post
            val fullscreen = searchView.isFullscreenLayout()
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = if (fullscreen) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            layoutParams.gravity = if (fullscreen) {
                Gravity.FILL_HORIZONTAL or Gravity.TOP
            } else {
                Gravity.BOTTOM
            }
            layoutParams.bottomMargin = barHeight + bar.paddingBottom
            layoutParams.topMargin = if (isCaptionsSearchPinnedToTop()) {
                chromeTopSafeInsetPx + captionsSearchTopInset
            } else {
                0
            }
            layoutParams.marginStart = 0
            layoutParams.marginEnd = 0
            searchView.layoutParams = layoutParams
        }
    }

    private fun applyCaptionsSearchControlChrome(active: Boolean) {
        val searchBackground = ContextCompat.getColor(context, R.color.kinescope_caption_search_overlay_bg)
        if (active) {
            mobileFooterGradient?.animate()?.cancel()
            mobileHeaderGradient?.animate()?.cancel()
            if (savedFooterGradientBackground == null) {
                savedFooterGradientBackground = mobileFooterGradient?.background
            }
            mobileFooterGradient?.setBackgroundColor(searchBackground)
            mobileFooterGradient?.isVisible = true
            mobileFooterGradient?.alpha = 1f
            if (savedControlBarBackground == null) {
                savedControlBarBackground = controlBar?.background
            }
            controlBar?.setBackgroundColor(searchBackground)
            captionsSearchView?.findViewById<View>(R.id.captions_search_bottom_divider)?.isVisible = false
            if (!usesGradientChrome()) {
                controlView?.setBackgroundColor(searchBackground)
            }
            controlBar?.isVisible = true
            return
        }
        mobileFooterGradient?.background = savedFooterGradientBackground
        savedFooterGradientBackground = null
        controlBar?.background = savedControlBarBackground
        savedControlBarBackground = null
        captionsSearchView?.findViewById<View>(R.id.captions_search_bottom_divider)?.isVisible = true
        controlView?.setBackgroundColor(getControlOverlayBackgroundColor())
        updateMobileBackgroundGradients()
    }

    private fun setVideoSubtitlesHiddenForCaptionsSearch(hidden: Boolean) {
        videoSubtitlesHiddenForCaptionsSearch = hidden
        if (hidden) {
            subtitleView?.setCues(emptyList())
            progressiveSubtitleOverlay?.clear()
            findViewById<View>(R.id.kinescope_progressive_subtitle_container)?.isVisible = false
            subtitleView?.isVisible = false
            return
        }
        if (trackController?.selectedSubtitleIndex != TrackController.SUBTITLES_OFF_ID) {
            subtitleView?.isVisible = true
            applyProgressiveSubtitles()
        }
    }

    private fun updateCaptionsSearchPlayback(positionMs: Long? = null) {
        val searchView = captionsSearchView ?: return
        if (!searchView.isVisible) {
            return
        }
        val player = activePlaybackPlayer ?: return
        val position = positionMs ?: (currentWindowOffset + player.contentPosition)
        searchView.updatePlaybackPosition(position)
    }

    private fun resolveCaptionsSearchSubtitleUrl(): String? {
        val subtitles = getVideo()?.subtitles.orEmpty()
        if (subtitles.isEmpty()) {
            return null
        }
        val selectedIndex = trackController?.selectedSubtitleIndex ?: TrackController.SUBTITLES_OFF_ID
        val resolvedIndex = if (selectedIndex >= 0) selectedIndex else 0
        return subtitles.getOrNull(resolvedIndex)?.url
    }

    private fun updateVideoScaleSettingsValue() {
        val controller = videoScaleController ?: return
        settingsMenuView?.setParameterCurrentValue(
            parameter = KinescopeSettingsView.Parameter.Scale,
            value = controller.percentLabel(),
        )
        settingsMenuView?.refreshVideoScaleScreenIfVisible()
    }

    private fun updateVideoScaleBadgeVisibility() {
        val suppress = settingsMenuView?.isVideoScaleScreenVisible() == true
        videoScaleController?.setBadgeSuppressed(suppress)
    }

    private fun updateSettingsMenuCurrentValues() {
        settingsMenuView?.runBatchUpdate {
            applySettingsMenuCurrentValues()
        }
        updateSubtitlesButtonIcon()
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

                    trackController?.isAutoQuality == true -> {
                        val vs = localExoPlayer?.videoSize
                        val w = vs?.width ?: 0
                        val h = vs?.height ?: 0
                        val shortSide = qualityDisplayHeightPx(w, h)
                        val names = kinescopePlayer?.qualityNamesByHeight().orEmpty()
                        val captionNumber =
                            names[h]?.let { digitsFromQualityName(it)?.toString() }
                                ?: names[shortSide]?.let { digitsFromQualityName(it)?.toString() }
                                ?: resolveQualityMapName(getVideo()?.qualityMap, w, h)
                                    ?.let { digitsFromQualityName(it)?.toString() }
                                ?: shortSide.takeIf { it > 0 }?.toString()
                                ?: h.takeIf { it > 0 }?.toString()
                                ?: ""
                        context.getString(
                            R.string.settings_video_quality_variant_auto_caption,
                            captionNumber,
                        )
                    }

                    else -> trackController?.selectedQualityVariant?.name.orEmpty()
                },
            )
            settingsMenuView?.setParameterCurrentValue(
                parameter = KinescopeSettingsView.Parameter.Scale,
                value = videoScaleController?.percentLabel().orEmpty(),
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
        pendingCueGroup = null
        subtitleView?.setCues(emptyList())
        progressiveSubtitleOverlay?.clear()
        stopSubtitleUpdates()
        updateSubtitlesButtonIcon()
    }

    private fun shouldShowPauseButton(): Boolean {
        val player = playbackPlayerForChrome() ?: return false
        if (player.playbackState == Player.STATE_ENDED) {
            return false
        }
        if (player.isPlaying) {
            return true
        }
        return player.playWhenReady
    }

    private fun shouldShowReplayButton(): Boolean {
        return playbackPlayerForChrome()?.playbackState == Player.STATE_ENDED
    }

    private fun setUIListeners() {
        if (isPictureInPictureActive) {
            return
        }

        val passThroughTouchListener = View.OnTouchListener { _, _ -> false }
        setOnTouchListener { _, event ->
            if (!isPictureInPictureActive) {
                videoScaleController?.onTouchEvent(event)
                if (videoScaleController?.shouldConsumeGestures() != true) {
                    gestureDetector.onTouchEvent(event)
                }
            }
            true
        }
        seekView?.setOnTouchListener(passThroughTouchListener)
        controlView?.setOnTouchListener(passThroughTouchListener)
        getChildAt(0)?.setOnTouchListener(passThroughTouchListener)

        timeBar?.addListener(componentListener)
        playPauseButton?.setOnClickListener(componentListener)
        pictureInPictureButton?.setOnClickListener(componentListener)
        optionsButton?.setOnClickListener(componentListener)
        chaptersButton?.setOnClickListener(componentListener)
        playlistButton?.setOnClickListener(componentListener)
        subtitlesButton?.setOnClickListener(componentListener)
        optionsDotsButton?.setOnClickListener { onOptionsDotsButtonClick() }
        fullscreenButton?.setOnClickListener(componentListener)
        timeContainer?.setOnClickListener { toggleTotalDurationVisibility() }

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
        enforceLiveTimeChromeIfNeeded()
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
        if (!isLiveState) {
            durationView?.text = durationLabelText(durationMs)
            syncTimeDurationSuffixLayoutIfExpanded()
        }
        timeBar?.setDuration(durationMs)
        updateProgress()
    }

    private fun updateProgress() {
        if (!isAttachedToWindow) {
            return
        }
        enforceLiveTimeChromeIfNeeded()
        val player: Player? = activePlaybackPlayer

        if (isLiveState) {
            syncLiveTimeChrome()
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
            updateCaptionsSearchPlayback(player?.currentPosition)
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
                it.text = formatPlayerTime(position)
            }

        durationView?.text = durationLabelText(duration)
        syncTimeDurationSuffixLayoutIfExpanded()

        timeBar?.setPosition(position)
        timeBar?.setBufferedPosition(bufferedPosition)
        if (progressUpdateListener != null) {
            progressUpdateListener.onProgressUpdate(position, bufferedPosition)
        }
        applyProgressiveSubtitles()
        updateCaptionsSearchPlayback(position)

        if (isCastOverlayVisible) {
            refreshCastOverlay()
        }

        evaluatePlaybackBufferingWatchdog()

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
        if (isCaptionsSearchActive()) {
            return
        }
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
        val viewWidth = when {
            subtitleView.width > 1 -> subtitleView.width.toFloat()
            width > 1 -> width.toFloat()
            else -> 0f
        }
        // Shorter side keeps vertical videos from getting huge captions.
        val referencePx = when {
            viewWidth > 1f && viewHeight > 1f -> minOf(viewWidth, viewHeight)
            viewHeight > 1f -> viewHeight
            viewWidth > 1f -> viewWidth
            else -> 0f
        }
        val sizeFraction = subtitleSizeFractionOfShorterSide()
        val maxTextSp = if (isVideoFullscreen) {
            SUBTITLE_MAX_TEXT_SIZE_FULLSCREEN_SP
        } else {
            SUBTITLE_MAX_TEXT_SIZE_SP
        }
        val maxTextPx = maxTextSp * resources.displayMetrics.scaledDensity
        val textSizePx = if (referencePx > 1f) {
            (sizeFraction * referencePx * subtitleStyle.fontSizePercent / 100f)
                .coerceAtMost(maxTextPx)
        } else {
            15f * subtitleStyle.fontSizePercent / 100f * resources.displayMetrics.scaledDensity
        }
        if (referencePx > 1f) {
            subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        } else {
            subtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                15f * subtitleStyle.fontSizePercent / 100f,
            )
        }

        val density = resources.displayMetrics.density
        val bottomPx = resolveSubtitleBottomPaddingPx(
            controlsVisible = controlsVisible,
            viewHeight = viewHeight,
            density = density,
        )
        if (subtitleView.paddingBottom != bottomPx) {
            subtitleView.setPadding(0, 0, 0, bottomPx)
        }
        subtitleView.setBottomPaddingFraction(0f)

        val (startMarginPx, endMarginPx) = resolveCaptionHorizontalMarginsPx()
        progressiveSubtitleOverlay?.applyStyle(
            style = subtitleStyle,
            textSizePx = textSizePx,
            bottomPaddingPx = bottomPx,
            isFullscreen = isVideoFullscreen,
            isLandscapeFullscreen = isLandscapeFullscreenCaptions(),
            startMarginPx = startMarginPx,
            endMarginPx = endMarginPx,
        )

        // Progressive overlay owns rendering; flashing cues onto SubtitleView when the control
        // chrome appears causes a brief double-draw of the next caption.
        if (shouldApplyProgressiveSubtitles()) {
            subtitleView.setCues(emptyList())
        } else {
            pendingCueGroup?.let { cueGroup ->
                subtitleView.setCues(cueGroup.cues)
            }
        }
        applyScrubChapterTitleStyle()
    }

    /**
     * Landscape fullscreen UI for horizontal video.
     * Prefer content orientation so portrait fullscreen is not misclassified on wide tablets.
     */
    private fun isLandscapeFullscreenCaptions(): Boolean {
        if (!isVideoFullscreen) return false
        if (isPortraitContent) return false
        if (width > 1 && height > 1) {
            return width > height
        }
        return true
    }

    /** Portrait video fullscreen (content-based, not just view aspect). */
    private fun isPortraitFullscreenCaptions(): Boolean {
        if (!isVideoFullscreen) return false
        return isPortraitContent || (width > 1 && height > 1 && height >= width)
    }

    /** Portrait video / tall player UI (inline or fullscreen). */
    private fun isPortraitCaptionsLayout(): Boolean {
        if (isPortraitContent) return true
        return width > 1 && height > 1 && height > width
    }

    /**
     * Side margins for the caption bar.
     * Portrait uses the same edge-to-edge style as horizontal fullscreen.
     */
    private fun resolveCaptionHorizontalMarginsPx(): Pair<Int, Int> {
        val res = resources
        fun dimen(id: Int) = res.getDimensionPixelSize(id)
        return when {
            // Same style as horizontal fullscreen for all portrait layouts.
            isLandscapeFullscreenCaptions() || isPortraitCaptionsLayout() -> {
                dimen(R.dimen.kinescope_caption_margin_start_fullscreen_landscape) to
                    dimen(R.dimen.kinescope_caption_margin_end_fullscreen_landscape)
            }
            isVideoFullscreen -> {
                dimen(R.dimen.kinescope_caption_margin_start_fullscreen) to
                    dimen(R.dimen.kinescope_caption_margin_end_fullscreen)
            }
            else -> {
                dimen(R.dimen.kinescope_caption_margin_start) to
                    dimen(R.dimen.kinescope_caption_margin_end)
            }
        }
    }

    private fun isPortraitCaptionsSearchLayout(fullscreen: Boolean = isVideoFullscreen): Boolean {
        if (!fullscreen) return false
        if (width > 1 && height > 1) {
            return height >= width
        }
        return isPortraitContent
    }

    /**
     * The one place the panel learns its layout — mode, pin and the margins
     * that go with them; every re-sync path goes through here.
     */
    private fun syncCaptionsSearchFullscreenMode() {
        val search = captionsSearchView ?: return
        search.setFullscreenMode(
            fullscreen = isVideoFullscreen,
            portrait = isPortraitCaptionsSearchLayout(),
        )
        search.setPinnedToTop(captionsSearchPlacement == KinescopeCaptionsSearchPlacement.TOP)
        updateCaptionsSearchInsets()
    }

    private fun resolveSubtitleBottomPaddingPx(
        controlsVisible: Boolean,
        viewHeight: Float,
        density: Float,
    ): Int {
        // Horizontal fullscreen — drop toward the bottom chrome when the control overlay
        // (incl. top title) is shown; sit near the edge when chrome is hidden.
        if (isLandscapeFullscreenCaptions()) {
            return resolveClassicFullscreenCaptionBottomPx(
                controlsVisible = controlsVisible,
                density = density,
            )
        }

        // Vertical fullscreen — move with overlay (higher when hidden, lower when shown).
        if (isPortraitFullscreenCaptions()) {
            return if (controlsVisible) {
                resolvePortraitFullscreenCaptionBottomPx(density)
            } else {
                resolvePortraitFullscreenCaptionBottomPxHidden(
                    viewHeight = viewHeight,
                    density = density,
                )
            }
        }

        if (isVideoFullscreen) {
            return resolveClassicFullscreenCaptionBottomPx(
                controlsVisible = controlsVisible,
                density = density,
            )
        }

        // Inline (non-fullscreen): same as fullscreen — sit just above the control bar
        // when chrome is shown; near the bottom edge when hidden.
        if (controlsVisible) {
            return resolveInlineCaptionBottomPx(density)
        }
        return (12f * density).toInt()
    }

    /** Inline player with overlay: tuck captions just above the bottom control bar. */
    private fun resolveInlineCaptionBottomPx(density: Float): Int {
        val bar = controlBar
        val barHeight = when {
            bar != null && bar.isVisible && bar.height > 0 -> bar.height
            else -> resources.getDimensionPixelSize(R.dimen.kinescope_control_bar_height)
        }
        val gapAboveBar = (4f * density).toInt()
        return barHeight + gapAboveBar
    }

    /** Overlay hidden: sit clearly above the seek-bar zone (higher on tablets). */
    private fun resolvePortraitFullscreenCaptionBottomPxHidden(
        viewHeight: Float,
        density: Float,
    ): Int {
        val tablet = isTabletDevice()
        val fraction = if (tablet) 0.28f else 0.14f
        val minDp = if (tablet) 132f else 64f
        return if (viewHeight > 1f) {
            (fraction * viewHeight).toInt().coerceAtLeast((minDp * density).toInt())
        } else {
            ((if (tablet) 140f else 72f) * density).toInt()
        }
    }

    /**
     * Landscape / classic fullscreen vertical offset.
     * Overlay shown: sit just above the bottom control bar (lower than the old ~20% height).
     * Overlay hidden: near the bottom edge.
     */
    private fun resolveClassicFullscreenCaptionBottomPx(
        controlsVisible: Boolean,
        density: Float,
    ): Int {
        if (controlsVisible) {
            val bar = controlBar
            val barHeight = when {
                bar != null && bar.isVisible && bar.height > 0 -> bar.height
                else -> resources.getDimensionPixelSize(R.dimen.kinescope_control_bar_height)
            }
            val gapAboveBar = (4f * density).toInt()
            return barHeight + gapAboveBar
        }
        return (12f * density).toInt()
    }

    /**
     * Portrait fullscreen with overlay: sit near the seek/control bar.
     * On tablets tuck lower so captions clear the top chrome and sit closer to the bar.
     */
    private fun resolvePortraitFullscreenCaptionBottomPx(density: Float): Int {
        val bar = controlBar
        val barHeight = when {
            bar != null && bar.isVisible && bar.height > 0 -> bar.height
            else -> resources.getDimensionPixelSize(R.dimen.kinescope_control_bar_height)
        }
        return if (isTabletDevice()) {
            // Tuck into/near the seek bar when overlay (incl. top chrome) is shown.
            val tuckPx = (36f * density).toInt()
            (barHeight - tuckPx).coerceAtLeast((2f * density).toInt())
        } else {
            val gapAboveBar = (2f * density).toInt()
            barHeight + gapAboveBar
        }
    }

    private fun isTabletDevice(): Boolean =
        resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP

    private fun applyScrubChapterTitleStyle() {
        val titleView = scrubChapterTitleView ?: return
        val backgroundColor = (subtitleStyle.bgColor and 0x00FFFFFF) or
            ((subtitleStyle.bgOpacityPercent * 255 / 100) shl 24)
        titleView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor)
        }
        titleView.setTextColor(subtitleStyle.fontColor)
        ResourcesCompat.getFont(context, R.font.roboto_regular)?.let { titleView.typeface = it }
    }

    private fun shouldApplyProgressiveSubtitles(): Boolean {
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

    private fun hideVideoSubtitlesForScrub() {
        stopSubtitleUpdates()
        subtitleView?.setCues(emptyList())
        subtitleView?.isVisible = false
        val container = findViewById<View>(R.id.kinescope_progressive_subtitle_container) ?: run {
            progressiveSubtitleOverlay?.clear()
            return
        }
        container.animate().cancel()
        if (!container.isVisible || container.alpha <= 0.01f) {
            progressiveSubtitleOverlay?.clear()
            container.isVisible = false
            container.alpha = 1f
            return
        }
        container.animate()
            .alpha(0f)
            .setDuration(SUBTITLE_SCRUB_FADE_DURATION_MS)
            .withEndAction {
                progressiveSubtitleOverlay?.clear()
                container.isVisible = false
                container.alpha = 1f
            }
            .start()
    }

    private fun restoreVideoSubtitlesAfterScrub() {
        if (videoSubtitlesHiddenForCaptionsSearch) {
            return
        }
        if (trackController?.selectedSubtitleIndex == TrackController.SUBTITLES_OFF_ID) {
            return
        }
        subtitleView?.isVisible = true
        val container = findViewById<View>(R.id.kinescope_progressive_subtitle_container)
        container?.animate()?.cancel()
        container?.alpha = 0f
        applyProgressiveSubtitles()
        if (shouldApplyProgressiveSubtitles()) {
            scheduleSubtitleUpdates()
        }
        val target = container ?: return
        if (!target.isVisible && progressiveSubtitleOverlay?.hasVisibleContent() != true) {
            target.alpha = 1f
            return
        }
        if (!target.isVisible) {
            target.isVisible = true
        }
        target.animate()
            .alpha(1f)
            .setDuration(SUBTITLE_SCRUB_FADE_DURATION_MS)
            .start()
    }

    private fun areVideoSubtitlesSuppressed(): Boolean {
        return videoSubtitlesHiddenForCaptionsSearch || scrubbing || scrubOverlayHiding
    }

    private fun applyProgressiveSubtitles() {
        if (areVideoSubtitlesSuppressed()) {
            // Keep current overlay pixels while scrub fade-out runs; do not hard-clear.
            subtitleView?.setCues(emptyList())
            return
        }
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
            progressiveSubtitleOverlay?.clear()
            subtitleView?.setCues(cueGroup.cues)
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

    private fun showLiveScrubBadge() {
        liveBadgeTextView?.isVisible = true
        liveBadgeCircleView?.isVisible = true
        updateLiveBadgeVisuals()
    }

    private fun updateLiveBadgeVisuals() {
        if (!isLiveState) {
            stopLiveBadgePulse()
            return
        }
        if (scrubbing && !isLiveSynced) {
            startLiveBadgePulse()
            return
        }
        stopLiveBadgePulse()
        setLiveBadgeCircleDrawable(isLiveSynced)
    }

    private fun setLiveBadgeCircleDrawable(isLiveSynced: Boolean) {
        liveBadgeCircleView?.background = ContextCompat.getDrawable(
            context,
            when (isLiveSynced) {
                true -> R.drawable.ic_live_synced
                else -> R.drawable.ic_live_not_synced
            },
        )
    }

    private fun startLiveBadgePulse() {
        val circle = liveBadgeCircleView ?: return
        if (liveBadgePulseAnimator?.isRunning == true) {
            circle.background = ContextCompat.getDrawable(context, R.drawable.ic_live_synced)
            return
        }
        stopLiveBadgePulse()
        circle.background = ContextCompat.getDrawable(context, R.drawable.ic_live_synced)
        circle.alpha = 1f
        liveBadgePulseAnimator = ValueAnimator.ofFloat(1f, LIVE_BADGE_PULSE_MIN_ALPHA).apply {
            duration = LIVE_BADGE_PULSE_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                circle.alpha = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun stopLiveBadgePulse() {
        liveBadgePulseAnimator?.cancel()
        liveBadgePulseAnimator = null
        liveBadgeCircleView?.alpha = 1f
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
        captionsSearchView?.dismiss()
        videoScaleController?.reset(animated = false)
        if (settingsMenuView?.isVisible == true) {
            settingsMenuView?.dismiss()
        }
        chaptersMenuView?.dismiss()
        isOptionsBarExpanded = false
        lastCompactProgressBarWidth = 0
        recoverCompactOptionsBarLayout()
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
        cancelCompactOptionsBarTransition()
        controlBarEndSpacer?.isVisible = false
        resetCompactExpandedChromeTransforms()
        optionsExpandableStrip?.isVisible = false
        controlBar?.isVisible = true
    }

    private fun restoreProgressChromeAfterPipExit() {
        cancelCompactOptionsBarTransition()
        isCompactOptionsBarAnimating = false
        isOptionsBarExpanded = false
        lastCompactProgressBarWidth = 0
        controlOverlayHiding = false
        scrubbing = false
        scrubOverlayHiding = false
        recoverCompactOptionsBarLayout()
        timeBar?.animate()?.cancel()
        timeBar?.scaleX = 1f
        timeBar?.scaleY = 1f
        timeBar?.setScrubVisualExpanded(expanded = false)
        controlElevationBeforeScrub = 0f
        controlView?.elevation = 0f
        updateProgressControlsVisibility(animated = false)
        progressContainer?.post {
            recoverCompactOptionsBarLayout()
            timeBar?.requestLayout()
            timeBar?.invalidate()
            progressContainer?.requestLayout()
        }
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
            restoreProgressChromeAfterPipExit()
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
        deviceName: String?,
        onStopCast: () -> Unit,
    ) {
        isCastOverlayVisible = true
        castOverlayView?.isVisible = true

        val deviceLabel = deviceName ?: context.getString(R.string.player_cast_device_unknown)
        castDeviceView?.text = context.getString(R.string.player_cast_device, deviceLabel)

        castPlayPauseView?.setOnClickListener { toggleCastPlayback() }
        castStopView?.setOnClickListener { onStopCast() }

        refreshCastOverlay()
    }

    fun refreshCastOverlay() {
        if (!isCastOverlayVisible) return
        val player = activePlaybackPlayer ?: return

        castPlayPauseView?.setImageResource(
            if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )

        val duration = player.duration
        val position = player.currentPosition
        val showSeek = duration > 0
        castSeekBar?.isVisible = showSeek
        castPositionView?.isVisible = showSeek
        castDurationView?.isVisible = showSeek

        if (showSeek) {
            castPositionView?.text = formatPlayerTime(position)
            castDurationView?.text = formatPlayerTime(duration)
            isUpdatingCastSeekBar = true
            castSeekBar?.progress = ((position.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000)
            isUpdatingCastSeekBar = false
        }
    }

    fun hideCastOverlay() {
        isCastOverlayVisible = false
        castOverlayView?.isVisible = false
        castPlayPauseView?.setOnClickListener(null)
        castStopView?.setOnClickListener(null)
    }

    private fun toggleCastPlayback() {
        val videoPlayer = kinescopePlayer ?: return
        if (activePlaybackPlayer?.isPlaying == true) {
            videoPlayer.pause()
        } else {
            videoPlayer.play()
        }
        refreshCastOverlay()
    }

    private fun syncPlaybackBufferingWatchdog(playbackState: Int) {
        if (shouldSuppressPlaybackStallWatchdog()) {
            resetPlaybackStallWatchdog()
            return
        }
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                playbackBufferingWatchdog.onBufferingStarted(
                    activePlaybackPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                )
                removeCallbacks(bufferingWatchdogRunnable)
                postDelayed(bufferingWatchdogRunnable, PlaybackBufferingWatchdog.POLL_MS)
            }
            else -> {
                playbackBufferingWatchdog.onBufferingStopped()
                removeCallbacks(bufferingWatchdogRunnable)
            }
        }
    }

    private fun evaluatePlaybackBufferingWatchdog() {
        if (shouldSuppressPlaybackStallWatchdog()) {
            return
        }
        val player = activePlaybackPlayer ?: return
        if (player.playbackState != Player.STATE_BUFFERING || playbackStallDispatched) return
        val positionMs = if (isLiveState) {
            player.currentPosition
        } else {
            currentWindowOffset + player.contentPosition
        }
        val message = playbackBufferingWatchdog.evaluate(
            isBuffering = true,
            positionMs = positionMs,
            hasActiveError = false,
        ) ?: return
        playbackStallDispatched = true
        kinescopePlayer?.pause()
        val listener = onPlaybackStallListener
        if (listener != null) {
            listener(message)
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

}
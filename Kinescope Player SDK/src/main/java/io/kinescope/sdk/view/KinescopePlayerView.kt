package io.kinescope.sdk.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import io.kinescope.sdk.R
import io.kinescope.sdk.adapter.KinescopeSettingsAdapter
import io.kinescope.sdk.analytics.KinescopeAnalyticsManager
import io.kinescope.sdk.analytics.KinescopeAnalyticsArgs
import io.kinescope.sdk.logger.KinescopeLogger
import io.kinescope.sdk.logger.KinescopeLoggerLevel
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.utils.EMPTY
import io.kinescope.sdk.utils.animateRotation
import io.kinescope.sdk.utils.formatLiveStartDate
import me.saket.cascade.CascadePopupMenu
import kotlin.math.roundToInt


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
                "double tap event, isForward=${isForward(e)}"
            )
            if (isForward(e)) seekView?.showForwardView(e) else seekView?.showBackView(e)
            return super.onDoubleTapEvent(e)
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

    private var customControlsLayoutId: Int = 0

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
    private var timeBar: TimeBar? = null
    private var playPauseButton: View? = null
    private var optionsButton: View? = null
    private var fullscreenButton: View? = null
    private var subtitlesButton: View? = null
    private var attachmentsButton: View? = null

    private var titleView: TextView? = null
    private var authorView: TextView? = null

    private var liveDataView: View? = null
    private var liveBadgeCircleView: View? = null
    private var liveBadgeTextView: View? = null
    private var liveTimeOffsetTextView: TextView? = null
    private var liveStartDateContainerView: View? = null
    private var liveStartDateTextView: TextView? = null

    private var settingsWindow: PopupWindow? = null
    private var settingsview: RecyclerView? = null
    private var settingsAdapter: KinescopeSettingsAdapter? = null
    private var playbackSpeedAdapter: KinescopeSettingsAdapter? = null

    private val settingsWindowMargin =
        resources.getDimensionPixelSize(R.dimen.kinescope_media_settings_offset)

    private var scrubbing = false
    private var scrubbingLiveDurationCached = 0L

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

    private var analyticsManager = KinescopeAnalyticsManager(context)

    private val updateProgressRunnable = Runnable {
        updateProgress()
        getAnalyticsArguments().let { args ->
            analyticsManager.tick(args = args)
        }
    }

    private var playbackSpeedOption: String = "normal"
    private val onPlaybackSpeedOptionsCallback = object : (String) -> Unit {
        override fun invoke(speed: String) {
            when (speed) {
                "normal" -> {
                    playbackSpeedOption = "normal"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(1f)
                }

                "0.25" -> {
                    playbackSpeedOption = "0.25"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(0.25f)
                }

                "0.5" -> {
                    playbackSpeedOption = "0.5"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(0.5f)
                }

                "0.75" -> {
                    playbackSpeedOption = "0.75"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(0.75f)
                }

                "1.25" -> {
                    playbackSpeedOption = "1.25"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(1.25f)
                }

                "1.5" -> {
                    playbackSpeedOption = "1.5"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(1.5f)
                }

                "1.75" -> {
                    playbackSpeedOption = "1.75"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(1.75f)
                }

                "2" -> {
                    playbackSpeedOption = "2"
                    kinescopePlayer?.exoPlayer?.setPlaybackSpeed(2f)
                }
            }
            settingsWindow?.dismiss()
        }
    }

    private val progressUpdateListener =
        PlayerControlView.ProgressUpdateListener { _, _ ->
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
                    Player.STATE_BUFFERING -> analyticsManager.buffering()

                    Player.STATE_READY -> {
                        posterView?.isVisible = false
                        liveStartDateContainerView?.isVisible = false
                        analyticsManager.ready(args = args)
                    }

                    Player.STATE_ENDED -> analyticsManager.end(args = args)

                    else -> {}
                }
            }

            updateBuffering()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            updateBuffering()
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
            if (!canceled && kinescopePlayer != null) {
                seekToTimeBarPosition(kinescopePlayer!!.exoPlayer!!, position)
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
            } else if (optionsButton === view) {
                displaySettingsWindow(playbackSpeedAdapter!!)
            } else if (subtitlesButton === view) {
                //TODO: Subtitles menu
            } else if (attachmentsButton === view) {

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

        timeBar = controlView?.findViewById<KinescopeTimeBar>(R.id.kinescope_progress)
        positionView = controlView?.findViewById(R.id.kinescope_position)
        durationView = controlView?.findViewById(R.id.kinescope_duration)
        timeSeparatorView = controlView?.findViewById(R.id.time_separator_view)
        playPauseButton = controlView?.findViewById(R.id.kinescope_play_pause)
        optionsButton = controlView?.findViewById(R.id.kinescope_settings)
        fullscreenButton = controlView?.findViewById(R.id.kinescope_fullscreen)
        subtitlesButton = controlView?.findViewById(R.id.kinescope_btn_subtitles)
        attachmentsButton = controlView?.findViewById(R.id.kinescope_btn_attachments)
        titleView = controlView?.findViewById(R.id.kinescope_title)
        authorView = controlView?.findViewById(R.id.kinescope_author)

        liveDataView = controlView?.findViewById(R.id.live_data_ll)
        liveBadgeCircleView = controlView?.findViewById(R.id.live_badge_circle_view)
        liveBadgeTextView = controlView?.findViewById(R.id.live_badge_tv)
        liveTimeOffsetTextView = controlView?.findViewById(R.id.live_time_offset)
        liveStartDateContainerView = findViewById(R.id.live_start_date_ll)
        liveStartDateTextView = findViewById(R.id.live_start_date_tv)

        settingsview =
            LayoutInflater.from(context).inflate(R.layout.view_options_list, null) as RecyclerView?
        settingsAdapter = KinescopeSettingsAdapter(arrayOf("Playback speed", "Quality"), null, null)
        playbackSpeedAdapter = KinescopeSettingsAdapter(
            resources.getStringArray(R.array.menu_playback_speed),
            playbackSpeedOption,
            onPlaybackSpeedOptionsCallback
        )
        settingsview?.adapter = playbackSpeedAdapter
        settingsview?.layoutManager = LinearLayoutManager(
            this@KinescopePlayerView.context,
            LinearLayoutManager.VERTICAL,
            false
        )
        settingsWindow = PopupWindow(
            settingsview,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        settingsWindow?.setOnDismissListener(componentListener)

        applyKinescopePlayerOptions()
        setSubtitlesStyling()
        setUIListeners()
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
        kinescopePlayer?.exoPlayer?.addListener(componentListener)
        kinescopePlayer?.onSourceChanged = { source ->
            analyticsManager.sourceChanged(newSource = source)
        }
        exoPlayerView?.player = kinescopePlayer?.exoPlayer
        applyKinescopePlayerOptions()
        applyExoPlayerVisibility()
        updateAll()
    }

    fun enableLiveState(
        posterUrl: String? = null,
        startDate: String? = null
    ) {
        isLiveState = true
        isLiveSynced = true

        positionView?.isVisible = false
        durationView?.isVisible = false
        timeSeparatorView?.isVisible = false
        liveDataView?.isVisible = true

        startDate?.let { date ->
            formatLiveStartDate(date)
                .takeIf { date.isNotEmpty() }
                ?.let { formattedDate ->
                    liveStartDateContainerView?.isVisible = true
                    liveStartDateTextView?.text = formattedDate
                }
        }

        with(posterView) {
            isVisible = true
            posterUrl?.let {
                Picasso.get()
                    .load(posterUrl)
                    .placeholder(ContextCompat.getDrawable(context, R.drawable.default_poster)!!)
                    .into(this)
            }
        }
    }

    private fun getVideo(): KinescopeVideo? = kinescopePlayer?.getVideo()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        /*isAttachedToWindow = true
        if (isFullyVisible()) {
            controlViewLayoutManager.resetHideCallbacks()
        }*/
        updateAll()
    }

    fun setCustomControllerLayoutID(value: Int) {
        customControlsLayoutId = value
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

    fun setIsFullscreen(value: Boolean) {
        isVideoFullscreen = value
        updateFullscreenButton()
    }

    private fun updateSettingsWindowSize() {
        settingsview!!.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val maxWidth: Int = width - settingsWindowMargin * 2
        val itemWidth: Int = settingsview!!.measuredWidth
        val width = Math.min(itemWidth, maxWidth)
        settingsWindow!!.width = width
        val maxHeight: Int = height - settingsWindowMargin * 2
        val totalHeight: Int = settingsview!!.measuredHeight
        val height = Math.min(maxHeight, totalHeight)
        settingsWindow!!.height = height
    }

    private fun displaySettingsWindow(settingsAdapter: RecyclerView.Adapter<*>) {
        settingsview?.adapter = settingsAdapter
        updateSettingsWindowSize()
        //settingsWindow!!.dismiss()
        val xoff: Int = width - settingsWindow!!.width - settingsWindowMargin
        val yoff: Int = -settingsWindow!!.height - optionsButton!!.height - settingsWindowMargin
        //settingsWindow!!.showAsDropDown(optionsButton, xoff, yoff)

        //CascadePopupMenu(context, optionsButton!!)

        val popup = CascadePopupMenu(
            context = context,
            anchor = optionsButton!!,
            styler = CascadePopupMenu.Styler(
                background = {
                    AppCompatResources.getDrawable(
                        context,
                        R.drawable.bg_options_rect
                    )
                },
                menuItem = {
                    it.titleView.setTextColor(Color.parseColor("#ffffff"))
                    it.subMenuArrowView.updatePadding(right = 56)
                },
                menuTitle = {
                    it.titleView.setTextColor(Color.parseColor("#ffffff"))
                    it.titleView.compoundDrawablePadding = 48
                    it.titleView.updatePadding(left = 36)
                },
            )
        )

        //popup.popup.setBackgroundDrawable(context.getDrawable(R.drawable.bg_options_rect))
        popup.menu.addSubMenu("Video quality")
            .setIcon(R.drawable.ic_option_quality)
            .also { sub ->
                sub.add("480p")
                sub.add("720p")
                sub.add("1080p")
            }

        popup.menu.addSubMenu("Playback speed")
            .setIcon(R.drawable.ic_option_playback_speed)
            .also { sub ->
                sub.add("0.5")
                sub.add("0.75")
                sub.add("Normal")
                sub.add("1.25")
                sub.add("1.5")
                sub.add("1.75")
                sub.add("2")
            }
        popup.show()
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
            fullscreenButton?.isVisible = options.showFullscreenButton
            seekView?.isVisible = options.showSeekBar
            subtitlesButton?.isVisible = options.showSubtitlesButton
            attachmentsButton?.isVisible = options.showAttachments
            optionsButton?.isVisible = options.showOptionsButton
        }
    }

    private fun updateTitles() {
        if (getVideo() == null) return
        titleView?.text = getVideo()!!.title
        authorView?.text = getVideo()!!.subtitle
    }

    private fun updatePlayPauseButton() {
        if (!controlView!!.isVisible || !isAttachedToWindow) {
            return
        }
        if (playPauseButton != null) {
            if (shouldShowPauseButton()) {
                (playPauseButton as ImageView)
                    .setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.kinescope_controls_pause
                        )
                    )
            } else {
                (playPauseButton as ImageView)
                    .setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.kinescope_controls_play
                        )
                    )
            }
        }
    }

    private fun updateFullscreenButton() {
        if (fullscreenButton != null) {
            if (isVideoFullscreen) {
                (fullscreenButton as ImageView)
                    .setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_fullscreen_disable
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
        optionsButton?.setOnClickListener(componentListener)
        fullscreenButton?.setOnClickListener(componentListener)
        subtitlesButton?.setOnClickListener(componentListener)
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
        controlView!!.isVisible = !controlView!!.isVisible
        updateAll()
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

        getAnalyticsArguments().let { args ->
            analyticsManager.play(args = args)
        }
    }

    private fun dispatchPause(player: Player) {
        player.pause()

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
        kinescopePlayer?.exoPlayer?.let { player ->
            player.playbackParameters.speed
            player.volume
            player.isDeviceMuted
            isVideoFullscreen
            player.bufferedPosition

            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volume = 100 * currentVolume / maxVolume
            val isMuted = volume == 0

            KinescopeAnalyticsArgs(
                duration = (player.duration.toFloat() / 1000f).roundToInt(),
                rate = player.playbackParameters.speed,
                volume = volume,
                quality = String.EMPTY,
                isMuted = isMuted,
                isFullScreen = isVideoFullscreen,
                previewPosition = (player.bufferedPosition.toFloat() / 1000f).roundToInt(),
                currentPosition = (player.currentPosition.toFloat() / 1000f).roundToInt(),
            )
        } ?: KinescopeAnalyticsArgs()
}
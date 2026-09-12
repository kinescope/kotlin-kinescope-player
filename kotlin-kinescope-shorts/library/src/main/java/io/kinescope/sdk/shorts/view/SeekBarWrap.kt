package io.kinescope.sdk.shorts.view

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import io.kinescope.sdk.shorts.R
import io.kinescope.sdk.shorts.config.KinescopeUiConfig
import io.kinescope.sdk.shorts.viewholders.VideoViewHolder
import kotlinx.serialization.InternalSerializationApi
import kotlin.math.abs

@InternalSerializationApi
@OptIn(androidx.media3.common.util.UnstableApi::class)
open class SeekBarWrap(private val videoViewHolder: VideoViewHolder) {
    private val seekBar: SeekBar = videoViewHolder.binding.playerView.findViewById(R.id.seekBar)
    private var wasPlaying = false
    private var isScrubbing = false
    /** True only after the finger moves past touch-slop — tap alone must not seek. */
    private var scrubDragConfirmed = false
    private var touchDownRawX = 0f
    private var anchorProgressMs = 0
    private val touchSlop =
        ViewConfiguration.get(videoViewHolder.itemView.context).scaledTouchSlop

    private val btnPlay: ImageButton =
        videoViewHolder.binding.playerView.findViewById(R.id.btnPlay)
    private val scrubTimeText: TextView =
        videoViewHolder.binding.playerView.findViewById(R.id.scrubTimeText)
    private val seekBarHitArea: View =
        videoViewHolder.binding.playerView.findViewById(R.id.seekBarHitArea)

    init {
        KinescopeUiConfig.applyToPlayerControls(videoViewHolder.binding.playerView)
        if (KinescopeUiConfig.isSeekBarEnabled()) {
            seekBar.visibility = View.INVISIBLE
        }
        btnPlay.visibility = View.GONE
        setupSeekBarListener()
        setupTouchListener()
    }

    private val uiElementsToHide = listOf<View>(
        videoViewHolder.binding.TitleVideo,
        videoViewHolder.binding.descriptionVideo,
        videoViewHolder.binding.btnLike,
        videoViewHolder.binding.btnDisLike,
        videoViewHolder.binding.btnComment,
        videoViewHolder.binding.btnShare,
    )

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || !isScrubbing) return
                if (!scrubDragConfirmed) {
                    // Ignore SeekBar's tap-to-position jump until a real drag starts.
                    seekBar?.progress = anchorProgressMs
                    return
                }
                showScrubTime(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isScrubbing = true
                scrubDragConfirmed = false
                btnPlay.visibility = View.GONE
                anchorProgressMs = videoViewHolder.playerManager.exoPlayer
                    ?.currentPosition?.toInt()?.coerceAtLeast(0)
                    ?: (seekBar?.progress ?: 0)
                seekBar?.progress = anchorProgressMs

                videoViewHolder.playerManager.exoPlayer?.apply {
                    wasPlaying = playWhenReady
                    if (!playWhenReady) {
                        playWhenReady = true
                    }
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val target = seekBar?.progress?.toLong() ?: 0L
                seekBar?.animate()?.scaleY(1f)?.setDuration(150)?.start()
                hideScrubTime()
                restoreChrome()

                videoViewHolder.playerManager.exoPlayer?.apply {
                    if (scrubDragConfirmed) {
                        seekTo(target)
                    } else {
                        // Tap only — keep playback position, reset bar.
                        seekBar?.progress = currentPosition.toInt()
                    }
                    playWhenReady = wasPlaying
                }

                isScrubbing = false
                scrubDragConfirmed = false
                btnPlay.visibility = View.GONE
                syncTimelineVisibility()
                updateButtonVisibility()
            }
        })

        seekBarHitArea.setOnTouchListener { _, event ->
            if (isTouchOnTextChrome(event)) {
                return@setOnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownRawX = event.rawX
                    scrubDragConfirmed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scrubDragConfirmed && abs(event.rawX - touchDownRawX) >= touchSlop) {
                        confirmScrubDrag()
                    }
                }
            }
            forwardTouchToSeekBar(event)
            true
        }
    }

    private fun isTouchOnTextChrome(event: MotionEvent): Boolean {
        val container = videoViewHolder.binding.textChromeContainer
        if (container.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        val x = event.rawX
        val y = event.rawY
        return x >= loc[0] && x <= loc[0] + container.width &&
            y >= loc[1] && y <= loc[1] + container.height
    }

    private fun confirmScrubDrag() {
        if (scrubDragConfirmed) return
        scrubDragConfirmed = true
        setTimelineVisible(true)
        seekBar.animate()?.scaleY(2.5f)?.setDuration(150)?.start()
        scrubTimeText.visibility = View.VISIBLE
        showScrubTime(seekBar.progress)

        for (element in uiElementsToHide) {
            element.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { element.visibility = View.GONE }
                .start()
        }
    }

    private fun restoreChrome() {
        for (element in uiElementsToHide) {
            element.visibility = View.VISIBLE
            element.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun forwardTouchToSeekBar(event: MotionEvent): Boolean {
        // Keep layout sized; visual show only after drag is confirmed (or when paused).
        if (scrubDragConfirmed || videoViewHolder.playerManager.exoPlayer?.playWhenReady != true) {
            setTimelineVisible(true)
        } else if (seekBar.visibility != View.VISIBLE) {
            // Need VISIBLE for reliable tracking while gesture is active.
            seekBar.visibility = View.VISIBLE
            seekBar.alpha = 0f
        }

        val hitLoc = IntArray(2)
        val seekLoc = IntArray(2)
        seekBarHitArea.getLocationOnScreen(hitLoc)
        seekBar.getLocationOnScreen(seekLoc)

        val mapped = MotionEvent.obtain(event)
        mapped.offsetLocation(
            (hitLoc[0] - seekLoc[0]).toFloat(),
            (hitLoc[1] - seekLoc[1]).toFloat(),
        )
        val clampedY = mapped.y.coerceIn(0f, seekBar.height.toFloat().coerceAtLeast(1f))
        mapped.setLocation(mapped.x, clampedY)
        val handled = seekBar.onTouchEvent(mapped)
        mapped.recycle()

        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            seekBar.alpha = 1f
        }
        return handled
    }

    private fun syncTimelineVisibility() {
        if (isScrubbing && scrubDragConfirmed) {
            setTimelineVisible(true)
            return
        }
        val wantsPlay = videoViewHolder.playerManager.exoPlayer?.playWhenReady == true
        setTimelineVisible(!wantsPlay)
    }

    private fun setTimelineVisible(visible: Boolean) {
        if (!KinescopeUiConfig.isSeekBarEnabled()) {
            seekBar.visibility = View.GONE
            seekBarHitArea.visibility = View.GONE
            scrubTimeText.visibility = View.GONE
            return
        }
        seekBar.alpha = 1f
        seekBar.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    @SuppressLint("DefaultLocale")
    private fun showScrubTime(positionMs: Int) {
        if (!scrubDragConfirmed) return
        val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        scrubTimeText.text = String.format("%d:%02d", minutes, seconds)
        scrubTimeText.visibility = View.VISIBLE
        positionScrubTimeAboveProgress(positionMs)
    }

    private fun positionScrubTimeAboveProgress(positionMs: Int) {
        val applyPosition = {
            val max = seekBar.max.coerceAtLeast(1)
            val trackWidth = (seekBar.width - seekBar.paddingStart - seekBar.paddingEnd).coerceAtLeast(0)
            val fraction = (positionMs.toFloat() / max).coerceIn(0f, 1f)
            val thumbCenterX = seekBar.left + seekBar.paddingStart + trackWidth * fraction
            val labelHalf = scrubTimeText.width / 2f
            val parentWidth = (scrubTimeText.parent as? View)?.width ?: seekBar.width
            val minX = 8f
            val maxX = (parentWidth - scrubTimeText.width - 8).toFloat().coerceAtLeast(minX)
            val targetX = (thumbCenterX - labelHalf).coerceIn(minX, maxX)
            scrubTimeText.translationX = targetX - scrubTimeText.left
        }
        if (scrubTimeText.width == 0) {
            scrubTimeText.post(applyPosition)
        } else {
            applyPosition()
        }
    }

    fun updateFullTimes(duration: Int) {
        // no-op
    }

    private fun hideScrubTime() {
        scrubTimeText.visibility = View.GONE
        scrubTimeText.translationX = 0f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        btnPlay.setOnClickListener {
            togglePlayPause()
        }

        var isScroll = false
        var startX = 0f
        var startY = 0f

        videoViewHolder.binding.playerView.setOnTouchListener { _, event ->
            if (isScrubbing || isTouchOnSeekBar(event) || isTouchOnTextChrome(event)) {
                return@setOnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isScroll = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - startX) > 20 || abs(event.y - startY) > 20) {
                        isScroll = true
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    if (!isScroll) {
                        togglePlayPause()
                    }
                    false
                }
                else -> false
            }
        }
    }

    private fun isTouchOnSeekBar(event: MotionEvent): Boolean {
        val location = IntArray(2)
        seekBarHitArea.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]
        return event.rawX >= x && event.rawX <= x + seekBarHitArea.width &&
            event.rawY >= y && event.rawY <= y + seekBarHitArea.height
    }

    fun togglePlayPause() {
        if (isScrubbing) return
        videoViewHolder.playerManager.exoPlayer?.let { player ->
            if (player.isPlaying || player.playWhenReady) {
                pauseVideo()
            } else {
                playVideo()
            }
        }
    }

    fun updateButtonVisibility() {
        if (isScrubbing || !KinescopeUiConfig.isPlayButtonEnabled()) {
            btnPlay.visibility = View.GONE
            return
        }
        videoViewHolder.playerManager.exoPlayer?.let { player ->
            btnPlay.visibility = if (player.playWhenReady) View.GONE else View.VISIBLE
        }
    }

    fun updateSeekBarMax(durationMs: Int) {
        seekBar.max = durationMs.coerceAtLeast(0)
    }

    fun hidePlayButton() {
        btnPlay.visibility = View.GONE
        if (!isScrubbing) {
            setTimelineVisible(false)
        }
    }

    fun playVideo() {
        if (isScrubbing) return
        videoViewHolder.playerManager.exoPlayer?.playWhenReady = true
        setTimelineVisible(false)
        btnPlay.visibility = View.GONE
    }

    fun updateSeekBarProgress(progress: Int) {
        if (!isScrubbing && !seekBar.isPressed) {
            seekBar.progress = progress
        }
    }

    fun pauseVideo() {
        if (isScrubbing) return
        videoViewHolder.playerManager.exoPlayer?.playWhenReady = false
        setTimelineVisible(true)
        btnPlay.visibility =
            if (KinescopeUiConfig.isPlayButtonEnabled()) View.VISIBLE else View.GONE
    }
}

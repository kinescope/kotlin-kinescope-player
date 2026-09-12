package io.kinescope.sdk.shorts.view

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.kinescope.sdk.shorts.R
import io.kinescope.sdk.shorts.config.KinescopeUiConfig
import kotlinx.serialization.InternalSerializationApi
import kotlin.math.abs

@InternalSerializationApi
class OfflineSeekBarWrap(
    private val playerView: PlayerView,
    private val exoPlayer: ExoPlayer?,
) {
    private val seekBar: SeekBar = playerView.findViewById(R.id.seekBar)
    private var wasPlaying = false
    private var isScrubbing = false
    private var scrubDragConfirmed = false
    private var touchDownRawX = 0f
    private var anchorProgressMs = 0
    private val touchSlop = ViewConfiguration.get(playerView.context).scaledTouchSlop

    private val btnPlay: ImageButton = playerView.findViewById(R.id.btnPlay)
    private val scrubTimeText: TextView = playerView.findViewById(R.id.scrubTimeText)
    private val seekBarHitArea: View = playerView.findViewById(R.id.seekBarHitArea)

    init {
        KinescopeUiConfig.applyToPlayerControls(playerView)
        if (KinescopeUiConfig.isSeekBarEnabled()) {
            seekBar.visibility = View.INVISIBLE
        }
        btnPlay.visibility = View.GONE
        setupSeekBarListener()
        setupTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || !isScrubbing) return
                if (!scrubDragConfirmed) {
                    seekBar?.progress = anchorProgressMs
                    return
                }
                showScrubTime(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isScrubbing = true
                scrubDragConfirmed = false
                btnPlay.visibility = View.GONE
                anchorProgressMs = exoPlayer?.currentPosition?.toInt()?.coerceAtLeast(0)
                    ?: (seekBar?.progress ?: 0)
                seekBar?.progress = anchorProgressMs

                exoPlayer?.apply {
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

                exoPlayer?.apply {
                    if (scrubDragConfirmed) {
                        seekTo(target)
                    } else {
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

    private fun confirmScrubDrag() {
        if (scrubDragConfirmed) return
        scrubDragConfirmed = true
        setTimelineVisible(true)
        seekBar.animate()?.scaleY(2.5f)?.setDuration(150)?.start()
        scrubTimeText.visibility = View.VISIBLE
        showScrubTime(seekBar.progress)
    }

    private fun forwardTouchToSeekBar(event: MotionEvent): Boolean {
        if (scrubDragConfirmed || exoPlayer?.playWhenReady != true) {
            setTimelineVisible(true)
        } else if (seekBar.visibility != View.VISIBLE) {
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
        val wantsPlay = exoPlayer?.playWhenReady == true
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

        playerView.setOnTouchListener { _, event ->
            if (isScrubbing || isTouchOnSeekBar(event)) {
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
        exoPlayer?.let { player ->
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
        exoPlayer?.let { player ->
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
        exoPlayer?.playWhenReady = true
        setTimelineVisible(false)
        btnPlay.visibility = View.GONE
    }

    fun pauseVideo() {
        if (isScrubbing) return
        exoPlayer?.playWhenReady = false
        setTimelineVisible(true)
        btnPlay.visibility =
            if (KinescopeUiConfig.isPlayButtonEnabled()) View.VISIBLE else View.GONE
    }
}

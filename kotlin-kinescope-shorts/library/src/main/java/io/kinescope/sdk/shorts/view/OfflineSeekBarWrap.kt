package io.kinescope.sdk.shorts.view

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.kinescope.sdk.shorts.R
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi

class OfflineSeekBarWrap(
    private val playerView: PlayerView,
    private val exoPlayer: ExoPlayer?
) {
    private val seekBar: SeekBar = playerView.findViewById(R.id.seekBar)
    private var wasPlaying = false
    private val btnPlay: ImageButton = playerView.findViewById(R.id.btnPlay)
    private val currentTimeText: TextView = playerView.findViewById(R.id.currentTimeText)
    private val fullTimeText: TextView = playerView.findViewById(R.id.fullTimeText)
    private val separatorText: TextView = playerView.findViewById(R.id.separator)
    private val seekBarHitArea: View = playerView.findViewById(R.id.seekBarHitArea)

    init {
        setupSeekBarListener()
        setupTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val seconds = progress / 1000
                    showCurrentTime(seconds)
                    exoPlayer?.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekBar?.animate()
                    ?.scaleX(1.0f)
                    ?.scaleY(3.0f)
                    ?.setDuration(200)
                    ?.start()
                seekBar?.visibility = View.VISIBLE
                currentTimeText.visibility = View.VISIBLE
                fullTimeText.visibility = View.VISIBLE
                separatorText.visibility = View.VISIBLE

                exoPlayer?.apply {
                    wasPlaying = playWhenReady
                    playWhenReady = true
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(200)
                    ?.withEndAction {
                        hideCurrentTime()
                    }
                    ?.start()
                exoPlayer?.apply {
                    seekTo(seekBar?.progress?.toLong() ?: 0)
                    playWhenReady = wasPlaying
                }
            }
        })

        seekBarHitArea.setOnTouchListener { _, event ->
            seekBar.onTouchEvent(event)
            true
        }
    }

    @SuppressLint("DefaultLocale")
    private fun showCurrentTime(seconds: Int) {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        currentTimeText.text = String.format("%d:%02d", minutes, remainingSeconds)
    }

    @SuppressLint("DefaultLocale")
    fun updateFullTimes(duration: Int) {
        val minutes = duration / 1000 / 60
        val seconds = (duration / 1000) % 60
        fullTimeText.text = String.format("%d:%02d", minutes, seconds)
    }

    private fun hideCurrentTime() {
        Handler(Looper.getMainLooper()).postDelayed({
            seekBar.visibility = View.GONE
            currentTimeText.visibility = View.GONE
            fullTimeText.visibility = View.GONE
            separatorText.visibility = View.GONE
        }, 90)
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
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isScroll = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(event.x - startX) > 20 || Math.abs(event.y - startY) > 20) {
                        isScroll = true
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    if (!isScroll && !isTouchOnSeekBar(event)) {
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
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                pauseVideo()
                seekBar.visibility = View.VISIBLE
            } else {
                playVideo()
                seekBar.visibility = View.GONE
            }
        }
        updateButtonVisibility()
    }

    fun updateButtonVisibility() {
        exoPlayer?.let { player ->
            btnPlay.visibility = if (player.isPlaying) View.GONE else View.VISIBLE
        }
    }

    fun updateSeekBarMax(durationMs: Int) {
        seekBar.max = durationMs
    }

    fun hidePlayButton() {
        btnPlay.visibility = View.GONE
    }

    fun playVideo() {
        exoPlayer?.playWhenReady = true
    }

    fun pauseVideo() {
        exoPlayer?.playWhenReady = false
    }
}
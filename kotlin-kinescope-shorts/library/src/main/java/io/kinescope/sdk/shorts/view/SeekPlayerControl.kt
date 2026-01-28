package io.kinescope.sdk.shorts.view

import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.media3.exoplayer.ExoPlayer


class SeekPlayerControl(
    private var exoPlayer: ExoPlayer? = null,
    private val seekBar: SeekBar
) {

    private val handler = Handler(Looper.getMainLooper())
    private val seekBarUpdateRunnable = object : Runnable {
        override fun run() {
            updateSeekBarProgress()
            handler.postDelayed(this, 16)
        }
    }

    fun setupPlayer(player: ExoPlayer) {
        this.exoPlayer = player
    }

    fun startSeekBarUpdates() {
        handler.post(seekBarUpdateRunnable)
    }

    fun stopSeekBarUpdates() {
        handler.removeCallbacks(seekBarUpdateRunnable)
    }

    fun updateSeekBarProgress() {
        exoPlayer?.let { player ->
            seekBar.progress = player.currentPosition.toInt()
        }
    }
}




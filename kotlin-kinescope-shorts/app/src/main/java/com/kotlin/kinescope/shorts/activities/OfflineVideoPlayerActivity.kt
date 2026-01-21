package com.kotlin.kinescope.shorts.activities

import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import com.kotlin.kinescope.shorts.R
import com.kotlin.kinescope.shorts.databinding.ActivitySaveVideoPlayerBinding
import com.kotlin.kinescope.shorts.download.VideoDownloadManager
import com.kotlin.kinescope.shorts.drm.DrmConfigurator
import com.kotlin.kinescope.shorts.managers.PlayerFactory
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.player.OfflinePlayer
import com.kotlin.kinescope.shorts.view.SeekPlayerControl
import com.kotlin.kinescope.shorts.view.OfflineSeekBarWrap
import kotlinx.serialization.InternalSerializationApi


@InternalSerializationApi
@UnstableApi
class OfflineVideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySaveVideoPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var offlinePlayer: OfflinePlayer
    private lateinit var seekBarWrap: OfflineSeekBarWrap
    private lateinit var seekPlayerControl: SeekPlayerControl

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_VIDEO_DATA = "video_data"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        offlinePlayer = OfflinePlayer(this, DrmConfigurator(this))

        val videoData = intent.getSerializableExtra(EXTRA_VIDEO_DATA) as? VideoData
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)

        if (videoData == null || downloadId == null) {
            Toast.makeText(this, "Не хватает данных", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val download = VideoDownloadManager.getDownloadIndex(this).getDownload(downloadId)
        if (download?.state != Download.STATE_COMPLETED) {
            Toast.makeText(this, "Видео не загружено", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.toolbar.findViewById<TextView>(R.id.TitleVideo).text = videoData.title
        binding.toolbar.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        exoPlayer = PlayerFactory(this).createPlayer()
        initPlayerControls()

        offlinePlayer.setupPlayer(
            videoData = videoData,
            download = download,
            binding = binding,
            onFallback = {
                Toast.makeText(this, "Ошибка офлайн-воспроизведения", Toast.LENGTH_LONG).show()
                finish()
            },
            player = exoPlayer!!
        )
        seekPlayerControl.startSeekBarUpdates()
        seekBarWrap.hidePlayButton()
        binding.playerView.player = exoPlayer
    }

    private fun initPlayerControls() {
        seekBarWrap = OfflineSeekBarWrap(binding.playerView, exoPlayer)
        val seekBar = binding.playerView.findViewById<SeekBar>(R.id.seekBar)
        seekPlayerControl = SeekPlayerControl(exoPlayer, seekBar)
        setupControlListeners()
    }

    private fun setupControlListeners() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        seekPlayerControl.startSeekBarUpdates()
                        val duration = exoPlayer?.duration?.toInt() ?: 0
                        seekBarWrap.updateFullTimes(duration)
                        seekBarWrap.updateSeekBarMax(duration)
                        seekBarWrap.hidePlayButton()
                    }
                    Player.STATE_ENDED -> {
                        exoPlayer?.seekTo(0)
                        exoPlayer?.playWhenReady = true
                    }
                }
            }

        })
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
        seekPlayerControl.startSeekBarUpdates()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
        seekPlayerControl.stopSeekBarUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        seekPlayerControl.stopSeekBarUpdates()
        exoPlayer?.release()
        exoPlayer = null
    }
}

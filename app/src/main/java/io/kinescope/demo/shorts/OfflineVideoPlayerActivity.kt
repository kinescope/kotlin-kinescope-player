@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package io.kinescope.demo.shorts

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
import io.kinescope.demo.R
import io.kinescope.demo.databinding.ActivitySaveVideoPlayerBinding
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.managers.PlayerFactory
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.player.OfflinePlayer
import io.kinescope.sdk.shorts.AppJson
import io.kinescope.sdk.shorts.view.SeekPlayerControl
import io.kinescope.sdk.shorts.view.OfflineSeekBarWrap

@UnstableApi
class OfflineVideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySaveVideoPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var offlinePlayer: OfflinePlayer
    private var seekBarWrap: OfflineSeekBarWrap? = null
    private var seekPlayerControl: SeekPlayerControl? = null

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_VIDEO_DATA_JSON = "video_data_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        offlinePlayer = OfflinePlayer(this, DrmConfigurator(this))

        val videoDataJson = intent.getStringExtra(EXTRA_VIDEO_DATA_JSON)
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)

        if (videoDataJson == null || downloadId == null) {
            Toast.makeText(this, "Не хватает данных", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val videoData = try {
            AppJson.decodeFromString(
                VideoData.serializer(),
                videoDataJson
            )
        } catch (e: Exception) {
            android.util.Log.e("OfflineVideoPlayerActivity", "Error parsing VideoData from JSON", e)
            Toast.makeText(this, "Ошибка загрузки данных видео", Toast.LENGTH_LONG).show()
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
        binding.playerView.player = exoPlayer

        binding.playerView.post {
            try {
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
                seekPlayerControl?.startSeekBarUpdates()
                seekBarWrap?.hidePlayButton()
            } catch (e: Exception) {
                android.util.Log.e("OfflineVideoPlayerActivity", "Error initializing player controls", e)
                Toast.makeText(this, "Ошибка инициализации плеера", Toast.LENGTH_LONG).show()
                finish()
            }
        }
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
                        seekPlayerControl?.startSeekBarUpdates()
                        val duration = exoPlayer?.duration?.toInt() ?: 0
                        seekBarWrap?.updateFullTimes(duration)
                        seekBarWrap?.updateSeekBarMax(duration)
                        seekBarWrap?.hidePlayButton()
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
        seekPlayerControl?.startSeekBarUpdates()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
        seekPlayerControl?.stopSeekBarUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        seekPlayerControl?.stopSeekBarUpdates()
        exoPlayer?.release()
        exoPlayer = null
    }
}

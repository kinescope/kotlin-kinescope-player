package io.kinescope.demo.customui

import io.kinescope.demo.R
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.media3.common.util.UnstableApi

import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@UnstableApi
class CustomUIActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom)
        kinescopeVideoPlayer = KinescopeVideoPlayer(this.applicationContext)
    }

    lateinit var playerView: KinescopePlayerView
    lateinit var kinescopeVideoPlayer: KinescopeVideoPlayer


    override fun onStart() {
        super.onStart()
        playerView = findViewById(R.id.kinescope_player)
        kinescopeVideoPlayer.setShowFullscreen(false)
        kinescopeVideoPlayer.setShowOptions(false)
        playerView.setPlayer(kinescopeVideoPlayer)
        //playerView.setCustomControllerLayoutID(R.layout.view_custom_ui)

        kinescopeVideoPlayer.loadVideo("b138bf19-72fc-474b-901b-00f323899598", onSuccess = {
            if (it != null) {
                kinescopeVideoPlayer.play()
            }
        })
        setListeners()
    }

    override fun onStop() {
        super.onStop();
        kinescopeVideoPlayer.stop();
    }

    private fun setListeners() {
        findViewById<View>(R.id.btn_pause)?.setOnClickListener { kinescopeVideoPlayer.pause() }
        findViewById<View>(R.id.btn_play)?.setOnClickListener { kinescopeVideoPlayer.play() }
        findViewById<View>(R.id.btn_stop)?.setOnClickListener { kinescopeVideoPlayer.stop() }
        findViewById<View>(R.id.btn_seek_forward)?.setOnClickListener {
            kinescopeVideoPlayer.seekTo(
                10000
            )
        }
        findViewById<View>(R.id.btn_seek_back)?.setOnClickListener { kinescopeVideoPlayer.seekTo(-10000) }
        findViewById<View>(R.id.btn_speed_05)?.setOnClickListener {
            kinescopeVideoPlayer.setPlaybackSpeed(
                0.5f
            )
        }
        findViewById<View>(R.id.btn_speed_1)?.setOnClickListener {
            kinescopeVideoPlayer.setPlaybackSpeed(
                1f
            )
        }
        findViewById<View>(R.id.btn_speed_2)?.setOnClickListener {
            kinescopeVideoPlayer.setPlaybackSpeed(
                2f
            )
        }
    }


}
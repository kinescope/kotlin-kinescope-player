package io.kinescope.demo.playlist

import io.kinescope.demo.R
import io.kinescope.demo.VideosAdapter
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.demo.KinescopeViewModel
import io.kinescope.demo.application.KinescopeSDKDemoApplication
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@UnstableApi
class PlaylistActivity : AppCompatActivity() {
    private val viewModel: KinescopeViewModel by viewModels {
        KinescopeViewModel.Factory((application as KinescopeSDKDemoApplication).apiHelper)
    }


    private var isVideoFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
        kinescopePlayer = KinescopeVideoPlayer(this.applicationContext)
    }

    lateinit var playerView: KinescopePlayerView
    lateinit var fullscreenPlayerView: KinescopePlayerView
    lateinit var kinescopePlayer: KinescopeVideoPlayer


    override fun onStart() {
        super.onStart()
        playerView = findViewById(R.id.kinescope_player)
        fullscreenPlayerView = findViewById(R.id.v_kinescope_player_fullscreen)
        playerView.setIsFullscreen(false)
        fullscreenPlayerView.setIsFullscreen(true)
        val videosView = findViewById<RecyclerView>(R.id.rv_videos)
        playerView.setPlayer(kinescopePlayer)
        playerView.onFullscreenButtonCallback = { toggleFullscreen() }
        fullscreenPlayerView.onFullscreenButtonCallback = { toggleFullscreen() }

        val adapter = VideosAdapter() { videoId ->
            kinescopePlayer.loadVideo(videoId, onSuccess = { data ->
                if (data != null) {
                    kinescopePlayer.play()
                }
            })
        }

        videosView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        videosView.adapter = adapter

        viewModel.allVideos.observe(this) {
            adapter.updateData(it)
        }

        viewModel.getAllVideos()
    }

    override fun onStop() {
        super.onStop();
        kinescopePlayer.stop();
    }

    private fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

            KinescopePlayerView.switchTargetView(playerView, fullscreenPlayerView, kinescopePlayer)

        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            } else {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            }

            KinescopePlayerView.switchTargetView(fullscreenPlayerView, playerView, kinescopePlayer)
        }
    }

    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            setFullscreen(false)
            if (supportActionBar != null) {
                supportActionBar?.show()
            }
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isVideoFullscreen = false
        } else {
            setFullscreen(true)
            if (supportActionBar != null) {
                supportActionBar?.hide()
            }
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            isVideoFullscreen = true
        }
        fullscreenPlayerView.isVisible = isVideoFullscreen
    }

    override fun onBackPressed() {
        if (isVideoFullscreen) {
            toggleFullscreen()
            return
        }
        super.onBackPressed()
    }


}
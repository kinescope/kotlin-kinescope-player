package io.kinescope.demo.chapters

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.demo.R
import io.kinescope.sdk.player.KinescopeContentOrientationController
import io.kinescope.sdk.player.KinescopePictureInPictureSession
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@UnstableApi
class ChaptersActivity : AppCompatActivity() {
    private var isVideoFullscreen = false
    private lateinit var pipSession: KinescopePictureInPictureSession
    private lateinit var orientationController: KinescopeContentOrientationController
    private lateinit var playerView: KinescopePlayerView
    private lateinit var fullscreenPlayerView: KinescopePlayerView
    private lateinit var kinescopePlayer: KinescopeVideoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapters)

        kinescopePlayer = KinescopeVideoPlayer(this)
        kinescopePlayer.kinescopePlayerOptions.showChaptersButton = true
        kinescopePlayer.setShowSubtitles(true)
        pipSession = KinescopePictureInPictureSession(
            activity = this,
            playerView = { playerView },
            player = { kinescopePlayer },
            additionalPlayerViews = { listOf(fullscreenPlayerView) },
        )
    }

    override fun onStart() {
        super.onStart()
        playerView = findViewById(R.id.kinescope_player)
        fullscreenPlayerView = findViewById(R.id.v_kinescope_player_fullscreen)
        playerView.setIsFullscreen(false)
        fullscreenPlayerView.setIsFullscreen(true)
        playerView.setPlayer(kinescopePlayer)
        playerView.applyTemplateOptions()
        playerView.onFullscreenButtonCallback = { toggleFullscreen() }
        fullscreenPlayerView.onFullscreenButtonCallback = { toggleFullscreen() }
        orientationController = KinescopeContentOrientationController(
            activity = this,
            playerViews = { listOf(playerView, fullscreenPlayerView) },
        )
        orientationController.attach()
        pipSession.attach()

        kinescopePlayer.loadVideo(KinescopeDemoConfig.CHAPTERS_VIDEO_ID, onSuccess = {
            if (it != null) {
                kinescopePlayer.play()
            }
        })
    }

    override fun onStop() {
        pipSession.onStop()
        super.onStop()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipSession.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            KinescopePlayerView.switchTargetView(playerView, fullscreenPlayerView, kinescopePlayer)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
            } else {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
            }
            KinescopePlayerView.switchTargetView(fullscreenPlayerView, playerView, kinescopePlayer)
        }
    }

    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            setFullscreen(false)
            supportActionBar?.show()
            isVideoFullscreen = false
        } else {
            setFullscreen(true)
            supportActionBar?.hide()
            isVideoFullscreen = true
        }
        fullscreenPlayerView.isVisible = isVideoFullscreen
        orientationController.setFullscreen(isVideoFullscreen)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isVideoFullscreen) {
            toggleFullscreen()
            return
        }
        super.onBackPressed()
    }
}

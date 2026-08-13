package io.kinescope.demo.live

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.demo.R
import io.kinescope.sdk.player.KinescopeCastSession
import io.kinescope.sdk.player.KinescopeContentOrientationController
import io.kinescope.sdk.player.KinescopePictureInPictureSession
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@UnstableApi
class LiveActivity : AppCompatActivity() {

    private lateinit var kinescopePlayerView: KinescopePlayerView
    private lateinit var kinescopePlayerFullscreenView: KinescopePlayerView

    private lateinit var kinescopePlayer: KinescopeVideoPlayer
    private lateinit var watchLiveContainerView: LinearLayout
    private lateinit var watchLiveIdInputView: EditText
    private lateinit var watchLiveBtnView: Button

    private var isFullscreen = false
    private lateinit var pipSession: KinescopePictureInPictureSession
    private lateinit var castSession: KinescopeCastSession
    private lateinit var orientationController: KinescopeContentOrientationController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        kinescopePlayerView = findViewById(R.id.player)
        kinescopePlayerFullscreenView = findViewById(R.id.player_fullscreen)

        kinescopePlayerView.setIsFullscreen(false)
        kinescopePlayerView.onFullscreenButtonCallback = { toggleFullscreen() }

        kinescopePlayerFullscreenView.setIsFullscreen(true)
        kinescopePlayerFullscreenView.onFullscreenButtonCallback = { toggleFullscreen() }

        watchLiveContainerView = findViewById(R.id.watch_live_ll)
        watchLiveIdInputView = findViewById(R.id.id_et)
        watchLiveBtnView = findViewById(R.id.watch_live_btn)

        kinescopePlayer = KinescopeVideoPlayer(this)
        kinescopePlayerView.setPlayer(kinescopePlayer)
        kinescopePlayerView.applyTemplateOptions()
        kinescopePlayerFullscreenView.applyTemplateOptions()

        pipSession = KinescopePictureInPictureSession(
            activity = this,
            playerView = { kinescopePlayerView },
            player = { kinescopePlayer },
            additionalPlayerViews = { listOf(kinescopePlayerFullscreenView) },
        )
        castSession = KinescopeCastSession(
            activity = this,
            playerView = { kinescopePlayerView },
            player = { kinescopePlayer },
            additionalPlayerViews = { listOf(kinescopePlayerFullscreenView) },
        )
        orientationController = KinescopeContentOrientationController(
            activity = this,
            playerViews = { listOf(kinescopePlayerView, kinescopePlayerFullscreenView) },
        )
        orientationController.attach()
        pipSession.attach()
        castSession.attach()

        watchLiveBtnView.setOnClickListener {
            tryLoadVideo(watchLiveIdInputView.text.toString())
        }
    }

    override fun onStop() {
        pipSession.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::orientationController.isInitialized) {
            orientationController.detach()
        }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipSession.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun tryLoadVideo(id: String) {
        val liveId = id.takeIf { it.isNotEmpty() } ?: KinescopeDemoConfig.DEFAULT_LIVE_ID

        watchLiveContainerView.isVisible = false
        kinescopePlayer.loadVideo(liveId, { video ->
            video?.let { data ->
                if (data.isLive) {
                    data.live?.startsAt?.let {
                        kinescopePlayerView.showLiveStartDate(startDate = it)
                        kinescopePlayerFullscreenView.showLiveStartDate(startDate = it)
                    }
                    kinescopePlayerView.setLiveState()
                    kinescopePlayerFullscreenView.setLiveState()
                    kinescopePlayerView.showLiveAwaitingCover()
                    kinescopePlayerFullscreenView.showLiveAwaitingCover()
                } else {
                    data.poster?.url?.let {
                        kinescopePlayerView.showPoster(
                            url = it,
                            onLoadFinished = { println("Poster loaded") }
                        )
                    }
                }
            }
            kinescopePlayer.play()
        }) {
            it?.printStackTrace()
        }
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            setFullscreen(false)
            if (supportActionBar != null) {
                supportActionBar?.show()
            }
            isFullscreen = false
        } else {
            setFullscreen(true)
            if (supportActionBar != null) {
                supportActionBar?.hide()
            }
            isFullscreen = true
        }
        kinescopePlayerFullscreenView.isVisible = isFullscreen
        orientationController.setFullscreen(isFullscreen)
    }

    private fun setFullscreen(isFullscreen: Boolean) {
        if (isFullscreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

            KinescopePlayerView.switchTargetView(
                kinescopePlayerView,
                kinescopePlayerFullscreenView,
                kinescopePlayer
            )

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

            KinescopePlayerView.switchTargetView(
                kinescopePlayerFullscreenView,
                kinescopePlayerView,
                kinescopePlayer
            )
        }
    }
}

package io.kinescope.demo.playlist

import io.kinescope.demo.R
import io.kinescope.demo.VideosAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.demo.KinescopeViewModel
import io.kinescope.demo.application.KinescopeSDKDemoApplication
import io.kinescope.sdk.models.videos.KinescopeVideoApi
import io.kinescope.sdk.player.KinescopeCastSession
import io.kinescope.sdk.player.KinescopeContentOrientationController
import io.kinescope.sdk.player.KinescopePictureInPictureSession
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView

@UnstableApi
class PlaylistActivity : AppCompatActivity() {
    private val viewModel: KinescopeViewModel by viewModels {
        KinescopeViewModel.Factory((application as KinescopeSDKDemoApplication).apiHelper)
    }

    private var isVideoFullscreen = false
    private lateinit var videosAdapter: VideosAdapter
    private lateinit var playlistProgressView: TextView
    private lateinit var pipSession: KinescopePictureInPictureSession
    private lateinit var orientationController: KinescopeContentOrientationController
    private lateinit var castSession: KinescopeCastSession
    private lateinit var playlistPanel: View
    private var playerLayoutParamsBackup: ConstraintLayout.LayoutParams? = null
    private var actionBarVisibleBeforePip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
        kinescopePlayer = KinescopeVideoPlayer(this.applicationContext)
        pipSession = KinescopePictureInPictureSession(
            activity = this,
            playerView = { playerView },
            player = { kinescopePlayer },
            additionalPlayerViews = { listOf(fullscreenPlayerView) },
        ).apply {
            onEnteringPip = { applyPictureInPictureLayout() }
            onExitingPip = { restorePictureInPictureLayout() }
        }
        castSession = KinescopeCastSession(
            activity = this,
            playerView = { playerView },
            player = { kinescopePlayer },
            additionalPlayerViews = { listOf(fullscreenPlayerView) },
        )
    }

    lateinit var playerView: KinescopePlayerView
    lateinit var fullscreenPlayerView: KinescopePlayerView
    lateinit var kinescopePlayer: KinescopeVideoPlayer


    override fun onStart() {
        super.onStart()
        playerView = findViewById(R.id.kinescope_player)
        fullscreenPlayerView = findViewById(R.id.v_kinescope_player_fullscreen)
        playlistPanel = findViewById(R.id.playlist_panel)
        playerView.setIsFullscreen(false)
        fullscreenPlayerView.setIsFullscreen(true)

        val videosView = findViewById<RecyclerView>(R.id.rv_videos)
        playlistProgressView = findViewById(R.id.tv_playlist_progress)
        findViewById<ImageButton>(R.id.btn_close_playlist).setOnClickListener {
            finish()
        }
        playerView.setPlayer(kinescopePlayer)
        playerView.applyTemplateOptions()
        playerView.onFullscreenButtonCallback = { toggleFullscreen() }
        fullscreenPlayerView.onFullscreenButtonCallback = { toggleFullscreen() }
        ensureOrientationController()
        pipSession.attach()
        castSession.attach()

        videosAdapter = VideosAdapter(
            onVideoClick = { videoId ->
                playVideo(videoId)
            },
            onCopyLinkClick = { video ->
                copyVideoLink(video)
            },
            onSelectionChanged = { selectedIndex, totalCount ->
                updatePlaylistProgress(selectedIndex, totalCount)
            },
        )

        videosView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        videosView.adapter = videosAdapter

        viewModel.allVideos.observe(this) { videos ->
            videosAdapter.updateData(videos)
        }
        viewModel.getAllVideos()
    }

    private fun ensureOrientationController() {
        if (!::orientationController.isInitialized) {
            orientationController = KinescopeContentOrientationController(
                activity = this,
                playerViews = { listOf(playerView, fullscreenPlayerView) },
            )
            orientationController.attach()
        }
        orientationController.setFullscreen(isVideoFullscreen)
    }

    private fun playVideo(videoId: String) {
        videosAdapter.setSelectedVideoId(videoId)
        kinescopePlayer.loadVideo(videoId, onSuccess = {
            kinescopePlayer.play()
        })
    }

    private fun updatePlaylistProgress(selectedIndex: Int, totalCount: Int) {
        playlistProgressView.text = if (totalCount > 0 && selectedIndex > 0) {
            getString(R.string.playlist_progress, selectedIndex, totalCount)
        } else {
            ""
        }
    }

    private fun copyVideoLink(video: KinescopeVideoApi) {
        val link = "https://kinescope.io/${video.id}"
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("video_link", link))
        Toast.makeText(this, R.string.playlist_link_copied, Toast.LENGTH_SHORT).show()
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

    private fun applyPictureInPictureLayout() {
        if (isVideoFullscreen) {
            setFullscreen(false)
            isVideoFullscreen = false
            fullscreenPlayerView.isVisible = false
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        actionBarVisibleBeforePip = supportActionBar?.isShowing == true
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val root = findViewById<View>(R.id.playlist_root)
        root.setBackgroundColor(Color.BLACK)
        if (playerLayoutParamsBackup == null) {
            playerLayoutParamsBackup = playerView.layoutParams as ConstraintLayout.LayoutParams
        }
        playlistPanel.isVisible = false
        playerView.layoutParams = ConstraintLayout.LayoutParams(0, 0).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        playerView.requestLayout()
    }

    private fun restorePictureInPictureLayout() {
        playlistPanel.isVisible = true
        playerLayoutParamsBackup?.let { playerView.layoutParams = it }
        playerLayoutParamsBackup = null
        fullscreenPlayerView.isVisible = isVideoFullscreen
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val root = findViewById<View>(R.id.playlist_root)
        root.setBackgroundResource(R.color.playlist_background)
        if (actionBarVisibleBeforePip && !isVideoFullscreen) {
            supportActionBar?.show()
        }
        actionBarVisibleBeforePip = false
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
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

            KinescopePlayerView.switchTargetView(fullscreenPlayerView, playerView, kinescopePlayer)
        }
    }

    private fun toggleFullscreen() {
        if (isVideoFullscreen) {
            setFullscreen(false)
            if (supportActionBar != null) {
                supportActionBar?.show()
            }
            isVideoFullscreen = false
        } else {
            setFullscreen(true)
            if (supportActionBar != null) {
                supportActionBar?.hide()
            }
            isVideoFullscreen = true
        }
        fullscreenPlayerView.isVisible = isVideoFullscreen
        orientationController.setFullscreen(isVideoFullscreen)
    }

    override fun onBackPressed() {
        if (isVideoFullscreen) {
            toggleFullscreen()
            return
        }
        super.onBackPressed()
    }
}

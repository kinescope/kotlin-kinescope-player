package io.kinescope.demo.compose



import android.content.pm.ActivityInfo

import android.os.Bundle

import android.view.View

import android.view.WindowManager

import androidx.activity.ComponentActivity

import androidx.activity.OnBackPressedCallback

import androidx.activity.compose.setContent

import androidx.activity.viewModels

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.core.view.WindowCompat

import androidx.core.view.WindowInsetsCompat

import androidx.core.view.WindowInsetsControllerCompat

import androidx.lifecycle.AbstractSavedStateViewModelFactory

import androidx.lifecycle.SavedStateHandle

import androidx.lifecycle.ViewModel

import androidx.media3.common.util.UnstableApi

import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.sdk.player.KinescopeComposeCastSession

import io.kinescope.sdk.player.KinescopeVideoPlayer

import io.kinescope.sdk.ui.KinescopePlayerScreen

import io.kinescope.sdk.ui.KinescopePlayerTheme

import io.kinescope.sdk.ui.KinescopePlayerViewModel



@UnstableApi
class ComposePlayerActivity : ComponentActivity() {
    private var castSession: KinescopeComposeCastSession? = null
    private var isVideoFullscreen by mutableStateOf(false)
    private val viewModel: KinescopePlayerViewModel by viewModels {
        object : AbstractSavedStateViewModelFactory(this@ComposePlayerActivity, null) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle,
            ): T {
                return KinescopePlayerViewModel(
                    savedStateHandle = handle,
                    playerFactory = { options ->
                        KinescopeVideoPlayer(applicationContext, options)
                    },
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: KinescopeDemoConfig.DEFAULT_VIDEO_ID
        viewModel.attach()
        viewModel.player.bindLifecycle(lifecycle, releaseOnDestroy = false)
        if (viewModel.videoId == null) {
            viewModel.rememberVideoId(videoId)
        }
        castSession = KinescopeComposeCastSession(
            lifecycleOwner = this,
            context = applicationContext,
            player = { viewModel.player },
        ).also { it.attach() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isVideoFullscreen) {
                        setFullscreen(false)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        setContent {
            KinescopePlayerTheme {
                KinescopePlayerScreen(
                    player = viewModel.player,
                    videoId = videoId,
                    fullscreen = isVideoFullscreen,
                    modifier = Modifier.fillMaxSize(),
                    onStopCast = { castSession?.controller?.stopCasting() },
                    restoreQualityId = viewModel.savedQualityId(),
                    restoreSubtitleId = viewModel.savedSubtitleId(),
                    restoreAudioTrackId = viewModel.savedAudioTrackId(),
                    onTrackSelectionPersist = viewModel::persistTrackSelection,
                    onFullscreenToggle = { setFullscreen(!isVideoFullscreen) },
                    onVideoLoaded = { viewModel.restorePlaybackIfNeeded() },
                )
            }
        }
    }

    private fun setFullscreen(fullscreen: Boolean) {
        isVideoFullscreen = fullscreen
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,

            )
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }



    override fun onDestroy() {
        castSession?.release()
        castSession = null
        super.onDestroy()
    }



    override fun onStop() {
        viewModel.persistToSavedState()
        super.onStop()
    }



    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
    }
}



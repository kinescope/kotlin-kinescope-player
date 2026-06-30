package io.kinescope.sdk.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.R
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Wires Picture-in-Picture for an activity hosting [KinescopePlayerView].
 *
 * Requires `android:supportsPictureInPicture="true"` on the activity in the manifest.
 */
@UnstableApi
class KinescopePictureInPictureSession(
    private val activity: AppCompatActivity,
    private val playerView: () -> KinescopePlayerView,
    private val player: () -> KinescopeVideoPlayer,
    private val additionalPlayerViews: () -> List<KinescopePlayerView> = { emptyList() },
) {
    var onEnteringPip: (() -> Unit)? = null
    var onExitingPip: (() -> Unit)? = null

    private var stopPlaybackAfterPipExit = false
    private var receiverRegistered = false
    private var pipEntryPending = false
    private var pipEntryRecoveryRunnable: Runnable? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KinescopePictureInPicture.ACTION_PLAY_PAUSE) {
                togglePlayback()
            }
        }
    }

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity.isInPictureInPictureMode
            ) {
                KinescopePictureInPicture.updateActions(activity, player().exoPlayer)
            }
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            detach()
        }
    }

    fun attach() {
        val enter = ::enterPictureInPicture
        playerView().onPictureInPictureButtonCallback = enter
        additionalPlayerViews().forEach { view ->
            view.onPictureInPictureButtonCallback = enter
        }
        player().bindLifecycle(
            lifecycle = activity.lifecycle,
            isPipActive = {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode
            },
        )
        registerPipReceiver()
        activity.lifecycle.addObserver(lifecycleObserver)
        player().exoPlayer?.addListener(playbackListener)
    }

    fun detach() {
        cancelPictureInPictureEntryRecovery()
        pipEntryPending = false
        unregisterPipReceiver()
        activity.lifecycle.removeObserver(lifecycleObserver)
        player().exoPlayer?.removeListener(playbackListener)
    }

    fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) {
            stopPlaybackAfterPipExit = true
        } else {
            stopPlaybackAfterPipExit = false
        }
    }

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, @Suppress("UNUSED_PARAMETER") newConfig: Configuration) {
        val view = playerView()
        val videoPlayer = player()
        if (isInPictureInPictureMode) {
            cancelPictureInPictureEntryRecovery()
            pipEntryPending = false
            prepareAllPlayerViewsForPictureInPicture(true)
            videoPlayer.play()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                KinescopePictureInPicture.updateActions(activity, videoPlayer.exoPlayer)
            }
        } else {
            cancelPictureInPictureEntryRecovery()
            pipEntryPending = false
            onExitingPip?.invoke()
            prepareAllPlayerViewsForPictureInPicture(false)
            refreshPlayerChromeAfterPictureInPictureExit()
            if (stopPlaybackAfterPipExit) {
                stopPlaybackAfterPipExit = false
                videoPlayer.stop()
            } else {
                KinescopePictureInPicture.onExitedPictureInPictureMode(
                    activity = activity,
                    anchorView = view,
                    onDismissed = { videoPlayer.stop() },
                )
            }
        }
    }

    private fun prepareAllPlayerViewsForPictureInPicture(preparing: Boolean) {
        (listOf(playerView()) + additionalPlayerViews()).distinct().forEach { view ->
            view.prepareForPictureInPicture(preparing)
        }
    }

    private fun refreshPlayerChromeAfterPictureInPictureExit() {
        (listOf(playerView()) + additionalPlayerViews()).distinct().forEach { view ->
            view.refreshPlayerChromeAfterPictureInPictureExit()
        }
    }

    private fun refreshAllPlayerViewsChrome() {
        (listOf(playerView()) + additionalPlayerViews()).distinct().forEach { view ->
            view.refreshPlayerChrome()
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val view = playerView()
        val videoPlayer = player()
        if (videoPlayer.exoPlayer?.isPlaying != true) {
            videoPlayer.play()
        }
        onEnteringPip?.invoke()
        pipEntryPending = true
        val anchorView = view.getPipAnchorView()
        view.doOnLayout {
            anchorView.doOnLayout {
                view.post {
                    val entered = KinescopePictureInPicture.enter(
                        activity = activity,
                        anchorView = anchorView,
                        aspectRatio = KinescopePictureInPicture.getAspectRatio(videoPlayer.exoPlayer),
                        exoPlayer = videoPlayer.exoPlayer,
                    )
                    if (!entered) {
                        cancelPictureInPictureEntryRecovery()
                        pipEntryPending = false
                        onExitingPip?.invoke()
                        prepareAllPlayerViewsForPictureInPicture(false)
                        refreshAllPlayerViewsChrome()
                        Toast.makeText(activity, R.string.player_pip_unavailable, Toast.LENGTH_SHORT).show()
                    } else {
                        schedulePictureInPictureEntryRecovery(view)
                    }
                }
            }
        }
    }

    private fun schedulePictureInPictureEntryRecovery(view: KinescopePlayerView) {
        cancelPictureInPictureEntryRecovery()
        val recovery = Runnable {
            pipEntryRecoveryRunnable = null
            if (!pipEntryPending) {
                return@Runnable
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) {
                pipEntryPending = false
                return@Runnable
            }
            pipEntryPending = false
            onExitingPip?.invoke()
            prepareAllPlayerViewsForPictureInPicture(false)
            refreshAllPlayerViewsChrome()
        }
        pipEntryRecoveryRunnable = recovery
        view.postDelayed(recovery, PIP_ENTRY_RECOVERY_TIMEOUT_MS)
    }

    private fun cancelPictureInPictureEntryRecovery() {
        pipEntryRecoveryRunnable?.let { runnable ->
            playerView().removeCallbacks(runnable)
        }
        pipEntryRecoveryRunnable = null
    }

    private companion object {
        private const val PIP_ENTRY_RECOVERY_TIMEOUT_MS = 2000L
    }

    private fun togglePlayback() {
        val videoPlayer = player()
        val exoPlayer = videoPlayer.exoPlayer ?: return
        if (exoPlayer.isPlaying) {
            videoPlayer.pause()
        } else {
            videoPlayer.play()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KinescopePictureInPicture.updateActions(activity, exoPlayer)
        }
    }

    private fun registerPipReceiver() {
        if (receiverRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val filter = IntentFilter(KinescopePictureInPicture.ACTION_PLAY_PAUSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(pipReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterPipReceiver() {
        if (!receiverRegistered) {
            return
        }
        activity.unregisterReceiver(pipReceiver)
        receiverRegistered = false
    }
}

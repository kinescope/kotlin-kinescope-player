package io.kinescope.sdk.player

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import io.kinescope.sdk.cast.KinescopeCastController
import io.kinescope.sdk.cast.KinescopeCastState
import io.kinescope.sdk.cast.toCastData
import io.kinescope.sdk.view.KinescopeCastUiHelper
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Wires Google Cast for an activity hosting [KinescopePlayerView].
 *
 * Requires [AppCompatActivity] (Cast device picker dialogs) and Cast-enabled device on the network.
 * Cast is unavailable without Google Play services — the session becomes a no-op.
 *
 * ```
 * castSession = KinescopeCastSession(activity, { playerView }, { kinescopePlayer })
 * castSession.attach()
 * // onDestroy: castSession.release()
 * ```
 */
@UnstableApi
class KinescopeCastSession(
    private val activity: AppCompatActivity,
    private val playerView: () -> KinescopePlayerView,
    private val player: () -> KinescopeVideoPlayer,
    private val additionalPlayerViews: () -> List<KinescopePlayerView> = { emptyList() },
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var castController: KinescopeCastController? = null
    private var castContext: CastContext? = null
    private var mediaRouterCallback: MediaRouter.Callback? = null
    private var attached = false
    private var resumeLocalAfterCast = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val controller = castController ?: return
            if (!controller.currentState.isCasting) return
            controller.refresh()
            mainHandler.postDelayed(this, CAST_REFRESH_MS)
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            release()
        }
    }

    val isAvailable: Boolean
        get() = castController != null

    val isCasting: Boolean
        get() = castController?.currentState?.isCasting == true

    fun attach() {
        if (attached) return
        attached = true

        player().setShowCast(true)

        val context = runCatching { CastContext.getSharedInstance(activity) }.getOrNull()
        if (context == null) {
            allPlayerViews().forEach { it.setCastSupported(false) }
            return
        }

        castContext = context
        val controller = KinescopeCastController(context).also { castController = it }
        controller.onSessionAvailable = { onCastSessionStarted() }
        controller.onSessionUnavailable = { onCastSessionEnded() }
        controller.setStateListener(::onCastStateChanged)

        allPlayerViews().forEach { view ->
            view.setCastSupported(true)
            view.setCastButtonClickListener {
                KinescopeCastUiHelper.showCastDialog(activity, context)
            }
        }

        registerMediaRouter(context)
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    private fun registerMediaRouter(context: CastContext) {
        val selector = context.mergedSelector ?: return
        val router = MediaRouter.getInstance(activity.applicationContext)
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refreshCastRouteAvailability()
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refreshCastRouteAvailability()
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refreshCastRouteAvailability()
            }
        }
        router.addCallback(
            selector,
            callback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
        )
        mediaRouterCallback = callback
        refreshCastRouteAvailability()
    }

    private fun refreshCastRouteAvailability() {
        val context = castContext ?: return
        val available = KinescopeCastUiHelper.isCastRouteAvailable(activity, context)
        allPlayerViews().forEach { it.setCastRouteAvailable(available) }
    }

    private fun unregisterMediaRouter() {
        val callback = mediaRouterCallback ?: return
        MediaRouter.getInstance(activity.applicationContext).removeCallback(callback)
        mediaRouterCallback = null
    }

    fun release() {
        if (!attached) return
        attached = false
        mainHandler.removeCallbacks(refreshRunnable)
        unregisterMediaRouter()
        activity.lifecycle.removeObserver(lifecycleObserver)
        castController?.release()
        castController = null
        castContext = null
        allPlayerViews().forEach {
            it.setCastSupported(false)
            it.hideCastOverlay()
        }
    }

    private fun onCastSessionStarted() {
        val videoPlayer = player()
        val controller = castController ?: return
        val positionMs = videoPlayer.exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        resumeLocalAfterCast = videoPlayer.exoPlayer?.playWhenReady == true
        videoPlayer.getVideo()?.toCastData()?.let { controller.load(it, positionMs) }
        videoPlayer.pause()
        mainHandler.post(refreshRunnable)
    }

    private fun onCastSessionEnded() {
        mainHandler.removeCallbacks(refreshRunnable)
        val videoPlayer = player()
        val controller = castController
        val positionMs = controller?.castPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        videoPlayer.exoPlayer?.seekTo(positionMs)
        if (resumeLocalAfterCast) {
            videoPlayer.play()
        }
        resumeLocalAfterCast = false
        allPlayerViews().forEach { it.hideCastOverlay() }
    }

    private fun onCastStateChanged(state: KinescopeCastState) {
        allPlayerViews().forEach { view ->
            if (state.isCasting) {
                view.showCastOverlay(
                    state = state,
                    onPlayPause = { castController?.playPause() },
                    onSeek = { fraction ->
                        val duration = state.durationMs
                        if (duration > 0) {
                            castController?.seekTo((fraction * duration).toLong())
                        }
                    },
                    onStop = { castController?.stopCasting() },
                )
            } else {
                view.hideCastOverlay()
            }
        }
    }

    private fun allPlayerViews(): List<KinescopePlayerView> =
        (listOf(playerView()) + additionalPlayerViews()).distinct()

    private companion object {
        private const val CAST_REFRESH_MS = 1_000L
    }
}

package io.kinescope.sdk.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import io.kinescope.sdk.cast.KinescopeCastController
import io.kinescope.sdk.cast.toCastData

/**
 * Cast wiring for Compose (or any headless) player host — mirrors [KinescopeCastSession]
 * without [io.kinescope.sdk.view.KinescopePlayerView].
 */
@UnstableApi
class KinescopeComposeCastSession(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val player: () -> KinescopeVideoPlayer,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var castController: KinescopeCastController? = null
    private var castContext: CastContext? = null
    private var mediaRouterCallback: MediaRouter.Callback? = null
    private var attached = false
    private var resumeLocalAfterCast = false

    val controller: KinescopeCastController?
        get() = castController

    val isAvailable: Boolean
        get() = castController != null

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

    fun attach() {
        if (attached) return
        attached = true

        player().setShowCast(true)

        val castCtx = runCatching { CastContext.getSharedInstance(context) }.getOrNull()
        if (castCtx == null) return

        castContext = castCtx
        val controller = KinescopeCastController(castCtx).also { castController = it }
        controller.onSessionAvailable = { onCastSessionStarted() }
        controller.onSessionUnavailable = { onCastSessionEnded() }

        registerMediaRouter(castCtx)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    fun release() {
        if (!attached) return
        attached = false
        mainHandler.removeCallbacks(refreshRunnable)
        unregisterMediaRouter()
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        castController?.release()
        castController = null
        castContext = null
    }

    private fun registerMediaRouter(context: CastContext) {
        val selector = context.mergedSelector ?: return
        val router = MediaRouter.getInstance(this.context.applicationContext)
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = Unit
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = Unit
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = Unit
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        mediaRouterCallback = callback
    }

    private fun unregisterMediaRouter() {
        val callback = mediaRouterCallback ?: return
        MediaRouter.getInstance(context.applicationContext).removeCallback(callback)
        mediaRouterCallback = null
    }

    private fun onCastSessionStarted() {
        val videoPlayer = player()
        val controller = castController ?: return
        val host = videoPlayer.getOrCreatePlayerHost() ?: return
        val positionMs = host.activePlayer.currentPosition.coerceAtLeast(0L)
        resumeLocalAfterCast = host.activePlayer.playWhenReady
        videoPlayer.getVideo()?.toCastData()?.let { controller.load(it, positionMs) }
        videoPlayer.exoPlayer?.pause()
        videoPlayer.switchToCastPlayer(controller.castPlayer)
        mainHandler.post(refreshRunnable)
    }

    private fun onCastSessionEnded() {
        mainHandler.removeCallbacks(refreshRunnable)
        val videoPlayer = player()
        val positionMs = videoPlayer.playbackPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        videoPlayer.switchToLocalPlayer()
        videoPlayer.exoPlayer?.seekTo(positionMs)
        if (resumeLocalAfterCast) {
            videoPlayer.play()
        }
        resumeLocalAfterCast = false
    }

    private companion object {
        private const val CAST_REFRESH_MS = 1_000L
    }
}

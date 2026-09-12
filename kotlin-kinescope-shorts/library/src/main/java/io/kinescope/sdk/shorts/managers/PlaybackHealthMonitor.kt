package io.kinescope.sdk.shorts.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

/**
 * Samples the active Shorts [ExoPlayer] buffer + shared bandwidth estimate and
 * pushes [PlaybackHealthSnapshot] to drive adaptive preload.
 */
@OptIn(UnstableApi::class)
class PlaybackHealthMonitor(
    context: Context,
    private val onSnapshot: (PlaybackHealthSnapshot) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(appContext)

    private var attachedPlayer: ExoPlayer? = null
    private var isRebuffering = false
    private var sampling = false

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!sampling) return
            publish()
            mainHandler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = attachedPlayer ?: return
            isRebuffering = playbackState == Player.STATE_BUFFERING && player.playWhenReady
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) isRebuffering = false
            publish()
        }
    }

    fun attach(player: ExoPlayer?) {
        detach()
        if (player == null) return
        attachedPlayer = player
        player.addListener(playerListener)
        isRebuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        sampling = true
        publish()
        mainHandler.postDelayed(sampleRunnable, SAMPLE_INTERVAL_MS)
    }

    fun setRebuffering(rebuffering: Boolean) {
        isRebuffering = rebuffering
        publish()
    }

    fun detach() {
        sampling = false
        mainHandler.removeCallbacks(sampleRunnable)
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = null
        isRebuffering = false
    }

    private fun publish() {
        val player = attachedPlayer
        val buffered = player?.totalBufferedDuration?.coerceAtLeast(0L) ?: 0L
        val (metered, wifi) = networkFlags()
        onSnapshot(
            PlaybackHealthSnapshot(
                isRebuffering = isRebuffering,
                bufferedDurationMs = buffered,
                estimatedBitrateBps = bandwidthMeter.bitrateEstimate.coerceAtLeast(0L),
                isMeteredNetwork = metered,
                hasWifi = wifi,
            ),
        )
    }

    private fun networkFlags(): Pair<Boolean, Boolean> {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false to false
        val network = cm.activeNetwork ?: return false to false
        val caps = cm.getNetworkCapabilities(network) ?: return false to false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val metered = try {
            cm.isActiveNetworkMetered
        } catch (_: Exception) {
            cellular && !wifi
        }
        return metered to wifi
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 500L
    }
}

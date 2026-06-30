package io.kinescope.sdk.cast

import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext

/**
 * Wraps media3 [CastPlayer] for the Kinescope custom receiver.
 */
@OptIn(UnstableApi::class)
class KinescopeCastController(
    private val castContext: CastContext,
) {
    val castPlayer: CastPlayer = CastPlayer(castContext, KinescopeMediaItemConverter())

    private var state = KinescopeCastState()
    private var stateListener: ((KinescopeCastState) -> Unit)? = null

    /** Called when a Cast session becomes available (device connected). */
    var onSessionAvailable: (() -> Unit)? = null

    /** Called when Cast session ends. */
    var onSessionUnavailable: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
        override fun onPlaybackStateChanged(playbackState: Int) = syncState()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncState()
    }

    init {
        castPlayer.addListener(playerListener)
        castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                state = state.copy(isCasting = true, deviceName = deviceName())
                publishState()
                onSessionAvailable?.invoke()
            }

            override fun onCastSessionUnavailable() {
                state = KinescopeCastState()
                publishState()
                onSessionUnavailable?.invoke()
            }
        })
    }

    fun setStateListener(listener: ((KinescopeCastState) -> Unit)?) {
        stateListener = listener
        listener?.invoke(state)
    }

    val currentState: KinescopeCastState
        get() = state

    fun load(data: KinescopeCastData, startPositionMs: Long) {
        val item = MediaItem.Builder()
            .setUri(data.manifestUrl)
            .setTag(data)
            .build()
        castPlayer.setMediaItem(item, startPositionMs)
        castPlayer.playWhenReady = true
        castPlayer.prepare()
    }

    fun playPause() {
        castPlayer.playWhenReady = !castPlayer.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        castPlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun stopCasting() {
        castContext.sessionManager.endCurrentSession(true)
    }

    fun refresh() = syncState()

    fun release() {
        castPlayer.removeListener(playerListener)
        castPlayer.setSessionAvailabilityListener(null)
        castPlayer.release()
        stateListener = null
    }

    private fun deviceName(): String? =
        castContext.sessionManager.currentCastSession?.castDevice?.friendlyName

    private fun syncState() {
        state = state.copy(
            isPlaying = castPlayer.playWhenReady,
            positionMs = castPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = castPlayer.duration.coerceAtLeast(0L),
            deviceName = deviceName() ?: state.deviceName,
        )
        publishState()
    }

    private fun publishState() {
        stateListener?.invoke(state)
    }
}

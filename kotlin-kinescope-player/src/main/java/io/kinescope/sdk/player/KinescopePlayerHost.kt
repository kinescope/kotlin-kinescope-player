package io.kinescope.sdk.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Holds the active media3 [Player] (local ExoPlayer, CastPlayer, etc.).
 * Custom UI should bind commands and state to [activePlayer] instead of a concrete engine.
 */
@UnstableApi
class KinescopePlayerHost(initialPlayer: Player) {
    var activePlayer: Player = initialPlayer
        private set

    var onActivePlayerChanged: ((Player) -> Unit)? = null

    fun switchTo(player: Player) {
        if (activePlayer === player) return
        activePlayer = player
        onActivePlayerChanged?.invoke(player)
    }
}

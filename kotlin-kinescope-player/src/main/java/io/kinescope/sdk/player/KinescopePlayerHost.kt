package io.kinescope.sdk.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Holds the active media3 [Player] (local ExoPlayer, CastPlayer, etc.).
 * Custom UI should bind commands and state to [activePlayer] instead of a concrete engine.
 */
@UnstableApi
class KinescopePlayerHost(
    val localPlayer: Player,
) {
    var activePlayer: Player = localPlayer
        private set

    var onActivePlayerChanged: ((Player) -> Unit)? = null

    val isCasting: Boolean
        get() = activePlayer !== localPlayer

    fun switchTo(player: Player) {
        if (activePlayer === player) return
        activePlayer = player
        onActivePlayerChanged?.invoke(player)
    }

    fun switchToLocal() {
        switchTo(localPlayer)
    }
}

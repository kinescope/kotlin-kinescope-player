package io.kinescope.sdk.shorts.managers

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Reuses [ExoPlayer] instances across Shorts pages.
 * [releasePlayer] returns a player to the pool (stop/clear only) — does not call [ExoPlayer.release].
 * Call [cleanup] when the feed is destroyed.
 */
@UnstableApi
class PoolPlayers private constructor(
    context: Context,
    private val playerFactory: PlayerFactory,
) {
    companion object {
        private const val MAX_POOL_SIZE = 4

        @Volatile
        private var instance: PoolPlayers? = null

        fun init(context: Context) {
            val app = context.applicationContext
            instance = PoolPlayers(app, PlayerFactory(app))
        }

        fun get(): PoolPlayers =
            instance ?: error("PoolPlayers.init(context) must be called first")

        /** Releases pooled players and clears the singleton (safe to call repeatedly). */
        fun shutdown() {
            instance?.cleanup()
        }
    }

    private val availablePlayers = ArrayDeque<ExoPlayer>()
    private val inUsePlayers = mutableSetOf<ExoPlayer>()

    @Synchronized
    fun acquirePlayer(): ExoPlayer {
        val player = if (availablePlayers.isNotEmpty()) {
            availablePlayers.removeFirst()
        } else {
            playerFactory.createPlayer()
        }
        inUsePlayers.add(player)
        return player
    }

    /**
     * Adopts an externally created player (e.g. handoff from preload) into the in-use set.
     */
    @Synchronized
    fun adoptPlayer(player: ExoPlayer) {
        availablePlayers.removeAll { it === player }
        inUsePlayers.add(player)
    }

    @Synchronized
    fun releasePlayer(player: ExoPlayer) {
        try {
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            player.clearVideoSurface()
            player.setVideoSurface(null)
        } catch (_: Exception) {
        }
        inUsePlayers.remove(player)
        if (availablePlayers.size < MAX_POOL_SIZE && !availablePlayers.contains(player)) {
            availablePlayers.addLast(player)
        } else if (!availablePlayers.contains(player)) {
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    fun releaseAll() {
        val snapshot = inUsePlayers.toList()
        inUsePlayers.clear()
        snapshot.forEach { player ->
            try {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
                player.clearVideoSurface()
            } catch (_: Exception) {
            }
            if (availablePlayers.size < MAX_POOL_SIZE) {
                availablePlayers.addLast(player)
            } else {
                try {
                    player.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    @Synchronized
    fun cleanup() {
        availablePlayers.forEach {
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        inUsePlayers.forEach {
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        availablePlayers.clear()
        inUsePlayers.clear()
        instance = null
    }
}

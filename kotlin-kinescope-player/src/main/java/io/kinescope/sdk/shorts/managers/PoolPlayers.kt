package io.kinescope.sdk.shorts.managers

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

class PoolPlayers private constructor(context: Context) {
    companion object {
        private lateinit var instance: PoolPlayers

        fun init(context: Context) {
            instance = PoolPlayers(context)
        }

        fun get(): PoolPlayers = instance
    }

    private val context: Context = context.applicationContext
    private val availablePlayers = ArrayDeque<ExoPlayer>()
    private val inUsePlayers = mutableSetOf<ExoPlayer>()

    fun acquirePlayer(): ExoPlayer {
        return if (availablePlayers.isNotEmpty()) {
            availablePlayers.removeFirst().also {
                inUsePlayers.add(it)
            }
        } else {
            ExoPlayer.Builder(context).build().also {
                inUsePlayers.add(it)
            }
        }
    }

    fun releaseAll() {
        inUsePlayers.forEach { player ->
            player.stop()
            player.clearMediaItems()
            availablePlayers.add(player)
        }
        inUsePlayers.clear()
    }

    fun cleanup() {
        availablePlayers.forEach { it.release() }
        inUsePlayers.forEach { it.release() }
        availablePlayers.clear()
        inUsePlayers.clear()
    }

    fun releasePlayer(player: ExoPlayer) {
        player.stop()
        player.clearMediaItems()
        inUsePlayers.remove(player)
        availablePlayers.add(player)
    }
}





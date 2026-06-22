package io.kinescope.sdk.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.kinescope.sdk.R

/**
 * Foreground [MediaSessionService] for background audio (W3).
 *
 * Call [connect] when playback should continue in background, [disconnect] to tear down.
 */
@OptIn(UnstableApi::class)
class KinescopePlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        boundPlayer?.playbackPlayer?.let(::createSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        if (boundPlayer === activeBinding?.player) {
            activeBinding = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createSession(player: Player) {
        mediaSession?.release()
        mediaSession = MediaSession.Builder(this, player).build()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.player_background_playback_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.player_background_playback_title))
            .setContentText(boundPlayer?.getVideo()?.title.orEmpty())
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private data class Binding(val player: KinescopeVideoPlayer)

    companion object {
        private const val CHANNEL_ID = "kinescope_playback"
        private const val NOTIFICATION_ID = 42

        private var activeBinding: Binding? = null

        private val boundPlayer: KinescopeVideoPlayer?
            get() = activeBinding?.player

        fun connect(context: Context, player: KinescopeVideoPlayer) {
            activeBinding = Binding(player)
            val intent = Intent(context, KinescopePlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun disconnect(context: Context) {
            activeBinding = null
            context.stopService(Intent(context, KinescopePlaybackService::class.java))
        }
    }
}

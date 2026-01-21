package com.kotlin.kinescope.shorts.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.kotlin.kinescope.shorts.AppJson
import com.kotlin.kinescope.shorts.R
import com.kotlin.kinescope.shorts.config.KinescopeUiConfig
import com.kotlin.kinescope.shorts.interfaces.ActivityProvider
import com.kotlin.kinescope.shorts.models.VideoData
import kotlinx.serialization.InternalSerializationApi
import kotlin.random.Random



@OptIn(UnstableApi::class)
@InternalSerializationApi
class NotificationHelper(
    private val context: Context,
    private val activityProvider: ActivityProvider? = null
) {
    private val channelId = "download_channel"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val random = Random(System.currentTimeMillis())

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Загрузки видео",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о завершении загрузки видео"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDownloadCompleteNotification(download: Download) {
        try {
            val uniqueNotificationId = random.nextInt(100000)

            val videoDataJson = String(download.request.data, Charsets.UTF_8)
            val videoData = AppJson.decodeFromString<VideoData>(videoDataJson)
            val videoTitle = videoData.title.takeIf { it.isNotBlank() } ?: "Видео"
            val notificationText = "\"$videoTitle\" успешно скачано и готово к просмотру"

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "✅ $videoTitle скачано", Toast.LENGTH_SHORT).show()
            }

            val mainIntent = activityProvider?.getMainActivityIntent() ?: Intent().apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val mainPendingIntent = PendingIntent.getActivity(
                context,
                uniqueNotificationId,
                mainIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            val offlineIntent = activityProvider?.getOfflinePlayerActivityIntent(
                videoData,
                download.request.id
            )

            val offlinePendingIntent = offlineIntent?.let { intent ->
                PendingIntent.getActivity(
                    context,
                    uniqueNotificationId + 1,
                    intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    else
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(KinescopeUiConfig.resolveDownloadNotificationIconResId())
                .setContentTitle("✅ Загрузка завершена")
                .setContentText(videoTitle)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(notificationText)
                        .setSummaryText("Готово к просмотру")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainPendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    KinescopeUiConfig.resolveDownloadNotificationIconResId(),
                    "Открыть",
                    mainPendingIntent
                )

            offlinePendingIntent?.let {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Смотреть",
                    it
                )
            }

            notificationManager.notify(uniqueNotificationId, builder.build())

        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "✅ Видео скачано", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
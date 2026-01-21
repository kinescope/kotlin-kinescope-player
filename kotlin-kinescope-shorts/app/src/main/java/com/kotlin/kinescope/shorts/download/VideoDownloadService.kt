package com.kotlin.kinescope.shorts.download

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.kotlin.kinescope.shorts.R
import com.kotlin.kinescope.shorts.config.KinescopeUiConfig

@OptIn(UnstableApi::class)
class VideoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "video_download_channel",
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    override fun getDownloadManager(): androidx.media3.exoplayer.offline.DownloadManager {
        return VideoDownloadManager.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler {
        return PlatformScheduler(this, JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<androidx.media3.exoplayer.offline.Download>,
        notMetRequirements: Int
    ): Notification {
        return buildNotification(this, downloads, notMetRequirements)
    }

    private fun buildNotification(
        context: Context,
        downloads: MutableList<androidx.media3.exoplayer.offline.Download>,
        notMetRequirements: Int
    ): Notification {
        val activeDownloads = downloads.filter {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }

        val notificationBuilder = Notification.Builder(context, "video_download_channel")
            .setSmallIcon(KinescopeUiConfig.resolveDownloadNotificationIconResId())

        if (activeDownloads.isNotEmpty()) {
            val download = activeDownloads.first()
            val (percent, _) = VideoDownloadManager.getDownloadProgress(download)

            notificationBuilder
                .setContentTitle("Загрузка видео")
                .setContentText("Прогресс: $percent%")
                .setProgress(100, percent, false)
        } else {
            notificationBuilder
                .setContentTitle("Загрузки видео")
                .setContentText("Нет активных загрузок")
                .setProgress(0, 0, false)
        }

        return notificationBuilder.build()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val JOB_ID = 1

        fun startDownload(context: Context, downloadRequest: androidx.media3.exoplayer.offline.DownloadRequest) {
            DownloadService.sendAddDownload(
                context,
                VideoDownloadService::class.java,
                downloadRequest,
                false
            )
        }

        fun removeDownload(context: Context, downloadId: String) {
            DownloadService.sendRemoveDownload(
                context,
                VideoDownloadService::class.java,
                downloadId,
                false
            )
        }
    }
}
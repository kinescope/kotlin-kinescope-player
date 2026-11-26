package io.kinescope.sdk.download

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.annotation.DrawableRes
import io.kinescope.sdk.R

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
            .setSmallIcon(notificationIconResId)

        if (activeDownloads.isNotEmpty()) {
            val download = activeDownloads.first()
            val (percent, bytesDownloaded) = VideoDownloadManager.getDownloadProgress(download)
            val indeterminate = percent <= 0 && bytesDownloaded <= 0
            notificationBuilder
                .setContentTitle("Загрузка видео")
                .setContentText(VideoDownloadManager.formatDownloadProgressLabel(download))
                .setProgress(100, percent.coerceAtLeast(0), indeterminate)
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

        /** Small icon of the download notification. Set before the first download. */
        @JvmStatic
        @DrawableRes
        var notificationIconResId: Int = android.R.drawable.stat_sys_download

        fun startDownload(context: Context, downloadRequest: androidx.media3.exoplayer.offline.DownloadRequest) {
            VideoDownloadManager.initialize(context)
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

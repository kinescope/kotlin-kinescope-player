package io.kinescope.sdk.download

import android.app.Activity
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.media3.common.util.UnstableApi::class)

/**
 * Waits for the Widevine PSSH of a playing [ExoPlayer] and hands it to [callback]
 * together with the data passed to [setOfflineDownloadPending] — the first step of
 * an offline DRM download ([DrmConfigurator.downloadOfflineLicense]).
 *
 * @param T your video metadata type.
 */
class DrmHelper<T : Any>(
    private val context: Context,
    private val drmConfigurator: DrmConfigurator,
    var callback: ((T, ByteArray) -> Unit)? = null
) {
 var psshData: ByteArray? = null
    private var pendingVideoData: T? = null
    private var isDownloadPending = false
    private var analyticsListener: AnalyticsListener? = null

    fun reset() {
        psshData = null
        pendingVideoData = null
        isDownloadPending = false
        analyticsListener = null
    }

    fun setOfflineDownloadPending(videoData: T) {
        reset()
        pendingVideoData = videoData
        isDownloadPending = true
    }

    fun attachToPlayer(player: ExoPlayer) {
        getPlayerPssh(player)?.let { pssh ->
            psshData = pssh
            notifyDownloadIfPending()
            return
        }

        analyticsListener = object : AnalyticsListener {
            override fun onDownstreamFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                mediaLoadData: MediaLoadData
            ) {
                mediaLoadData.trackFormat?.drmInitData?.let { extractPssh(it) }
            }

            override fun onDrmSessionAcquired(eventTime: AnalyticsListener.EventTime, state: Int) {
                getPlayerPssh(player)?.let { pssh ->
                    psshData = pssh
                    notifyDownloadIfPending()
                }
            }

            override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, trackGroups: Tracks) {
                trackGroups.groups.forEach { group ->
                    group.mediaTrackGroup.getFormat(0).drmInitData?.let { extractPssh(it) }
                }
            }
        }.also { listener ->
            player.addAnalyticsListener(listener)
        }

        CoroutineScope(Dispatchers.Main).launch {
            repeat(6) {
                delay(500)
                if (psshData == null) {
                    getPlayerPssh(player)?.let { pssh ->
                        psshData = pssh
                        notifyDownloadIfPending()
                        return@launch
                    }
                } else {
                    return@launch
                }
            }
        }
    }

    private fun extractPssh(drmInitData: DrmInitData) {
        for (i in 0 until drmInitData.schemeDataCount) {
            val schemeData = drmInitData.get(i)
            if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                psshData = schemeData.data
                notifyDownloadIfPending()
                return
            }
        }
    }

    private fun getPlayerPssh(player: ExoPlayer): ByteArray? {
        return player.videoFormat?.drmInitData?.let { drmInitData ->
            for (i in 0 until drmInitData.schemeDataCount) {
                val schemeData = drmInitData.get(i)
                if (schemeData.matches(C.WIDEVINE_UUID) && schemeData.hasData()) {
                    return schemeData.data
                }
            }
            null
        }
    }

    private fun notifyDownloadIfPending() {
        if (isDownloadPending && psshData != null && pendingVideoData != null && callback != null) {
            (context as? Activity)?.runOnUiThread {
                callback?.invoke(pendingVideoData!!, psshData!!)
                isDownloadPending = false
                pendingVideoData = null
            }
        }
    }
}

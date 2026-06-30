package io.kinescope.sdk.cast

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject

/**
 * Builds Cast [MediaInfo] + Kinescope receiver [customData] (Widevine license, duration, live flag).
 */
@OptIn(UnstableApi::class)
class KinescopeMediaItemConverter : MediaItemConverter {

    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val data = mediaItem.localConfiguration?.tag as? KinescopeCastData
            ?: return default.toMediaQueueItem(mediaItem)

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            data.title?.let { putString(MediaMetadata.KEY_TITLE, it) }
            data.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            data.posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }

        val customData = JSONObject().apply {
            if (data.durationSec > 0) put("duration", data.durationSec)
            if (data.isLive) put("live", true)
            data.drmLicenseUrl?.let { url ->
                put("playbackOptions", JSONObject().apply {
                    put("licenseUrl", url)
                    put("protectionSystem", "widevine")
                })
            }
        }

        val mediaInfo = MediaInfo.Builder(data.manifestUrl)
            .setStreamType(
                if (data.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED,
            )
            .setContentType(data.contentType)
            .setContentUrl(data.manifestUrl)
            .setMetadata(metadata)
            .setCustomData(customData)
            .build()

        return MediaQueueItem.Builder(mediaInfo).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val info = mediaQueueItem.media
        val uri = info?.contentUrl ?: info?.contentId ?: ""
        return MediaItem.Builder().setUri(uri).build()
    }
}

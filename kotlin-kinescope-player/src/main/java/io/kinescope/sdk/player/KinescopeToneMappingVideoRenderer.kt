package io.kinescope.sdk.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper
import androidx.media3.exoplayer.video.VideoFrameReleaseControl

/**
 * Forces OpenGL HDR→SDR tone-mapping on SDR displays (W7).
 */
@OptIn(UnstableApi::class)
internal class KinescopeToneMappingVideoRenderer(
    builder: Builder,
    private val requestOpenGlToneMapping: Boolean,
) : MediaCodecVideoRenderer(builder) {

    override fun createPlaybackVideoGraphWrapper(
        context: Context,
        videoFrameReleaseControl: VideoFrameReleaseControl,
    ): PlaybackVideoGraphWrapper {
        return super.createPlaybackVideoGraphWrapper(context, videoFrameReleaseControl).also {
            it.setRequestOpenGlToneMapping(requestOpenGlToneMapping)
        }
    }
}

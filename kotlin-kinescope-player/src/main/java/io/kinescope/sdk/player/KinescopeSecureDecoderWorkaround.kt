package io.kinescope.sdk.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * OEM secure AVC decoders (notably `c2.qti.avc.decoder.secure`) often fail to re-init
 * after prior DRM sessions — same class of bug Shorts hit while scrolling Widevine.
 *
 * For video we prefer a non-secure decoder list (CDM still decrypts when allowed) and
 * keep secure codecs as fallback; [DefaultRenderersFactory.setEnableDecoderFallback]
 * then recovers when the first codec fails to start.
 */
@OptIn(UnstableApi::class)
object KinescopeSecureDecoderWorkaround {

    val mediaCodecSelector: MediaCodecSelector = object : MediaCodecSelector {
        override fun getDecoderInfos(
            mimeType: String,
            requiresSecureDecoder: Boolean,
            requiresTunnelingDecoder: Boolean,
        ): List<MediaCodecInfo> {
            if (!mimeType.startsWith("video/")) {
                return MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder,
                )
            }
            val clear = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                /* requiresSecureDecoder= */ false,
                /* requiresTunnelingDecoder= */ false,
            )
            if (!requiresSecureDecoder) {
                return clear
            }
            val secure = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                /* requiresSecureDecoder= */ true,
                requiresTunnelingDecoder,
            )
            // Non-secure first (Shorts behaviour), then secure as fallback candidates.
            return mergeDecoderInfos(preferred = clear, fallback = secure)
        }
    }

    fun applyTo(factory: DefaultRenderersFactory): DefaultRenderersFactory {
        factory.setMediaCodecSelector(mediaCodecSelector)
        factory.setEnableDecoderFallback(true)
        return factory
    }

    private fun mergeDecoderInfos(
        preferred: List<MediaCodecInfo>,
        fallback: List<MediaCodecInfo>,
    ): List<MediaCodecInfo> {
        if (preferred.isEmpty()) return fallback
        if (fallback.isEmpty()) return preferred
        val names = preferred.mapTo(HashSet()) { it.name }
        return preferred + fallback.filter { it.name !in names }
    }
}

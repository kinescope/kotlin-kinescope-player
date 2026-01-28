package com.kotlin.kinescope.shorts.managers

import android.content.Context
import android.os.Build
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector


@UnstableApi
class PlayerFactory(private val context: Context) {

    fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(createLoadControl())
            .setTrackSelector(createTrackSelector())
            .setRenderersFactory(createRenderersFactory(context))
            .setUsePlatformDiagnostics(false)
            .build()
    }

    fun createPreloadPlayer(): ExoPlayer {
        val isWeakDevice = isWeakDevice()
        val preloadLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (isWeakDevice) 500 else 800,
                if (isWeakDevice) 3000 else 5000,
                if (isWeakDevice) 300 else 500,
                if (isWeakDevice) 500 else 1000
            )
            .setBackBuffer(if (isWeakDevice) 2000 else 3000, true)
            .setTargetBufferBytes(if (isWeakDevice) -1 else DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(preloadLoadControl)
            .setTrackSelector(createTrackSelector())
            .setRenderersFactory(createRenderersFactory(context))
            .setUsePlatformDiagnostics(false)
            .build()
    }

    private fun createLoadControl(): DefaultLoadControl {
        val isWeakDevice = isWeakDevice()
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (isWeakDevice) 1000 else 1500,
                if (isWeakDevice) 5000 else 8000,
                if (isWeakDevice) 500 else 1000,
                if (isWeakDevice) 1000 else 1500
            )
            .setBackBuffer(if (isWeakDevice) 3000 else 5000, true)
            .setTargetBufferBytes(if (isWeakDevice) -1 else DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .build()
    }

    private fun isWeakDevice(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val processorCount = Runtime.getRuntime().availableProcessors()

        return maxMemory < 2048 || 
               processorCount < 4 || 
               (Build.BRAND.equals("honor", ignoreCase = true) && maxMemory < 3072) ||
               (Build.BRAND.equals("huawei", ignoreCase = true) && maxMemory < 3072)
    }

    private fun createTrackSelector(): TrackSelector {
        return DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(false)
                    .setMaxVideoSize(854, 480)
                    .setForceLowestBitrate(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowMultipleAdaptiveSelections(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(false)
            )
        }
    }

    private fun createRenderersFactory(context: Context): RenderersFactory {
        return DefaultRenderersFactory(context).apply {
            if (isProblemMediaTekDevice() || isWeakDevice()) {
                setMediaCodecSelector(object : MediaCodecSelector {
                    override fun getDecoderInfos(mimeType: String, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean): List<MediaCodecInfo> {
                        if (mimeType.startsWith("video/")) {
                            return MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, false, false)
                        }
                        return MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    }
                })
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                setEnableDecoderFallback(true)
            }
        }
    }

    private fun isProblemMediaTekDevice(): Boolean {
        return Build.MANUFACTURER.equals("mediatek", ignoreCase = true) ||
                Build.HARDWARE.contains("mt") ||
                Build.BRAND.equals("redmi", ignoreCase = true) ||
                Build.BRAND.equals("oppo", ignoreCase = true) ||
                Build.BRAND.equals("honor", ignoreCase = true) ||
                Build.BRAND.equals("samsung", ignoreCase = true) ||
                Build.BRAND.equals("google", ignoreCase = true) ||
                Build.BRAND.equals("oneplus", ignoreCase = true) ||
                Build.BRAND.equals("motorola", ignoreCase = true) ||
                Build.BRAND.equals("vivo", ignoreCase = true) ||
                Build.BRAND.equals("huawei", ignoreCase = true) ||
                Build.BRAND.equals("realme", ignoreCase = true)
    }
}

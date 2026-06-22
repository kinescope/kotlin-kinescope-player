package io.kinescope.sdk.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * HDR helpers (W7): SurfaceView in PlayerView XML + OpenGL HDR→SDR tone-mapping on
 * non-HDR displays via [KinescopeToneMappingRenderersFactory].
 */
@OptIn(UnstableApi::class)
object KinescopeHdrHelper {
    fun shouldToneMapToSdr(context: Context, enabled: Boolean): Boolean =
        enabled && !displaySupportsHdr(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun configure(player: ExoPlayer, context: Context, enabled: Boolean) {
        if (!enabled) return
        player.videoScalingMode = if (displaySupportsHdr(context)) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    fun displaySupportsHdr(context: Context): Boolean {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return false
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return display.isHdr
        }
        return false
    }
}

package io.kinescope.sdk.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Screen-orientation helpers for portrait vs landscape content.
 *
 * [Activity.requestedOrientation] must be set by the host Activity; this API computes
 * the value and can wire [KinescopePlayerView] content-size callbacks for you.
 *
 * Rules:
 * - **Portrait video** → stay [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT] (inline and fullscreen)
 * - **Landscape video + fullscreen** → [ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE]
 * - **Landscape video + inline** → [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT] (phone chrome)
 */
@OptIn(UnstableApi::class)
object KinescopeContentOrientation {

    fun isPortrait(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && height > width

    /**
     * @return an [ActivityInfo] `SCREEN_ORIENTATION_*` constant for the current content + UI mode.
     */
    fun preferredScreenOrientation(
        isPortraitContent: Boolean,
        isFullscreen: Boolean,
    ): Int = when {
        isPortraitContent -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        isFullscreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun applyTo(
        activity: Activity,
        isPortraitContent: Boolean,
        isFullscreen: Boolean,
    ) {
        activity.requestedOrientation = preferredScreenOrientation(
            isPortraitContent = isPortraitContent,
            isFullscreen = isFullscreen,
        )
    }
}

/**
 * Keeps [Activity.requestedOrientation] in sync with the playing video aspect ratio
 * and whether the host UI is in fullscreen.
 *
 * Attach after creating player views; call [setFullscreen] from your fullscreen toggle.
 */
@OptIn(UnstableApi::class)
class KinescopeContentOrientationController(
    private val activity: Activity,
    private val playerViews: () -> List<KinescopePlayerView>,
) {
    private var isPortraitContent: Boolean = false
    private var isFullscreen: Boolean = false
    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        playerViews().forEach { view ->
            val previous = view.onContentOrientationChanged
            view.onContentOrientationChanged = { portrait ->
                previous?.invoke(portrait)
                isPortraitContent = portrait
                apply()
            }
            // Sync from an already-known size (e.g. after switchTargetView).
            if (view.isPortraitContent) {
                isPortraitContent = true
            }
        }
        apply()
    }

    fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        apply()
    }

    fun apply() {
        KinescopeContentOrientation.applyTo(
            activity = activity,
            isPortraitContent = isPortraitContent,
            isFullscreen = isFullscreen,
        )
    }

    fun detach() {
        if (!attached) return
        attached = false
        playerViews().forEach { view ->
            view.onContentOrientationChanged = null
        }
    }
}

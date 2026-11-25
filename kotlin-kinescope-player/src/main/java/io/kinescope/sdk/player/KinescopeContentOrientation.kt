package io.kinescope.sdk.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.Settings
import android.view.OrientationEventListener
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
 *
 * Optional fullscreen on rotate: set [fullscreenOnRotate] to `true` and handle
 * [onRotateFullscreenRequest]. Turning a landscape video's device to landscape asks the
 * host to enter fullscreen; turning it back to portrait asks to exit. The host performs
 * the switch (same code as the fullscreen button) and then calls [setFullscreen].
 */
@OptIn(UnstableApi::class)
class KinescopeContentOrientationController(
    private val activity: Activity,
    private val playerViews: () -> List<KinescopePlayerView>,
) {
    private var isPortraitContent: Boolean = false
    private var isFullscreen: Boolean = false
    private var attached = false

    /**
     * Enter fullscreen when the device is turned to landscape and exit when it is turned
     * back to portrait. Landscape videos only; portrait videos are ignored. Off by default.
     */
    var fullscreenOnRotate: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            resetRotateGesture()
            updateRotationListener()
        }

    /**
     * When `true` (default), rotation is ignored while the system auto-rotate setting is off,
     * as in YouTube and other system apps.
     */
    var respectSystemRotationLock: Boolean = true

    /**
     * Called with `true` to ask the host to enter fullscreen and `false` to exit.
     * Fires once per rotation; the host must call [setFullscreen] after switching.
     */
    var onRotateFullscreenRequest: ((enterFullscreen: Boolean) -> Unit)? = null

    /** Set once the device has been held in the orientation that matches the current mode. */
    private var rotateGestureArmed = false
    private var rotateRequestPending = false

    private val rotationListener: OrientationEventListener by lazy {
        object : OrientationEventListener(activity) {
            override fun onOrientationChanged(orientation: Int) {
                onDeviceOrientation(orientation)
            }
        }
    }

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
        updateRotationListener()
    }

    fun setFullscreen(fullscreen: Boolean) {
        if (isFullscreen != fullscreen) {
            // A new mode needs a fresh rotation: the device must first be seen in the
            // orientation of the new mode, so exiting by button while holding the phone
            // in landscape does not immediately re-enter fullscreen.
            resetRotateGesture()
        }
        rotateRequestPending = false
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
        updateRotationListener()
        playerViews().forEach { view ->
            view.onContentOrientationChanged = null
        }
    }

    private fun updateRotationListener() {
        if (attached && fullscreenOnRotate && rotationListener.canDetectOrientation()) {
            rotationListener.enable()
        } else {
            rotationListener.disable()
        }
    }

    private fun resetRotateGesture() {
        rotateGestureArmed = false
        rotateRequestPending = false
    }

    private fun onDeviceOrientation(degrees: Int) {
        if (!fullscreenOnRotate || degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return
        if (isPortraitContent) return
        if (respectSystemRotationLock && isSystemRotationLocked()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) return

        val deviceLandscape = when {
            degrees in 60..120 || degrees in 240..300 -> true
            degrees <= 30 || degrees >= 330 -> false
            else -> return // diagonal or upside-down: wait for a clear orientation
        }
        val matchesMode = deviceLandscape == isFullscreen
        if (matchesMode) {
            rotateGestureArmed = true
            rotateRequestPending = false
            return
        }
        if (!rotateGestureArmed || rotateRequestPending) return
        rotateRequestPending = true
        onRotateFullscreenRequest?.invoke(deviceLandscape)
    }

    private fun isSystemRotationLocked(): Boolean =
        Settings.System.getInt(
            activity.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1,
        ) == 0
}

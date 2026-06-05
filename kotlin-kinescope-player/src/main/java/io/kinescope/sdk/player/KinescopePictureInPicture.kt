package io.kinescope.sdk.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
object KinescopePictureInPicture {

    fun isSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun getAspectRatio(exoPlayer: ExoPlayer?): Rational {
        val videoSize = exoPlayer?.videoSize ?: return Rational(16, 9)
        var width = videoSize.width
        var height = videoSize.height
        if (width <= 0 || height <= 0) {
            return Rational(16, 9)
        }
        if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
            val tmp = width
            width = height
            height = tmp
        }
        return Rational(width, height)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun enter(
        activity: Activity,
        playerView: View,
        aspectRatio: Rational? = null,
    ): Boolean {
        if (!isSupported(activity)) {
            return false
        }

        val paramsBuilder = PictureInPictureParams.Builder()
        val ratio = aspectRatio ?: Rational(16, 9)
        paramsBuilder.setAspectRatio(ratio)

        val sourceRect = Rect()
        if (playerView.getGlobalVisibleRect(sourceRect) && !sourceRect.isEmpty) {
            paramsBuilder.setSourceRectHint(sourceRect)
        }

        return activity.enterPictureInPictureMode(paramsBuilder.build())
    }

    /**
     * Call from [Activity.onPictureInPictureModeChanged] when PiP is exited.
     * [onDismissed] runs when the user closed PiP (e.g. dragged to trash), not when they expanded back to the app.
     */
    fun onExitedPictureInPictureMode(
        activity: Activity,
        anchorView: View,
        onDismissed: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        anchorView.post {
            if (activity.isInPictureInPictureMode) {
                return@post
            }
            if (wasPictureInPictureDismissed(activity)) {
                onDismissed()
            }
        }
    }

    private fun wasPictureInPictureDismissed(activity: Activity): Boolean {
        if (activity.isFinishing) {
            return true
        }
        val lifecycle = (activity as? ComponentActivity)?.lifecycle ?: return true
        return !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}

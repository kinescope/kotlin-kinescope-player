package io.kinescope.sdk.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.kinescope.sdk.R

@UnstableApi
object KinescopePictureInPicture {

    const val ACTION_PLAY_PAUSE = "io.kinescope.sdk.action.PIP_PLAY_PAUSE"

    private const val REQUEST_PLAY_PAUSE = 1001

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
        anchorView: View,
        aspectRatio: Rational? = null,
        exoPlayer: ExoPlayer? = null,
    ): Boolean {
        if (!isSupported(activity)) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) {
            updateActions(activity, exoPlayer)
            return true
        }

        val params = buildParams(
            activity = activity,
            exoPlayer = exoPlayer,
            aspectRatio = aspectRatio,
            includeSourceRectHint = true,
            anchorView = anchorView,
        )
        return activity.enterPictureInPictureMode(params)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateActions(activity: Activity, exoPlayer: ExoPlayer?) {
        if (!activity.isInPictureInPictureMode) {
            return
        }
        activity.setPictureInPictureParams(
            buildParams(
                activity = activity,
                exoPlayer = exoPlayer,
                aspectRatio = getAspectRatio(exoPlayer),
                includeSourceRectHint = false,
                anchorView = null,
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildParams(
        activity: Activity,
        exoPlayer: ExoPlayer?,
        aspectRatio: Rational?,
        includeSourceRectHint: Boolean,
        anchorView: View?,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio ?: getAspectRatio(exoPlayer))
            .setActions(buildActions(activity, exoPlayer?.isPlaying == true))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
        }

        if (includeSourceRectHint && anchorView != null) {
            val sourceRect = Rect()
            if (anchorView.getGlobalVisibleRect(sourceRect) && !sourceRect.isEmpty) {
                builder.setSourceRectHint(sourceRect)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setTitle("")
            builder.setSubtitle("")
        }

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildActions(activity: Activity, isPlaying: Boolean): List<RemoteAction> {
        val playPauseTitle = activity.getString(
            if (isPlaying) R.string.player_pip_pause else R.string.player_pip_play
        )
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        return listOf(
            createBroadcastAction(
                context = activity,
                iconRes = playPauseIcon,
                title = playPauseTitle,
                action = ACTION_PLAY_PAUSE,
                requestCode = REQUEST_PLAY_PAUSE,
            ),
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createBroadcastAction(
        context: Context,
        iconRes: Int,
        title: String,
        action: String,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(action).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(
            Icon.createWithResource(context, iconRes),
            title,
            title,
            pendingIntent,
        )
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

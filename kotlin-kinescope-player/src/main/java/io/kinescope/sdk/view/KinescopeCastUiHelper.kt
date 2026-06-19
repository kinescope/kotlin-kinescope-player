package io.kinescope.sdk.view

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.framework.CastContext

internal object KinescopeCastUiHelper {

    fun showCastDialog(activity: AppCompatActivity, castContext: CastContext) {
        val selector = castContext.mergedSelector ?: return
        val connected = castContext.sessionManager.currentCastSession?.isConnected == true
        if (connected) {
            MediaRouteControllerDialog(activity).show()
        } else {
            MediaRouteChooserDialog(activity).apply {
                routeSelector = selector
            }.show()
        }
    }

    fun isCastRouteAvailable(context: Context, castContext: CastContext): Boolean {
        val selector = castContext.mergedSelector ?: return false
        val router = androidx.mediarouter.media.MediaRouter.getInstance(context.applicationContext)
        return router.isRouteAvailable(
            selector,
            androidx.mediarouter.media.MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE,
        )
    }
}

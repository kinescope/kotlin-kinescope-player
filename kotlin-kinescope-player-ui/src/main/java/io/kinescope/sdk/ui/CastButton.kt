package io.kinescope.sdk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouter
import io.kinescope.sdk.ui.R
import com.google.android.gms.cast.framework.CastContext

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val colors = playerColors()
    val context = LocalContext.current
    val selector = remember {
        runCatching { CastContext.getSharedInstance(context).mergedSelector }.getOrNull()
    } ?: return
    val router = remember { MediaRouter.getInstance(context.applicationContext) }
    var available by remember { mutableStateOf(false) }

    DisposableEffect(selector) {
        fun refresh() {
            available = router.isRouteAvailable(
                selector,
                MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE,
            )
        }
        val cb = object : MediaRouter.Callback() {
            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        }
        router.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
        onDispose { router.removeCallback(cb) }
    }

    if (!available) return

    Box(
        modifier = modifier
            .size(PlayerTokens.Dimens.ControlIcon)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val connected = runCatching {
                    CastContext.getSharedInstance(context)
                        .sessionManager.currentCastSession?.isConnected == true
                }.getOrDefault(false)
                if (connected) {
                    MediaRouteControllerDialog(context).show()
                } else {
                    MediaRouteChooserDialog(context).apply { routeSelector = selector }.show()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_cast),
            contentDescription = "Транслировать на устройство",
            colorFilter = ColorFilter.tint(colors.iconBar),
        )
    }
}

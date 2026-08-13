# Fullscreen

Fullscreen is implemented on the app side by switching the active `KinescopePlayerView`.

**1.** Add to `configChanges` in your Activity manifest:

```xml
<activity android:name=".YourActivity"
    android:configChanges="orientation|screenSize|screenLayout|layoutDirection|smallestScreenSize" />
```

**2.** Switch target view and window flags:

```kotlin
private fun setFullscreen(fullscreen: Boolean) {
    if (fullscreen) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        KinescopePlayerView.switchTargetView(playerView, fullscreenPlayerView, kinescopePlayer)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN
                    and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        } else {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN
                    and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
        KinescopePlayerView.switchTargetView(fullscreenPlayerView, playerView, kinescopePlayer)
    }
}
```

**3.** Wire the fullscreen button and content-aware screen orientation:

```kotlin
val orientationController = KinescopeContentOrientationController(
    activity = this,
    playerViews = { listOf(playerView, fullscreenPlayerView) },
)
orientationController.attach()

playerView.onFullscreenButtonCallback = {
    // … toggle fullscreen UI …
    orientationController.setFullscreen(isVideoFullscreen)
}
```

Portrait / vertical videos stay in portrait (including fullscreen). Landscape videos still rotate to landscape when fullscreen. Prefer this over hard-coding `SCREEN_ORIENTATION_LANDSCAPE`.

`switchTargetView` preserves playback state, track selection, analytics, subtitle overlay state, and content orientation across views.

If you use [`useTextureSurface`](picture-in-picture.md) (Smooth PiP transition with TextureView) for smooth PiP, apply the same flag when creating both inline and fullscreen player views.

Hide the fullscreen button via options:

```kotlin
kinescopePlayer.kinescopePlayerOptions.showFullscreenButton = false
playerView.applyTemplateOptions()
```

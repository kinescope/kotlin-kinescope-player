# Picture-in-Picture

PiP requires Android 8+ and `supportsPictureInPicture="true"` on the Activity.

```xml
<activity
    android:name=".YourActivity"
    android:configChanges="orientation|screenSize|screenLayout|layoutDirection|smallestScreenSize"
    android:supportsPictureInPicture="true" />
```

## Recommended: `KinescopePictureInPictureSession`

Wires the PiP button, lifecycle pause/resume, play/pause remote action in the PiP window, and exit handling:

```kotlin
import io.kinescope.sdk.player.KinescopePictureInPictureSession

private lateinit var pipSession: KinescopePictureInPictureSession

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pipSession = KinescopePictureInPictureSession(
        activity = this,
        playerView = { playerView },
        player = { kinescopePlayer },
        additionalPlayerViews = { listOf(fullscreenPlayerView) }, // optional
    )
}

override fun onStart() {
    super.onStart()
    pipSession.attach()
}

override fun onStop() {
    pipSession.onStop()
    super.onStop()
}

override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    pipSession.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
}
```

For manual control, use `KinescopePictureInPicture.enter()` and `updateActions()` directly.

Hide the PiP button:

```kotlin
kinescopePlayer.kinescopePlayerOptions.pictureInPicture = false
playerView.applyTemplateOptions()
```

## Smooth PiP transition (TextureView)

By default, `KinescopePlayerView` renders video through `SurfaceView`. That is the right default for **DRM** (Widevine L1) and is generally cheaper on the GPU.

On some devices (e.g. OxygenOS), `SurfaceView` pixels do not take part in the system PiP animation: during the transition the OS may briefly show an app-icon placeholder, then video snaps into the PiP window. A `TextureView` lives in the normal view hierarchy, so the video frame can animate smoothly into PiP (similar to apps like Vimeo).

For **non-DRM** playback where a smooth PiP transition matters, create the view programmatically:

```kotlin
import io.kinescope.sdk.view.KinescopePlayerView

val playerView = KinescopePlayerView(context, useTextureSurface = true)
```

| `useTextureSurface` | Surface | When to use |
|-------------------|---------|-------------|
| `false` (default) | `SurfaceView` | DRM, general playback; also used when the view is declared in XML |
| `true` | `TextureView` | Non-DRM only; smoother PiP on some OEM skins |

Notes:

- **XML layout** always inflates the `SurfaceView` variant. The flag is constructor-only; `@JvmOverloads` keeps `KinescopePlayerView(context, attrs)` binary-compatible.
- **`TextureView` cannot show protected content** — do not enable for Widevine or other DRM streams.
- If you use **fullscreen** with a second `KinescopePlayerView`, pass the same `useTextureSurface` value to both inline and fullscreen views so behaviour stays consistent.

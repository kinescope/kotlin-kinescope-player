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

# Chromecast (Google Cast)

Cast uses the Kinescope custom receiver (`KinescopeCastOptionsProvider` is merged from the library manifest). Requires Google Play services on the device.

## `KinescopeCastSession`

```kotlin
import io.kinescope.sdk.player.KinescopeCastSession

private lateinit var castSession: KinescopeCastSession

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    castSession = KinescopeCastSession(
        activity = this,
        playerView = { playerView },
        player = { kinescopePlayer },
        additionalPlayerViews = { listOf(fullscreenPlayerView) }, // optional
    )
}

override fun onStart() {
    super.onStart()
    castSession.attach()
}
```

`KinescopeCastSession`:

- Enables the cast button and shows the device picker on tap
- Loads the current video on the receiver with manifest + DRM license
- Syncs position on connect/disconnect
- Pauses local playback while casting
- Shows a cast overlay (play/pause, seek, stop)
- Resumes local playback at the cast position when the session ends

Call `castSession.release()` if you detach before activity destroy (otherwise lifecycle handles cleanup).

## Enable the cast button

```kotlin
kinescopePlayer.kinescopePlayerOptions.showCastButton = true
// or
kinescopePlayer.setShowCast(true)
playerView.applyTemplateOptions()
```

## View switching

`KinescopePlayerHost` and `KinescopePlayerView.switchTargetView` let you swap the active `Player` between local ExoPlayer and the cast session without losing UI state.

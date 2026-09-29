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

- Shows the cast button only while a Cast device is reachable, and the device picker on tap
- Loads the current video on the receiver with manifest + DRM license (Widevine, only for DRM videos)
- Sends a newly loaded video (`loadVideo` while casting) to the receiver
- Picks up a session that is already connected when the screen is (re)created
- Syncs position on connect/disconnect
- Pauses local playback while casting
- Shows a cast overlay (play/pause/replay, seek, stop); replay reloads finished media on the receiver
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

**Compose:** cast overlay reads `PlayerUiState` from the active `Player`.

**View/XML:** `KinescopeCastSession` switches `KinescopePlayerHost` and `KinescopePlayerView.refreshCastOverlay()` reads position/duration/play state from the active `Player` (not a separate `KinescopeCastState`).

## Troubleshooting: no devices in the picker

Discovery runs in Google Play services over mDNS (`_googlecast._tcp`) and then connects to the device on TCP 8009. Check, in order:

1. **VPN on the TV or on the phone.** A VPN on the Cast device (for example an Android TV box) answers from the tunnel, so the phone never gets the mDNS reply or the TCP connection: the picker stays empty for every receiver, including Google's default one. Turn the VPN off or exclude the local network.
2. **Same network.** Phone and TV must be on the same subnet; guest Wi-Fi / client isolation and blocked multicast hide devices.
3. **Chromecast built-in.** The TV must support Google Cast (YouTube's cast button should see it).

To tell a receiver problem from a network one, temporarily set the receiver id to `CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`: if the device still does not appear, the cause is the network, not the app.

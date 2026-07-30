# Quick start

## Player setup

**1.** Add `KinescopePlayerView` to your layout:

```xml
<io.kinescope.sdk.view.KinescopePlayerView
    android:id="@+id/player_view"
    android:layout_width="match_parent"
    android:layout_height="250dp" />
```

**2.** Initialize `KinescopeVideoPlayer` with options (recommended):

```kotlin
import io.kinescope.sdk.models.players.syncLegacyChromeFlags
import io.kinescope.sdk.player.KinescopePlayerOptions
import io.kinescope.sdk.player.KinescopeVideoPlayer

val options = KinescopePlayerOptions().apply {
    accentColor = "#6161FC"
    syncLegacyChromeFlags()
}
val kinescopePlayer = KinescopeVideoPlayer(context, options)
```

A no-options constructor is also available: `KinescopeVideoPlayer(context)`.

**3.** Attach the player and apply template UI:

```kotlin
playerView.setPlayer(kinescopePlayer)
playerView.applyTemplateOptions()
```

**4.** Load and play:

```kotlin
kinescopePlayer.loadVideo(videoId, onSuccess = { video ->
    if (!kinescopePlayer.kinescopePlayerOptions.autoplay) {
        kinescopePlayer.play()
    }
}, onFailed = {
    it?.printStackTrace()
})
```

When `autoplay = true`, playback starts automatically after `loadVideo` succeeds.

## Local / progressive file

To play a local or progressive URI (`content://`, `file://`, progressive HTTP) without a Kinescope media id:

```kotlin
kinescopePlayer.setLocalSource(uri, autoplay = false)
```

See [Local / progressive playback](local-playback.md) for limitations (no quality/subtitle metadata) and how to hide unused menus.

## Related docs

- [Player options](player-options.md) — all `KinescopePlayerOptions` flags
- [Player chrome](player-chrome.md) — icons, seek bar, gestures on the video
- [Settings menu](settings-menu.md) — Quality, Speed, Audio, Subtitles (gear popup)
- [Subtitles](subtitles-and-settings.md) — tracks, appearance, captions search
- [Local / progressive playback](local-playback.md) — `setLocalSource(uri)`
- [Customization](customization.md) — accent colour, poster, custom button
- [Demo app](demo-app.md)
- [Picture-in-Picture](picture-in-picture.md)
- [Chromecast](chromecast.md)
- [Fullscreen](fullscreen.md)

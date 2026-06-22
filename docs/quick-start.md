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

## Related docs

- [Player options](player-options.md)
- [Customization](customization.md) — accent colour, poster, custom button
- [Subtitles & settings](subtitles-and-settings.md)
- [Compose UI](compose-ui.md)
- [Demo app](demo-app.md)
- [Picture-in-Picture](picture-in-picture.md)
- [Chromecast](chromecast.md)
- [Fullscreen](fullscreen.md)

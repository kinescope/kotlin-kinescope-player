# Player options

`KinescopePlayerOptions` controls playback behaviour and player chrome.

| Option | Default | Description |
|--------|---------|-------------|
| `autoplay` | `false` | Start playback after `loadVideo` |
| `muted` | `false` | Mute audio |
| `loop` | `false` | Loop current video |
| `controls` | `true` | Show player controls |
| `playsinline` | `true` | Inline playback (no forced fullscreen) |
| `preload` | `"metadata"` | Preload strategy: `none`, `metadata`, `auto` |
| `quality` | `"auto"` | Default quality: `auto`, `audio`, or height in px |
| `pictureInPicture` | `true` | Show PiP button (Android 8+) |
| `fullscreen` | `true` | Show fullscreen button |
| `playbackRate` | `true` | Show playback speed in settings |
| `accentColor` | `"#6161FC"` | Accent colour for progress bar and initial play button |
| `showCastButton` | `false` | Show Chromecast button |
| `showChaptersButton` | `true` | Show chapters button when video has chapters |
| `showSubtitlesButton` | `false` | Show dedicated subtitles button in chrome |
| `showFullscreenButton` | `true` | Show fullscreen button |
| `showOptionsButton` | `true` | Show settings (gear) button |
| `showSeekBar` | `true` | Show seek bar |
| `showDuration` | `true` | Show current/total duration |
| `showPlayPauseButton` | `true` | Show play/pause control |
| `showPlaybackSpeedInSettings` | `true` | Show playback speed in settings menu |
| `showAudioTracksInSettings` | `true` | Show audio track submenu when the stream has multiple tracks |
| `showAudioOnlyQualityInSettings` | `true` | Show audio-only quality option in settings |
| `videoScale` | `true` | Pinch-to-zoom on video and **Scale** item in settings (1×–5×) |
| `showDefaultPoster` | `true` | Built-in `default_poster` when the video has no poster URL |
| `showLiveAwaitingCover` | `true` | Built-in `live_awaiting_cover` while a live broadcast has not started |
| `drmAuthToken` | `null` | Authorization Backend token (`drmauthtoken`) sent with `{videoId}.json` on `loadVideo` |
| `referer` | `https://kinescope.io/` | HTTP `Referer` for metadata / DRM requests (must match domain restrictions in the dashboard) |

Chrome icons (play, seek bar, PiP, fullscreen, …) vs settings popup items (Quality, Speed, …) are documented separately: [Player chrome](player-chrome.md), [Settings menu](settings-menu.md).

### Built-in posters

Both placeholders are **on by default**. Disable if you want an empty surface or your own art:

```kotlin
kinescopePlayer.kinescopePlayerOptions.apply {
    showDefaultPoster = false       // no default_poster.png fallback
    showLiveAwaitingCover = false   // no live_awaiting_cover.png before live starts
}
```

- `showDefaultPoster = false` — when metadata has no `poster.url`, nothing is shown (you can still call `showPoster(...)` yourself).
- `showLiveAwaitingCover = false` — `showLiveAwaitingCover()` becomes a no-op; see [Live](live.md).

### DRM Authorization Backend (`drmAuthToken`)

When video access is gated by a [Kinescope Authorization Backend](https://docs.kinescope.com/developer-guides/authorization-backend/), pass the same token you would put in the embed `?drmauthtoken=` query:

```kotlin
kinescopePlayer.kinescopePlayerOptions.drmAuthToken = userJwtOrId
kinescopePlayer.loadVideo(videoId)
```

The SDK appends `drmauthtoken` to the metadata request (`https://kinescope.io/{videoId}.json`). Set the token **before** `loadVideo`. Reload the video after changing the token.

If the video is also limited to specific domains in the dashboard, set a matching Referer (does not open embedding on other sites):

```kotlin
kinescopePlayer.setReferer("https://your-domain.com/")
```

### Settings icon quality badge

When playback resolution is **1080p or higher**, the settings button shows an **HD** or **4K** badge on the gear icon (including under **auto** quality, based on the current `videoSize`). No extra configuration is required.

> **Note.** If the Kinescope dashboard adds player settings that are not yet implemented in the SDK, they are ignored when a template is fetched and applied on the device.

## Apply changes at runtime

```kotlin
kinescopePlayer.applyPlaybackOptions()  // muted, loop on ExoPlayer
playerView.applyTemplateOptions()       // chrome, accent colour, default quality
```

Or refresh chrome only:

```kotlin
playerView.refreshPlayerChrome()
```

## Lifecycle

Bind the player to the Activity/Fragment lifecycle so playback pauses on `onStop` and resumes on `onStart` (skipped while PiP is active):

```kotlin
kinescopePlayer.bindLifecycle(
    lifecycle = lifecycle,
    isPipActive = { pipSession.isInPictureInPictureMode },
)
```

`onDestroy` calls `release()` automatically. Use `unbindLifecycle()` if you detach the player earlier.

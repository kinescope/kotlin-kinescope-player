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
| `showSubtitlesButton` | `false` | Show dedicated subtitles button in chrome |
| `showFullscreenButton` | `true` | Show fullscreen button |
| `showOptionsButton` | `true` | Show settings (gear) button |
| `showSeekBar` | `true` | Show seek bar |
| `showDuration` | `true` | Show current/total duration |
| `showPlayPauseButton` | `true` | Show play/pause control |
| `showPlaybackSpeedInSettings` | `true` | Show playback speed in settings menu |
| `showAudioOnlyQualityInSettings` | `true` | Show audio-only quality option in settings |

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

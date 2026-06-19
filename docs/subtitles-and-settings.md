# Subtitles & settings

When `showOptionsButton` is enabled, the player shows a nested settings menu:

- **Quality** — auto, fixed resolution variants, audio-only (when available)
- **Playback speed** — when `showPlaybackSpeedInSettings = true`
- **Subtitles** — track selection; appearance submenu when tracks are available

## External subtitle tracks

For DASH videos, external tracks (VTT, TTML, SRT) are merged via `MergingMediaSource`. Tracks can be switched at runtime from the settings menu. When `showSubtitlesButton` is on, the first available track is auto-enabled.

## Subtitle appearance

Appearance is controlled via `SubtitleStyle` on `KinescopePlayerView`:

- Font colour and size
- Background colour and opacity

The settings view exposes `onSubtitleStyleChanged` for custom integrations.

## Enable subtitles in chrome

```kotlin
kinescopePlayer.kinescopePlayerOptions.showSubtitlesButton = true
kinescopePlayer.setShowSubtitles(true)
playerView.applyTemplateOptions()
```

## Settings chrome flags

| Option | Default | Description |
|--------|---------|-------------|
| `showOptionsButton` | `true` | Settings (gear) button |
| `showSubtitlesButton` | `false` | Dedicated CC button |
| `showPlaybackSpeedInSettings` | `true` | Speed submenu |
| `showAudioOnlyQualityInSettings` | `true` | Audio-only quality option |

See also [Player options](player-options.md).

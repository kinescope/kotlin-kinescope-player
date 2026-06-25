# Subtitles & settings

When `showOptionsButton` is enabled, the player shows a nested settings menu:

- **Quality** — auto, fixed resolution variants, audio-only (when available)
- **Playback speed** — when `showPlaybackSpeedInSettings = true`
- **Subtitles** — track selection; appearance submenu when tracks are available

## External subtitle tracks

For DASH videos, external tracks (VTT, TTML, SRT) are merged via `MergingMediaSource`. Tracks can be switched at runtime from the settings menu. When `showSubtitlesButton` is on, the first available track is auto-enabled.

## Progressive subtitles overlay

`KinescopePlayerView` renders subtitles through a custom **progressive overlay** (not the default ExoPlayer `SubtitleView`):

| Behaviour | Description |
|-----------|-------------|
| **Line-by-line reveal** | Text appears per VTT line (`\n`) or per wrapped visual line — not word-by-word |
| **Two lines** | Bottom line shows the current phrase; when it overflows, the previous line moves to the top |
| **Two-line alignment** | When both lines are visible, text is centred inside the caption block |
| **Single line** | Left-aligned |
| **Caption box** | Dark rounded background (`bg_kinescope_caption`); width spans from the left margin to the right margin (12 dp each side) |
| **Controls offset** | When the control overlay is visible, captions move up; they animate back down (200 ms) when controls hide |

### Layout dimensions

| Resource | Value | Purpose |
|----------|-------|---------|
| `kinescope_caption_margin_start` | 12 dp | Left inset from player edge |
| `kinescope_caption_margin_end` | 12 dp | Right inset from player edge |
| `kinescope_caption_padding_horizontal` | 8 dp | Padding inside the caption box |
| `kinescope_caption_max_width` | 280 dp | Reference width (text uses available space between margins) |

### Custom UI

For custom chrome (controls hidden via `KinescopePlayerOptions`), call `KinescopePlayerView.syncSubtitleChromeForControls(controlsVisible)` so captions follow your own overlay visibility.

## Subtitle appearance

Appearance is controlled via `SubtitleStyle` on `KinescopePlayerView`:

- Font colour and size (`fontSizePercent`)
- Background colour and opacity (`bgColor`, `bgOpacityPercent`)

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

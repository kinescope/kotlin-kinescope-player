# Subtitles

How the SDK loads, renders, and configures subtitle tracks. For player icons see [Player chrome](player-chrome.md); for the settings popup structure see [Settings menu](settings-menu.md).

## Quick links

| Topic | Where |
|-------|--------|
| Subtitles row in settings | [Settings menu → Subtitles](settings-menu.md) |
| Enable subtitles menu item | [Settings menu — Settings-only options](settings-menu.md) |
| Control overlay / tap behaviour | [Player chrome](player-chrome.md) |

## Enable subtitles

```kotlin
kinescopePlayer.kinescopePlayerOptions.showSubtitlesButton = true
kinescopePlayer.setShowSubtitles(true)
playerView.applyTemplateOptions()
```

The **Subtitles** row appears in settings when the video has tracks. With `setShowSubtitles(true)`, the first track is auto-selected.

## External subtitle tracks

For DASH, external tracks (VTT, TTML, SRT) are merged via `MergingMediaSource`. Tracks can be switched at runtime from **Settings → Subtitles/CC**.

## Captions search

**Path:** Settings → Subtitles/CC → **Captions search**

Available when the video has external subtitle tracks.

| Behaviour | Description |
|-----------|-------------|
| **Transcript** | Loads the active subtitle file (VTT/SRT) and lists cues in a bottom panel |
| **Search** | Filters and highlights matches; ↑/↓ jumps between matches and seeks |
| **Playback sync** | Current cue marked with a **vertical bar** on the left; list auto-scrolls |
| **Row tap** | Seeks to cue start; timestamp on the **right** for the selected row |
| **On-video subtitles** | Progressive overlay hidden while search is open |

The panel sits above the control bar; bottom player icons stay visible and clickable.

Inline placement is a view-level choice, `KinescopePlayerView.captionsSearchPlacement`:

| Placement | Layout |
|-----------|--------|
| `BOTTOM` (default) | Fixed-height panel docked above the control bar |
| `TOP` | Panel docked to the top edge, list fills down to the control bar — for hosts whose player band changes height (a draggable sheet), so the panel stays put |

A top-docked panel clears the system safe area at the top (status bar, display cutout) on its own; `captionsSearchTopInset` (px) adds the host's own header drawn over the band.

Fullscreen layout is unaffected; the choice is kept across fullscreen toggles, orientation changes and resizes.

Closes automatically on Picture-in-Picture entry.

## Progressive subtitles overlay

`KinescopePlayerView` renders subtitles through a custom **progressive overlay** (not the default ExoPlayer `SubtitleView`):

| Behaviour | Description |
|-----------|-------------|
| **Line-by-line reveal** | Text appears per VTT line (`\n`) or per wrapped visual line |
| **Two lines** | Bottom line = current phrase; overflow moves previous line to top |
| **Two-line alignment** | Both lines centred in the caption block |
| **Single line** | Left-aligned |
| **Caption box** | Dark rounded background; 12 dp side margins |
| **Z-order** | Drawn above the control overlay scrim (not dimmed behind chrome) |
| **Controls offset** | Sit just above the control bar when chrome is visible (inline and fullscreen); animate with the overlay (~200 ms) |
| **Scrub** | Fade out while dragging the seek bar; fade back in after scrub ends |

### Layout dimensions

| Resource | Value | Purpose |
|----------|-------|---------|
| `kinescope_caption_margin_start` | 12 dp | Left inset |
| `kinescope_caption_margin_end` | 12 dp | Right inset |
| `kinescope_caption_padding_horizontal` | 8 dp | Inner padding |
| `kinescope_caption_max_width` | 280 dp | Reference max width |

### Custom UI

```kotlin
playerView.syncSubtitleChromeForControls(controlsVisible = yourControlsVisible)
```

## Subtitle appearance

**Path:** Settings → Subtitles/CC → **Options** → Appearance

Controlled by `SubtitleStyle` on `KinescopePlayerView`:

- Font colour and size (`fontSizePercent`)
- Background colour and opacity (`bgColor`, `bgOpacityPercent`)

```kotlin
settingsMenuView.onSubtitleStyleChanged = { style ->
    // custom integration
}
```

## Related options

| Option | Default | Description |
|--------|---------|-------------|
| `showSubtitlesButton` | `false` | Show Subtitles row in settings |
| `showOptionsButton` | `true` | Required to access settings |

See [Player options](player-options.md) for the full list.

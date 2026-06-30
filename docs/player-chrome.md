# Player chrome

Built-in controls rendered by `KinescopePlayerView` on top of the video. This is **not** the settings popup — see [Settings menu](settings-menu.md) for Quality, Speed, Subtitles, and other nested menus opened from the **gear** icon.

Tap the video to show or hide the control overlay (auto-hides after a few seconds while playing).

## Layout overview

```
┌─────────────────────────────────────────┐
│  Title / author (optional)              │
│                                         │
│              ▶ Play / Pause             │  ← centre control
│                                         │
│  [progressive subtitles overlay]        │
│                                         │
│  12:34  ───●────────────  [icons…] ⛶ ⋯  │  ← bottom control bar
└─────────────────────────────────────────┘
```

On **narrow layouts** (width ≤ ~480 dp) and in **fullscreen**, the bottom bar uses a **compact** mode: seek bar and time can collapse when the **⋯** strip is expanded.

## Centre control

| Element | Option | Default | Description |
|---------|--------|---------|-------------|
| Play / Pause / Replay | `showPlayPauseButton` | `true` | Large centre button; shows replay when playback ended |
| — | `controls` | `true` | Master switch for the whole overlay |

Hidden during Picture-in-Picture, buffering spinner (initial load), and while [captions search](subtitles-and-settings.md) is open.

## Bottom control bar

### Time and seek

| Element | Option | Default | Description |
|---------|--------|---------|-------------|
| Current time | `showDuration` | `true` | Shown on mobile and when duration is enabled |
| Total duration | `showDuration` | `true` | Tap time to toggle ` / duration` suffix |
| Seek bar | `showSeekBar` | `true` | `KinescopeTimeBar`; chapter markers when video has chapters |
| Live badge | — | — | Shown for live streams instead of VOD time layout |

### Icon buttons (right side)

Icons appear in the **options strip** to the left of **fullscreen**. Order (when visible):

| Icon | Option | Default | Action |
|------|--------|---------|--------|
| **Custom** | `configureChrome { addButton(...) }` or `showCustomButton()` | hidden | App-defined; multiple buttons supported |
| **PiP** | `pictureInPicture` | `true` | Enter Picture-in-Picture — see [PiP](picture-in-picture.md) |
| **Chapters** | `showChaptersButton` | `true` | Opens chapter picker (only if video has chapters, not live) |
| **Cast** | `showCastButton` | `false` | Chromecast — see [Chromecast](chromecast.md) |
| **Settings (gear)** | `showOptionsButton` | `true` | Opens [settings menu](settings-menu.md) |
| **Fullscreen** | `showFullscreenButton` / `fullscreen` | `true` | Always at the trailing edge — see [Fullscreen](fullscreen.md) |
| **⋯ (dots)** | `showOptionsButton` | `true` | **Mobile / fullscreen only** — expands the options strip |

### Settings gear badge

The **gear icon** (not the menu itself) can show a quality badge based on actual playback height:

| Height | Icon |
|--------|------|
| &lt; 1080p | Default gear |
| ≥ 1080p | Gear + **HD** |
| ≥ 2160p | Gear + **4K** |

Works under **auto** quality (`videoSize.height`). See [Settings menu → Quality](settings-menu.md).

### Compact options bar (mobile / fullscreen)

When `usesCompactOptionsChrome()` is active:

1. **⋯** expands the strip → reveals gear, PiP, chapters, cast (according to options).
2. While expanded, **seek bar and time hide** to make room.
3. Tap **gear** → opens settings (works from captions search without expanding **⋯** first).

## Title block

| Element | Visibility |
|---------|------------|
| Video title | When metadata is loaded and controls are shown |
| Author / subtitle | When `KinescopeVideo.subtitle` is set |

Hidden during scrubbing, captions search, and PiP exit flash suppression.

## Gestures

| Gesture | Behaviour |
|---------|-----------|
| **Single tap** | Toggle control overlay (ignored while captions search is open for hide-only; close search via ✕) |
| **Double tap** (left / right half) | Seek −10 s / +10 s with on-screen feedback |
| **Pinch** | Zoom video 1×–5× from the centre (`videoScale = true`) |
| **One-finger drag** | Pan when zoomed in; double-tap seek is disabled while zoomed |

When scale is above 100%, a **scale badge** (`2.5x`, etc.) appears at the top centre. It stays visible when the control overlay auto-hides. Tap the badge to reset zoom. The badge is hidden under the open settings menu and on **Settings → Scale**.

## Other overlays (not settings)

| Overlay | Trigger |
|---------|---------|
| Buffering spinner | Initial load / rebuffer |
| Poster | Before first playback — [Customization → Poster](customization.md) |
| Cast overlay | While casting |
| Chapters menu | Chapters icon — numbered list + seek |
| Captions search | Settings → Subtitles → [Captions search](subtitles-and-settings.md) |
| Mobile gradients | Top/bottom scrim when controls visible on mobile |

## Enable / refresh chrome

```kotlin
kinescopePlayer.kinescopePlayerOptions.apply {
    controls = true
    showPlayPauseButton = true
    showSeekBar = true
    showDuration = true
    showOptionsButton = true
    showFullscreenButton = true
    pictureInPicture = true
    showChaptersButton = true
    showCastButton = false
    videoScale = true
}
playerView.applyTemplateOptions()
```

Full option list: [Player options](player-options.md).

## Custom chrome

If you hide built-in controls (`controls = false`) or build your own UI:

- Use `KinescopePlayerStateController` / `PlayerUiState` for playback state.
- Call `playerView.syncSubtitleChromeForControls(controlsVisible)` so subtitles move with your overlay.
- Wire `onFullscreenButtonCallback`, `onPictureInPictureButtonCallback`, etc. when reusing individual behaviours.

To extend the built-in chrome instead of replacing it, see [Customization](customization.md) (`configureChrome`, `settingsMenu`, custom settings rows).

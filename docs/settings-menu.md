# Settings menu

Popup opened by the **settings (gear)** icon in [player chrome](player-chrome.md). Implemented by `KinescopeSettingsView` — nested screens with back navigation, anchored to the gear button.

```kotlin
// Gear opens the menu automatically when showOptionsButton = true
kinescopePlayer.kinescopePlayerOptions.showOptionsButton = true
playerView.applyTemplateOptions()
```

Apply changes after mutating options:

```kotlin
playerView.applyTemplateOptions()
// or
playerView.refreshPlayerChrome()
```

## Main screen

Items appear in this order (hidden if disabled or not applicable):

| # | Item | Option gate | Visible when |
|---|------|-------------|--------------|
| 1 | **Subtitles** | `showSubtitlesButton` | Video has subtitle tracks |
| 2 | **Audio** | `showAudioTracksInSettings` | Stream has **2+** audio tracks |
| 3 | **Speed** | `showPlaybackSpeedInSettings` | Always (if controls on) |
| 4 | **Quality** | `showAudioOnlyQualityInSettings` + variants | Always (if controls on) |
| 5 | **Scale** | `videoScale` | Always (if controls on) |
| 6 | **Attachments** | `showAttachments` | Video has attachments |
| 7 | **Picture in picture** | registered as action | If added to menu (optional) |

Each row shows the **current value** on the right (e.g. selected quality, active subtitle language).

## Quality

**Path:** Settings → Quality

| Choice | Description |
|--------|-------------|
| **Auto** | ABR; current height shown in the row caption |
| **Fixed variants** | From manifest (360p, 720p, 1080p, …) |
| **Audio only** | When `showAudioOnlyQualityInSettings = true` and variant exists |

The gear icon in [Player chrome — Settings gear badge](player-chrome.md) reflects playback height (HD / 4K badge).

```kotlin
kinescopePlayer.kinescopePlayerOptions.showAudioOnlyQualityInSettings = true
playerView.applyTemplateOptions()
```

## Playback speed

**Path:** Settings → Speed

Preset rates: 0.25× … 2× (including **Normal** = 1×).

```kotlin
kinescopePlayer.kinescopePlayerOptions.showPlaybackSpeedInSettings = true
```

## Scale

**Path:** Settings → Scale

Adjust video zoom from the centre of the frame (1×–5×):

| Action | Description |
|--------|-------------|
| **− 3%** | Decrease scale by 3% |
| **+ 3%** | Increase scale by 3% |
| **100%** | Reset to 1× |

The header shows the current percentage (e.g. `125%`). While this screen is open, the on-video **scale badge** is hidden (the value is shown in the menu instead).

Pinch-to-zoom and one-finger pan when zoomed are described in [Player chrome — Gestures](player-chrome.md).

```kotlin
kinescopePlayer.kinescopePlayerOptions.videoScale = true
playerView.applyTemplateOptions()
```

## Audio tracks

**Path:** Settings → Audio → submenu **Audio track**

See **Audio tracks (detail)** below for track labels and API.

```kotlin
kinescopePlayer.kinescopePlayerOptions.showAudioTracksInSettings = true
```

## Subtitles

**Path:** Settings → Subtitles/CC

| Sub-screen | Description |
|------------|-------------|
| **Track list** | Off + external tracks (VTT / SRT / TTML merged for DASH) |
| **Captions search** | Row at top of track list — opens [transcript search overlay](subtitles-and-settings.md) |
| **Options → Appearance** | Font colour/size, background colour/opacity (`SubtitleStyle`) |

```kotlin
kinescopePlayer.kinescopePlayerOptions.showSubtitlesButton = true
kinescopePlayer.setShowSubtitles(true)
playerView.applyTemplateOptions()
```

Subtitle rendering, progressive overlay, and captions search behaviour: [Subtitles](subtitles-and-settings.md).

## Attachments

**Path:** Settings → Attachments

Lists downloadable attachments from video metadata. Selection fires `KinescopePlayerView.onAttachmentSelected`.

```kotlin
kinescopePlayer.kinescopePlayerOptions.showAttachments = true
```

## Settings-only options

| Option | Default | Affects |
|--------|---------|---------|
| `showOptionsButton` | `true` | Gear icon + menu access |
| `showPlaybackSpeedInSettings` | `true` | Speed row |
| `showAudioTracksInSettings` | `true` | Audio row |
| `showAudioOnlyQualityInSettings` | `true` | Audio-only quality + quality list |
| `showSubtitlesButton` | `false` | Subtitles row (set `true` when tracks exist) |
| `showAttachments` | `false` | Attachments row |
| `videoScale` | `true` | Scale row + pinch-to-zoom on video |

Chrome icons (PiP, fullscreen, chapters, …) are documented in [Player chrome](player-chrome.md), not here.

## Custom rows

Add app-defined items with `addCustomParameter` (via [Customization](customization.md)):

```kotlin
playerView.configureChrome {
    configureSettingsMenu {
        addCustomParameter(
            id = "share",
            title = "Share",
            icon = R.drawable.ic_share,
            isAction = true,
        )
        onParameterAction = { parameter ->
            if (parameter is KinescopeSettingsView.Parameter.Custom && parameter.id == "share") {
                shareCurrentVideo()
            }
        }
    }
}
```

## Headless / custom UI

| API | Use |
|-----|-----|
| `KinescopeSettingsView` | Embed or replicate menu structure |
| `TrackController` | Quality, audio, subtitle track lists |
| `KinescopePlayerStateController` | `PlayerUiState` + `selectAudioTrack`, quality, speed |
| `onSubtitleStyleChanged` | Appearance updates |

---

## Audio tracks (detail)

When the manifest exposes **more than one** audio track (HLS `#EXT-X-MEDIA` / DASH `AdaptationSet`), the **Audio** item appears.

### Track labels

| Source | EN example | RU example |
|--------|------------|------------|
| Language tag (`ja`, `en`, `ru`) | Japanese, English | Японский, Английский |
| `label` from stream | Publisher text as-is | Same |
| No metadata | Track 1, Track 2 | Дорожка 1, Дорожка 2 |

Names use the **app UI locale** (`values` / `values-ru`).

Duplicate names are disambiguated: **Japanese · 1**, **Japanese · 2**.

### API

```kotlin
trackController?.buildAudioOptions()
trackController?.applyAudioSelection(optionId)
// or
stateController.selectAudioTrack(id)
```

---

## Chapters (related)

Chapter **picker** opens from the **chapters icon** in [player chrome](player-chrome.md), not from the settings menu. Rows are numbered (`1. …`, `2. …`). Markers on the seek bar come from `KinescopeVideo.chapters`.

```kotlin
kinescopePlayer.kinescopePlayerOptions.showChaptersButton = true
playerView.applyTemplateOptions()
```

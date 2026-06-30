# Customization

## Accent colour

The recommended way to customise the player appearance is `accentColor` in `KinescopePlayerOptions`:

```kotlin
kinescopePlayer.kinescopePlayerOptions.accentColor = "#6161FC"
playerView.applyTemplateOptions()
```

## Fine-grained colour overrides

`setColors` is available for per-control overrides:

```kotlin
playerView.setColors(
    buttonColor = resources.getColor(R.color.custom_color_res),
    progressBarColor = Color.parseColor("#228B22"),
    scrubberColor = Color.parseColor("#EC3440"),
    playedColor = Color.parseColor("#EBABCF"),
    bufferedColor = Color.YELLOW,
)
```

## Poster

```kotlin
playerView.showPoster(
    url = POSTER_URL,
    placeholder = R.drawable.placeholder,
    errorPlaceholder = R.drawable.placeholder,
    onLoadFinished = { }
)
```

Use the poster from video metadata (`KinescopeVideo.poster.url`):

```kotlin
video.poster?.url?.let { posterUrl ->
    playerView.showPoster(
        url = posterUrl,
        placeholder = R.drawable.placeholder,
        errorPlaceholder = R.drawable.placeholder,
        onLoadFinished = { }
    )
}
```

Hide poster:

```kotlin
playerView.hidePoster()
```

> The poster is hidden automatically once playback starts.

## Built-in chrome toggles

Show or hide standard controls via [KinescopePlayerOptions](player-options.md), then call `playerView.applyTemplateOptions()`.

See [Player chrome](player-chrome.md) for the full list of icons and settings rows.

## Custom control-bar buttons

### Single button (legacy)

```kotlin
playerView.showCustomButton(
    iconRes = R.drawable.custom_btn_icon,
    onClick = { }
)

playerView.hideCustomButton()
```

### Multiple buttons

Use `configureChrome` to add any number of app-defined icons to the options strip:

```kotlin
playerView.configureChrome {
    addButton(
        id = "share",
        iconRes = R.drawable.ic_share,
        contentDescription = "Share",
    ) {
        shareCurrentVideo()
    }
    addButton(
        id = "bookmark",
        iconRes = R.drawable.ic_bookmark,
        contentDescription = "Bookmark",
    ) {
        toggleBookmark()
    }
}
```

Call once after `setPlayer`. To replace buttons, build a new customization or use `clearButtons()` inside the block.

## Settings menu customization

`KinescopePlayerView.settingsMenu` exposes the built-in `KinescopeSettingsView`.

### Add a custom action row

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

`isAction = true` closes the menu and fires `onParameterAction` on tap (no submenu).

### Tune built-in rows

```kotlin
playerView.configureSettingsMenu {
    setParameterVisible(KinescopeSettingsView.Parameter.Scale, false)
    setParameterCurrentValue(
        KinescopeSettingsView.Parameter.PlaybackSpeed,
        "1.5×",
    )
}
```

Built-in parameters: `Subtitles`, `AudioTracks`, `PlaybackSpeed`, `VideoQuality`, `Scale`, `Attachments`, `PictureInPicture`.

Option visibility is still driven by [KinescopePlayerOptions](player-options.md); custom rows are additive.

## Fully custom UI

Hide built-in chrome and build your own overlay:

```kotlin
kinescopePlayer.kinescopePlayerOptions.controls = false
playerView.applyTemplateOptions()
```

Wire playback with `KinescopePlayerStateController` / `PlayerUiState`, tracks via `TrackController`, and optional `KinescopeSettingsView` in your own layout. See [Player chrome → Custom chrome](player-chrome.md).

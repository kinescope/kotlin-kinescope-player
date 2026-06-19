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

## Custom button

```kotlin
playerView.showCustomButton(
    iconRes = R.drawable.custom_btn_icon,
    onClick = { }
)
```

Hide:

```kotlin
playerView.hideCustomButton()
```

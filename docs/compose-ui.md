# Compose UI (`kotlin-kinescope-player-ui`)

Optional Jetpack Compose layer on top of the core SDK. The player engine (`KinescopeVideoPlayer`) stays headless; `KinescopePlayerScreen` renders custom controls and wires playback state via `KinescopePlayerStateController`.

## Installation

Add the Compose module in addition to the core library:

```groovy
dependencies {
    implementation 'com.github.kinescope:kotlin-kinescope-player:<LATEST_VERSION>'
    implementation 'com.github.kinescope:kotlin-kinescope-player-ui:<LATEST_VERSION>'
}
```

Requirements: **JDK 17**, `compileSdk 35`, Compose enabled in your app module.

## Minimal setup

```kotlin
setContent {
    KinescopePlayerTheme {
        KinescopePlayerScreen(
            player = kinescopePlayer,
            videoId = "your-video-id",
            fullscreen = isFullscreen,
            onFullscreenToggle = { isFullscreen = !isFullscreen },
            castController = castSession?.controller,
            onVideoLoaded = { /* restore position, autoplay, etc. */ },
        )
    }
}
```

`KinescopePlayerScreen` embeds `KinescopePlayerView` for video rendering only — built-in SDK controls are hidden. Gestures (tap to show/hide controls, double-tap ±10 s seek) are handled in Compose.

## State & ViewModel

| Type | Role |
|------|------|
| `KinescopePlayerStateController` | Bridges `KinescopeVideoPlayer` → `StateFlow<PlayerUiState>` |
| `KinescopeComposePlayerController` | Compose-friendly wrapper (`uiState`, track/quality actions) |
| `KinescopePlayerViewModel` | Optional: `SavedStateHandle` for video id, position, quality, subtitle track |

```kotlin
val viewModel: KinescopePlayerViewModel by viewModels { /* factory with playerFactory */ }
viewModel.attach()
viewModel.player.bindLifecycle(lifecycle)
```

## Theming & slots

`KinescopePlayerTheme` provides colours and optional slot composables:

```kotlin
KinescopePlayerTheme(
    theme = PlayerTheme(
        colors = PlayerThemeColors(accent = Color(0xFF6161FC)),
        slots = PlayerThemeSlots(
            centerOverlay = { /* custom overlay */ },
        ),
    ),
) { KinescopePlayerScreen(...) }
```

## Chromecast

Use `KinescopeComposeCastSession` (from the core module) and pass `castController` into `KinescopePlayerScreen`. Cast overlay and local/cast player switching are handled automatically.

## Subtitles

Progressive subtitles use the same overlay as `KinescopePlayerView`. When Compose controls are shown, call `syncSubtitleChromeForControls` via the embedded player view (done inside `KinescopePlayerScreen`) so captions move up and down smoothly with the control bar.

See [Subtitles & settings](subtitles-and-settings.md).

## Demo

In the demo app: **Compose player test** (`ComposePlayerActivity`) — fullscreen, Cast, settings, double-tap seek, mobile gradients with video title.

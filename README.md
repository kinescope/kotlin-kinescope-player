# kotlin-kinescope-player

[![JitPack](https://jitpack.io/v/kinescope/kotlin-kinescope-player.svg)](https://jitpack.io/#kinescope/kotlin-kinescope-player)

Android SDK for [Kinescope](https://kinescope.io/) video: player, vertical Shorts feed, and offline downloads with DRM (Widevine) in a single dependency.

---

## Features

| Feature | Description |
|--------|-------------|
| **Player** | `KinescopeVideoPlayer`, `KinescopePlayerView` — HLS, DASH, Live, DRM, posters, player options, fullscreen, PiP, settings (quality, speed, subtitles), analytics |
| **Chromecast** | `KinescopeCastSession` — cast to TV via Kinescope custom receiver, cast overlay, position sync on connect/disconnect |
| **Dashboard API** | `KinescopeApiHelper` — list/create/update/delete player templates via `api.kinescope.io` |
| **Shorts** | `io.kinescope.sdk.shorts` — TikTok-style vertical feed, `KinescopeVideoProvider` for your API |
| **Offline** | `DownloadVideoOffline`, `VideoDownloadService` — HLS/DASH downloads, Widevine DRM, `DownloadManager` |
| **Demo app** | Stand APK and `app` module — try player, templates, Shorts, online/offline DRM, Cast before integration |

One dependency includes the player, Shorts, and the offline pipeline; `VideoDownloadService` and required permissions are merged from the library manifest.

---

## Documentation

Full docs are split by feature in [`docs/`](docs/README.md).

| Topic | File |
|-------|------|
| **Index** | [docs/README.md](docs/README.md) |
| Installation | [docs/installation.md](docs/installation.md) |
| Quick start | [docs/quick-start.md](docs/quick-start.md) |
| Player options | [docs/player-options.md](docs/player-options.md) |
| Customization | [docs/customization.md](docs/customization.md) |
| Live | [docs/live.md](docs/live.md) |
| Fullscreen | [docs/fullscreen.md](docs/fullscreen.md) |
| Picture-in-Picture | [docs/picture-in-picture.md](docs/picture-in-picture.md) |
| Chromecast | [docs/chromecast.md](docs/chromecast.md) |
| Subtitles & settings | [docs/subtitles-and-settings.md](docs/subtitles-and-settings.md) |
| Analytics | [docs/analytics.md](docs/analytics.md) |
| Dashboard API | [docs/dashboard-api.md](docs/dashboard-api.md) |
| Offline downloads | [docs/offline-downloads.md](docs/offline-downloads.md) |
| Shorts — usage guide | [kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md](kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md) |
| Shorts — quick start | [kotlin-kinescope-shorts/QUICK_START.md](kotlin-kinescope-shorts/QUICK_START.md) |
| Kinescope API | [kotlin-kinescope-shorts/API_USAGE_GUIDE.md](kotlin-kinescope-shorts/API_USAGE_GUIDE.md) |
| API troubleshooting | [kotlin-kinescope-shorts/API_TROUBLESHOOTING.md](kotlin-kinescope-shorts/API_TROUBLESHOOTING.md) |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |

---

## Quick start

```groovy
dependencies {
    implementation 'com.github.kinescope:kotlin-kinescope-player:<LATEST_VERSION>'
}
```

```kotlin
val kinescopePlayer = KinescopeVideoPlayer(context, KinescopePlayerOptions())
playerView.setPlayer(kinescopePlayer)
playerView.applyTemplateOptions()
kinescopePlayer.loadVideo(videoId, onSuccess = { kinescopePlayer.play() })
```

See [docs/quick-start.md](docs/quick-start.md) for the full setup.

---

## Demo app

A **stand/demo APK** is available to explore the SDK before integration:

| Screen | What it shows |
|--------|----------------|
| **Playlist test** | Player with a video list, Cast, PiP, fullscreen |
| **Subtitles test** | Subtitles playback and appearance settings |
| **DRM viewing** | Online Widevine-protected playback |
| **Custom UI test** | Player without built-in controls |
| **Custom Player test** | Player options, Dashboard API, player templates |
| **Live test** | Live stream mode |
| **Shorts** | Vertical feed |
| **Offline viewing** | DRM offline downloads and playback |

Set your API key in [`KinescopeDemoConfig`](app/src/main/java/io/kinescope/demo/KinescopeDemoConfig.kt):

```kotlin
object KinescopeDemoConfig {
    const val API_KEY = "your-api-key"
}
```

Build:

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

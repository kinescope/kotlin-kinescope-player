# Demo app

The repository includes an **`app`** module — a stand-alone demo APK to explore the SDK before integrating it into your project. Each screen focuses on one feature or integration pattern.

The demo is **not** published to JitPack; clone the repo and build locally.

## Configuration

**1.** Set your Kinescope API key and sample video IDs in [`KinescopeDemoConfig`](../app/src/main/java/io/kinescope/demo/KinescopeDemoConfig.kt):

```kotlin
object KinescopeDemoConfig {
    const val API_KEY = "your-api-key"
    const val DEFAULT_VIDEO_ID = "..."      // Custom UI, Custom Player
    const val DRM_VIDEO_ID = "..."          // DRM viewing
    const val SUBTITLES_VIDEO_ID = "..."    // Subtitles test
    const val DEFAULT_LIVE_ID = "..."       // Live test (empty input fallback)
}
```

The API key is also appended to Widevine license URLs for offline downloads.

## Build and install

Requires **JDK 17** (same as the SDK).

```bash
./gradlew :app:assembleDebug
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`

Release build (optional):

```bash
./gradlew :app:assembleRelease
```

Install on a connected device:

```bash
./gradlew :app:installDebug
```

## Launcher screens

[`MainActivity`](../app/src/main/java/io/kinescope/demo/MainActivity.kt) opens the menu with these entries:

| Screen | Activity | What it demonstrates | Related docs |
|--------|----------|----------------------|--------------|
| **Playlist test** | `PlaylistActivity` | Video list from Dashboard API, `KinescopePlayerView` with template options, fullscreen, PiP, Chromecast | [quick-start.md](quick-start.md), [chromecast.md](chromecast.md), [picture-in-picture.md](picture-in-picture.md) |
| **Subtitles test** | `SubtitlesActivity` | Subtitle tracks, appearance settings, progressive line-by-line reveal, fullscreen, PiP | [subtitles-and-settings.md](subtitles-and-settings.md) |
| **DRM viewing** | `DrmViewingActivity` | Online Widevine-protected VOD playback | [offline-downloads.md](offline-downloads.md) |
| **Custom UI test** | `CustomUIActivity` | `KinescopePlayerView` with built-in controls hidden — wire your own buttons | [customization.md](customization.md) |
| **Custom Player test** | `CustomPlayerActivity` | `KinescopePlayerOptions` toggles, quality/preload, Dashboard API — list/create/update/delete player templates | [player-options.md](player-options.md), [dashboard-api.md](dashboard-api.md) |
| **Live test** | `LiveActivity` | Live stream playback and live-specific UI | [live.md](live.md) |
| **Shorts** | `ShortsActivity` | Vertical feed (`ViewPager2`), preloading, optional offline download from feed | [../kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md](../kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md) |
| **Offline viewing** | `OfflineDrmDemoActivity` | HLS download with Widevine offline license, download list, offline playback | [offline-downloads.md](offline-downloads.md) |

## Module layout

```
app/src/main/java/io/kinescope/demo/
├── MainActivity.kt              # launcher menu
├── KinescopeDemoConfig.kt       # API key
├── application/                 # Application + KinescopeApiHelper
├── playlist/                    # playlist + Cast + PiP
├── subtitles/
├── drm/
├── customui/
├── customplayer/                # player options + Dashboard API
├── live/
├── shorts/
└── offlinedrm/                  # offline downloads
```

The demo depends on local project modules (`:kotlin-kinescope-player`, `:kotlin-kinescope-shorts`), not on JitPack artifacts.

## Related docs

- [Installation](installation.md) — add the SDK to your own app
- [Quick start](quick-start.md) — minimal player integration

# Demo app

The repository includes an **`app`** module — a stand-alone demo APK to explore the SDK before integrating it into your project. Each screen focuses on one feature or integration pattern.

The demo is **not** published to JitPack; clone the repo and build locally.

## Configuration

**1.** Set your Kinescope API key in [`KinescopeDemoConfig`](../app/src/main/java/io/kinescope/demo/KinescopeDemoConfig.kt):

```kotlin
object KinescopeDemoConfig {
    const val API_KEY = "your-api-key"
}
```

The key is used by [`KinescopeSDKDemoApplication`](../app/src/main/java/io/kinescope/demo/application/KinescopeSDKDemoApplication.kt) to create `KinescopeApiHelper` for Dashboard API screens (playlist, custom player templates).

**2.** Some screens use hard-coded demo video IDs from the Kinescope sample project. If playback fails, replace them with video IDs from your own account.

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
| **Compose player** | `ComposePlayerActivity` | Jetpack Compose UI (`KinescopePlayerScreen`), Cast, settings, double-tap seek, fullscreen | [compose-ui.md](compose-ui.md) |

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
├── offlinedrm/                  # offline downloads
└── compose/                     # Compose player screen
```

The demo depends on local project modules (`:kotlin-kinescope-player`, `:kotlin-kinescope-player-ui`, `:kotlin-kinescope-shorts`), not on JitPack artifacts.

## Related docs

- [Installation](installation.md) — add the SDK to your own app
- [Quick start](quick-start.md) — minimal player integration
- [Compose UI](compose-ui.md) — Jetpack Compose controls

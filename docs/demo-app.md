# Demo app

The repository includes an **`app`** module — a stand-alone demo APK to explore the SDK before integrating it into your project. Each screen focuses on one feature or integration pattern.

The demo is **not** published to Maven Central; clone the repo and build locally.

## Configuration

**1.** Set your Kinescope API key and sample video IDs in [`KinescopeDemoConfig`](../app/src/main/java/io/kinescope/demo/KinescopeDemoConfig.kt):

```kotlin
object KinescopeDemoConfig {
    const val API_KEY = "your-api-key"

    // Shorts feed filters (passed to GET /v1/videos)
    val PROJECT_ID: String? = null          // null = all projects in the token workspace
    val FOLDER_ID: String? = null           // null = any folder
    const val SHORTS_FEED_LIMIT = 50        // max videos in the Shorts feed (1–100)

    const val DEFAULT_VIDEO_ID = "..."      // Custom Player
    const val DRM_VIDEO_ID = "..."          // DRM viewing
    const val SUBTITLES_VIDEO_ID = "..."    // Subtitles test
    const val CHAPTERS_VIDEO_ID = "..."     // Chapters test (video with chapters in metadata)
    const val DEFAULT_LIVE_ID = "..."       // Live test (empty input fallback)
}
```

The API key is also appended to Widevine license URLs for offline downloads.

**Client / production Shorts** use SDK [`KinescopeShortsConfig`](../kotlin-kinescope-shorts/library/src/main/java/io/kinescope/sdk/shorts/KinescopeShortsConfig.kt) (`io.kinescope.sdk.shorts`) — not this demo object.

For Shorts **in this demo APK only**, [`ShortsActivity`](../app/src/main/java/io/kinescope/demo/shorts/ShortsActivity.kt) uses [`DemoKinescopeVideoProvider`](../app/src/main/java/io/kinescope/demo/shorts/DemoKinescopeVideoProvider.kt) + `KinescopeDemoConfig`:

- Catalog: `GET /v1/videos?project_id=&folder_id=&per_page=` via `KinescopeApiHelper.getAllVideos(...)`
- Playback: catalog `hls_link` when present, otherwise `{id}.json` (fetched in parallel)
- UI starts as soon as the first video is ready (`loadVideosProgressive`); the rest append into the pager
- Preload depth follows adaptive health tiers (see [LIBRARY_USAGE_GUIDE — Preload, pool, and cache](../kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md#preload-pool-and-cache))
- Tune `PROJECT_ID`, `FOLDER_ID`, and `SHORTS_FEED_LIMIT` in `KinescopeDemoConfig`

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
| **Subtitles test** | `SubtitlesActivity` | Subtitles, captions search, appearance | [subtitles-and-settings.md](subtitles-and-settings.md) |
| **Chapters test** | `ChaptersActivity` | Chapters icon + seek markers; subtitles on (`setShowSubtitles(true)`) | [player-chrome.md](player-chrome.md) · [subtitles-and-settings.md](subtitles-and-settings.md) · [settings-menu.md — Chapters](settings-menu.md) |
| **DRM viewing** | `DrmViewingActivity` | Online Widevine-protected VOD playback | [offline-downloads.md](offline-downloads.md) |
| **Custom Player test** | `CustomPlayerActivity` | `KinescopePlayerOptions` toggles, quality/preload, Dashboard API — list/create/update/delete player templates | [player-options.md](player-options.md), [dashboard-api.md](dashboard-api.md) |
| **Live test** | `LiveActivity` | Live stream playback and live-specific UI | [live.md](live.md) |
| **Shorts** | `ShortsActivity` | Vertical feed (`ViewPager2`), progressive provider-backed load via `DemoKinescopeVideoProvider` (`PROJECT_ID` / `FOLDER_ID` / `SHORTS_FEED_LIMIT`) | [../kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md](../kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md), [../kotlin-kinescope-shorts/API_USAGE_GUIDE.md](../kotlin-kinescope-shorts/API_USAGE_GUIDE.md) |
| **Offline viewing** | `OfflineDrmDemoActivity` | HLS download with Widevine offline license, download list, offline playback | [offline-downloads.md](offline-downloads.md) |

## Module layout

```
app/src/main/java/io/kinescope/demo/
├── MainActivity.kt              # launcher menu
├── KinescopeDemoConfig.kt       # API key, Shorts filters/limit, video IDs
├── application/                 # Application + KinescopeApiHelper
├── playlist/                    # playlist + Cast + PiP
├── subtitles/
├── chapters/                    # chapters menu + seek markers
├── drm/
├── customplayer/                # player options + Dashboard API
├── live/
├── shorts/                      # ShortsActivity + DemoKinescopeVideoProvider
└── offlinedrm/                  # offline downloads
```

The demo depends on local project modules (`:kotlin-kinescope-player`, `:kotlin-kinescope-shorts`), not on Maven Central artifacts.

## Related docs

- [Installation](installation.md) — add the SDK to your own app
- [Quick start](quick-start.md) — minimal player integration

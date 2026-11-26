# Features

| Feature | Description |
|--------|-------------|
| **Player** | `KinescopeVideoPlayer`, `KinescopePlayerView` — HLS, DASH, Live, DRM (+ `drmAuthToken` for Authorization Backend), [local/progressive URI](local-playback.md) via `setLocalSource`, [player chrome](player-chrome.md) (seek bar, PiP, chapters, cast, fullscreen, pinch-to-zoom), [settings menu](settings-menu.md) (quality, speed, scale, audio, subtitles), captions search, progressive subtitles, HD/4K gear badge, [fullscreen on rotate](fullscreen.md), [analytics](analytics.md) with viewer ID (`externalId`), [server watermark](customization.md) |
| **Chromecast** | `KinescopeCastSession` — cast to TV via Kinescope custom receiver, cast overlay, position sync on connect/disconnect |
| **Android TV** | Cast **to** Android TV / Google Cast receivers (e.g. NVIDIA SHIELD); phone/tablet is the sender — see [android-tv.md](android-tv.md) |
| **Dashboard API** | `KinescopeApiHelper` — video catalog (`page` / `per_page` / `project_id` / `folder_id`) and list/create/update/delete player templates via `api.kinescope.io` |
| **Shorts** | Separate artifact `kotlin-kinescope-shorts`, `io.kinescope.sdk.shorts` — TikTok-style vertical feed, `KinescopeShortsConfig`, `KinescopeUiConfig` (actions / play / scrub / preload stub), player pool + adaptive preload/cache, `KinescopeVideoProvider` for your Feed API; **captions/subtitles off by default** (`PlayerFactory` disables `TRACK_TYPE_TEXT`) |
| **Offline** | Player artifact only: `io.kinescope.sdk.download` — `DownloadVideoOffline`, `VideoDownloadManager`, `VideoDownloadService`, `DrmHelper<T>` / `DrmConfigurator` — HLS/DASH downloads, Widevine DRM, [quality selection](offline-downloads.md) |
| **Demo app** | Stand APK and `app` module — try player, templates, Shorts, online/offline DRM, Cast before integration |

Player and Shorts are two separate artifacts — see [installation.md](installation.md). The player artifact includes the offline pipeline; `VideoDownloadService` and its permissions are merged from the player manifest. Shorts has no offline downloads.

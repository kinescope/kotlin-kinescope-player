# Features

| Feature | Description |
|--------|-------------|
| **Player** | `KinescopeVideoPlayer`, `KinescopePlayerView` — HLS, DASH, Live, DRM (+ `drmAuthToken` for Authorization Backend), [local/progressive URI](local-playback.md) via `setLocalSource`, [player chrome](player-chrome.md) (seek bar, PiP, chapters, cast, fullscreen, pinch-to-zoom), [settings menu](settings-menu.md) (quality, speed, scale, audio, subtitles), captions search, progressive subtitles, HD/4K gear badge, analytics |
| **Chromecast** | `KinescopeCastSession` — cast to TV via Kinescope custom receiver, cast overlay, position sync on connect/disconnect |
| **Dashboard API** | `KinescopeApiHelper` — video catalog (`page` / `per_page` / `project_id` / `folder_id`) and list/create/update/delete player templates via `api.kinescope.io` |
| **Shorts** | `io.kinescope.sdk.shorts` — TikTok-style vertical feed, `KinescopeShortsConfig`, `KinescopeUiConfig` (actions / play / scrub / preload stub), player pool + adaptive preload/cache, `KinescopeVideoProvider` for your Feed API; **captions/subtitles off by default** (`PlayerFactory` disables `TRACK_TYPE_TEXT`) |
| **Offline** | `DownloadVideoOffline`, `VideoDownloadService` — HLS/DASH downloads, Widevine DRM, `DownloadManager` |
| **Demo app** | Stand APK and `app` module — try player, templates, Shorts, online/offline DRM, Cast before integration |

One dependency includes the player, Shorts, and the offline pipeline; `VideoDownloadService` and required permissions are merged from the library manifest.

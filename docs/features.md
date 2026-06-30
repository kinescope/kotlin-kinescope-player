# Features

| Feature | Description |
|--------|-------------|
<<<<<<< HEAD
| **Player** | `KinescopeVideoPlayer`, `KinescopePlayerView` — HLS, DASH, Live, DRM, posters, player options, fullscreen, PiP, settings (quality, speed, subtitles), progressive line-by-line subtitles, analytics |
=======
| **Player** | `KinescopeVideoPlayer`, `KinescopePlayerView` — HLS, DASH, Live, DRM, [player chrome](player-chrome.md) (seek bar, PiP, chapters, cast, fullscreen, pinch-to-zoom), [settings menu](settings-menu.md) (quality, speed, scale, audio, subtitles), captions search, progressive subtitles, HD/4K gear badge, analytics |
>>>>>>> 9608062 (release 0.1.0)
| **Chromecast** | `KinescopeCastSession` — cast to TV via Kinescope custom receiver, cast overlay, position sync on connect/disconnect |
| **Dashboard API** | `KinescopeApiHelper` — list/create/update/delete player templates via `api.kinescope.io` |
| **Shorts** | `io.kinescope.sdk.shorts` — TikTok-style vertical feed, `KinescopeVideoProvider` for your API |
| **Offline** | `DownloadVideoOffline`, `VideoDownloadService` — HLS/DASH downloads, Widevine DRM, `DownloadManager` |
| **Demo app** | Stand APK and `app` module — try player, templates, Shorts, online/offline DRM, Cast before integration |

One dependency includes the player, Shorts, and the offline pipeline; `VideoDownloadService` and required permissions are merged from the library manifest.

# Features

| Feature | Description |
|--------|-------------|
| **Player** | `KinescopeVideoPlayer`, `KinescopePlayerView` — HLS, DASH, Live, DRM, posters, player options, fullscreen, PiP, settings (quality, speed, subtitles), progressive line-by-line subtitles, analytics |
| **Chromecast** | `KinescopeCastSession` — cast to TV via Kinescope custom receiver, cast overlay, position sync on connect/disconnect |
| **Dashboard API** | `KinescopeApiHelper` — list/create/update/delete player templates via `api.kinescope.io` |
| **Shorts** | `io.kinescope.sdk.shorts` — TikTok-style vertical feed, `KinescopeVideoProvider` for your API |
| **Offline** | `DownloadVideoOffline`, `VideoDownloadService` — HLS/DASH downloads, Widevine DRM, `DownloadManager` |
| **Demo app** | Stand APK and `app` module — try player, templates, Shorts, online/offline DRM, Cast before integration |

One dependency includes the player, Shorts, and the offline pipeline; `VideoDownloadService` and required permissions are merged from the library manifest.

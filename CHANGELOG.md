# Changelog

## [0.0.6] — (release date)

### Added

- **Unified library**: One dependency `com.github.kinescope:kotlin-kinescope-player` now includes the **Player**, **Shorts** (`com.kotlin.kinescope.shorts`), and **Offline** pipeline. No separate Shorts artifact needed.
- **DownloadVideoOffline** (`io.kinescope.sdk.download.DownloadVideoOffline`): Entry point for offline downloads — `initialize`, `startDownload`, `removeDownload`, `getAllCompletedDownloads`, `getDownloadById`, `getDownloadCache`, `getDownloadManager`, `getDownloadProgress`, `addDownloadListener`, `removeDownloadListener`. `VideoDownloadService` and required permissions are declared in the library manifest.
- **Offline DRM (Widevine)**: Download and playback of DRM-protected HLS/DASH with `keySetId`; `DrmConfigurator` and `DrmHelper` for PSSH and offline license. Documented support for DASH (`MimeTypes.APPLICATION_MPD`).
- **Shorts**: TikTok-style vertical feed, `ViewPager2`, `KinescopeVideoProvider`, `ActivityProvider`, offline download UI, `VideoDownloadManager`, `NotificationHelper` — all available via the main player dependency.

### Changed

- **Documentation**:
  - README: Features table, Installation, Quick start, Offline (DownloadVideoOffline), Shorts, and Documentation links.
  - All guides and KDoc translated to **English**: `LIBRARY_USAGE_GUIDE.md`, `QUICK_START.md`, `API_USAGE_GUIDE.md`, `API_TROUBLESHOOTING.md`, `library/README_API.md`, `DownloadVideoOffline` KDoc.
- **DownloadVideoOffline KDoc**: Examples for HLS, DASH, and Widevine DRM; note that `OfflinePlayer` supports HLS only; DASH playback via `DashMediaSource` and the same cache.

### Fixed

- Corrected duplicate step numbering in Player Quick start (Installation / README).

---

## [0.0.5]

- Base player: `KinescopeVideoPlayer`, `KinescopePlayerView`, HLS, DASH, Live, posters, analytics, fullscreen.
- Shorts and offline were separate or in development.

---

*Replace `(release date)` with the actual date (e.g. `2025-01-15`) when creating the GitHub Release.*

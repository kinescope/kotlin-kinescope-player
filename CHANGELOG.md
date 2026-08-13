# Changelog

## [Unreleased]

## [0.1.4] — 13.08.2026

### Added
- **Offline download quality picker** — before caching, choose a single HLS/DASH quality (`DownloadVideoOffline.listDownloadQualities` / `startDownloadWithQuality`)
- Quality labels from embed `quality_map.name` in settings and the download picker
- **`KinescopeContentOrientationController`** — screen orientation follows video aspect (portrait stays portrait, including fullscreen) for any `KinescopePlayerView`

### Changed
- Offline download cache uses `NoOpCacheEvictor` (Media3 requirement) instead of a 300 MB LRU limit
- Offline quality selection uses an explicit `TrackSelectionOverride` / filtered HLS master so the chosen height is what gets cached
- Demo activities use content-aware orientation instead of always forcing landscape in fullscreen
- Fullscreen captions sit lower (just above the control bar) when the control overlay is shown
- Inline (non-fullscreen) captions also sit just above the control bar when chrome is shown
- Paused playback no longer keeps top/bottom gradient overlays stuck after a tap to dismiss chrome
- Offline fullscreen no longer flashes the play icon after `switchTargetView` (removed premature `setPlayer`)

### Fixed
- **Decoder / playback after re-download** — probing qualities (or downloading the same video again) no longer leaves the OEM secure AVC decoder stuck (`c2.qti.avc.decoder.secure`); offline playback uses the secure-decoder workaround + fallback so the video plays after download
- Download progress no longer rolls back mid-download when the cache hit the old 300 MB LRU cap
- Intermittent offline Source error from holey completed downloads after LRU eviction; offline `CacheDataSource` no longer sets `FLAG_IGNORE_CACHE_ON_ERROR` with a null upstream

## [0.1.3] — 30.07.2026

### Added
- **`setLocalSource(uri, autoplay)`** — play a local/progressive URI (`content://`, `file://`, progressive HTTP) through `KinescopeVideoPlayer` without a Kinescope media id; see [docs/local-playback.md](docs/local-playback.md)

## [0.1.2] — 23.07.2026

### Added
- **Tap captions to search** — tapping the on-video caption box opens Captions search
- **Fullscreen Captions search** — panel fills the player height; dark backdrop stays full-bleed while search field and list use side insets

### Changed
- Fullscreen captions sit slightly lower and use wider side margins so the caption box is not edge-to-edge
- Inline (non-fullscreen) captions sit slightly closer to the bottom edge
- Fullscreen caption text size is slightly smaller relative to player height

### Fixed
- Subtitles settings menu no longer clips the last language when the Captions search row is shown (one language was hidden with a single track; with two languages only one appeared)
- Captions no longer flash/flicker when the control overlay appears (progressive overlay no longer briefly doubles onto `SubtitleView`)

## [0.1.1] — 14.07.2026

### Added (Live)
- **Live informer** — bottom-left badge while a scheduled broadcast has not started yet; replaces the buffering spinner and shows the start time plus a countdown title (`Прямой эфир через N мин` / hours / days)
- **`showLiveStartDate(startDate)`** — display the scheduled start from `KinescopeVideo.live.startsAt`, `hideLiveStartDate()` to dismiss
- **`showLiveAwaitingCover()`** — default awaiting poster before the stream starts; opt out with `KinescopePlayerOptions.showLiveAwaitingCover = false`
- **Live badge layout** — horizontal gap between the **Live** / **В эфире** badge and the seek bar so labels no longer overlap

### Fixed
- Playback stall toast no longer appears while waiting for a live broadcast to start
- Live informer switches to **В ожидании эфира** when one second or less remains before the scheduled start

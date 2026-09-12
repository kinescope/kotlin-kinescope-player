# Changelog

## [0.1.7] — 11.09.2026
- **Maven Central** — canonical coordinates: `io.kinescope:kotlin-kinescope-player:0.1.6`; **JitPack is no longer supported** for new integrations (legacy `com.github.kinescope:...` pins remain documented so existing apps are not forced to break)


### Fixed
- **Demo Shorts progressive feed** — failed catalog items no longer stall ordered emission; later videos still reach the pager
- **Demo catalog filters** — `PROJECT_ID` / `FOLDER_ID` default to `null`; blank strings are stripped before Dashboard `getAllVideos`
- **Demo Shorts DRM** — catalog HLS + description no longer skips playback JSON (Widevine license preserved)
- **Captions search insets** — top search panel refreshes when status-bar / safe-area overlap changes
- **Shorts sample feed** — Dashboard/playback OkHttp work runs on `Dispatchers.IO`; Widevine `token=` license URLs append the API key
- **Shorts onLowMemory** — no longer `VideoCache.release()` while players are reading the cache (avoids mid-playback stalls)

## [0.1.6] — 04.09.2026

### Distribution
- **Maven Central** — canonical coordinates: `io.kinescope:kotlin-kinescope-player:0.1.6`; **JitPack is no longer supported** for new integrations (legacy `com.github.kinescope:...` pins remain documented so existing apps are not forced to break)

### Added
- **Shorts description** — `description` from catalog/playback is bound under the title (18 Medium / 14 Regular, Figma Shorts)
- **Shorts posters** — `VideoData.posterUrl` is now rendered in the Shorts thumbnail overlay until the first playback frame arrives
- **Shorts API-only sample flow** — hardcoded Shorts videos were removed from the SDK/demo path; demo Shorts screens now load via `KinescopeVideoProvider`
- **Shorts feed filters** — `project_id` / `folder_id` / `per_page` on `KinescopeApiHelper.getAllVideos(...)`; demo config: `PROJECT_ID`, `FOLDER_ID`, `SHORTS_FEED_LIMIT` (default `50`)
- **Configurable feed size** — `KinescopeUrls(limit = …)` (default `50`)
- **`KinescopeShortsConfig`** — public runtime config in the Shorts SDK (`API_KEY`, `PROJECT_ID`, `FOLDER_ID`, `FEED_LIMIT`) for host apps; sample Dashboard provider stays in `kotlin-kinescope-shorts/app` only (not in the AAR)
- **`KinescopeUiConfig`** — show/hide Shorts side actions, play overlay, scrub/timeline, and preload stub; swap icons (`*IconResId`), sizes (`actionButtonSizeDp` / `playButtonSizeDp`), seek bar colours, and custom stub (`preloadImageResId`)
- **Adaptive Shorts preload** — `PlaybackHealthMonitor` + `PreloadHealth` tiers (`CRITICAL` / `LOW` / `MEDIUM` / `HIGH`) from buffer ahead, bandwidth estimate, Wi‑Fi/metered network, and weak-device caps; depth of `+1` player preload and segment prefetch scales with health

### Changed
- **Shorts scrub UX** — timeline visible on pause / while scrubbing; seek only after hold+drag (tap alone does not seek); time chip follows progress while scrubbing
- **Shorts preload / pooling** — visible pages now reuse the player pool; `+1` prepared player is handed off to the bound page instead of being recreated
- **Shorts cache warmup** — `+2` (and one back item on stronger devices) now warms `VideoCache` by fetching HLS playlists / first segments without creating a second player
- **Shorts current-playback priority** — non-essential preload pauses while the current page rebuffers or while the feed is actively scrolling/flinging, then resumes with debounce
- **Shorts lifecycle** — backgrounding only pauses playback (no `releaseAll` / `clearMediaItems`); resume restores the current page
- **Demo Shorts startup** — progressive feed (`loadVideosProgressive`): pager opens with the first ready video; remaining items append while playback metadata loads in parallel (catalog `hls_link` preferred when present; playback JSON fetched when description is missing)
- **Dashboard catalog client** — `getAllVideos` can filter by project/folder instead of only client-side filtering after the first unfiltered page; `KinescopeVideoApi` includes optional `subtitle` / `description`
- **Shorts layout resource** — renamed to `shorts_*` layouts/drawables/colors so demo/player resources cannot override Shorts UI; removed leftover Shorts assets from the main player module and demo (`custom_player_control_view`, old `list_video` / `activity_save_video_player`)

### Fixed
- **Player settings gear (non-HD)** — restored a valid `evenOdd` vector so the gear no longer renders with a missing quadrant below 1080p
- **Shorts reopen playback** — `VideoCache.release()` no longer leaves a released cache that blocked playback on the next Shorts open; pool shutdown clears players cleanly
- **Shorts domain-restriction toast** — HTTP 403 / Dashboard `4030403` («forbidden») shows a domain-restriction message instead of a generic Source error


## [0.1.5] — 17.08.2026

### Added
- **`drmAuthToken`** — `KinescopePlayerOptions.drmAuthToken` passes `drmauthtoken` on `{videoId}.json` (same as web embed / RN), enabling Authorization Backend with DRM-protected videos
- **`showDefaultPoster`** — opt out of the built-in `default_poster` fallback when metadata has no poster URL (default remains `true`)

### Notes
- Live awaiting cover was already controllable via `showLiveAwaitingCover` (default `true`)
- **Domain restrictions** — when the video is limited to specific domains, call `kinescopePlayer.setReferer("https://your-domain.com/")` before `loadVideo` so the app’s `Referer` matches the allow list (default is `https://kinescope.io/`). This does not open embedding on other sites; see [player-options.md](docs/player-options.md)

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

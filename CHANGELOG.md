# Changelog

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

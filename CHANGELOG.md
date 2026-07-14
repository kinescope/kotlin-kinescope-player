# Changelog
## [0.1.1] — 14.07.2026

### Added (Live)
- **Live informer** — bottom-left badge while a scheduled broadcast has not started yet; replaces the buffering spinner and shows the start time plus a countdown title (`Прямой эфир через N мин` / hours / days)
- **`showLiveStartDate(startDate)`** — display the scheduled start from `KinescopeVideo.live.startsAt`, `hideLiveStartDate()` to dismiss
- **`showLiveAwaitingCover()`** — default awaiting poster before the stream starts; opt out with `KinescopePlayerOptions.showLiveAwaitingCover = false`
- **Live badge layout** — horizontal gap between the **Live** / **В эфире** badge and the seek bar so labels no longer overlap

### Fixed
- Playback stall toast no longer appears while waiting for a live broadcast to start
- Live informer switches to **В ожидании эфира** when one second or less remains before the scheduled start


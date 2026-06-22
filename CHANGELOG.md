# Changelog

## [0.0.9] — 22.06.2026

### Playback & reliability
- **Lifecycle** — `bindLifecycle()` pauses on `onStop`, resumes on `onStart`; skipped while PiP is active; `release()` on `onDestroy`
- **Subtitles** — external tracks (VTT, TTML, SRT) merged into DASH via `MergingMediaSource`; runtime track switching; progressive **line-by-line** reveal with two-line layout; caption box with 12 dp side margins; smooth vertical offset when controls show/hide; first track auto-enabled when `showSubtitlesButton` is on
- **Chromecast** — manifest + DRM license sent to Kinescope receiver; position sync on connect/disconnect; local player paused while casting; overlay controls (play/pause, seek, stop)
- **View switching** — `KinescopePlayerHost` for swapping active `Player` (local ↔ cast); `switchTargetView` for fullscreen without losing state

### Added (UI & integration)
- **Compose UI module** (`kotlin-kinescope-player-ui`) — `KinescopePlayerScreen`, `KinescopePlayerTheme`, `KinescopeComposePlayerController`, `KinescopePlayerViewModel`, double-tap seek overlay, mobile header/footer gradients
- **State bridge** — `KinescopePlayerStateController`, `PlayerUiState` (`StateFlow`) for headless/Compose integrations
- **Background audio** — `KinescopePlaybackService` (Media3 session)
- **Settings menu** — nested UI: quality, playback speed, subtitle tracks, subtitle appearance (`SubtitleStyle`)
- **Dependencies** — `media3-cast`, Play Services Cast Framework; `KinescopeCastOptionsProvider` merged from library manifest


### Changed
- **Player options** — new chrome flags: `showCastButton`, `showSubtitlesButton`, `showFullscreenButton`, `showOptionsButton`, `showSeekBar`, `showDuration`, `showPlayPauseButton`, `showPlaybackSpeedInSettings`, `showAudioOnlyQualityInSettings`
- **Quality manager** — improved variant labelling and auto-quality caption in settings

### Fixed
- PiP aspect ratio accounts for video rotation; PiP remote actions stay in sync with playback state

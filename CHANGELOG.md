# Changelog
## [0.1.0] — 30.06.2026

### Playback & reliability
- **Lifecycle** — `bindLifecycle()` pauses on `onStop`, resumes on `onStart`; skipped while PiP is active; `release()` on `onDestroy`
- **Subtitles** — external tracks (VTT, TTML, SRT) merged into DASH via `MergingMediaSource`; runtime track switching; progressive **line-by-line** reveal with two-line layout; caption box with 12 dp side margins; smooth vertical offset when controls show/hide; first track auto-enabled when `showSubtitlesButton` is on
- **Chromecast** — manifest + DRM license sent to Kinescope receiver; position sync on connect/disconnect; local player paused while casting; overlay controls (play/pause, seek, stop)
- **View switching** — `KinescopePlayerHost` for swapping active `Player` (local ↔ cast); `switchTargetView` for fullscreen without losing state

### Added (UI & integration)
- **State bridge** — `KinescopePlayerStateController`, `PlayerUiState` (`StateFlow`) for headless integrations
- **Background audio** — `KinescopePlaybackService` (Media3 session)
- **Settings menu** — nested UI: quality, playback speed, subtitle tracks, subtitle appearance (`SubtitleStyle`)
- **Dependencies** — `media3-cast`, Play Services Cast Framework; `KinescopeCastOptionsProvider` merged from library manifest
- **PiP** — `KinescopePlayerView(context, useTextureSurface = true)` uses `TextureView` instead of `SurfaceView` for smoother Picture-in-Picture transitions on some devices; opt-in only, non-DRM playback
- **Captions search** — in the subtitles settings submenu: search the VTT/SRT transcript, highlight matches, seek on row tap; playback position sync with a left indicator bar; on-video subtitles are hidden while search is open
- **Chapters** — chapters button and menu in player chrome when video metadata includes chapters; chapter markers on the seek bar; demo **Chapters test** screen
- **Video scale** — pinch-to-zoom (1×–5×) from the centre, one-finger pan when zoomed, top **scale badge** (tap to reset), **Settings → Scale** (−3% / +3% / 100%)
- **Player chrome customization** — `configureChrome { }` for multiple app-defined control-bar buttons; `KinescopePlayerView.settingsMenu` and `configureSettingsMenu { }`; `KinescopeSettingsView.addCustomParameter()` / `Parameter.Custom` for custom settings rows; legacy `showCustomButton()` / `hideCustomButton()` kept for a single extra button
- **Player options** — new chrome flags: `showCastButton`, `showSubtitlesButton`, `showFullscreenButton`, `showOptionsButton`, `showSeekBar`, `showDuration`, `showPlayPauseButton`, `showPlaybackSpeedInSettings`, `showAudioOnlyQualityInSettings`
- **Quality manager** — improved variant labelling and auto-quality caption in settings


### Fixed
- PiP aspect ratio accounts for video rotation; PiP remote actions stay in sync with playback state

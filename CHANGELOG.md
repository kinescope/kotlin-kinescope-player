# Changelog


## [0.0.7] — 05.06.2026

### Added
- `KinescopePlayerOptions` — unified player configuration (autoplay, muted, loop, controls, quality, PiP, accent color, etc.)
- `applyTemplateOptions()`, `applyPlaybackOptions()`, `refreshPlayerChrome()` — apply settings to player UI and ExoPlayer
- Dashboard REST API: player template CRUD via `KinescopeApiHelper` (`GET/POST/PUT/DELETE /v1/players`)
- `KinescopeApiConfig.createApiHelper(apiKey)` — API client initialization
- Picture-in-Picture: `KinescopePictureInPicture`, PiP button, `onPictureInPictureButtonCallback`
- API error helpers: `readApiErrorMessage()`, `isDashboardPlayerDeleteRestriction()`
- Demo: stand APK / `app` module to explore player, templates, Shorts, and offline DRM

### Changed
- **Player UI refresh** —, updated controls, seekbar, and settings menu
- `accentColor` in options is the recommended way to customize appearance instead of manual `setColors()`


### Deprecated
- `setColors()` and `showCustomButton()` still work, but `KinescopePlayerOptions` + `applyTemplateOptions()` is recommended for the new UI


### Notes
- The Android SDK currently supports only the player options documented in `README.md`. Additional dashboard template settings not yet implemented in the SDK are ignored on the device.

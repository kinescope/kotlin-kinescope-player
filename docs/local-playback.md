# Local / progressive playback

Play a local file or any progressive URI through `KinescopeVideoPlayer` and reuse
[player chrome](player-chrome.md) (controls, scrubber, fullscreen) without a Kinescope media id.

## API

```kotlin
import android.net.Uri
import io.kinescope.sdk.player.KinescopeVideoPlayer

kinescopePlayer.setLocalSource(uri, autoplay = false)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `uri` | — | Progressive source: `content://`, `file://`, or a progressive HTTP(S) URL |
| `autoplay` | `false` | Start playback after `prepare` |

Behaviour mirrors `loadVideo` / internal `setVideo` for the attached view:

1. Clears Kinescope video metadata (`getVideo()` returns `null`)
2. Calls `onSourceChanged(uri.toString(), metricUrl = null)` so `KinescopePlayerView` refreshes chrome/analytics
3. Sets `MediaItem.fromUri(uri)`, applies muted/loop options, then `prepare()`

No DASH/HLS factory is used — progressive playback is enough for a local upload-preview file.

## Typical use

Upload confirmation screen: play the picked file with SDK chrome before a media id exists.

```kotlin
playerView.setPlayer(kinescopePlayer)
playerView.applyTemplateOptions()

// Optional: hide menus that need Kinescope metadata
kinescopePlayer.setShowSubtitles(false)
kinescopePlayer.setShowOptions(false) // or hide Quality / CC rows in settings
playerView.applyTemplateOptions()

kinescopePlayer.setLocalSource(pickedUri, autoplay = false)
playerView.setPoster(R.drawable.your_placeholder) // optional; no API poster
```

## Limitations

| Area | Behaviour |
|------|-----------|
| **Quality / renditions** | Empty — progressive file has no ABR ladder |
| **Subtitles / chapters / live** | Not loaded from Kinescope; `getVideo()` is `null` |
| **Analytics** | `onSourceChanged` fires with `metricUrl = null` (no Kinescope metric endpoint) |
| **DRM** | Not applied; use Kinescope VOD/`loadVideo` for Widevine streams |
| **Menus** | Caller should hide unused settings (`setShow*` / settings visibility) |

## Related

- [Quick start](quick-start.md) — Kinescope id playback via `loadVideo`
- [Player chrome](player-chrome.md) — controls reused for local sources
- [Settings menu](settings-menu.md) — what to hide when metadata is missing

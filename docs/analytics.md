# Analytics

Set a callback on `KinescopePlayerView` to receive analytics events. The callback is invoked when events are dispatched; arguments are the event name and the event data as a string.

```kotlin
playerView.setAnalyticsCallback { event, data ->
    // forward to your analytics backend
}
```

Events are also handled internally by `KinescopeAnalyticsManager` (play, pause, seek, quality changes, ticks, etc.) when a metric URL is available from the loaded video.

## Viewer ID (`externalId`)

Pass your own viewer identifier to attribute views in Kinescope analytics to your users — the same as `externalid` in the web player embed.

```kotlin
kinescopePlayer.kinescopePlayerOptions.externalId = user.id
```

- Sent with every event as `Session.ExternalID`.
- Can be set or changed at any time, also after `loadVideo`; the next event uses the new value. It is kept across fullscreen (`switchTargetView`).
- Surrounding whitespace is trimmed. `null` or blank sends nothing.

## What is sent

| Block | Fields |
|-------|--------|
| Event | name (`play`, `pause`, `end`, `replay`, `seek`, `rate`, `buffering`, `view`, `playback`, `enterfullscreen`, `exitfullscreen`, `qualitychanged`, `autoqualitychanged`), value, time |
| Video | stream URL, duration |
| Playback | position, buffered position, rate, volume, muted, quality, fullscreen |
| Device | OS, OS version, screen size |
| Player | SDK type, SDK version |
| Session | install ID, view ID, watched seconds, `externalId` |

`externalId` is the only field set by the app; everything else is filled by the SDK.

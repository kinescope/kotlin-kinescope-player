# Analytics

Set a callback on `KinescopePlayerView` to receive analytics events. The callback is invoked when events are dispatched; arguments are the event name and a date string.

```kotlin
playerView.setAnalyticsCallback { event, data ->
    // forward to your analytics backend
}
```

Events are also handled internally by `KinescopeAnalyticsManager` (play, pause, seek, quality changes, ticks, etc.) when a metric URL is available from the loaded video.

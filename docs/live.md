# Live

Kinescope supports Live mode. Call `setLiveState` on `KinescopePlayerView` to enable Live UI. Check whether the video is a live broadcast via `KinescopeVideo.isLive`.

While the broadcast has not started yet, the player can show a default awaiting cover (`live_awaiting_cover` / Cover 1 design). It is **enabled by default**. Disable it if you prefer your own poster or no placeholder:

```kotlin
kinescopePlayer.kinescopePlayerOptions.showLiveAwaitingCover = false
```

Once the stream is ready, the seek bar shows **Live** / **В эфире** with a red indicator instead of elapsed time.

```kotlin
with(kinescopePlayer) {
    kinescopePlayerOptions.showLiveAwaitingCover = true // default; set false to skip

    loadVideo(liveId, onSuccess = { video ->
        if (video.isLive) {
            playerView.setLiveState()
            playerView.showLiveAwaitingCover()
        }
        play()
    }, onFailed = {
        it?.printStackTrace()
    })
}
```

## Broadcast start date

Show the scheduled live informer (bottom-left badge from the Player design) with the broadcast start date (ISO-8601). The value from event settings is in `KinescopeVideo.live.startsAt`. While the stream has not started, the informer replaces the buffering spinner and shows a countdown when the start is within an hour.

```kotlin
with(kinescopePlayer) {
    loadVideo(liveId, onSuccess = { video ->
        if (video.isLive) {
            playerView.setLiveState()
            playerView.showLiveAwaitingCover()
            video.live?.startsAt?.let { date ->
                playerView.showLiveStartDate(startDate = date)
            }
        }
        play()
    }, onFailed = {
        it?.printStackTrace()
    })
}
```

Hide the start date:

```kotlin
playerView.hideLiveStartDate()
```

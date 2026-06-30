# Live

Kinescope supports Live mode. Call `setLiveState` on `KinescopePlayerView` to enable Live UI. Check whether the video is a live broadcast via `KinescopeVideo.isLive`.

```kotlin
with(kinescopePlayer) {
    loadVideo(liveId, onSuccess = { video ->
        if (video.isLive) {
            playerView.setLiveState()
        }
        play()
    }, onFailed = {
        it?.printStackTrace()
    })
}
```

## Broadcast start date

Show the start date of the broadcast (ISO-8601). The value from event settings is in `KinescopeVideo.live.startsAt`:

```kotlin
with(kinescopePlayer) {
    loadVideo(liveId, onSuccess = { video ->
        if (video.isLive) {
            playerView.setLiveState()
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

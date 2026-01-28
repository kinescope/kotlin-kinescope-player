# kotlin-kinescope-player

[![JitPack](https://jitpack.io/v/kinescope/kotlin-kinescope-player.svg)](https://jitpack.io/#kinescope/kotlin-kinescope-player)

Android SDK for [Kinescope](https://kinescope.io/) video: player, vertical Shorts feed, and offline downloads with DRM (Widevine) in a single dependency.

---
## Features

| Feature | Description |
|--------|-------------|
| **Player** | `KinescopeVideoPlayer`, `KinescopePlayerView` — HLS, DASH, Live, posters, custom colors, fullscreen, analytics |
| **Shorts** | `io.kinescope.sdk.shorts` — TikTok-style vertical feed, `ViewPager2`, `KinescopeVideoProvider` for your API |
| **Offline** | `DownloadVideoOffline`, `VideoDownloadService` — HLS/DASH downloads, Widevine DRM, `DownloadManager` |

One dependency includes the player, Shorts, and the offline pipeline; `VideoDownloadService` and required permissions are merged from the library manifest.

---

## Installation

**Step 1.** Add the JitPack repository to your build file.
Add it in your root `build.gradle`/`setting.gradle` file at the end of repositories:

```groovy
dependencyResolutionManagement {
   repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
   repositories {
      mavenCentral()
      maven { url 'https://jitpack.io' }
   }
}
```

**Step 2.** Add the dependency to your module's `build.gradle` file. Replace `<LATEST_VERSION>` with the current version (can be found in the JitPack badge at the top):

```groovy
dependencies {
   implementation 'com.github.kinescope:kotlin-kinescope-player:<LATEST_VERSION>'
}
```

---

## Quick start

### Player setup

1. Add `KinescopePlayerView` to your view's layout

```xml
<io.kinescope.sdk.view.KinescopePlayerView
   android:id="@+id/player_view"
   android:layout_width="match_parent"
   android:layout_height="250dp" />
```

2. Initialize `KinescopeVideoPlayer` instance.

```kotlin
val kinescopePlayer = KinescopeVideoPlayer(context)
```

3. Attach the player to `KinescopePlayerView`:

```kotlin
val playerView = binding.playerView
   .apply {
      setPlayer(kinescopePlayer)
   }
```

4. Load and play video:

```kotlin
with(kinescopePlayer) {
   loadVideo(liveId, onSuccess = { video ->
      play()
   }, onFailed = {
      it?.printStackTrace()
   })
}
```

### Live

Kinescope supports Live mode. Call the `setLiveState` method to enable Live mode.
In order to check whether the video is a Live broadcast, you can use the `KinescopeVideo.isLive` variable.
Simple example:

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

You can also add a display of the start date of the broadcast. To do this, you need to call the `showLiveStartDate` method, passing a date in ISO-8601 format as a parameter. The broadcast start date set in the event settings panel is in the `KinescopeVideo.live.startsAt` variable.

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

To hide the display of the start date of the broadcast, use the `hideLiveStartDate` method.

```kotlin
playerView.hideLiveStartDate()
```

### Poster

```kotlin
playerView.showPoster(
    url = POSTER_URL,
    placeholder = R.drawable.placeholder,
    errorPlaceholder = R.drawable.placeholder,
    onLoadFinished = {  }
)
```

You can use the poster set in the event settings panel. The URL is in the `KinescopeVideo.poster.url` variable.

```kotlin
video.poster?.url?.let { posterUrl ->
    playerView.showPoster(
        url = posterUrl,
        placeholder = R.drawable.placeholder,
        errorPlaceholder = R.drawable.placeholder,
        onLoadFinished = {  }
    )
}
```

Hide poster:

```kotlin
playerView.hidePoster()
```

**NOTE!** The poster will be hidden once the video is loaded.

### Custom colors

```kotlin
setColors(
    buttonColor = resources.getColor(R.color.custom_color_res),
    progressBarColor = Color.parseColor("#228B22"),
    scrubberColor = Color.parseColor("#EC3440"),
    playedColor = Color.parseColor("#EBABCF"),
    bufferedColor = Color.YELLOW,
)
```

### Custom button

```kotlin
showCustomButton(
    iconRes = R.drawable.custom_btn_icon,
    onClick = { }
)
```

Hide custom button:

```kotlin
playerView.hideCustomButton()
```

### Fullscreen

For fullscreen feature usage switching player to another view should be implemented in the app side.

1. Add these to `configChanges` in your app's manifest for orientation support:

```xml
<activity android:name=".YourActivity"
        android:configChanges="orientation|screenSize|screenLayout|layoutDirection" />
```

2. Add logic to change target view for player and change flags to make this view fullscreen

```kotlin
private fun setFullscreen(fullscreen: Boolean) {
   if (fullscreen) {
      window.setFlags(
         WindowManager.LayoutParams.FLAG_FULLSCREEN,
         WindowManager.LayoutParams.FLAG_FULLSCREEN
      )
      window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
      KinescopePlayerView.switchTargetView(playerView, fullscreenPlayerView, kinescopePlayer)

   } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
         window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                    and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
      } else {
         window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                    and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
      }

      KinescopePlayerView.switchTargetView(fullscreenPlayerView, playerView, kinescopePlayer)
   }
}
```

### Analytics

You can set a callback for analytics events. It is called every time any of the events are dispatched. A date object in string format and event name are passed as the arguments.

```kotlin
playerView.setAnalyticsCallback { event, data -> }
```

### Offline downloads (DownloadVideoOffline)

The library includes an offline download pipeline: `VideoDownloadService` (declared in the library manifest) and `io.kinescope.sdk.download.DownloadVideoOffline` as the entry point.

1. Initialize at app startup: `DownloadVideoOffline.initialize(context)`
2. Start a download (HLS): `DownloadRequest.Builder(contentId, hlsUri).setMimeType(MimeTypes.APPLICATION_M3U8).setData(metadata).build()` then `DownloadVideoOffline.startDownload(context, request)`
3. For **DASH**: use `MimeTypes.APPLICATION_MPD` and the `.mpd` manifest URI
4. For **DRM (Widevine)**: add `.setKeySetId(keySetId)` to the request; use `io.kinescope.sdk.shorts.drm.DrmConfigurator` and `DrmHelper` to obtain PSSH and the offline license
5. Subscribe to changes: `DownloadVideoOffline.addDownloadListener(context, listener)`; in `onDestroy` call `removeDownloadListener(listener)`
6. Playback: `DownloadVideoOffline.getDownloadCache(context)` with `CacheDataSource`; for DRM use `MediaItem` with `setDrmKeySetId`. For DASH use `DashMediaSource` with the same cache and keySetId.

### Shorts (vertical feed)

`io.kinescope.sdk.shorts.*` provides a TikTok-style vertical feed and `KinescopeVideoProvider` for your API.

See `kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md` and `kotlin-kinescope-shorts/QUICK_START.md` in this repository.

---

## Documentation

| Topic | File |
|-------|------|
| Shorts (feed, API, ActivityProvider) | [kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md](kotlin-kinescope-shorts/LIBRARY_USAGE_GUIDE.md) |
| Shorts quick start | [kotlin-kinescope-shorts/QUICK_START.md](kotlin-kinescope-shorts/QUICK_START.md) |
| Kinescope API (`KinescopeVideoProvider`) | [kotlin-kinescope-shorts/API_USAGE_GUIDE.md](kotlin-kinescope-shorts/API_USAGE_GUIDE.md) |
| API 404 troubleshooting | [kotlin-kinescope-shorts/API_TROUBLESHOOTING.md](kotlin-kinescope-shorts/API_TROUBLESHOOTING.md) |
| Offline downloads | `DownloadVideoOffline` KDoc (HLS, DASH, DRM). kotlin-kinescope-player/ src/main/java/download |
---

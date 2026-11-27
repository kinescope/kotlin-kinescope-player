# kotlin-kinescope-shorts Library Usage Guide

This guide helps you integrate the Kinescope video playback library into your Android app.

---

## Adding the dependency

### 1. Add the library to your project

Since 0.1.22 Shorts is a separate artifact. It does **not** include the full player or offline downloads:

```groovy
// build.gradle
dependencies {
    implementation 'io.kinescope:kotlin-kinescope-shorts:0.1.22'
}
```

Need the full player (`KinescopePlayerView`) or offline downloads with DRM as well? Add `io.kinescope:kotlin-kinescope-player:0.1.22` next to it — see [installation.md](../docs/installation.md).

Maven Central is recommended. **JitPack is no longer supported** for new apps; legacy `com.github.kinescope:…` pins are documented in [installation.md](../docs/installation.md#legacy--jitpack-unsupported).
---

## Basic setup

### 1. Initialization in Activity

```kotlin
import io.kinescope.sdk.shorts.adapters.ViewPager2Adapter
import io.kinescope.sdk.shorts.cache.VideoCache
import io.kinescope.sdk.shorts.managers.PoolPlayers
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider

class MainActivity : AppCompatActivity(), ActivityProvider {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ViewPager2Adapter
    private val exoPlayerItems = ArrayList<PlayerItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize library components
        VideoCache.initialize(this)
        PoolPlayers.init(this)
        
        // ViewPager2 setup
        binding.viewPager2.offscreenPageLimit = 1
        
        // Load videos
        loadVideos()
    }
}
```

---

## Using the Kinescope API

### 1. Implementing KinescopeVideoProvider

The library provides the `KinescopeVideoProvider` interface for connecting to the API. See `API_USAGE_GUIDE.md` for details.

Example implementation:

```kotlin
import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider
import io.kinescope.sdk.shorts.models.VideoData

class MyKinescopeVideoProvider(
    private val apiToken: String
) : KinescopeVideoProvider {
    
    override suspend fun loadVideo(videoId: String): VideoData? {
        // Your implementation to load video by ID
        // ...
    }
    
    override suspend fun loadVideos(
        projectId: String?,
        folderId: String?,
        limit: Int,
        offset: Int
    ): List<VideoData> {
        // Your implementation to load video list
        // ...
    }
}
```

### 2. Loading videos via API

```kotlin
import io.kinescope.sdk.shorts.KinescopeShortsConfig
import io.kinescope.sdk.shorts.utils.KinescopeUrls
import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun loadVideosFromApi() {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val videoProvider: KinescopeVideoProvider =
                MyKinescopeVideoProvider(apiToken = KinescopeShortsConfig.API_KEY)

            val kinescopeVideo = KinescopeUrls(
                videoProvider = videoProvider,
                projectId = KinescopeShortsConfig.PROJECT_ID,
                folderId = KinescopeShortsConfig.FOLDER_ID,
                limit = KinescopeShortsConfig.FEED_LIMIT,
            )
            
            val videoUrls = kinescopeVideo.getVideosFromApi()
            setupViewPager(videoUrls)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading videos", e)
        }
    }
}
```

`KinescopeUrls` is provider-only. If `KinescopeVideoProvider` is missing or returns no playable items, the Shorts feed stays empty.

Configure feed credentials via [`KinescopeShortsConfig`](library/src/main/java/io/kinescope/sdk/shorts/KinescopeShortsConfig.kt) (`API_KEY`, optional `PROJECT_ID` / `FOLDER_ID`, `FEED_LIMIT`). Implement your own `KinescopeVideoProvider` (sample Dashboard OkHttp provider is under `kotlin-kinescope-shorts/app` only).

### UI customization (`KinescopeUiConfig`)

Hide or restyle Shorts chrome before opening the feed:

```kotlin
import io.kinescope.sdk.shorts.config.KinescopeUiConfig

KinescopeUiConfig.showLikeButton = false
KinescopeUiConfig.showPlayButton = true
KinescopeUiConfig.showSeekBar = true
KinescopeUiConfig.showPreloadImage = false   // no stub; black until poster / first frame
KinescopeUiConfig.actionButtonSizeDp = 28
KinescopeUiConfig.playButtonSizeDp = 46
```

Full table and seek-bar colour options: [QUICK_START — UI customization](QUICK_START.md#ui-customization-kinescopeuiconfig).

---

## Implementing ActivityProvider

The library uses the `ActivityProvider` interface to abstract the Activity. It has two methods: `getMainActivityIntent()` and `pauseCurrentPlayer()`.

```kotlin
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import android.content.Intent

class MainActivity : AppCompatActivity(), ActivityProvider {
    
    // Returns Intent to open the main Activity
    override fun getMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
    
    // Pauses the current player
    override fun pauseCurrentPlayer() {
        exoPlayerItems.forEach { item ->
            item.exoPlayer.pause()
            item.exoPlayer.playWhenReady = false
        }
    }
}
```

---

## ViewPager2 setup

```kotlin
private fun setupViewPager(videos: List<VideoData>) {
    adapter = ViewPager2Adapter(
        context = this@MainActivity,
        videos = videos,
        videoPreparedListener = object : ViewPager2Adapter.OnVideoPreparedListener {
            override fun onVideoPrepared(exoPlayerItem: PlayerItem) {
                exoPlayerItems.add(exoPlayerItem)
            }
        },
        exoPlayerItems = exoPlayerItems,
        activityProvider = this // Pass ActivityProvider
    )
    
    binding.viewPager2.adapter = adapter
    adapter.attachToViewPager(binding.viewPager2)
    binding.viewPager2.setCurrentItem(0, false)
}
```

---

## Permissions

### AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Both permissions are already merged from the library manifest. Shorts does not need `POST_NOTIFICATIONS` or foreground-service permissions.

---

## Full code example

```kotlin
package com.example.myapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.shorts.adapters.ViewPager2Adapter
import io.kinescope.sdk.shorts.cache.VideoCache
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import io.kinescope.sdk.shorts.managers.PoolPlayers
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.utils.KinescopeUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.util.ArrayList

@UnstableApi
@InternalSerializationApi
class MainActivity : AppCompatActivity(), ActivityProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ViewPager2Adapter
    private val exoPlayerItems = ArrayList<PlayerItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize library
        VideoCache.initialize(this)
        PoolPlayers.init(this)

        // ViewPager2 setup
        binding.viewPager2.offscreenPageLimit = 1

        // Load videos
        loadVideosFromApi()
    }

    private fun loadVideosFromApi() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Create provider (implement KinescopeVideoProvider)
                val videoProvider: KinescopeVideoProvider = MyKinescopeVideoProvider(apiToken = "your-api-token")
                
                val kinescopeVideo = KinescopeUrls(
                    videoProvider = videoProvider,
                    projectId = "your-project-id",
                    folderId = "your-folder-id", // optional
                    limit = 50,                  // optional, default 50
                )

                val videoUrls = kinescopeVideo.getVideosFromApi()
                setupViewPager(videoUrls)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading videos", e)
            }
        }
    }

    private fun setupViewPager(videos: List<VideoData>) {
        adapter = ViewPager2Adapter(
            context = this@MainActivity,
            videos = videos,
            videoPreparedListener = object : ViewPager2Adapter.OnVideoPreparedListener {
                override fun onVideoPrepared(exoPlayerItem: PlayerItem) {
                    exoPlayerItems.add(exoPlayerItem)
                }
            },
            exoPlayerItems = exoPlayerItems,
            activityProvider = this
        )

        binding.viewPager2.adapter = adapter
        adapter.attachToViewPager(binding.viewPager2)
        binding.viewPager2.setCurrentItem(0, false)
    }

    // ActivityProvider implementation
    override fun getMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    override fun pauseCurrentPlayer() {
        exoPlayerItems.forEach { item ->
            item.exoPlayer.pause()
            item.exoPlayer.playWhenReady = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PoolPlayers.release()
    }
}
```

---

## Preload, pool, and cache

Shorts feed warmup (handled inside `ViewPager2Adapter` / `VideoPreloader`):

| Slot | Mechanism |
|------|-----------|
| **Current** | Pooled `ExoPlayer` from `PoolPlayers` (recycle returns to pool — no `release()` until `cleanup`) |
| **+1** | Full preload `ExoPlayer` → handed off via `getPreloadedPlayer` when the page binds (when health allows) |
| **+2 / back** | `MediaCachePrefetcher` — HLS playlist + first segments into `VideoCache` (no player; skipped for DRM; depth depends on health tier) |

**Hard pause:** ViewPager2 scrolling/flinging pauses non-essential preload (debounced ~180 ms after settle). Already prepared `+1` players are kept for handoff.

**Adaptive health** (`PlaybackHealthMonitor` + `PreloadHealth`): samples buffer ahead, bandwidth estimate, Wi‑Fi/metered, and rebuffer state ~every 500 ms. Tiers:

| Tier | Preload behaviour |
|------|-------------------|
| `CRITICAL` | Pause non-essential preload (keep ready `+1` if present) |
| `LOW` | `+1` player only |
| `MEDIUM` | `+1` player + limited forward segment cache |
| `HIGH` | `+1` player + forward/back segment cache (more segments/bytes) |

Weak devices are capped at most at `MEDIUM`. Online playback still uses ExoPlayer ABR under a ~480p / lowest-bitrate cap in `PlayerFactory`.

**Subtitles / captions:** disabled by default in Shorts. `PlayerFactory` sets `TrackSelectionParameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` so HLS/DASH text tracks are not selected. The Shorts `PlayerView` would otherwise paint cues full-bleed over the vertical feed. To enable captions later, clear that disable (or set it to `false`) on the player’s `DefaultTrackSelector` parameters after creating the player.

`VideoData.posterUrl` is rendered over the player until the first frame is ready.

---

## Additional features

### Offline video downloads

Shorts has no offline downloads since 0.1.22 (no download button, no offline playback, no download service). For offline video with DRM, use the player artifact `io.kinescope:kotlin-kinescope-player` and `io.kinescope.sdk.download.DownloadVideoOffline` / `VideoDownloadManager` — see [offline-downloads.md](../docs/offline-downloads.md).

---

## Support

If you have questions or issues:
1. Check Logcat for the `KinescopeUrls` tag
2. Ensure your `KinescopeVideoProvider` implementation is correct
3. Verify the API token and Project ID
4. Check the internet connection
5. See `API_USAGE_GUIDE.md` for provider implementation details

---

# kotlin-kinescope-shorts Library Usage Guide

This guide helps you integrate the Kinescope video playback library into your Android app.

---

## Adding the dependency

### 1. Add the library to your project

**Recommended** — via kotlin-kinescope-player (includes the player, Shorts, and offline downloads):

```groovy
// build.gradle
dependencies {
    implementation 'com.github.kinescope:kotlin-kinescope-player:<VERSION>'
}
```
---

## Basic setup

### 1. Initialization in Activity

```kotlin
import com.kotlin.kinescope.shorts.adapters.ViewPager2Adapter
import com.kotlin.kinescope.shorts.cache.VideoCache
import com.kotlin.kinescope.shorts.download.VideoDownloadManager
import com.kotlin.kinescope.shorts.managers.PoolPlayers
import com.kotlin.kinescope.shorts.managers.NotificationHelper
import com.kotlin.kinescope.shorts.models.PlayerItem
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.interfaces.ActivityProvider
import com.kotlin.kinescope.shorts.interfaces.KinescopeVideoProvider

class MainActivity : AppCompatActivity(), ActivityProvider {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ViewPager2Adapter
    private val exoPlayerItems = ArrayList<PlayerItem>()
    private lateinit var notificationHelper: NotificationHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize library components
        VideoCache.initialize(this)
        PoolPlayers.init(this)
        VideoDownloadManager.initialize(this)
        notificationHelper = NotificationHelper(this, this) // this as ActivityProvider
        
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
import com.kotlin.kinescope.shorts.interfaces.KinescopeVideoProvider
import com.kotlin.kinescope.shorts.models.VideoData

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
import com.kotlin.kinescope.shorts.utils.KinescopeUrls
import com.kotlin.kinescope.shorts.interfaces.KinescopeVideoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun loadVideosFromApi() {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            // Create provider
            val videoProvider: KinescopeVideoProvider = MyKinescopeVideoProvider(apiToken = "your-api-token")
            
            // Use with KinescopeUrls
            val kinescopeVideo = KinescopeUrls(
                videoProvider = videoProvider,
                projectId = "your-project-id"
            )
            
            val videoUrls = kinescopeVideo.getVideosFromApi()
            
            if (videoUrls.isEmpty()) {
                // Fallback to hardcoded list if API returns empty
                val fallbackVideos = kinescopeVideo.getNextVideoUrls()
                setupViewPager(fallbackVideos)
            } else {
                setupViewPager(videoUrls)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading videos", e)
            // Fallback to hardcoded on error
            val kinescopeVideo = KinescopeUrls()
            val fallbackVideos = kinescopeVideo.getNextVideoUrls()
            setupViewPager(fallbackVideos)
        }
    }
}
```

---

## Using without API (hardcoded)

If you do not want to use the API, you can use hardcoded videos:

```kotlin
import com.kotlin.kinescope.shorts.utils.KinescopeUrls

private fun loadVideosFromHardcode() {
    val kinescopeVideo = KinescopeUrls()
    val videoUrls = kinescopeVideo.getNextVideoUrls()
    setupViewPager(videoUrls)
}
```

Or build your own list:

```kotlin
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.models.DrmInfo
import com.kotlin.kinescope.shorts.models.WidevineInfo

val videos = listOf(
    VideoData(
        hlsLink = "https://kinescope.io/.../master.m3u8?token=...",
        drm = DrmInfo(
            widevine = WidevineInfo(
                licenseUrl = "https://license.kinescope.io/.../widevine?token=..."
            )
        ),
        title = "Video title"
    ),
    // ... other videos
)
```

---

## Implementing ActivityProvider

The library uses the `ActivityProvider` interface to abstract the Activity. You implement `OfflineVideoPlayerActivity` yourself and define the extra keys (e.g. `EXTRA_VIDEO_DATA`/`EXTRA_VIDEO_DATA_JSON` and `EXTRA_DOWNLOAD_ID`).

```kotlin
import com.kotlin.kinescope.shorts.interfaces.ActivityProvider
import com.kotlin.kinescope.shorts.models.VideoData
import android.content.Intent

class MainActivity : AppCompatActivity(), ActivityProvider {
    
    // Returns Intent to open the main Activity
    override fun getMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
    
    // Intent for the offline player. In your OfflineVideoPlayerActivity use
    // EXTRA_VIDEO_DATA (Serializable) or EXTRA_VIDEO_DATA_JSON (JSON String) + EXTRA_DOWNLOAD_ID.
    override fun getOfflinePlayerActivityIntent(videoData: VideoData, downloadId: String): Intent {
        return Intent(this, OfflineVideoPlayerActivity::class.java).apply {
            putExtra(OfflineVideoPlayerActivity.EXTRA_VIDEO_DATA, videoData)
            putExtra(OfflineVideoPlayerActivity.EXTRA_DOWNLOAD_ID, downloadId)
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
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Requesting permissions in code (Android 13+)

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Request notification permission (Android 13+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
}
```

---

## Download notifications

The library can show notifications when downloads complete. Configure a listener:

```kotlin
import com.kotlin.kinescope.shorts.download.VideoDownloadManager
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager

private val downloadListener = object : DownloadManager.Listener {
    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {
        if (download.state == Download.STATE_COMPLETED) {
            // Show download complete notification
            notificationHelper.showDownloadCompleteNotification(download)
        }
    }
    
    override fun onDownloadRemoved(
        downloadManager: DownloadManager,
        download: Download
    ) {
        // Handle download removal
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ...
    VideoDownloadManager.addDownloadListener(this, downloadListener)
}
```

---

## Full code example

```kotlin
package com.example.myapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.kotlin.kinescope.shorts.adapters.ViewPager2Adapter
import com.kotlin.kinescope.shorts.cache.VideoCache
import com.kotlin.kinescope.shorts.download.VideoDownloadManager
import com.kotlin.kinescope.shorts.interfaces.ActivityProvider
import com.kotlin.kinescope.shorts.managers.NotificationHelper
import com.kotlin.kinescope.shorts.managers.PoolPlayers
import com.kotlin.kinescope.shorts.models.PlayerItem
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.utils.KinescopeUrls
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
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize library
        VideoCache.initialize(this)
        PoolPlayers.init(this)
        VideoDownloadManager.initialize(this)
        notificationHelper = NotificationHelper(this, this)

        // ViewPager2 setup
        binding.viewPager2.offscreenPageLimit = 1

        // Request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }

        // Download listener
        VideoDownloadManager.addDownloadListener(this, downloadListener)

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
                    projectId = "your-project-id"
                )

                val videoUrls = kinescopeVideo.getVideosFromApi()

                if (videoUrls.isEmpty()) {
                    Log.w("MainActivity", "API returned empty list, using fallback")
                    val fallbackVideos = kinescopeVideo.getNextVideoUrls()
                    setupViewPager(fallbackVideos)
                } else {
                    setupViewPager(videoUrls)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading videos", e)
                val kinescopeVideo = KinescopeUrls()
                val fallbackVideos = kinescopeVideo.getNextVideoUrls()
                setupViewPager(fallbackVideos)
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

    override fun getOfflinePlayerActivityIntent(
        videoData: VideoData,
        downloadId: String
    ): Intent {
        return Intent(this, OfflineVideoPlayerActivity::class.java).apply {
            putExtra(OfflineVideoPlayerActivity.EXTRA_VIDEO_DATA, videoData)
            putExtra(OfflineVideoPlayerActivity.EXTRA_DOWNLOAD_ID, downloadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    override fun pauseCurrentPlayer() {
        exoPlayerItems.forEach { item ->
            item.exoPlayer.pause()
            item.exoPlayer.playWhenReady = false
        }
    }

    // Download listener
    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            if (download.state == Download.STATE_COMPLETED) {
                notificationHelper.showDownloadCompleteNotification(download)
            }
        }

        override fun onDownloadRemoved(
            downloadManager: DownloadManager,
            download: Download
        ) {
            // Handle download removal
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoDownloadManager.removeDownloadListener(downloadListener)
        PoolPlayers.release()
    }
}
```

---

## Additional features

### Offline video downloads

The library supports video downloads. The user can tap the download button on a video to save it for offline playback.

### Offline playback

Offline videos are detected automatically and play without an internet connection.

### Managing downloads

Via `VideoDownloadManager` (package `com.kotlin.kinescope.shorts.download` or `io.kinescope.sdk.shorts.download`):

```kotlin
val downloads = VideoDownloadManager.getAllDownloadsWithActiveFirst(context)
val download = VideoDownloadManager.getDownloadById(contentId)
VideoDownloadManager.removeDownload(context, downloadId)
```

When using **kotlin-kinescope-player**, you can alternatively use `io.kinescope.sdk.download.DownloadVideoOffline`: `initialize`, `startDownload`, `removeDownload`, `getAllCompletedDownloads`, `addDownloadListener`, etc. — see its KDoc and the root README.

---

## Support

If you have questions or issues:
1. Check Logcat for tags: `DOWNLOAD_MANAGER`, `KinescopeUrls`
2. Ensure your `KinescopeVideoProvider` implementation is correct
3. Verify the API token and Project ID
4. Check the internet connection
5. See `API_USAGE_GUIDE.md` for provider implementation details

---

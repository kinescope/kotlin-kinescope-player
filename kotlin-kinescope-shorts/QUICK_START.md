# Quick start — kotlin-kinescope-shorts

## Configure Shorts (`KinescopeShortsConfig`)

Public API in the Shorts SDK (`io.kinescope.sdk.shorts.KinescopeShortsConfig`).
Assign before loading the feed (e.g. `Application.onCreate` or your Shorts activity):

```kotlin
import io.kinescope.sdk.shorts.KinescopeShortsConfig

KinescopeShortsConfig.API_KEY = "your-dashboard-api-token"
KinescopeShortsConfig.PROJECT_ID = null   // optional
KinescopeShortsConfig.FOLDER_ID = null    // optional
KinescopeShortsConfig.FEED_LIMIT = 50
```

| Field | Purpose |
|-------|---------|
| `API_KEY` | Dashboard Bearer token (required for Dashboard catalog) |
| `PROJECT_ID` | Optional `GET /v1/videos?project_id=` |
| `FOLDER_ID` | Optional `GET /v1/videos?folder_id=` |
| `FEED_LIMIT` | Catalog page size (default `50`, max `100`) |

Then pass into your provider / `KinescopeUrls`:

```kotlin
KinescopeUrls(
    videoProvider = MyProvider(KinescopeShortsConfig.API_KEY),
    projectId = KinescopeShortsConfig.PROJECT_ID,
    folderId = KinescopeShortsConfig.FOLDER_ID,
    limit = KinescopeShortsConfig.FEED_LIMIT,
)
```

Empty `API_KEY` → empty Dashboard feed.

Implement `KinescopeVideoProvider` in your app (the sample `ApiKinescopeVideoProvider` under `kotlin-kinescope-shorts/app` is an example only — not part of the published AAR).

---

## UI customization (`KinescopeUiConfig`)

Optional runtime flags in `io.kinescope.sdk.shorts.config.KinescopeUiConfig` (`null` = keep layout defaults):

```kotlin
import io.kinescope.sdk.shorts.config.KinescopeUiConfig

// Side actions
KinescopeUiConfig.showLikeButton = false
KinescopeUiConfig.showShareButton = false

// Play overlay / scrub / preload stub
KinescopeUiConfig.showPlayButton = false
KinescopeUiConfig.showSeekBar = false
KinescopeUiConfig.showPreloadImage = false

// Icons & sizes
KinescopeUiConfig.playButtonIconResId = R.drawable.my_play
KinescopeUiConfig.preloadImageResId = R.drawable.my_stub
KinescopeUiConfig.actionButtonSizeDp = 32
KinescopeUiConfig.playButtonSizeDp = 48

// Seek bar colours
KinescopeUiConfig.seekBarProgressColor = 0xFF6161FC.toInt()
KinescopeUiConfig.seekBarTrackColor = 0x52FFFFFF
```

| Flag / field | Effect |
|--------------|--------|
| `showLikeButton` / `showDislikeButton` / `showCommentButton` / `showShareButton` | Side action visibility |
| `showOfflineButton` / `showSavedVideosButton` | Offline / saved-menu buttons |
| `showPlayButton` | Center play overlay on pause (`false` hides icon; tap-to-toggle still works) |
| `showSeekBar` | Bottom timeline + hit area + time chip |
| `showPreloadImage` | Figma stub while poster loads / when poster is missing (`false` → black until poster/frame) |
| `*IconResId` | Replace drawable (drawn inside the button bounds) |
| `actionButtonSizeDp` / `playButtonSizeDp` | Button size in dp |
| `preloadImageResId` | Custom stub (ignored when `showPreloadImage = false`) |

Scrub seeks only after **hold + drag** (tap alone does not seek). Timeline is shown on pause and while scrubbing.

**Captions / subtitles** are off by default (`PlayerFactory` disables `C.TRACK_TYPE_TEXT`). Details: [LIBRARY_USAGE_GUIDE — Preload, pool, and cache](LIBRARY_USAGE_GUIDE.md#preload-pool-and-cache).

---

## Minimal usage example (in your own app)

### 1. Add the dependency

Via **kotlin-kinescope-player** (recommended; includes Shorts, player, and offline):

```groovy
dependencies {
    implementation 'io.kinescope:kotlin-kinescope-player:0.1.7'
}
```

Maven Central is recommended. **JitPack is no longer supported** for new apps (legacy pins: [installation.md](../docs/installation.md#legacy--jitpack-unsupported)).

Or a local Shorts module: `implementation(project(":kotlin-kinescope-shorts"))` (with `include ':kotlin-kinescope-shorts'` and `projectDir = file('kotlin-kinescope-shorts/library')`).

### 2. Create an Activity with ActivityProvider

```kotlin
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import io.kinescope.sdk.shorts.adapters.ViewPager2Adapter
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.utils.KinescopeUrls

class MainActivity : AppCompatActivity(), ActivityProvider {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ViewPager2Adapter
    private val exoPlayerItems = ArrayList<PlayerItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize
        VideoCache.initialize(this)
        PoolPlayers.init(this)
        VideoDownloadManager.initialize(this)
        
        // Load videos
        loadVideos()
    }
    
    private fun loadVideos() {
        CoroutineScope(Dispatchers.Main).launch {
            val myProvider: KinescopeVideoProvider = MyKinescopeVideoProvider(apiToken = "your-api-token")
            val videos = KinescopeUrls(
                videoProvider = myProvider,
                projectId = "your-project-id", // optional
                folderId = "your-folder-id",   // optional
                limit = 50,                    // optional, default 50
            ).getVideosFromApi()

            setupViewPager(videos)
        }
    }
    
    private fun setupViewPager(videos: List<VideoData>) {
        adapter = ViewPager2Adapter(
            context = this,
            videos = videos,
            videoPreparedListener = object : ViewPager2Adapter.OnVideoPreparedListener {
                override fun onVideoPrepared(item: PlayerItem) {
                    exoPlayerItems.add(item)
                }
            },
            exoPlayerItems = exoPlayerItems,
            activityProvider = this
        )
        binding.viewPager2.adapter = adapter
        adapter.attachToViewPager(binding.viewPager2)
    }
    
    // ActivityProvider: use your offline player Activity and its extra key constants
    override fun getMainActivityIntent() = Intent(this, MainActivity::class.java)
    override fun getOfflinePlayerActivityIntent(videoData: VideoData, downloadId: String) =
        Intent(this, OfflineVideoPlayerActivity::class.java).apply {
            putExtra(OfflineVideoPlayerActivity.EXTRA_VIDEO_DATA, videoData)  // or EXTRA_VIDEO_DATA_JSON + AppJson
            putExtra(OfflineVideoPlayerActivity.EXTRA_DOWNLOAD_ID, downloadId)
        }
    override fun pauseCurrentPlayer() {
        exoPlayerItems.forEach { it.exoPlayer.pause() }
    }
}
```

### 3. Layout file (activity_main.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPager2"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
        
</androidx.constraintlayout.widget.ConstraintLayout>
```

### 4. Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and `VideoDownloadService` are merged from the kotlin-kinescope-player / kotlin-kinescope-shorts manifest.

## Done

You now have a video player with:
- Kinescope video playback
- Offline video downloads
- DRM-protected video support
- Vertical scrolling (TikTok-style)

For more details see [LIBRARY_USAGE_GUIDE.md](LIBRARY_USAGE_GUIDE.md)

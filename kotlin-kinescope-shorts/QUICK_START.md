# Quick start — kotlin-kinescope-shorts

## Minimal usage example

### 1. Add the dependency

Via **kotlin-kinescope-player** (recommended; includes Shorts, player, and offline):

```groovy
dependencies {
    implementation 'com.github.kinescope:kotlin-kinescope-player:<VERSION>'
}
```

Or a local Shorts module: `implementation(project(":kotlin-kinescope-shorts"))` (with `include ':kotlin-kinescope-shorts'` and `projectDir = file('kotlin-kinescope-shorts/library')`).

### 2. Create an Activity with ActivityProvider

```kotlin
import com.kotlin.kinescope.shorts.interfaces.ActivityProvider
import com.kotlin.kinescope.shorts.adapters.ViewPager2Adapter
import com.kotlin.kinescope.shorts.models.PlayerItem
import com.kotlin.kinescope.shorts.models.VideoData
import com.kotlin.kinescope.shorts.utils.KinescopeUrls

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
            // Without API (hardcoded)
            val videos = KinescopeUrls().getNextVideoUrls()
            
            // With API: KinescopeUrls(videoProvider = myProvider, projectId = "x").getVideosFromApi()
            
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

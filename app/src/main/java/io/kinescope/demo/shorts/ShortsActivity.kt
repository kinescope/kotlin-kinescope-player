@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package io.kinescope.demo.shorts

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import java.util.ArrayList
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.adapters.ViewPager2Adapter
import io.kinescope.sdk.shorts.cache.VideoCache
import io.kinescope.demo.KinescopeDemoConfig
import io.kinescope.demo.databinding.ActivityShortsBinding
import io.kinescope.sdk.shorts.managers.PoolPlayers
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class ShortsActivity : AppCompatActivity(), ActivityProvider {

    private lateinit var binding: ActivityShortsBinding
    private var adapter: ViewPager2Adapter? = null
    private val exoPlayerItems = ArrayList<PlayerItem>()

    @OptIn(UnstableApi::class) @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityShortsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding.viewPager2.offscreenPageLimit = 1

        VideoCache.initialize(this)
        PoolPlayers.init(this)
        loadVideos()
    }

    private val feedVideos = ArrayList<VideoData>()

    private fun loadVideos() {
        lifecycleScope.launch {
            try {
                val provider = DemoKinescopeVideoProvider(this@ShortsActivity)
                provider.loadVideosProgressive(
                    projectId = KinescopeDemoConfig.PROJECT_ID,
                    folderId = KinescopeDemoConfig.FOLDER_ID,
                    limit = KinescopeDemoConfig.SHORTS_FEED_LIMIT,
                ) { video ->
                    if (adapter == null) {
                        feedVideos.add(video)
                        setupViewPager(feedVideos)
                    } else {
                        adapter?.appendVideos(listOf(video))
                    }
                }
                if (adapter == null) {
                    Log.w(TAG, "Shorts feed is empty")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Provider failed to load Shorts feed", e)
            }
        }
    }

    private fun setupViewPager(videos: MutableList<VideoData>) {
        adapter = ViewPager2Adapter(
            context = this,
            videos = videos,
            videoPreparedListener = object : ViewPager2Adapter.OnVideoPreparedListener {
                override fun onVideoPrepared(exoPlayerItem: PlayerItem) {
                    exoPlayerItems.add(exoPlayerItem)
                }
            },
            exoPlayerItems = exoPlayerItems,
            activityProvider = this,
        )

        binding.viewPager2.adapter = adapter
        adapter?.attachToViewPager(binding.viewPager2)
        setupViewPager2ForFastScroll()
        binding.viewPager2.setCurrentItem(0, false)
    }

    override fun pauseCurrentPlayer(){
        val currentItem = binding.viewPager2.currentItem
        val exoPlayerItem = exoPlayerItems.find { it.position == currentItem}
        exoPlayerItem?.exoPlayer?.playWhenReady = false
    }

    override fun getMainActivityIntent(): android.content.Intent {
        return android.content.Intent(this, ShortsActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    override fun onPause() {
        super.onPause()
        adapter?.pausePlayback()
    }

    override fun onResume() {
        super.onResume()
        adapter?.resumePlayback()
    }

    override fun onDestroy() {
        adapter?.cleanup()
        adapter = null
        PoolPlayers.shutdown()
        VideoCache.release()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Do not VideoCache.release() here: live players still hold CacheDataSource on the
        // same SimpleCache. Releasing mid-playback stalls playback and leaves sources on a dead cache.
    }
    
    private fun setupViewPager2ForFastScroll() {
        val recyclerView = binding.viewPager2.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        recyclerView?.let { rv ->
            rv.clipToPadding = false
            rv.clipChildren = false
            rv.overScrollMode = android.view.View.OVER_SCROLL_NEVER
            rv.setPadding(0, 0, 0, 0)
            rv.setClipToPadding(false)
            rv.setBackgroundColor(android.graphics.Color.BLACK)
            
            while (rv.itemDecorationCount > 0) {
                rv.removeItemDecorationAt(0)
            }
            
            rv.isNestedScrollingEnabled = true
            
            rv.setOnFlingListener(object : androidx.recyclerview.widget.RecyclerView.OnFlingListener() {
                override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                    val layoutManager = rv.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                    layoutManager ?: return false
                    
                    val currentPosition = binding.viewPager2.currentItem
                    val targetPosition = if (velocityY > 0) {
                        currentPosition + 1
                    } else {
                        currentPosition - 1
                    }
                    
                    val itemCount = rv.adapter?.itemCount ?: 0
                    if (targetPosition !in 0 until itemCount) {
                        return false
                    }
                    
                    val minVelocity = 300
                    if (kotlin.math.abs(velocityY) < minVelocity) {
                        binding.viewPager2.setCurrentItem(targetPosition, true)
                        return true
                    }
                    
                    val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(rv.context) {
                        override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                            return 6f / displayMetrics.densityDpi
                        }
                        
                        override fun calculateTimeForScrolling(dx: Int): Int {
                            return kotlin.math.max(30, super.calculateTimeForScrolling(dx) / 5)
                        }
                    }
                    
                    smoothScroller.targetPosition = targetPosition
                    layoutManager.startSmoothScroll(smoothScroller)
                    return true
                }
            })
        }
        
        binding.viewPager2.isUserInputEnabled = true
        
        binding.viewPager2.setPageTransformer { page, position ->
            page.alpha = 1f - kotlin.math.abs(position) * 0.3f
            page.scaleY = 1f - kotlin.math.abs(position) * 0.1f
        }
    }

    companion object {
        private const val TAG = "ShortsActivity"
    }
}

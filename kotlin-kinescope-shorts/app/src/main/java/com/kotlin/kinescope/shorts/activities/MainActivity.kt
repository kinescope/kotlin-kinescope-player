package com.kotlin.kinescope.shorts.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.util.ArrayList
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.utils.KinescopeUrls
import io.kinescope.sdk.shorts.adapters.ViewPager2Adapter
import io.kinescope.sdk.shorts.cache.VideoCache
import com.kotlin.kinescope.shorts.databinding.ActivityMainBinding
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.drm.DrmConfigurator
import io.kinescope.sdk.shorts.managers.NotificationHelper
import io.kinescope.sdk.shorts.managers.PoolPlayers
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

@UnstableApi @InternalSerializationApi
class MainActivity : AppCompatActivity(), ActivityProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ViewPager2Adapter
    private val exoPlayerItems = ArrayList<PlayerItem>()
    private val drmConfigurator = DrmConfigurator(this)
    private lateinit var notificationHelper: NotificationHelper


    @OptIn(UnstableApi::class) @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding.viewPager2.offscreenPageLimit = 1

        VideoCache.initialize(this)
        PoolPlayers.init(this)
        notificationHelper = NotificationHelper(this, this)


        VideoDownloadManager.initialize(this)

        VideoDownloadManager.addDownloadListener(this, listener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        loadVideos()
    }

    private fun loadVideos() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val kinescopeVideo = KinescopeUrls()
                val videoUrls = kinescopeVideo.getNextVideoUrls()
                
                adapter = ViewPager2Adapter(
                    context = this@MainActivity,
                    videos = videoUrls,
                    videoPreparedListener = object : ViewPager2Adapter.OnVideoPreparedListener {
                        override fun onVideoPrepared(exoPlayerItem: PlayerItem) {
                            exoPlayerItems.add(exoPlayerItem)
                        }
                    },
                    exoPlayerItems = exoPlayerItems,
                    activityProvider = this@MainActivity
                )

                binding.viewPager2.adapter = adapter
                adapter.attachToViewPager(binding.viewPager2)
                setupViewPager2ForFastScroll()
                binding.viewPager2.setCurrentItem(0, false)
            } catch (e: Exception) {
                val kinescopeVideo = KinescopeUrls()
                val videoUrls = kinescopeVideo.getNextVideoUrls()
                
                adapter = ViewPager2Adapter(
                    context = this@MainActivity,
                    videos = videoUrls,
                    videoPreparedListener = object : ViewPager2Adapter.OnVideoPreparedListener {
                        override fun onVideoPrepared(exoPlayerItem: PlayerItem) {
                            exoPlayerItems.add(exoPlayerItem)
                        }
                    },
                    exoPlayerItems = exoPlayerItems,
                    activityProvider = this@MainActivity
                )

                binding.viewPager2.adapter = adapter
                adapter.attachToViewPager(binding.viewPager2)
                setupViewPager2ForFastScroll()
                binding.viewPager2.setCurrentItem(0, false)
            }
        }

    }

    @UnstableApi
    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: java.lang.Exception?
        ) {
            if (download.state == Download.STATE_COMPLETED) {
                notificationHelper.showDownloadCompleteNotification(download)
            }
        }

        }

    override fun pauseCurrentPlayer(){
        val currentItem = binding.viewPager2.currentItem
        val exoPlayerItem = exoPlayerItems.find { it.position == currentItem}
        exoPlayerItem?.exoPlayer?.playWhenReady = false
    }

    override fun getMainActivityIntent(): android.content.Intent {
        return android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    override fun getOfflinePlayerActivityIntent(videoData: VideoData, downloadId: String): android.content.Intent {
        return android.content.Intent(this, OfflineVideoPlayerActivity::class.java).apply {
            putExtra(OfflineVideoPlayerActivity.EXTRA_VIDEO_DATA, videoData)
            putExtra(OfflineVideoPlayerActivity.EXTRA_DOWNLOAD_ID, downloadId)
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayerItems.forEach{ it.exoPlayer.playWhenReady = false}
        PoolPlayers.get().releaseAll()

    }

    override fun onResume() {
        super.onResume()

        val currentItem = binding.viewPager2.currentItem
        val exoPlayerItem = exoPlayerItems.find { it.position == currentItem }
        exoPlayerItem?.let { item ->
            item.exoPlayer.playWhenReady = true
        }
    }

    override fun onDestroy() {
        adapter?.cleanup()
        PoolPlayers.get().cleanup()
        VideoCache.clearCache()
        VideoCache.release()
        VideoDownloadManager.removeDownloadListener(listener)
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        VideoCache.clearCache()
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
}
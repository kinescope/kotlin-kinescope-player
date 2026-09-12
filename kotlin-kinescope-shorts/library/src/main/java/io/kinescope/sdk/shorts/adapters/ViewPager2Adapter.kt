package io.kinescope.sdk.shorts.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.kinescope.sdk.shorts.R
import io.kinescope.sdk.shorts.databinding.ShortsListVideoBinding
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import io.kinescope.sdk.shorts.managers.PlaybackHealthMonitor
import io.kinescope.sdk.shorts.managers.PlayerFactory
import io.kinescope.sdk.shorts.managers.VideoPreloader
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.viewholders.VideoViewHolder
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
@UnstableApi
class ViewPager2Adapter(
    private val context: Context,
    private val videos: MutableList<VideoData>,
    private val videoPreparedListener: OnVideoPreparedListener,
    private val exoPlayerItems: ArrayList<PlayerItem>,
    private val activityProvider: ActivityProvider? = null,
) : RecyclerView.Adapter<VideoViewHolder>() {

    private var viewPager2: ViewPager2? = null
    private var videoPreloader: VideoPreloader? = null
    private var healthMonitor: PlaybackHealthMonitor? = null
    private var scrollState = ViewPager2.SCROLL_STATE_IDLE

    fun attachToViewPager(viewPager2: ViewPager2) {
        this.viewPager2 = viewPager2

        val playerFactory = PlayerFactory(context)
        videoPreloader = VideoPreloader(context, playerFactory)
        healthMonitor = PlaybackHealthMonitor(context) { snapshot ->
            videoPreloader?.onHealthUpdated(snapshot)
        }
        videoPreloader?.updateVideoList(videos, 0)

        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                scrollState = state
                updateScrollPreloadGate()
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                pauseAllPlayersExcept(position)
                videoPreloader?.onPageChanged(position)
                updateScrollPreloadGate()
                attachHealthToCurrentPage(position)

                val neighbors = listOf(position - 1, position + 1)
                    .filter { it in 0 until itemCount }
                neighbors.forEach { neighborPos ->
                    exoPlayerItems.find { it.position == neighborPos }?.exoPlayer?.let { player ->
                        if (player.playbackState == Player.STATE_IDLE) {
                            player.prepare()
                        }
                    }
                }
            }
        })
        attachHealthToCurrentPage(viewPager2.currentItem)
    }

    private fun updateScrollPreloadGate() {
        videoPreloader?.setPreloadPaused(scrollState != ViewPager2.SCROLL_STATE_IDLE)
    }

    private fun attachHealthToCurrentPage(position: Int) {
        val player = exoPlayerItems.find { it.position == position }?.exoPlayer as? ExoPlayer
        healthMonitor?.attach(player)
    }

    fun appendVideos(more: List<VideoData>) {
        if (more.isEmpty()) return
        val start = videos.size
        videos.addAll(more)
        notifyItemRangeInserted(start, more.size)
        videoPreloader?.updateVideoList(videos, viewPager2?.currentItem ?: 0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = ShortsListVideoBinding.inflate(LayoutInflater.from(context), parent, false)
        return VideoViewHolder(view, context, wrappingPreparedListener(), activityProvider).also { holder ->
            holder.onRebufferingChanged = { rebuffering ->
                if (holder.bindingAdapterPosition == (viewPager2?.currentItem ?: -1)) {
                    healthMonitor?.setRebuffering(rebuffering)
                }
            }
        }
    }

    private fun wrappingPreparedListener() = object : OnVideoPreparedListener {
        override fun onVideoPrepared(exoPlayerItem: PlayerItem) {
            exoPlayerItems.removeAll { it.position == exoPlayerItem.position }
            exoPlayerItems.add(exoPlayerItem)
            videoPreparedListener.onVideoPrepared(exoPlayerItem)
            if (exoPlayerItem.position == (viewPager2?.currentItem ?: -1)) {
                healthMonitor?.attach(exoPlayerItem.exoPlayer as? ExoPlayer)
            }
        }
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val videoData = videos[position]
        val contentId = holder.generateStableContentId(videoData.hlsLink)
        val download = VideoDownloadManager.getDownloadIndex(context).getDownload(contentId)

        val isDownloaded = download != null && download.state == Download.STATE_COMPLETED

        if (isDownloaded) {
            holder.bindOfflineVideos(videoData, download)
        } else {
            val preloaded = videoPreloader?.getPreloadedPlayer(position)
            holder.bindOnlineVideos(videoData, preloadedPlayer = preloaded)
        }
    }

    override fun getItemCount(): Int = videos.size

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.bindingAdapterPosition
        holder.binding.playerView.player = null
        holder.playerManager.releasePlayer()
        if (position != RecyclerView.NO_POSITION) {
            exoPlayerItems.removeAll { it.position == position }
        }

        holder.binding.playerView.findViewById<SeekBar>(R.id.seekBar).apply {
            progress = 0
            max = 0
        }
    }

    override fun onViewAttachedToWindow(holder: VideoViewHolder) {
        super.onViewAttachedToWindow(holder)
        val currentPos = viewPager2?.currentItem ?: return
        if (holder.bindingAdapterPosition == currentPos) {
            holder.playerManager.shouldAutoPlay = true
            holder.playerManager.resumePlayback()
        }
    }

    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.playerManager.exoPlayer?.playWhenReady = false
        holder.onDetached()
    }

    private fun pauseAllPlayersExcept(currentPosition: Int) {
        exoPlayerItems.forEach { item ->
            if (item.position != currentPosition) {
                item.exoPlayer.playWhenReady = false
                item.exoPlayer.seekTo(0)
            } else {
                item.exoPlayer.playWhenReady = true
                if (item.exoPlayer.playbackState == Player.STATE_IDLE) {
                    item.exoPlayer.prepare()
                }
            }
        }
    }

    fun pausePlayback() {
        forEachVisibleHolder { it.playerManager.pausePlayback() }
        exoPlayerItems.forEach { it.exoPlayer.playWhenReady = false }
    }

    fun resumePlayback() {
        val current = viewPager2?.currentItem ?: return
        var resumedHolder = false
        forEachVisibleHolder { holder ->
            if (holder.bindingAdapterPosition == current) {
                holder.playerManager.shouldAutoPlay = true
                holder.playerManager.resumeAfterBackground()
                resumedHolder = true
            } else {
                holder.playerManager.pausePlayback()
            }
        }
        if (!resumedHolder) {
            exoPlayerItems.find { it.position == current }?.let { item ->
                if (item.exoPlayer.playbackState == Player.STATE_IDLE && item.exoPlayer.mediaItemCount > 0) {
                    item.exoPlayer.prepare()
                }
                item.exoPlayer.playWhenReady = true
            }
        }
    }

    private fun forEachVisibleHolder(block: (VideoViewHolder) -> Unit) {
        val rv = viewPager2?.getChildAt(0) as? RecyclerView ?: return
        for (i in 0 until rv.childCount) {
            val holder = rv.getChildViewHolder(rv.getChildAt(i)) as? VideoViewHolder ?: continue
            block(holder)
        }
    }

    fun cleanup() {
        healthMonitor?.detach()
        healthMonitor = null
        videoPreloader?.cleanup()
        videoPreloader = null
    }

    interface OnVideoPreparedListener {
        fun onVideoPrepared(exoPlayerItem: PlayerItem)
    }
}

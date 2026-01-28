package io.kinescope.sdk.shorts.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.kinescope.sdk.shorts.R
import io.kinescope.sdk.shorts.databinding.ListVideoBinding
import io.kinescope.sdk.shorts.managers.PoolPlayers
import io.kinescope.sdk.shorts.models.PlayerItem
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.viewholders.VideoViewHolder
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import io.kinescope.sdk.shorts.download.VideoDownloadManager
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
@UnstableApi
class ViewPager2Adapter(
    private val context: Context,
    private val videos: List<VideoData>,
    private val videoPreparedListener: OnVideoPreparedListener,
    private val exoPlayerItems: ArrayList<PlayerItem>,
    private val activityProvider: ActivityProvider? = null
) : RecyclerView.Adapter<VideoViewHolder>() {

    private var viewPager2: ViewPager2? = null
    private var videoPreloader: io.kinescope.sdk.shorts.managers.VideoPreloader? = null

    fun attachToViewPager(viewPager2: ViewPager2) {
        this.viewPager2 = viewPager2

        val playerFactory = io.kinescope.sdk.shorts.managers.PlayerFactory(context)
        videoPreloader = io.kinescope.sdk.shorts.managers.VideoPreloader(context, playerFactory)
        videoPreloader?.updateVideoList(videos, 0)
        
        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                pauseAllPlayersExcept(position)

                videoPreloader?.onPageChanged(position)

                val neighbors = listOf(position - 1, position + 1, position - 2, position + 2)
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = ListVideoBinding.inflate(LayoutInflater.from(context), parent, false)
        val player = PoolPlayers.get().acquirePlayer()
        return VideoViewHolder(view, context, videoPreparedListener, player, activityProvider)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val videoData = videos[position]
        holder.binding.TitleVideo.text = videoData.title
        val contentId = holder.generateStableContentId(videoData.hlsLink)
        val downloadManager = VideoDownloadManager.getDownloadManager(context)
        val download = VideoDownloadManager.getDownloadIndex(context).getDownload(contentId)

        val isDownloaded = download != null && download.state == Download.STATE_COMPLETED

        if (isDownloaded) {
            holder.bindOfflineVideos(videoData, download)
        } else {
            holder.bindOnlineVideos(videoData)
        }

        val currentPosition = viewPager2?.currentItem ?: 0
        videoPreloader?.updateVideoList(videos, currentPosition)
    }

    override fun getItemCount(): Int = videos.size


    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.playerView.player = null
        holder.playerManager.releasePlayer()

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

    fun cleanup() {
        videoPreloader?.cleanup()
        videoPreloader = null
    }

    interface OnVideoPreparedListener {
        fun onVideoPrepared(exoPlayerItem: PlayerItem)
    }

}
package io.kinescope.sdk.shorts.viewholders

import android.content.Context
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.kinescope.sdk.shorts.R
import io.kinescope.sdk.shorts.adapters.ViewPager2Adapter
import io.kinescope.sdk.shorts.config.KinescopeUiConfig
import io.kinescope.sdk.shorts.databinding.ShortsListVideoBinding
import io.kinescope.sdk.shorts.interfaces.ActivityProvider
import io.kinescope.sdk.shorts.managers.PlayerManager
import io.kinescope.sdk.shorts.models.VideoData
import io.kinescope.sdk.shorts.utils.ThumbnailLoader
import io.kinescope.sdk.shorts.view.ExpandableTextHelper
import io.kinescope.sdk.shorts.view.SeekBarWrap
import io.kinescope.sdk.shorts.view.SeekPlayerControl
import kotlinx.serialization.InternalSerializationApi



@UnstableApi @InternalSerializationApi

@OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoViewHolder(
    val binding: ShortsListVideoBinding,
    private val context: Context,
    private val videoPreparedListener: ViewPager2Adapter.OnVideoPreparedListener,
    private val activityProvider: ActivityProvider? = null,
) : RecyclerView.ViewHolder(binding.root) {

    var videoData: VideoData? = null
    private val seekBar: SeekBar =
        binding.playerView.findViewById<SeekBar>(R.id.seekBar).also { KinescopeUiConfig.applyToSeekBar(it) }
    private val seekBarWrap = SeekBarWrap(this)
    private val seekPlayerControl = SeekPlayerControl(null, seekBar)

    private val titleExpand = ExpandableTextHelper(binding.TitleVideo, expandedMaxLines = 5)
    private val descriptionExpand =
        ExpandableTextHelper(binding.descriptionVideo, expandedMaxLines = 10)


    /** Optional: pause Shorts preload while this page rebuffers. */
    var onRebufferingChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            playerManager.onRebufferingChanged = value
        }

    val playerManager = PlayerManager(
        context,
        videoPreparedListener,
        seekPlayerControl,
        seekBarWrap,
        this,
    )

    init {
        val layoutParams = binding.root.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        binding.root.layoutParams = layoutParams
        KinescopeUiConfig.applyToVideoItem(binding.root)
    }

    fun bindOnlineVideos(videoData: VideoData, preloadedPlayer: ExoPlayer? = null) {
        binding.playerView.player = null
        this.videoData = videoData
        val surfaceView = binding.playerView.videoSurfaceView as? SurfaceView
        surfaceView?.holder?.setFixedSize(1, 1)

        bindTextChrome(videoData)
        showThumbnail()

        playerManager.setupOnlinePlayer(
            videoData,
            absoluteAdapterPosition,
            binding,
            preloadedPlayer = preloadedPlayer,
        )
    }
    
    fun showThumbnail() {
        val placeholderRes = ThumbnailLoader.placeholderRes()
        val posterUrl = videoData?.posterUrl

        if (posterUrl.isNullOrBlank()) {
            if (placeholderRes == 0) {
                binding.thumbnailView.setImageDrawable(null)
                binding.thumbnailView.visibility = android.view.View.GONE
            } else {
                binding.thumbnailView.visibility = android.view.View.VISIBLE
                binding.thumbnailView.setImageResource(placeholderRes)
            }
            return
        }

        binding.thumbnailView.visibility = android.view.View.VISIBLE
        // Load poster until the first playback frame arrives (hideThumbnail() is called on Player.STATE_READY).
        val request = Glide.with(context)
            .load(posterUrl)
            .centerCrop()
        if (placeholderRes != 0) {
            request.placeholder(placeholderRes).error(placeholderRes)
        }
        request.into(binding.thumbnailView)
    }
    
    fun hideThumbnail() {
        binding.thumbnailView.visibility = android.view.View.GONE
    }

    fun onDetached() {
        titleExpand.onDetached()
        descriptionExpand.onDetached()
        titleExpand.collapse(animate = false)
        descriptionExpand.collapse(animate = false)
    }

    fun bindTextChrome(videoData: VideoData) {
        titleExpand.bind(videoData.title)
        val description = videoData.description?.trim().orEmpty()
        if (description.isEmpty()) {
            binding.descriptionVideo.visibility = android.view.View.GONE
            descriptionExpand.bind("")
        } else {
            binding.descriptionVideo.visibility = android.view.View.VISIBLE
            descriptionExpand.bind(description)
        }
    }
}
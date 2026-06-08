package io.kinescope.demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import io.kinescope.sdk.models.videos.KinescopeVideoApi

class VideosAdapter(
    private val onVideoClick: (String) -> Unit,
    private val onCopyLinkClick: (KinescopeVideoApi) -> Unit,
    private val onSelectionChanged: ((selectedIndex: Int, totalCount: Int) -> Unit)? = null,
) : RecyclerView.Adapter<VideosAdapter.ViewHolder>() {

    private val items = ArrayList<KinescopeVideoApi>()
    private var selectedVideoId: String? = null

    fun updateData(value: List<KinescopeVideoApi>) {
        items.clear()
        items.addAll(value)
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun setSelectedVideoId(videoId: String?) {
        if (selectedVideoId == videoId) {
            return
        }
        val previousIndex = items.indexOfFirst { it.id == selectedVideoId }
        val newIndex = items.indexOfFirst { it.id == videoId }
        selectedVideoId = videoId
        if (previousIndex >= 0) {
            notifyItemChanged(previousIndex)
        }
        if (newIndex >= 0) {
            notifyItemChanged(newIndex)
        }
        notifySelectionChanged()
    }

    private fun notifySelectionChanged() {
        val selectedIndex = items.indexOfFirst { it.id == selectedVideoId }
        onSelectionChanged?.invoke(
            if (selectedIndex >= 0) selectedIndex + 1 else 0,
            items.size,
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false),
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position + 1, items[position].id == selectedVideoId)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indexView: TextView = itemView.findViewById(R.id.tv_index)
        private val playingIcon: ImageView = itemView.findViewById(R.id.iv_playing)
        private val thumbnailView: ShapeableImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val durationView: TextView = itemView.findViewById(R.id.tv_duration)
        private val titleView: TextView = itemView.findViewById(R.id.tv_title)
        private val copyLinkButton: ImageButton = itemView.findViewById(R.id.btn_copy_link)
        private var video: KinescopeVideoApi? = null

        init {
            itemView.setOnClickListener {
                video?.id?.let(onVideoClick)
            }
            copyLinkButton.setOnClickListener {
                video?.let(onCopyLinkClick)
            }
        }

        fun bind(video: KinescopeVideoApi, index: Int, isSelected: Boolean) {
            this.video = video
            titleView.text = video.title
            itemView.isSelected = isSelected
            indexView.text = index.toString()
            indexView.isVisible = !isSelected
            playingIcon.isVisible = isSelected

            val durationText = formatDuration(video.duration)
            durationView.text = durationText
            durationView.isVisible = durationText != null

            val posterUrl = video.poster?.thumbnailUrl()
            if (posterUrl.isNullOrBlank()) {
                Glide.with(thumbnailView).clear(thumbnailView)
                thumbnailView.setImageDrawable(null)
            } else {
                Glide.with(thumbnailView)
                    .load(posterUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_playlist_thumbnail)
                    .error(R.drawable.bg_playlist_thumbnail)
                    .into(thumbnailView)
            }
        }
    }

    companion object {
        fun formatDuration(durationSeconds: Double?): String? {
            if (durationSeconds == null || durationSeconds <= 0) {
                return null
            }
            val totalSeconds = durationSeconds.toInt()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
    }
}

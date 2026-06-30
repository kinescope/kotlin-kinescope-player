package io.kinescope.sdk.playlist

import android.content.Context
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
import io.kinescope.sdk.R

internal class KinescopePlaylistMenuAdapter(
    private val onItemClick: (KinescopePlaylistItem) -> Unit,
    private val onCopyLinkClick: (KinescopePlaylistItem) -> Unit,
) : RecyclerView.Adapter<KinescopePlaylistMenuAdapter.ViewHolder>() {

    private val items = ArrayList<KinescopePlaylistItem>()
    private var selectedId: String? = null

    fun submitList(value: List<KinescopePlaylistItem>, selectedId: String?) {
        items.clear()
        items.addAll(value)
        this.selectedId = selectedId
        notifyDataSetChanged()
    }

    fun setSelectedId(id: String?) {
        if (selectedId == id) {
            return
        }
        val previousIndex = items.indexOfFirst { it.id == selectedId }
        val newIndex = items.indexOfFirst { it.id == id }
        selectedId = id
        if (previousIndex >= 0) {
            notifyItemChanged(previousIndex)
        }
        if (newIndex >= 0) {
            notifyItemChanged(newIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.view_playlist_menu_row, parent, false),
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position + 1, items[position].id == selectedId)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indexView: TextView = itemView.findViewById(R.id.playlist_row_index)
        private val playingIcon: ImageView = itemView.findViewById(R.id.playlist_row_playing)
        private val thumbnailView: ShapeableImageView = itemView.findViewById(R.id.playlist_row_thumbnail)
        private val durationView: TextView = itemView.findViewById(R.id.playlist_row_duration)
        private val titleView: TextView = itemView.findViewById(R.id.playlist_row_title)
        private val copyLinkButton: ImageButton = itemView.findViewById(R.id.playlist_row_copy_link)
        private var item: KinescopePlaylistItem? = null

        init {
            itemView.setOnClickListener {
                item?.let(onItemClick)
            }
            copyLinkButton.setOnClickListener {
                item?.let(onCopyLinkClick)
            }
        }

        fun bind(item: KinescopePlaylistItem, index: Int, isSelected: Boolean) {
            this.item = item
            titleView.text = item.title
            itemView.isSelected = isSelected
            indexView.text = index.toString()
            indexView.isVisible = !isSelected
            playingIcon.isVisible = isSelected
            copyLinkButton.isVisible = isSelected && !item.shareUrl.isNullOrBlank()

            val durationText = formatDuration(item.durationSeconds)
            durationView.text = durationText
            durationView.isVisible = durationText != null

            val posterUrl = item.posterUrl
            if (posterUrl.isNullOrBlank()) {
                Glide.with(thumbnailView).clear(thumbnailView)
                thumbnailView.setImageDrawable(null)
            } else {
                Glide.with(thumbnailView)
                    .load(posterUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_playlist_menu_thumbnail)
                    .error(R.drawable.bg_playlist_menu_thumbnail)
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

package io.kinescope.sdk.view

import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.sdk.R
import io.kinescope.sdk.player.subtitles.SubtitleSearchMatch
import io.kinescope.sdk.player.subtitles.SubtitleTranscriptEntry
import io.kinescope.sdk.utils.formatPlayerTime

internal class CaptionsSearchAdapter(
    private val onEntryClick: (SubtitleTranscriptEntry, Int) -> Unit,
) : ListAdapter<CaptionsSearchAdapter.Row, CaptionsSearchAdapter.ViewHolder>(Diff) {

    data class Row(
        val entry: SubtitleTranscriptEntry,
        val entryIndex: Int,
        val query: String,
        val isActiveMatch: Boolean,
        val hasMatch: Boolean,
        val showTime: Boolean,
        val isPlayingNow: Boolean,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_captions_search_row, parent, false)
        return ViewHolder(view, onEntryClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onEntryClick: (SubtitleTranscriptEntry, Int) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {

        private val timeView: TextView = itemView.findViewById(R.id.captions_search_row_time_tv)
        private val textView: TextView = itemView.findViewById(R.id.captions_search_row_text_tv)
        private val playingIndicator: View = itemView.findViewById(R.id.captions_search_row_indicator)
        private val highlightColor =
            ContextCompat.getColor(itemView.context, R.color.kinescope_caption_search_highlight)
        private val singleLineIndicatorHeight =
            itemView.resources.getDimensionPixelSize(R.dimen.kinescope_captions_search_indicator_height)
        private val multiLineIndicatorHeight =
            itemView.resources.getDimensionPixelSize(R.dimen.kinescope_captions_search_indicator_height_multiline)

        fun bind(row: Row) {
            val entry = row.entry
            timeView.isVisible = row.showTime
            if (row.showTime) {
                timeView.text = formatPlayerTime(entry.startTimeMs)
            }
            val textAlpha = when {
                row.isPlayingNow -> 1f
                row.query.isBlank() || row.hasMatch -> 1f
                else -> 0.45f
            }
            textView.alpha = textAlpha
            textView.text = buildHighlightedText(
                text = entry.text,
                query = row.query,
                highlightColor = highlightColor,
                emphasize = row.isActiveMatch,
            )
            updatePlayingIndicator(row.isPlayingNow)
            itemView.alpha = 1f
            itemView.setBackgroundColor(
                when {
                    row.isPlayingNow -> ContextCompat.getColor(
                        itemView.context,
                        R.color.kinescope_caption_search_line_hover,
                    )

                    row.isActiveMatch || row.showTime -> ContextCompat.getColor(
                        itemView.context,
                        R.color.kinescope_caption_search_line_hover,
                    )

                    else -> android.graphics.Color.TRANSPARENT
                },
            )
            itemView.setOnClickListener {
                onEntryClick(entry, row.entryIndex)
            }
        }

        private fun updatePlayingIndicator(isPlayingNow: Boolean) {
            playingIndicator.isVisible = isPlayingNow
            if (!isPlayingNow) {
                playingIndicator.updateLayoutParams {
                    height = singleLineIndicatorHeight
                }
                return
            }
            textView.doOnLayout {
                val targetHeight = if (textView.lineCount <= 1) {
                    singleLineIndicatorHeight
                } else {
                    textView.height.coerceAtLeast(multiLineIndicatorHeight)
                }
                playingIndicator.updateLayoutParams {
                    height = targetHeight
                }
            }
        }

        private fun buildHighlightedText(
            text: String,
            query: String,
            highlightColor: Int,
            emphasize: Boolean,
        ): CharSequence {
            if (query.isBlank()) {
                return text
            }
            val builder = SpannableStringBuilder(text)
            var start = 0
            while (true) {
                val found = text.indexOf(query, start, ignoreCase = true)
                if (found < 0) {
                    break
                }
                val end = found + query.length
                val color = if (emphasize) {
                    highlightColor
                } else {
                    adjustAlpha(highlightColor, 0.45f)
                }
                builder.setSpan(
                    BackgroundColorSpan(color),
                    found,
                    end,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                start = end
            }
            return builder
        }

        private fun adjustAlpha(color: Int, factor: Float): Int {
            val alpha = ((color ushr 24) * factor).toInt().coerceIn(0, 255)
            return (color and 0x00FFFFFF) or (alpha shl 24)
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean =
            oldItem.entryIndex == newItem.entryIndex

        override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean = oldItem == newItem
    }
}

internal fun buildCaptionsSearchRows(
    entries: List<SubtitleTranscriptEntry>,
    query: String,
    matches: List<SubtitleSearchMatch>,
    currentMatchIndex: Int,
    focusedEntryIndex: Int,
    playingEntryIndex: Int,
): List<CaptionsSearchAdapter.Row> {
    val activeEntryIndex = matches.getOrNull(currentMatchIndex)?.entryIndex
    val focusedIndex = when {
        focusedEntryIndex >= 0 -> focusedEntryIndex
        activeEntryIndex != null -> activeEntryIndex
        else -> -1
    }
    return entries.mapIndexed { index, entry ->
        val hasMatch = query.isNotBlank() &&
            entry.text.contains(query, ignoreCase = true)
        CaptionsSearchAdapter.Row(
            entry = entry,
            entryIndex = index,
            query = query,
            isActiveMatch = index == activeEntryIndex,
            hasMatch = hasMatch,
            showTime = index == focusedIndex,
            isPlayingNow = index == playingEntryIndex,
        )
    }
}

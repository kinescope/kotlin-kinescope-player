package io.kinescope.sdk.view

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.sdk.R
import io.kinescope.sdk.player.subtitles.SubtitleSearchMatch
import io.kinescope.sdk.player.subtitles.SubtitleTranscriptEntry
import io.kinescope.sdk.player.subtitles.SubtitleTranscriptParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class KinescopeCaptionsSearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    private val panel: LinearLayout
    private val queryInput: EditText
    private val counterView: TextView
    private val prevButton: ImageButton
    private val nextButton: ImageButton
    private val closeButton: ImageButton
    private val listView: RecyclerView
    private lateinit var adapter: CaptionsSearchAdapter

    private var entries: List<SubtitleTranscriptEntry> = emptyList()
    private var matches: List<SubtitleSearchMatch> = emptyList()
    private var currentMatchIndex: Int = 0
    private var focusedEntryIndex: Int = -1
    private var playingEntryIndex: Int = -1
    private var autoFollowPlayback: Boolean = true
    private var isFullscreenMode = false
    private var isPortraitFullscreenMode = false
    private var isPinnedToTop = false

    var onDismiss: (() -> Unit)? = null
    var onSeekToMs: ((Long) -> Unit)? = null
    var onVisibilityChanged: ((Boolean) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_captions_search_overlay, this, true)
        isVisible = false
        panel = findViewById(R.id.captions_search_panel)
        queryInput = findViewById(R.id.captions_search_query_et)
        counterView = findViewById(R.id.captions_search_counter_tv)
        prevButton = findViewById(R.id.captions_search_prev_btn)
        nextButton = findViewById(R.id.captions_search_next_btn)
        closeButton = findViewById(R.id.captions_search_close_btn)
        listView = findViewById(R.id.captions_search_list)
        applyModeLayout()

        adapter = CaptionsSearchAdapter { entry, entryIndex ->
            onSeekToMs?.invoke(entry.startTimeMs)
            focusedEntryIndex = entryIndex
            autoFollowPlayback = false
            val matchIndex = matches.indexOfFirst { it.entryIndex == entryIndex }
            if (matchIndex >= 0) {
                currentMatchIndex = matchIndex
            }
            renderList(preserveScroll = true)
            updateNavigationUi()
        }

        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = adapter
        listView.itemAnimator = null

        val iconTint = ContextCompat.getColor(context, R.color.white)
        findViewById<android.widget.ImageView>(R.id.captions_search_icon_iv)
            .setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        listOf(prevButton, nextButton, closeButton).forEach { button ->
            button.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        }

        closeButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    queryInput.clearFocus()
                    hideKeyboard()
                }
                MotionEvent.ACTION_UP -> dismiss()
            }
            true
        }
        prevButton.setOnClickListener { stepMatch(delta = -1) }
        nextButton.setOnClickListener { stepMatch(delta = 1) }
        queryInput.doAfterTextChanged { updateSearch(selectFirst = false) }
        queryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                updateSearch(selectFirst = true)
                true
            } else {
                false
            }
        }
    }

    fun setFullscreenMode(fullscreen: Boolean, portrait: Boolean = false) {
        if (isFullscreenMode == fullscreen && isPortraitFullscreenMode == portrait) {
            return
        }
        isFullscreenMode = fullscreen
        isPortraitFullscreenMode = portrait
        applyModeLayout()
    }

    /**
     * Whether the panel spans the player height: the fullscreen layout, or the
     * inline panel docked to the top by
     * [KinescopePlayerView.captionsSearchPlacement], which uses the same one.
     */
    fun isFullscreenLayout(): Boolean = isFullscreenMode || isPinnedToTop

    /**
     * Inline placement: docked to the top edge with the list filling down to
     * the control bar, instead of a fixed-height panel above the bar. The
     * fullscreen layout fills the player either way, so the pin only matters
     * while [isFullscreenLayout] is false. Driven by
     * [KinescopePlayerView.captionsSearchPlacement] — the one public handle —
     * so the two cannot drift apart.
     */
    internal fun setPinnedToTop(pinned: Boolean) {
        if (isPinnedToTop == pinned) {
            return
        }
        isPinnedToTop = pinned
        applyModeLayout()
    }

    private fun applyModeLayout() {
        val fills = isFullscreenLayout()
        val sideInset = when {
            isFullscreenMode && isPortraitFullscreenMode -> {
                resources.getDimensionPixelSize(
                    R.dimen.kinescope_captions_search_fullscreen_side_inset_portrait,
                )
            }
            isFullscreenMode -> {
                resources.getDimensionPixelSize(R.dimen.kinescope_captions_search_fullscreen_side_inset)
            }
            else -> {
                resources.getDimensionPixelSize(R.dimen.kinescope_mobile_control_margin_horizontal)
            }
        }

        if (layoutParams is FrameLayout.LayoutParams) {
            updateLayoutParams<FrameLayout.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = if (fills) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }
                gravity = if (fills) {
                    Gravity.FILL_HORIZONTAL or Gravity.TOP
                } else {
                    Gravity.BOTTOM
                }
                marginStart = 0
                marginEnd = 0
                leftMargin = 0
                rightMargin = 0
            }
        }

        (panel.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = if (fills) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            params.gravity = if (fills) {
                Gravity.FILL
            } else {
                Gravity.BOTTOM
            }
            params.marginStart = 0
            params.marginEnd = 0
            params.leftMargin = 0
            params.rightMargin = 0
            params.topMargin = 0
            params.bottomMargin = 0
            panel.layoutParams = params
        }

        panel.setPaddingRelative(
            sideInset,
            panel.paddingTop,
            sideInset,
            panel.paddingBottom,
        )

        val listParams = listView.layoutParams as? LinearLayout.LayoutParams ?: return
        if (fills) {
            listParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            listParams.height = 0
            listParams.weight = 1f
        } else {
            listParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            listParams.height = resources.getDimensionPixelSize(
                R.dimen.kinescope_captions_search_panel_max_height,
            )
            listParams.weight = 0f
        }
        listView.layoutParams = listParams

        if (isVisible) {
            requestLayout()
        }
    }

    fun show(subtitleUrl: String) {
        applyModeLayout()
        isVisible = true
        onVisibilityChanged?.invoke(true)
        entries = emptyList()
        matches = emptyList()
        currentMatchIndex = 0
        focusedEntryIndex = -1
        playingEntryIndex = -1
        autoFollowPlayback = true
        adapter.submitList(emptyList())
        queryInput.text?.clear()
        updateNavigationUi()
        loadJob?.cancel()
        loadJob = scope.launch {
            val content = withContext(Dispatchers.IO) { fetchSubtitleText(subtitleUrl) } ?: return@launch
            entries = SubtitleTranscriptParser.parse(content)
            renderList()
            updateSearch(selectFirst = false)
        }
        queryInput.requestFocus()
    }

    private fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun dismiss() {
        loadJob?.cancel()
        if (!isVisible) {
            return
        }
        isVisible = false
        playingEntryIndex = -1
        autoFollowPlayback = true
        onVisibilityChanged?.invoke(false)
        onDismiss?.invoke()
    }

    fun updatePlaybackPosition(positionMs: Long) {
        if (!isVisible || entries.isEmpty()) {
            return
        }
        val newPlayingIndex = SubtitleTranscriptParser.entryIndexAtTime(entries, positionMs)
        if (newPlayingIndex == playingEntryIndex) {
            return
        }
        playingEntryIndex = newPlayingIndex
        renderList()
        if (autoFollowPlayback && newPlayingIndex >= 0) {
            scrollToEntry(newPlayingIndex)
        }
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun updateSearch(selectFirst: Boolean) {
        val query = queryInput.text?.toString().orEmpty()
        matches = SubtitleTranscriptParser.findMatches(entries, query)
        currentMatchIndex = when {
            matches.isEmpty() -> 0
            selectFirst -> 0
            currentMatchIndex >= matches.size -> matches.lastIndex
            else -> currentMatchIndex
        }
        if (selectFirst) {
            autoFollowPlayback = false
        }
        renderList()
        updateNavigationUi()
        if (matches.isNotEmpty() && selectFirst) {
            focusCurrentMatch()
        }
    }

    private fun stepMatch(delta: Int) {
        if (matches.isEmpty()) {
            return
        }
        autoFollowPlayback = false
        currentMatchIndex = (currentMatchIndex + delta).mod(matches.size)
        renderList()
        updateNavigationUi()
        focusCurrentMatch()
    }

    private fun focusCurrentMatch() {
        val match = matches.getOrNull(currentMatchIndex) ?: return
        val entry = entries.getOrNull(match.entryIndex) ?: return
        focusedEntryIndex = match.entryIndex
        onSeekToMs?.invoke(entry.startTimeMs)
        scrollToEntry(match.entryIndex)
    }

    private fun scrollToEntry(entryIndex: Int) {
        if (entryIndex < 0) {
            return
        }
        listView.doOnLayout {
            val layoutManager = listView.layoutManager as? LinearLayoutManager ?: return@doOnLayout
            layoutManager.scrollToPositionWithOffset(entryIndex, listView.height / 4)
        }
    }

    private fun renderList(preserveScroll: Boolean = false) {
        val query = queryInput.text?.toString().orEmpty()
        val rows = buildCaptionsSearchRows(
            entries = entries,
            query = query,
            matches = matches,
            currentMatchIndex = currentMatchIndex,
            focusedEntryIndex = focusedEntryIndex,
            playingEntryIndex = playingEntryIndex,
        )
        if (preserveScroll) {
            val layoutManager = listView.layoutManager as? LinearLayoutManager
            val state = layoutManager?.onSaveInstanceState()
            adapter.submitList(rows) {
                layoutManager?.onRestoreInstanceState(state)
            }
            return
        }
        adapter.submitList(rows)
    }

    private fun updateNavigationUi() {
        val hasMatches = matches.isNotEmpty()
        counterView.isVisible = hasMatches
        prevButton.isVisible = hasMatches
        nextButton.isVisible = hasMatches
        counterView.text = if (hasMatches) {
            context.getString(
                R.string.settings_captions_search_counter,
                currentMatchIndex + 1,
                matches.size,
            )
        } else {
            ""
        }
    }

    private fun fetchSubtitleText(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
        }
        connection.inputStream.bufferedReader().use { it.readText() }.also {
            connection.disconnect()
        }
    }.getOrNull()
}

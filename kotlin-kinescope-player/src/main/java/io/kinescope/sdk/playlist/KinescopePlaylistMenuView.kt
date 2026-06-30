package io.kinescope.sdk.playlist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kinescope.sdk.R

class KinescopePlaylistMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val panel: LinearLayout
    private val countView: TextView
    private val listView: RecyclerView
    private val adapter: KinescopePlaylistMenuAdapter
    private var isHiding = false

    var onItemSelected: ((KinescopePlaylistItem) -> Unit)? = null
    var onCopyLinkClick: ((KinescopePlaylistItem) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_playlist_menu, this, true)
        isVisible = false
        panel = findViewById(R.id.playlist_menu_panel)
        countView = findViewById(R.id.playlist_menu_count)
        listView = findViewById(R.id.playlist_menu_list)

        adapter = KinescopePlaylistMenuAdapter(
            onItemClick = { item ->
                onItemSelected?.invoke(item)
                dismiss(animated = true)
            },
            onCopyLinkClick = { item ->
                onCopyLinkClick?.invoke(item)
            },
        )
        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = adapter
        listView.itemAnimator = null
    }

    fun setItems(items: List<KinescopePlaylistItem>, selectedId: String?) {
        adapter.submitList(items, selectedId)
        countView.text = resources.getString(R.string.player_playlist_count, items.size)
        updatePanelHeight()
    }

    fun setSelectedId(id: String?) {
        adapter.setSelectedId(id)
    }

    fun show() {
        if (adapter.itemCount == 0) {
            return
        }
        isHiding = false
        isVisible = true
        panel.isVisible = true
        panel.alpha = 1f
        panel.translationY = panel.height.toFloat()
        doOnLayout {
            panel.translationY = panel.height.toFloat()
            panel.animate()
                .translationY(0f)
                .setDuration(PANEL_ANIMATION_DURATION_MS)
                .start()
        }
    }

    fun dismiss(animated: Boolean = true) {
        if (!isVisible || isHiding) {
            return
        }
        if (!animated || !panel.isVisible) {
            finishDismiss()
            return
        }
        isHiding = true
        panel.animate()
            .translationY(panel.height.toFloat())
            .setDuration(PANEL_ANIMATION_DURATION_MS)
            .withEndAction { finishDismiss() }
            .start()
    }

    fun isShowing(): Boolean = isVisible && panel.isVisible

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isVisible) {
            return super.dispatchTouchEvent(event)
        }
        if (event.action == MotionEvent.ACTION_DOWN && panel.isVisible) {
            val location = IntArray(2)
            panel.getLocationOnScreen(location)
            val panelTop = location[1]
            if (event.rawY < panelTop) {
                dismiss(animated = true)
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun finishDismiss() {
        isHiding = false
        panel.animate().cancel()
        panel.isVisible = false
        panel.alpha = 1f
        panel.translationY = 0f
        isVisible = false
        onDismiss?.invoke()
    }

    private fun updatePanelHeight() {
        val maxHeight = resources.getDimensionPixelSize(R.dimen.kinescope_playlist_menu_max_height)
        listView.layoutParams = listView.layoutParams.apply {
            height = maxHeight
        }
    }

    private companion object {
        private const val PANEL_ANIMATION_DURATION_MS = 200L
    }
}

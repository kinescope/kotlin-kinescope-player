package io.kinescope.sdk.chapters

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import io.kinescope.sdk.R
import io.kinescope.sdk.models.videos.KinescopeVideoChapterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Chapters popup must not paint at top-start before re-anchoring to the
 * trailing edge (band flash: left for a frame, then right).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w800dp-h450dp-xhdpi")
class KinescopeChaptersViewPositionTest {

    private lateinit var activity: Activity
    private lateinit var host: FrameLayout
    private lateinit var chapters: KinescopeChaptersView

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        chapters = KinescopeChaptersView(activity, null)
        host = FrameLayout(activity).apply {
            addView(
                chapters,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        activity.setContentView(host)
        layoutHost()
    }

    /**
     * Shown before its first layout (it starts GONE): the popup waits for the
     * layout without spinning the main looper, then becomes opaque.
     * Robolectric may run the window's layout pass while the looper drains, so
     * the popup can already be shown here; what matters is that the queue empties.
     */
    @Test
    fun show_beforeLayout_waitsForLayoutWithoutLooping() {
        chapters.setChapters(listOf(KinescopeVideoChapterItem(time = 0, title = "One")))
        chapters.show()

        // Bounded instead of idleMainLooper(): a re-post loop would never drain.
        val looper = shadowOf(Looper.getMainLooper())
        repeat(MAX_TASKS) {
            if (!looper.isIdle) looper.runOneTask()
        }
        assertTrue("main looper keeps re-posting the popup", looper.isIdle)

        val popup = chapters.findViewById<View>(R.id.chapters_popup_container)
        layoutHost()

        assertEquals(1f, popup.alpha, 0.001f)
    }

    private fun layoutHost() {
        host.measure(
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 1600, 900)
    }

    @Test
    fun show_positionsPopupOnTrailingEdgeWithoutLeftFlash() {
        chapters.setChapters(
            listOf(
                KinescopeVideoChapterItem(time = 0, title = "One"),
                KinescopeVideoChapterItem(time = 12, title = "Two"),
            ),
        )
        chapters.show()

        // First frame after show: popup may be laid out but must stay transparent
        // until positionPopupWithinBounds runs on the posted callback.
        val popup = chapters.findViewById<View>(R.id.chapters_popup_container)
        assertTrue(popup.isVisible)
        assertEquals(0f, popup.alpha, 0.001f)

        layoutHost()
        ShadowLooper.idleMainLooper()
        layoutHost()

        assertEquals(1f, popup.alpha, 0.001f)
        val params = popup.layoutParams as FrameLayout.LayoutParams
        val endMargin = activity.resources
            .getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        assertEquals(chapters.width - popup.width - endMargin, params.leftMargin)
        assertTrue(params.leftMargin > chapters.width / 2)
    }

    private companion object {
        const val MAX_TASKS = 100
    }
}

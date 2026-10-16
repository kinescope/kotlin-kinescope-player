package io.kinescope.sdk.chapters

import android.app.Activity
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

        ShadowLooper.idleMainLooper()

        assertEquals(1f, popup.alpha, 0.001f)
        val params = popup.layoutParams as FrameLayout.LayoutParams
        val endMargin = activity.resources
            .getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        assertEquals(chapters.width - popup.width - endMargin, params.leftMargin)
        assertTrue(params.leftMargin > chapters.width / 2)
    }
}

package io.kinescope.sdk.view

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import io.kinescope.sdk.R
import io.kinescope.sdk.player.KinescopeVideoPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

/**
 * [KinescopePlayerView.captionsSearchPlacement] on the real inline view.
 *
 * The panel re-learns its layout on several paths — the layout-change listener
 * on every resize, [KinescopePlayerView.setIsFullscreen],
 * [KinescopePlayerView.adoptContentOrientationFrom], the show itself — and each
 * of them has to keep the placement, or the panel snaps back to the bottom edge
 * mid-gesture (the host's player band changing height under a sheet).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xxhdpi")
class KinescopePlayerViewCaptionsSearchPlacementTest {

    private lateinit var activity: Activity
    private lateinit var view: KinescopePlayerView
    private lateinit var player: KinescopeVideoPlayer

    @Before
    fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME_MS))
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = KinescopePlayerView(activity, null).apply { setIsFullscreen(false) }
        activity.setContentView(
            FrameLayout(activity).apply {
                addView(view, FrameLayout.LayoutParams(WIDTH_PX, SPLIT_BAND_PX))
            },
        )
        player = KinescopeVideoPlayer(activity)
        view.setPlayer(player)
        pumpFrames(4)
    }

    @After
    fun tearDown() {
        player.exoPlayer?.release()
    }

    @Test
    fun defaultPlacement_panelSitsAboveControlBar() {
        showSearch()

        val panel = bounds(R.id.captions_search_panel)
        assertTrue("panel not laid out", !panel.isEmpty)
        assertTrue("panel should hang below the top edge", panel.top > 0)
        assertPanelEndsAtControlBar()
    }

    @Test
    fun topPlacement_docksPanelToTopEdgeDownToControlBar() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()

        assertPinnedToTop()
    }

    @Test
    fun topPlacement_switchedWhileOpen_relayoutsPanel() {
        showSearch()
        assertTrue(bounds(R.id.captions_search_panel).top > 0)

        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        pumpFrames(FRAMES_TO_SETTLE)
        assertPinnedToTop()

        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.BOTTOM
        pumpFrames(FRAMES_TO_SETTLE)
        assertTrue("panel should be back at the bottom", bounds(R.id.captions_search_panel).top > 0)
        assertPanelEndsAtControlBar()
    }

    /** The band grows under a sheet: the resize re-syncs the panel layout. */
    @Test
    fun topPlacement_survivesBandResize() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()

        view.layoutParams = FrameLayout.LayoutParams(WIDTH_PX, TALL_BAND_PX)
        pumpFrames(FRAMES_TO_SETTLE)

        assertEquals(TALL_BAND_PX, view.height)
        assertPinnedToTop()
    }

    @Test
    fun topPlacement_survivesFullscreenRoundTrip() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()

        view.setIsFullscreen(true)
        pumpFrames(FRAMES_TO_SETTLE)
        view.setIsFullscreen(false)
        pumpFrames(FRAMES_TO_SETTLE)

        assertPinnedToTop()
    }

    /** Inline ↔ fullscreen view switch copies the content orientation and re-syncs the panel. */
    @Test
    fun topPlacement_survivesContentOrientationAdoption() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()
        val portraitPeer = KinescopePlayerView(activity, null).also { it.setPortraitContentForTest(true) }

        view.adoptContentOrientationFrom(portraitPeer)
        pumpFrames(FRAMES_TO_SETTLE)

        assertTrue("orientation should have been adopted", view.isPortraitContent)
        assertPinnedToTop()
    }

    private fun assertPinnedToTop() {
        val panel = bounds(R.id.captions_search_panel)
        assertTrue("panel not laid out", !panel.isEmpty)
        assertEquals("panel should start at the top edge", 0, panel.top)
        assertPanelEndsAtControlBar()
    }

    /** Ends right above the control bar: neither over it, nor short of it. */
    private fun assertPanelEndsAtControlBar() {
        val panel = bounds(R.id.captions_search_panel)
        val bar = bounds(R.id.kinescope_control_bar)
        val barPaddingBottom = view.findViewById<View>(R.id.kinescope_control_bar).paddingBottom
        assertFalse("control bar not laid out", bar.isEmpty)
        assertTrue("panel overlaps the control bar", panel.bottom <= bar.top)
        assertTrue("panel stops short of the control bar", panel.bottom >= bar.top - barPaddingBottom)
    }

    private fun showSearch() {
        // As on a subtitle tap: the transcript itself is not needed here, a
        // closed local port refuses the fetch right away.
        view.findViewById<KinescopeCaptionsSearchView>(R.id.captions_search_overlay).show(UNREACHABLE_VTT)
        pumpFrames(FRAMES_TO_SETTLE)
    }

    /** Bounds of a descendant in the player view's own coordinates. */
    private fun bounds(id: Int): Rect {
        val target = view.findViewById<View>(id)
        var left = 0
        var top = 0
        var node: View = target
        while (node !== view) {
            left += node.left
            top += node.top
            node = node.parent as View
        }
        return Rect(left, top, left + target.width, top + target.height)
    }

    /** The flag is fed by the engine's video size; here it is set directly. */
    private fun KinescopePlayerView.setPortraitContentForTest(portrait: Boolean) {
        KinescopePlayerView::class.java.getDeclaredField("isPortraitContent").apply {
            isAccessible = true
            setBoolean(this@setPortraitContentForTest, portrait)
        }
    }

    private fun pumpFrames(count: Int) {
        repeat(count) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val FRAMES_TO_SETTLE = 20

        // xxhdpi, 360 dp wide: compact (mobile) chrome.
        const val WIDTH_PX = 1080

        /** Half of a card scene — the band on the default split. */
        const val SPLIT_BAND_PX = 1200

        /** Sheet dragged down — the band well taller than the fixed 280 dp list. */
        const val TALL_BAND_PX = 2100

        const val UNREACHABLE_VTT = "http://127.0.0.1:9/subtitles.vtt"
    }
}

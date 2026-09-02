package io.kinescope.sdk.view

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
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
 *
 * The path tests first knock the panel's own pin out through the internal
 * [KinescopeCaptionsSearchView.setPinnedToTop]: the pin is sticky, so a path
 * that quietly stopped going through the one sync would otherwise pass on the
 * strength of an earlier sync. After the knock-out only the path under test
 * can put the panel back at the top.
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
    fun defaultPlacement_fixedHeightPanelAboveControlBar() {
        showSearch()

        val panel = bounds(R.id.captions_search_panel)
        assertFalse("panel not laid out", panel.isEmpty)
        assertTrue("panel should hang below the top edge", panel.top > 0)
        assertPanelEndsAtControlBar()
        assertEquals("list should keep its fixed height", fixedListHeightPx(), listHeight())
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
        assertEquals(fixedListHeightPx(), listHeight())
    }

    /** The band grows under a sheet: the resize re-syncs the panel, and the list fills the room. */
    @Test
    fun topPlacement_survivesBandResize() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()
        knockPinOut()

        view.layoutParams = FrameLayout.LayoutParams(WIDTH_PX, TALL_BAND_PX)
        pumpFrames(FRAMES_TO_SETTLE)

        assertEquals(TALL_BAND_PX, view.height)
        assertPinnedToTop()
        assertTrue("list should fill the tall band", listHeight() > fixedListHeightPx())
    }

    @Test
    fun topPlacement_survivesFullscreenRoundTrip() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()
        knockPinOut()

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
        knockPinOut()
        val portraitPeer = KinescopePlayerView(activity, null).also { it.setPortraitContentForTest(true) }

        view.adoptContentOrientationFrom(portraitPeer)
        pumpFrames(FRAMES_TO_SETTLE)

        assertTrue("orientation should have been adopted", view.isPortraitContent)
        assertPinnedToTop()
    }

    /** Edge-to-edge: the top edge is under the status bar and the cutout — the panel clears both. */
    @Test
    fun topPlacement_clearsSystemSafeArea() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()

        dispatchTopInsets(statusBar = STATUS_BAR_PX, cutout = CUTOUT_PX)

        val safeInset = expectedSafeInset(CUTOUT_PX)
        assertTrue("test needs a real overlap", safeInset > 0)
        assertPinnedToTop(expectedTop = safeInset)
    }

    /** The host draws its own header over the band: its inset stacks on the safe area. */
    @Test
    fun topPlacement_addsHostInsetOnTopOfSafeArea() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        view.captionsSearchTopInset = HOST_INSET_PX
        showSearch()

        dispatchTopInsets(statusBar = STATUS_BAR_PX)

        assertPinnedToTop(expectedTop = expectedSafeInset(STATUS_BAR_PX) + HOST_INSET_PX)
    }

    @Test
    fun hostInset_changedWhileOpen_relayoutsPanel() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()
        assertPinnedToTop(expectedTop = 0)

        view.captionsSearchTopInset = HOST_INSET_PX
        pumpFrames(FRAMES_TO_SETTLE)

        assertPinnedToTop(expectedTop = HOST_INSET_PX)
    }

    /** Insets and the host inset are a top-placement affair; the bottom panel keeps its geometry. */
    @Test
    fun defaultPlacement_ignoresInsets() {
        view.captionsSearchTopInset = HOST_INSET_PX
        showSearch()
        val before = bounds(R.id.captions_search_panel)

        dispatchTopInsets(statusBar = STATUS_BAR_PX, cutout = CUTOUT_PX)

        assertEquals(before, bounds(R.id.captions_search_panel))
        assertPanelEndsAtControlBar()
    }

    /**
     * Scrubbing raises a hint header along the top edge and lifts the control
     * overlay above the panel. With the panel docked to the top both would draw
     * over the search field — so neither happens while it is up.
     */
    @Test
    fun topPlacement_scrubKeepsChromeUnderThePanel() {
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()

        startScrub()

        assertFalse("scrub hint header over the search field", child(R.id.scrub_top_bar).isVisible)
        assertEquals("control overlay lifted over the panel", 0f, overlay().elevation)
        assertPinnedToTop()

        stopScrub()
        assertEquals(0f, overlay().elevation)
        assertPinnedToTop()
    }

    /** The bottom panel keeps the scrub chrome as it was: header up, overlay lifted. */
    @Test
    fun defaultPlacement_scrubRaisesHintHeader() {
        showSearch()

        startScrub()

        assertTrue(child(R.id.scrub_top_bar).isVisible)
        assertTrue(overlay().elevation > 0f)

        stopScrub()
        assertFalse(child(R.id.scrub_top_bar).isVisible)
        assertEquals(0f, overlay().elevation)
    }

    /** Wide (non-mobile) chrome: same docking, and the opaque scrub overlay stays under the panel. */
    @Test
    @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun topPlacement_inWideChrome_docksAndScrubStaysUnder() {
        assertFalse("test needs the wide chrome", child(R.id.kinescope_options_dots).isVisible)
        view.captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        showSearch()
        assertPinnedToTop()

        startScrub()

        assertFalse(child(R.id.scrub_top_bar).isVisible)
        assertEquals(0f, overlay().elevation)
        assertPinnedToTop()
    }

    private fun assertPinnedToTop(expectedTop: Int = 0) {
        val panel = bounds(R.id.captions_search_panel)
        assertFalse("panel not laid out", panel.isEmpty)
        assertEquals("panel top edge", expectedTop, panel.top)
        assertPanelEndsAtControlBar()
    }

    /** Ends right above the control bar: neither over it, nor short of it. */
    private fun assertPanelEndsAtControlBar() {
        val panel = bounds(R.id.captions_search_panel)
        val bar = bounds(R.id.kinescope_control_bar)
        val barPaddingBottom = child(R.id.kinescope_control_bar).paddingBottom
        assertFalse("control bar not laid out", bar.isEmpty)
        assertTrue("panel overlaps the control bar", panel.bottom <= bar.top)
        assertTrue("panel stops short of the control bar", panel.bottom >= bar.top - barPaddingBottom)
    }

    private fun showSearch() {
        // As on a subtitle tap: the transcript itself is not needed here, a
        // closed local port refuses the fetch right away.
        search().show(UNREACHABLE_VTT)
        pumpFrames(FRAMES_TO_SETTLE)
    }

    /** Drops the panel's own pin; only a path going through the view's sync can restore it. */
    private fun knockPinOut() {
        search().setPinnedToTop(false)
        pumpFrames(FRAMES_TO_SETTLE)
        assertTrue("knock-out should have dropped the panel", bounds(R.id.captions_search_panel).top > 0)
    }

    private fun dispatchTopInsets(statusBar: Int, cutout: Int = 0) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBar, 0, 0))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, cutout, 0, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
        pumpFrames(FRAMES_TO_SETTLE)
    }

    /** The overlap the view computes: inset measured from the screen top, minus where the view starts. */
    private fun expectedSafeInset(insetTop: Int): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return (insetTop - location[1]).coerceAtLeast(0)
    }

    private fun startScrub() {
        val bar = child(R.id.kinescope_progress) as KinescopeTimeBar
        bar.setDuration(SCRUB_DURATION_MS)
        val now = SystemClock.uptimeMillis()
        bar.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bar.width / 2f, bar.height / 2f, 0),
        )
        pumpFrames(FRAMES_TO_SETTLE)
        assertTrue("scrub did not start", scrubbing())
    }

    private fun stopScrub() {
        val bar = child(R.id.kinescope_progress) as KinescopeTimeBar
        // The chrome refresh on scrub start re-reads the (empty) engine timeline
        // and zeroes the bar's duration, after which it ignores touches.
        bar.setDuration(SCRUB_DURATION_MS)
        val now = SystemClock.uptimeMillis()
        bar.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, bar.width / 2f, bar.height / 2f, 0),
        )
        pumpFrames(FRAMES_TO_SETTLE)
        assertFalse("scrub did not stop", scrubbing())
    }

    private fun scrubbing(): Boolean {
        return KinescopePlayerView::class.java.getDeclaredField("scrubbing").run {
            isAccessible = true
            getBoolean(view)
        }
    }

    private fun listHeight(): Int = child(R.id.captions_search_list).height

    private fun fixedListHeightPx(): Int =
        view.resources.getDimensionPixelSize(R.dimen.kinescope_captions_search_panel_max_height)

    private fun search(): KinescopeCaptionsSearchView = child(R.id.captions_search_overlay) as KinescopeCaptionsSearchView

    private fun overlay(): View = child(R.id.view_control)

    private fun child(id: Int): View = view.findViewById(id)

    /** Bounds of a descendant in the player view's own coordinates. */
    private fun bounds(id: Int): Rect {
        val target = child(id)
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

        const val STATUS_BAR_PX = 150
        const val CUTOUT_PX = 210
        const val HOST_INSET_PX = 120
        const val SCRUB_DURATION_MS = 60_000L

        const val UNREACHABLE_VTT = "http://127.0.0.1:9/subtitles.vtt"
    }
}

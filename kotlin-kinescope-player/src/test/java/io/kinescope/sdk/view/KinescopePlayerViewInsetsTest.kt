package io.kinescope.sdk.view

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.kinescope.sdk.R
import io.kinescope.sdk.player.KinescopeVideoPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * How the view learns where the system safe area ends: window insets plus
 * its own position on screen. Observed through the top-docked captions search
 * panel, which clears that area.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xxhdpi")
class KinescopePlayerViewInsetsTest {

    private lateinit var container: FrameLayout
    private lateinit var view: KinescopePlayerView
    private lateinit var player: KinescopeVideoPlayer

    @Before
    fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME_MS))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = KinescopePlayerView(activity, null).apply {
            setIsFullscreen(false)
            captionsSearchPlacement = KinescopeCaptionsSearchPlacement.TOP
        }
        container = FrameLayout(activity).apply {
            addView(view, FrameLayout.LayoutParams(WIDTH_PX, BAND_HEIGHT_PX))
        }
        activity.setContentView(container)
        player = KinescopeVideoPlayer(activity)
        view.setPlayer(player)
        pumpFrames(4)
        view.findViewById<KinescopeCaptionsSearchView>(R.id.captions_search_overlay).show(UNREACHABLE_VTT)
        pumpFrames(FRAMES_TO_SETTLE)
    }

    @After
    fun tearDown() {
        player.exoPlayer?.release()
    }

    /**
     * The insets listener slot on the view belongs to the host. A host
     * installing its own after attach must not lose the safe-area overlap —
     * and must still get the insets itself.
     */
    @Test
    fun hostInsetsListener_afterAttach_bothTheHostAndTheViewSeeTheInsets() {
        var hostInsets: WindowInsetsCompat? = null
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            hostInsets = insets
            insets
        }

        dispatchTopInsets(statusBar = STATUS_BAR_PX)

        assertNotNull("host listener not called", hostInsets)
        assertEquals(STATUS_BAR_PX, hostInsets!!.getInsets(WindowInsetsCompat.Type.statusBars()).top)
        val expected = expectedSafeInset(STATUS_BAR_PX)
        assertTrue("test needs a real overlap", expected > 0)
        assertEquals("panel should clear the status bar", expected, panelTop())
    }

    /**
     * The band rides a sheet: the view moves on screen through its ancestors
     * (translation, scroll) with no layout of its own — the overlap follows.
     */
    @Test
    fun topPlacement_followsTheViewsScreenPosition() {
        dispatchTopInsets(statusBar = STATUS_BAR_PX)
        val before = panelTop()
        assertTrue("test needs a real overlap", before > SHIFT_PX)

        container.translationY = SHIFT_PX.toFloat()
        pumpFrames(FRAMES_TO_SETTLE)

        assertEquals(expectedSafeInset(STATUS_BAR_PX), panelTop())
        assertEquals(before - SHIFT_PX, panelTop())
    }

    private fun dispatchTopInsets(statusBar: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBar, 0, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
        pumpFrames(FRAMES_TO_SETTLE)
    }

    /** The overlap the view computes: inset measured from the screen top, minus where the view starts. */
    private fun expectedSafeInset(insetTop: Int): Int = (insetTop - screenY()).coerceAtLeast(0)

    private fun screenY(): Int = IntArray(2).also { view.getLocationOnScreen(it) }[1]

    private fun panelTop(): Int {
        val panel = view.findViewById<View>(R.id.captions_search_panel)
        var top = 0
        var node: View = panel
        while (node !== view) {
            top += node.top
            node = node.parent as View
        }
        val bounds = Rect(0, top, panel.width, top + panel.height)
        assertTrue("panel not laid out", !bounds.isEmpty)
        return bounds.top
    }

    private fun pumpFrames(count: Int) {
        repeat(count) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val FRAMES_TO_SETTLE = 20
        const val WIDTH_PX = 1080
        const val BAND_HEIGHT_PX = 1200
        // Larger than the decor offset of the view on this screen, so it really overlaps.
        const val STATUS_BAR_PX = 300
        const val SHIFT_PX = 60
        const val UNREACHABLE_VTT = "http://127.0.0.1:9/subtitles.vtt"
    }
}

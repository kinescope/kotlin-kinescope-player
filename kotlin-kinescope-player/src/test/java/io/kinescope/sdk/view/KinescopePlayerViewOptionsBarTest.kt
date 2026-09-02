package io.kinescope.sdk.view

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
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
 * Compact options bar (the "…" strip) against the control overlay fade.
 *
 * Runs on the real view inside an Activity window with the Choreographer
 * paused and frames pumped 16 ms at a time, so layout passes, `post()`
 * runnables and animators land in the order they take on a device: a `post()`
 * queued from a click runs after the next traversal, animators advance per
 * frame instead of completing at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xxhdpi")
class KinescopePlayerViewOptionsBarTest {

    private lateinit var view: KinescopePlayerView
    private lateinit var player: KinescopeVideoPlayer

    @Before
    fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME_MS))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = KinescopePlayerView(activity, null).apply { setIsFullscreen(false) }
        activity.setContentView(
            FrameLayout(activity).apply {
                addView(view, FrameLayout.LayoutParams(PORTRAIT_WIDTH_PX, BAND_HEIGHT_PX))
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
    fun dotsTap_expandsStrip() {
        clickDots()
        pumpFrames(FRAMES_TO_SETTLE)

        assertStripExpanded()
    }

    /**
     * Tapping the dots while the overlay fades out (auto-hide, or a tap on the
     * video) looked swallowed: the button is still clickable during the fade,
     * the click ran the expansion, then the fade's end action hid the overlay
     * and force-collapsed the bar. Tapping a control means "keep the chrome".
     */
    @Test
    fun dotsTapDuringOverlayFadeOut_expandsStripAndKeepsOverlay() {
        tapVideo()
        pumpUntil { overlay().alpha < 1f }
        pumpFrames(5)
        assertTrue("fade-out should be running", overlay().isVisible && overlay().alpha in 0.01f..0.99f)

        clickDots()
        pumpFrames(FRAMES_TO_SETTLE)

        assertTrue("overlay hidden by the fade end", overlay().isVisible)
        assertEquals(1f, overlay().alpha)
        assertStripExpanded()
    }

    /**
     * The expansion finishes in a deferred pass. When the bar gets collapsed in
     * between — an overlay fade end, a chrome mode change, a view switch; here
     * the host flips the view to fullscreen, which re-applies the chrome — the
     * pass measured a zero strip (its buttons were hidden by the collapse) and
     * still "finished": progress row and timer gone, end spacer on, bar
     * collapsed.
     */
    @Test
    fun deferredExpandPass_afterBarCollapsed_keepsProgressChrome() {
        clickDots()
        view.setIsFullscreen(true)
        pumpFrames(FRAMES_TO_SETTLE)

        assertTrue("progress row hidden", child(R.id.kinescope_progress_container).isVisible)
        assertTrue("timer hidden", child(R.id.kinescope_time_container).isVisible)
        assertFalse("end spacer left on", child(R.id.kinescope_control_bar_end_spacer).isVisible)
        assertFalse("strip left on", child(R.id.kinescope_options_expandable_strip).isVisible)
    }

    private fun assertStripExpanded() {
        val strip = child(R.id.kinescope_options_expandable_strip)
        assertTrue("strip not visible", strip.isVisible)
        assertTrue("strip has no width", strip.width > 0)
        assertTrue("settings button not visible", child(R.id.kinescope_settings).isVisible)
    }

    private fun overlay(): View = child(R.id.view_control)

    private fun child(id: Int): View = view.findViewById(id)

    private fun clickDots() {
        child(R.id.kinescope_options_dots).performClick()
    }

    /** Single tap on the video area: toggles the control overlay. */
    private fun tapVideo() {
        val x = 30f
        val y = view.height / 2f
        val downTime = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        pumpFrames(1)
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0),
        )
        // GestureDetector confirms a single tap after the double-tap timeout.
        pumpFrames(ViewConfiguration.getDoubleTapTimeout() / FRAME_MS.toInt() + 2)
    }

    private fun pumpFrames(count: Int) {
        repeat(count) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
        }
    }

    private fun pumpUntil(condition: () -> Boolean) {
        repeat(MAX_WAIT_FRAMES) {
            if (condition()) return
            pumpFrames(1)
        }
        assertTrue("condition not met within $MAX_WAIT_FRAMES frames", condition())
    }

    private companion object {
        const val FRAME_MS = 16L
        const val FRAMES_TO_SETTLE = 40
        const val MAX_WAIT_FRAMES = 400

        // xxhdpi, 360 dp wide: compact (mobile) chrome with the dots button.
        const val PORTRAIT_WIDTH_PX = 1080
        const val BAND_HEIGHT_PX = 608
    }
}

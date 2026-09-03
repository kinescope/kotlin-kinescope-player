package io.kinescope.sdk.view

import android.app.Activity
import android.graphics.Rect
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
     * Same race, zero frames in: the hide was requested but its fade has not
     * rendered a frame yet, so the overlay is still fully opaque. `show` used
     * to take its fully-visible fast path before cancelling the animator, and
     * the fade's end action still hid the overlay and collapsed the bar.
     */
    @Test
    fun dotsTapAfterHideRequested_beforeFirstFadeFrame_expandsStripAndKeepsOverlay() {
        // Hold the first animation frame back: looper time advances, frames do not.
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(HELD_FRAME_MS))
        tapVideo()
        assertTrue("overlay must still be fully visible", overlay().isVisible)
        assertEquals(1f, overlay().alpha)
        clickDots()
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME_MS))
        // The expansion's first frame was scheduled under the long delay: wait it out.
        pumpFrames(HELD_FRAME_MS.toInt() / FRAME_MS.toInt() + FRAMES_TO_SETTLE)
        assertTrue("overlay hidden by the fade end", overlay().isVisible)
        assertEquals(1f, overlay().alpha)
        assertStripExpanded()
    }

    /**
     * A press that lands during the fade is released after it: the button was
     * hit-tested while visible, so the release (and the click it posts) still
     * reaches it once the fade's end action has hidden the overlay, cleared
     * the "hiding" flag and force-collapsed the bar. The handler saw neither a
     * fade to cancel nor a visible overlay to check and expanded the strip on
     * hidden chrome; the next tap on the video raised the overlay with the
     * strip open instead of the progress row. A finger stays down 80–120 ms,
     * the fade runs 200 ms — the window is real.
     */
    @Test
    fun dotsPressedDuringFade_releasedAfterFadeEnd_showsOverlayAndExpandsStrip() {
        tapVideo()
        pumpUntil { overlay().alpha < 1f }
        pumpFrames(5)
        assertTrue("fade-out should be running", overlay().isVisible && overlay().alpha in 0.01f..0.99f)

        pressDots()
        pumpUntil { !overlay().isVisible }
        releaseDots()
        pumpFrames(FRAMES_TO_SETTLE)

        assertTrue("overlay left hidden", overlay().isVisible)
        assertEquals(1f, overlay().alpha)
        assertStripExpanded()
        assertFalse("progress row should give way to the strip", child(R.id.kinescope_progress_container).isVisible)
        assertFalse("timer should give way to the strip", child(R.id.kinescope_time_container).isVisible)
    }

    /**
     * Same race, then the user closes the strip: the progress row and the timer
     * come back, and auto-hide — held off while the strip is open — is armed
     * again by the collapse. A plain tap on the video keeps toggling the chrome.
     */
    @Test
    fun dotsPressedDuringFade_releasedAfterFadeEnd_collapseRestoresProgressChromeAndAutoHide() {
        markPlaybackStarted()
        tapVideo()
        pumpUntil { overlay().alpha < 1f }
        pumpFrames(5)

        pressDots()
        pumpUntil { !overlay().isVisible }
        releaseDots()
        pumpFrames(FRAMES_TO_SETTLE)
        assertTrue("overlay left hidden", overlay().isVisible)
        assertStripExpanded()

        pumpFrames(AUTO_HIDE_FRAMES + FRAMES_TO_SETTLE)
        assertTrue("open strip must hold the chrome", overlay().isVisible)
        assertStripExpanded()

        clickDots()
        pumpFrames(FRAMES_TO_SETTLE)
        assertTrue("overlay hidden by the collapse", overlay().isVisible)
        assertFalse("strip left on", child(R.id.kinescope_options_expandable_strip).isVisible)
        assertTrue("progress row not restored", child(R.id.kinescope_progress_container).isVisible)
        assertTrue("timer not restored", child(R.id.kinescope_time_container).isVisible)

        pumpFrames(AUTO_HIDE_FRAMES + FRAMES_TO_SETTLE)
        assertFalse("auto-hide not re-armed by the collapse", overlay().isVisible)

        tapVideo()
        pumpFrames(FRAMES_TO_SETTLE)
        assertTrue("tap on the video should raise the chrome", overlay().isVisible)
        assertEquals(1f, overlay().alpha)
        assertFalse("strip should stay closed", child(R.id.kinescope_options_expandable_strip).isVisible)
        assertTrue("progress row should be back", child(R.id.kinescope_progress_container).isVisible)
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

    private var dotsDownTime = 0L

    /** Finger down on the dots through the real pipeline: root → overlay → button. */
    private fun pressDots() {
        dotsDownTime = SystemClock.uptimeMillis()
        touchDots(MotionEvent.ACTION_DOWN)
    }

    /** Release goes to the view captured on press, whatever the overlay is by now. */
    private fun releaseDots() {
        touchDots(MotionEvent.ACTION_UP)
    }

    private fun touchDots(action: Int) {
        val dots = child(R.id.kinescope_options_dots)
        val bounds = Rect()
        dots.getDrawingRect(bounds)
        view.offsetDescendantRectToMyCoords(dots, bounds)
        view.dispatchTouchEvent(
            MotionEvent.obtain(
                dotsDownTime,
                SystemClock.uptimeMillis(),
                action,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                0,
            ),
        )
    }

    /**
     * Auto-hide arms only once playback has started, and Robolectric has no
     * media to play: flip the flag the way `onIsPlayingChanged(true)` would.
     */
    private fun markPlaybackStarted() {
        KinescopePlayerView::class.java.getDeclaredField("hasStartedPlayback")
            .apply { isAccessible = true }
            .setBoolean(view, true)
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
        const val HELD_FRAME_MS = 1000L
        const val FRAMES_TO_SETTLE = 40
        const val MAX_WAIT_FRAMES = 400

        // KinescopePlayerView.CONTROL_OVERLAY_AUTO_HIDE_MS in frames.
        const val AUTO_HIDE_FRAMES = (3000L / FRAME_MS).toInt()

        // xxhdpi, 360 dp wide: compact (mobile) chrome with the dots button.
        const val PORTRAIT_WIDTH_PX = 1080
        const val BAND_HEIGHT_PX = 608
    }
}

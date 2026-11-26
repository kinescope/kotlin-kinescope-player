package io.kinescope.sdk.view

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KinescopeWatermarkViewTest {

    private lateinit var view: KinescopeWatermarkView

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = KinescopeWatermarkView(activity)
        activity.setContentView(
            FrameLayout(activity).apply {
                addView(view, FrameLayout.LayoutParams(1080, 608))
            },
        )
    }

    @Test
    fun hiddenWithoutWatermark() {
        assertEquals(View.GONE, view.visibility)
        assertNull(view.text)
        assertFalse(view.isClickable)
    }

    @Test
    fun watermark_showsTheView() {
        view.watermark = KinescopeWatermarkStyle(text = "WATERMARK test")

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("WATERMARK test", view.text)
        assertTrue(view.isTextShown)
    }

    @Test
    fun clearing_hidesTheView() {
        view.watermark = KinescopeWatermarkStyle(text = "WATERMARK test")
        view.watermark = null

        assertEquals(View.GONE, view.visibility)
        assertNull(view.text)
    }

    @Test
    fun random_movesAfterTheInterval() {
        view.watermark = KinescopeWatermarkStyle(text = "WATERMARK test")
        val before = view.position

        advance(KinescopeWatermarkView.MOVE_INTERVAL_MS)

        assertNotEquals(before, view.position)
    }

    @Test
    fun displayTimeout_hidesAndShowsInTurn() {
        view.watermark = KinescopeWatermarkStyle(text = "x", visibleMs = 2_000, hiddenMs = 1_000)

        assertTrue(view.isTextShown)
        advance(2_000)
        assertFalse(view.isTextShown)
        advance(1_000)
        assertTrue(view.isTextShown)
    }

    @Test
    fun stripes_drawWithoutErrors() {
        view.watermark = KinescopeWatermarkStyle(
            text = "WATERMARK test",
            mode = KinescopeWatermarkStyle.Mode.STRIPES,
            scale = 0.53f,
        )
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(608, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 608)

        view.draw(Canvas(Bitmap.createBitmap(1080, 608, Bitmap.Config.ARGB_8888)))
    }

    private fun advance(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }
}

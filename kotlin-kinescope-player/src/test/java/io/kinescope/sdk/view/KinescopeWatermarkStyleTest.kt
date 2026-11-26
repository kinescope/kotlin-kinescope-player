package io.kinescope.sdk.view

import android.graphics.Color
import io.kinescope.sdk.models.videos.KinescopeSdkWatermark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KinescopeWatermarkStyleTest {

    @Test
    fun serverExample_isResolved() {
        val style = KinescopeWatermarkStyle.from(
            KinescopeSdkWatermark(
                text = "WATERMARK test123124124124",
                scale = 0.53f,
                displayTimeout = 29000.0,
                mode = "stripes",
                color = "#FFFFFFA1",
            ),
        )!!

        assertEquals(KinescopeWatermarkStyle.Mode.STRIPES, style.mode)
        assertEquals(0.53f, style.scale, 0.0001f)
        assertEquals(29000L, style.visibleMs)
        assertEquals(29000L, style.hiddenMs)
        assertTrue(style.blinks)
        assertEquals(Color.argb(0xA1, 0xFF, 0xFF, 0xFF), style.color)
    }

    @Test
    fun textOnly_usesDefaults() {
        val style = KinescopeWatermarkStyle.from(KinescopeSdkWatermark(text = " id-42 "))!!

        assertEquals("id-42", style.text)
        assertEquals(KinescopeWatermarkStyle.Mode.RANDOM, style.mode)
        assertEquals(KinescopeWatermarkStyle.DEFAULT_SCALE, style.scale, 0f)
        assertFalse(style.blinks)
        assertEquals(KinescopeWatermarkStyle.DEFAULT_COLOR, style.color)
    }

    @Test
    fun blankOrMissingText_isNoWatermark() {
        assertNull(KinescopeWatermarkStyle.from(null))
        assertNull(KinescopeWatermarkStyle.from(KinescopeSdkWatermark(text = "  ")))
    }

    @Test
    fun cssColors_areConvertedToArgb() {
        assertEquals(Color.argb(0xA1, 0xFF, 0x00, 0x80), KinescopeWatermarkStyle.parseCssColor("#FF0080A1"))
        assertEquals(Color.argb(0xFF, 0x12, 0x34, 0x56), KinescopeWatermarkStyle.parseCssColor("#123456"))
        assertEquals(Color.argb(0xFF, 0xFF, 0xFF, 0xFF), KinescopeWatermarkStyle.parseCssColor("#fff"))
        assertNull(KinescopeWatermarkStyle.parseCssColor("red"))
        assertNull(KinescopeWatermarkStyle.parseCssColor("#12345"))
    }

    @Test
    fun timeout_numberOrObject() {
        assertEquals(3000L to 3000L, KinescopeWatermarkStyle.parseTimeout(3000.0))
        assertEquals(3000L to 1000L, KinescopeWatermarkStyle.parseTimeout(mapOf("visible" to 3000.0, "hidden" to 1000.0)))
        assertEquals(0L to 0L, KinescopeWatermarkStyle.parseTimeout(null))
    }

    @Test
    fun invalidScale_fallsBackToDefault() {
        val style = KinescopeWatermarkStyle.from(KinescopeSdkWatermark(text = "x", scale = -1f))!!

        assertEquals(KinescopeWatermarkStyle.DEFAULT_SCALE, style.scale, 0f)
    }
}

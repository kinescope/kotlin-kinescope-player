package io.kinescope.sdk.models.videos

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KinescopeSdkWatermarkTest {

    private val adapter = Moshi.Builder().build().adapter(KinescopeSdk::class.java)

    @Test
    fun allFields_areParsedFromSdkBlock() {
        val sdk = adapter.fromJson(
            """
            {
              "metric_url": "https://player-metrics.kinescope.dev/v1/batch",
              "watermark": {
                "text": "WATERMARK test123124124124",
                "scale": 0.53,
                "display_timeout": 29000,
                "mode": "stripes",
                "color": "#FFFFFFA1"
              }
            }
            """.trimIndent(),
        )!!

        val wm = sdk.watermark!!
        assertEquals("https://player-metrics.kinescope.dev/v1/batch", sdk.metricUrl)
        assertEquals("WATERMARK test123124124124", wm.text)
        assertEquals(0.53f, wm.scale!!, 0.0001f)
        assertEquals(29000.0, (wm.displayTimeout as Number).toDouble(), 0.0)
        assertEquals("stripes", wm.mode)
        assertEquals("#FFFFFFA1", wm.color)
    }

    @Test
    fun textOnly_isParsed() {
        val sdk = adapter.fromJson(
            """{ "metric_url": null, "watermark": { "text": "WATERMARK test" } }""",
        )!!

        assertEquals("WATERMARK test", sdk.watermark?.text)
        assertNull(sdk.watermark?.scale)
        assertNull(sdk.watermark?.displayTimeout)
    }

    @Test
    fun timeoutObject_isParsed() {
        val sdk = adapter.fromJson(
            """{ "metric_url": null, "watermark": { "text": "x", "display_timeout": { "visible": 3000, "hidden": 1000 } } }""",
        )!!

        val timeout = sdk.watermark?.displayTimeout as Map<*, *>
        assertEquals(3000.0, (timeout["visible"] as Number).toDouble(), 0.0)
    }

    @Test
    fun noWatermark_isNull() {
        val sdk = adapter.fromJson("""{ "metric_url": "https://m" }""")!!

        assertNull(sdk.watermark)
    }
}

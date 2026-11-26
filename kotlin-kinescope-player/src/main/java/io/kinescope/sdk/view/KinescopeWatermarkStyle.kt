package io.kinescope.sdk.view

import android.graphics.Color
import androidx.annotation.ColorInt
import io.kinescope.sdk.models.videos.KinescopeSdkWatermark

/** Resolved server watermark: defaults applied, CSS values converted for Android. */
internal data class KinescopeWatermarkStyle(
    val text: String,
    val mode: Mode = Mode.RANDOM,
    val scale: Float = DEFAULT_SCALE,
    val visibleMs: Long = 0L,
    val hiddenMs: Long = 0L,
    @ColorInt val color: Int = DEFAULT_COLOR,
) {
    enum class Mode { RANDOM, STRIPES }

    /** Shown for [visibleMs], hidden for [hiddenMs], in turn. */
    val blinks: Boolean
        get() = visibleMs > 0L && hiddenMs > 0L

    companion object {
        const val DEFAULT_SCALE = 0.25f
        val DEFAULT_COLOR: Int = Color.argb(102, 255, 255, 255) // white, 40 %

        /** `null` when there is no watermark or its text is blank. */
        fun from(source: KinescopeSdkWatermark?): KinescopeWatermarkStyle? {
            val text = source?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val (visible, hidden) = parseTimeout(source.displayTimeout)
            return KinescopeWatermarkStyle(
                text = text,
                mode = if (source.mode.equals("stripes", ignoreCase = true)) Mode.STRIPES else Mode.RANDOM,
                scale = source.scale?.takeIf { it.isFinite() && it > 0f }?.coerceAtMost(1f) ?: DEFAULT_SCALE,
                visibleMs = visible,
                hiddenMs = hidden,
                color = parseCssColor(source.color) ?: DEFAULT_COLOR,
            )
        }

        /** Number → same visible and hidden time; `{visible, hidden}` → as given. */
        internal fun parseTimeout(value: Any?): Pair<Long, Long> = when (value) {
            is Number -> value.toLong().coerceAtLeast(0L).let { it to it }
            is Map<*, *> -> {
                val visible = (value["visible"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                val hidden = (value["hidden"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: visible
                visible to hidden
            }
            else -> 0L to 0L
        }

        /** CSS `#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA` → ARGB int; `null` if invalid. */
        @ColorInt
        internal fun parseCssColor(value: String?): Int? {
            val hex = value?.trim()?.removePrefix("#") ?: return null
            if (hex.any { Character.digit(it, 16) < 0 }) return null
            val full = when (hex.length) {
                3, 4 -> hex.map { "$it$it" }.joinToString("")
                6, 8 -> hex
                else -> return null
            }
            val r = full.substring(0, 2).toInt(16)
            val g = full.substring(2, 4).toInt(16)
            val b = full.substring(4, 6).toInt(16)
            val a = if (full.length == 8) full.substring(6, 8).toInt(16) else 255
            return Color.argb(a, r, g, b)
        }
    }
}

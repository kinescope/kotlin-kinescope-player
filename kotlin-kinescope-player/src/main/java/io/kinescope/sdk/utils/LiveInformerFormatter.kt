package io.kinescope.sdk.utils

import android.content.res.Resources
import android.os.Build
import io.kinescope.sdk.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private val UTC = TimeZone.getTimeZone("UTC")

private val ISO8601_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss",
)

private fun liveInformerSubtitlePattern(locale: Locale): String {
    return if (locale.language.equals("ru", ignoreCase = true)) {
        "d MMMM, HH:mm"
    } else {
        "MMMM d, HH:mm"
    }
}

object LiveInformerFormatter {

    fun formatSubtitle(startDate: String, locale: Locale = Locale.getDefault()): String {
        val startMs = parseLiveStartDateMillis(startDate) ?: return String()
        return try {
            val formatter = SimpleDateFormat(liveInformerSubtitlePattern(locale), locale)
            formatter.timeZone = TimeZone.getDefault()
            formatter.format(startMs)
        } catch (_: Exception) {
            String()
        }
    }

    fun parseLiveStartDateMillis(startDate: String): Long? {
        val trimmed = startDate.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        trimmed.toLongOrNull()?.let { numeric ->
            return if (numeric < 1_000_000_000_000L) {
                numeric * 1_000L
            } else {
                numeric
            }
        }

        for (pattern in ISO8601_PATTERNS) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    if (!pattern.endsWith("XXX")) {
                        timeZone = UTC
                    }
                    isLenient = false
                }
                formatter.parse(trimmed)?.time?.let { return it }
            } catch (_: Exception) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return java.time.Instant.parse(trimmed).toEpochMilli()
            } catch (_: Exception) {
            }
        }

        return null
    }

    fun formatTitle(resources: Resources, startDate: String, nowMs: Long = System.currentTimeMillis()): String {
        val startMs = parseLiveStartDateMillis(startDate) ?: return String()
        val diffMs = startMs - nowMs
        if (diffMs <= TimeUnit.SECONDS.toMillis(1)) {
            return resources.getString(R.string.live_informer_awaiting_broadcast)
        }

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMs).coerceAtLeast(1L)
        val totalHours = TimeUnit.MILLISECONDS.toHours(diffMs).coerceAtLeast(1L)
        val totalDays = TimeUnit.MILLISECONDS.toDays(diffMs).coerceAtLeast(1L)

        return when {
            totalMinutes <= 120 -> resources.getQuantityString(
                R.plurals.live_informer_in_minutes,
                totalMinutes.toInt(),
                totalMinutes.toInt(),
            )
            totalHours <= 48 -> resources.getQuantityString(
                R.plurals.live_informer_in_hours,
                totalHours.toInt(),
                totalHours.toInt(),
            )
            else -> resources.getQuantityString(
                R.plurals.live_informer_in_days,
                totalDays.toInt(),
                totalDays.toInt(),
            )
        }
    }

    fun needsCountdownUpdates(startDate: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val startMs = parseLiveStartDateMillis(startDate) ?: return false
        val diffMs = startMs - nowMs
        return diffMs in 1..TimeUnit.MINUTES.toMillis(120)
    }
}

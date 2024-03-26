package io.kinescope.sdk.utils

import java.text.SimpleDateFormat
import java.util.Locale

private const val DATE_FORMAT_ISO8601_STR = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
private const val DATE_FORMAT_LIVE_START_DATE = "MMMM d, hh:mm a"

fun formatLiveStartDate(startDate: String): String =
    try {
        val dateFormatISO8601 = SimpleDateFormat(DATE_FORMAT_ISO8601_STR, Locale.getDefault())
        val dateFormatLiveStartDate =
            SimpleDateFormat(DATE_FORMAT_LIVE_START_DATE, Locale.getDefault())
        dateFormatLiveStartDate.format(dateFormatISO8601.parse(startDate)!!)
    } catch (e: Exception) {
        String()
    }

fun currentTimestamp() = (System.currentTimeMillis() / 1000).toInt()

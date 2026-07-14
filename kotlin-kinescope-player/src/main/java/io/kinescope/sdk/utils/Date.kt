package io.kinescope.sdk.utils

fun formatLiveStartDate(startDate: String): String =
    LiveInformerFormatter.formatSubtitle(startDate)

fun currentTimestamp() = (System.currentTimeMillis() / 1000).toInt()

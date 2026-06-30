package io.kinescope.sdk.models.videos

fun KinescopeVideo.availableChapters(): List<KinescopeVideoChapterItem> =
    chapters.items.orEmpty()
        .filter { it.title.isNotBlank() }
        .sortedBy { it.startTimeMs() }

fun KinescopeVideoChapterItem.startTimeMs(): Long = time.toLong()

fun List<KinescopeVideoChapterItem>.chapterAt(positionMs: Long): KinescopeVideoChapterItem? =
    filter { it.startTimeMs() <= positionMs }
        .maxByOrNull { it.startTimeMs() }

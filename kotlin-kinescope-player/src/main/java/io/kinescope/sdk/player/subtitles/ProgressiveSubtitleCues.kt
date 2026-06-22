package io.kinescope.sdk.player.subtitles

import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal object ProgressiveSubtitleCues {

    fun buildState(
        cues: List<Cue>,
        positionUs: Long,
        cueStartUs: Long,
        cueId: Long,
        cueEndUs: Long = C.TIME_UNSET,
    ): ProgressiveSubtitleState? {
        if (cueStartUs == C.TIME_UNSET) {
            return null
        }

        val lines = extractLines(cues)
        val words = extractWords(cues)
        if (words.isEmpty()) {
            return null
        }

        if (positionUs < cueStartUs) {
            return null
        }

        val lineCount = lines.size.coerceAtLeast(1)
        val endUs = estimateCueEndUs(cueStartUs, lineCount, cueEndUs)
        val visibleLineCount = if (positionUs >= endUs) {
            lineCount
        } else {
            val exactProgress = computeExactProgress(positionUs, cueStartUs, endUs, lineCount)
            countVisibleUnits(exactProgress, lineCount)
        }.coerceAtLeast(1)

        val visibleCount = wordCountForVisibleLines(lines, visibleLineCount)
            .coerceIn(1, words.size)

        return buildStateForVisibleCount(
            words = words,
            visibleCount = visibleCount,
            cueId = cueId,
        )
    }

    fun extractLines(cues: List<Cue>): List<String> {
        return cues
            .mapNotNull { cue ->
                cue.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }
            .flatMap { text ->
                text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
    }

    fun extractWords(cues: List<Cue>): List<String> {
        return extractLines(cues).flatMap { line ->
            line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
    }

    fun wordCountForVisibleLines(lines: List<String>, visibleLineCount: Int): Int {
        if (visibleLineCount <= 0 || lines.isEmpty()) {
            return 0
        }
        return lines.take(visibleLineCount.coerceAtMost(lines.size))
            .sumOf { line -> line.split(Regex("\\s+")).filter { it.isNotEmpty() }.size }
    }

    fun estimateCueEndUs(
        cueStartUs: Long,
        wordCount: Int,
        cueEndUs: Long = C.TIME_UNSET,
    ): Long {
        return resolveCueEndUs(cueStartUs, cueEndUs, wordCount)
    }

    fun cueVisibleUntilUs(cueStartUs: Long, wordCount: Int, cueEndUs: Long = C.TIME_UNSET): Long {
        return estimateCueEndUs(cueStartUs, wordCount, cueEndUs) + CUE_VISIBLE_TAIL_US
    }

    fun stableCueId(cueStartUs: Long, @Suppress("UNUSED_PARAMETER") words: List<String>): Long {
        return cueStartUs
    }

    fun buildStateForVisibleCount(
        words: List<String>,
        visibleCount: Int,
        cueId: Long,
    ): ProgressiveSubtitleState? {
        if (visibleCount <= 0 || words.isEmpty()) {
            return null
        }

        val count = visibleCount.coerceIn(1, words.size)

        return ProgressiveSubtitleState(
            topLine = words.take(count).joinToString(" "),
            bottomLine = "",
            cueId = cueId,
            visibleWordCount = count,
            words = words,
        )
    }

    private fun countVisibleUnits(exactProgress: Float, unitCount: Int): Int {
        if (exactProgress <= 0f) {
            return 0
        }
        val whole = exactProgress.toInt()
        val count = if (exactProgress > whole) whole + 1 else whole
        return count.coerceIn(0, unitCount)
    }

    private fun resolveCueEndUs(cueStartUs: Long, cueEndUs: Long, wordCount: Int): Long {
        return if (cueEndUs != C.TIME_UNSET && cueEndUs > cueStartUs) {
            cueEndUs
        } else {
            cueStartUs + estimateCueDurationUs(wordCount)
        }
    }

    private fun computeExactProgress(
        positionUs: Long,
        cueStartUs: Long,
        endUs: Long,
        wordCount: Int,
    ): Float {
        val durationUs = (endUs - cueStartUs).coerceAtLeast(1)
        return (positionUs - cueStartUs).toFloat() / durationUs * wordCount
    }

    private fun estimateCueDurationUs(wordCount: Int): Long {
        return (wordCount * PER_WORD_US).coerceAtLeast(MIN_CUE_DURATION_US)
    }

    private const val PER_WORD_US = 480_000L
    private const val MIN_CUE_DURATION_US = 1_500_000L
    private const val CUE_VISIBLE_TAIL_US = 4_000_000L
}

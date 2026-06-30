package io.kinescope.sdk.player.subtitles

/**
 * Words accumulate on the bottom line. On overflow the bottom line moves up (replacing the top)
 * and the new word starts the next bottom line.
 */
internal object ProgressiveSubtitleLineLayout {

    fun appendWord(
        topLine: String,
        bottomLine: String,
        word: String,
        lineFits: (String) -> Boolean,
    ): Pair<String, String> {
        if (bottomLine.isEmpty()) {
            return "" to word
        }

        val candidate = joinWords(bottomLine, word)
        if (lineFits(candidate)) {
            return topLine to candidate
        }

        return bottomLine to word
    }

    fun buildLines(
        words: List<String>,
        visibleCount: Int,
        lineFits: (String) -> Boolean,
    ): Pair<String, String> {
        if (visibleCount <= 0 || words.isEmpty()) {
            return "" to ""
        }

        var topLine = ""
        var bottomLine = ""
        val count = visibleCount.coerceAtMost(words.size)

        for (index in 0 until count) {
            val (newTop, newBottom) = appendWord(
                topLine = topLine,
                bottomLine = bottomLine,
                word = words[index],
                lineFits = lineFits,
            )
            topLine = newTop
            bottomLine = newBottom
        }

        return topLine to bottomLine
    }

    /**
     * Word counts at which a wrapped line is complete and should be revealed at once
     * (roll-up to the top line or end of cue).
     */
    fun lineRevealWordCounts(
        words: List<String>,
        lineFits: (String) -> Boolean,
    ): List<Int> {
        if (words.isEmpty()) {
            return emptyList()
        }

        val stages = mutableListOf<Int>()
        var topLine = ""
        var bottomLine = ""

        for (index in words.indices) {
            val previousBottom = bottomLine
            val (newTop, newBottom) = appendWord(
                topLine = topLine,
                bottomLine = bottomLine,
                word = words[index],
                lineFits = lineFits,
            )
            val rolledUp = newTop != topLine && previousBottom.isNotEmpty()
            topLine = newTop
            bottomLine = newBottom
            if (rolledUp || index == words.lastIndex) {
                stages.add(index + 1)
            }
        }

        return stages
    }

    fun snapToLineRevealWordCount(
        rawWordCount: Int,
        words: List<String>,
        lineFits: (String) -> Boolean,
    ): Int {
        if (rawWordCount <= 0 || words.isEmpty()) {
            return 0
        }
        return lineRevealWordCounts(words, lineFits)
            .filter { it <= rawWordCount }
            .maxOrNull()
            ?: 0
    }

    private fun joinWords(line: String, word: String): String {
        return if (line.isEmpty()) word else "$line $word"
    }
}

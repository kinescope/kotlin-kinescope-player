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

    private fun joinWords(line: String, word: String): String {
        return if (line.isEmpty()) word else "$line $word"
    }
}

package io.kinescope.sdk.player.subtitles

internal data class ProgressiveSubtitleState(
    val topLine: String,
    val bottomLine: String,
    val cueId: Long,
    val visibleWordCount: Int,
    val words: List<String>,
) {
    fun isEmpty(): Boolean = topLine.isBlank() && bottomLine.isBlank()

    fun hasTopLine(): Boolean = topLine.isNotBlank()

    fun hasSecondLine(): Boolean = topLine.isNotBlank() && bottomLine.isNotBlank()

    fun displayText(): String = when {
        topLine.isBlank() -> bottomLine
        bottomLine.isBlank() -> topLine
        else -> "$topLine\n$bottomLine"
    }
}

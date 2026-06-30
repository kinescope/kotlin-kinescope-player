package io.kinescope.sdk.player.subtitles

internal data class SubtitleTranscriptEntry(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
)

internal data class SubtitleSearchMatch(
    val entryIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
)

internal object SubtitleTranscriptParser {

    fun parse(content: String): List<SubtitleTranscriptEntry> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        return if (trimmed.startsWith("WEBVTT", ignoreCase = true)) {
            parseWebVtt(trimmed)
        } else {
            parseSrt(trimmed)
        }
    }

    fun findMatches(entries: List<SubtitleTranscriptEntry>, query: String): List<SubtitleSearchMatch> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        val matches = mutableListOf<SubtitleSearchMatch>()
        entries.forEachIndexed { index, entry ->
            var start = 0
            while (true) {
                val found = entry.text.indexOf(needle, start, ignoreCase = true)
                if (found < 0) {
                    break
                }
                matches += SubtitleSearchMatch(
                    entryIndex = index,
                    startOffset = found,
                    endOffset = found + needle.length,
                )
                start = found + needle.length
            }
        }
        return matches
    }

    fun entryIndexAtTime(entries: List<SubtitleTranscriptEntry>, positionMs: Long): Int {
        if (entries.isEmpty() || positionMs < 0L) {
            return -1
        }
        var fallbackIndex = -1
        entries.forEachIndexed { index, entry ->
            if (positionMs < entry.startTimeMs) {
                return fallbackIndex
            }
            fallbackIndex = index
            val endMs = when {
                entry.endTimeMs > entry.startTimeMs -> entry.endTimeMs
                else -> entries.getOrNull(index + 1)?.startTimeMs ?: Long.MAX_VALUE
            }
            if (positionMs < endMs) {
                return index
            }
        }
        return fallbackIndex
    }

    private fun parseWebVtt(content: String): List<SubtitleTranscriptEntry> {
        val blocks = content
            .lineSequence()
            .dropWhile { line ->
                line.isBlank() || line.startsWith("WEBVTT", ignoreCase = true) || line.contains("::")
            }
            .joinToString("\n")
            .split(Regex("\n{2,}"))
        return blocks.mapNotNull(::parseTimedBlock)
    }

    private fun parseSrt(content: String): List<SubtitleTranscriptEntry> {
        return content
            .split(Regex("\n{2,}"))
            .mapNotNull { block ->
                val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size < 2) {
                    return@mapNotNull null
                }
                val timingIndex = if (lines[0].contains("-->")) 0 else 1
                if (timingIndex >= lines.size) {
                    return@mapNotNull null
                }
                val timingLine = lines[timingIndex]
                val textLines = lines.drop(timingIndex + 1)
                parseTimedText(timingLine, textLines.joinToString("\n"))
            }
    }

    private fun parseTimedBlock(block: String): SubtitleTranscriptEntry? {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return null
        }
        val timingIndex = lines.indexOfFirst { it.contains("-->") }
        if (timingIndex < 0) {
            return null
        }
        val text = lines.drop(timingIndex + 1).joinToString("\n")
        return parseTimedText(lines[timingIndex], text)
    }

    private fun parseTimedText(timingLine: String, text: String): SubtitleTranscriptEntry? {
        val cleanedText = stripCueTags(text).trim()
        if (cleanedText.isEmpty()) {
            return null
        }
        val parts = timingLine.split("-->")
        if (parts.size != 2) {
            return null
        }
        val start = parseTimestamp(parts[0].trim()) ?: return null
        val end = parseTimestamp(parts[1].trim().substringBefore(' ')) ?: return null
        return SubtitleTranscriptEntry(startTimeMs = start, endTimeMs = end, text = cleanedText)
    }

    private fun stripCueTags(text: String): String =
        text.replace(Regex("<[^>]+>"), "")

    private fun parseTimestamp(value: String): Long? {
        val normalized = value.trim().replace(',', '.')
        val segments = normalized.split(':')
        return when (segments.size) {
            3 -> {
                val hours = segments[0].toLongOrNull() ?: return null
                val minutes = segments[1].toLongOrNull() ?: return null
                val seconds = segments[2].toDoubleOrNull() ?: return null
                ((hours * 3600L + minutes * 60L) * 1000L + (seconds * 1000.0).toLong())
            }

            2 -> {
                val minutes = segments[0].toLongOrNull() ?: return null
                val seconds = segments[1].toDoubleOrNull() ?: return null
                ((minutes * 60L) * 1000L + (seconds * 1000.0).toLong())
            }

            else -> null
        }
    }
}

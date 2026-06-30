package io.kinescope.sdk.settings

data class SubtitleStyle(
    val fontColor: Int = 0xFFFFFFFF.toInt(),
    val fontSizePercent: Int = 100,
    val bgColor: Int = 0xFF000000.toInt(),
    val bgOpacityPercent: Int = 75,
)

data class SubtitleColorOption(
    val label: String,
    val color: Int,
)

object SubtitleStyleDefaults {
    val colors: List<SubtitleColorOption> = listOf(
        SubtitleColorOption("White", 0xFFFFFFFF.toInt()),
        SubtitleColorOption("Green", 0xFF00FF00.toInt()),
        SubtitleColorOption("Cyan", 0xFF00FFFF.toInt()),
        SubtitleColorOption("Blue", 0xFF0000FF.toInt()),
        SubtitleColorOption("Magenta", 0xFFFF00FF.toInt()),
        SubtitleColorOption("Red", 0xFFFF0000.toInt()),
        SubtitleColorOption("Black", 0xFF000000.toInt()),
    )

    val fontSizes: List<Int> = listOf(50, 75, 100, 125, 150, 200, 300)
    val bgOpacities: List<Int> = listOf(0, 25, 50, 75, 100)

    fun colorLabel(color: Int, customLabel: String): String =
        colors.firstOrNull { it.color == color }?.label ?: customLabel
}

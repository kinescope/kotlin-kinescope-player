package io.kinescope.sdk.ui



import androidx.compose.runtime.Composable

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.ReadOnlyComposable

import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.ui.graphics.Color



data class PlayerThemeColors(

    val accent: Color = PlayerTokens.Colors.Accent,
    val menuBackground: Color = PlayerTokens.Colors.MenuBackground,
    val feedbackBackground: Color = PlayerTokens.Colors.FeedbackBackground,
    val textPrimary: Color = PlayerTokens.Colors.TextPrimary,
    val textSecondary: Color = PlayerTokens.Colors.TextSecondary,
    val iconPrimary: Color = PlayerTokens.Colors.IconPrimary,
    val iconBar: Color = PlayerTokens.Colors.IconBar,
    val iconDimmed: Color = PlayerTokens.Colors.IconDimmed,
    val seekGray: Color = PlayerTokens.Colors.SeekGray,
    val ccActive: Color = PlayerTokens.Colors.CcActive,
    val timelinePlayed: Color = PlayerTokens.Colors.Accent,
    val timelineBuffered: Color = PlayerTokens.Colors.TimelineBuffered,
    val timelineTrack: Color = PlayerTokens.Colors.TimelineTrack,
    val playButtonFill: Color = PlayerTokens.Colors.PlayButtonFill,
    val scrim: Color = PlayerTokens.Colors.Scrim,
)

data class PlayerThemeSlots(
    val centerOverlay: (@Composable (PlayerUiStateSlot) -> Unit)? = null,
    val controlBarStart: (@Composable (PlayerUiStateSlot) -> Unit)? = null,
    val controlBarEnd: (@Composable (PlayerUiStateSlot) -> Unit)? = null,
    val settingsTrailing: (@Composable (PlayerUiStateSlot) -> Unit)? = null,
)

data class PlayerUiStateSlot(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)



data class PlayerTheme(
    val colors: PlayerThemeColors = PlayerThemeColors(),
    val slots: PlayerThemeSlots = PlayerThemeSlots(),
)
val LocalPlayerTheme = staticCompositionLocalOf { PlayerTheme() }


@Composable
fun KinescopePlayerTheme(
    theme: PlayerTheme = PlayerTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPlayerTheme provides theme, content = content)
}
@Composable
@ReadOnlyComposable
fun playerColors(): PlayerThemeColors = LocalPlayerTheme.current.colors
@Composable
@ReadOnlyComposable
fun playerSlots(): PlayerThemeSlots = LocalPlayerTheme.current.slots



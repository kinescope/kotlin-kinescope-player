package io.kinescope.sdk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PlayerTokens {

    object Colors {
        val MenuBackground = Color(0xFF111111)
        val FeedbackBackground = Color(0xCC111111)
        val Accent = Color(0xFF6161FC)
        val PlayButtonFill = Color(0xA36161FC)
        val TimelineTrack = Color(0x52FFFFFF)
        val TimelineBuffered = Color(0x52FFFFFF)
        val TextPrimary = Color(0xFFFFFFFF)
        val TextSecondary = Color(0xA3FFFFFF)
        val IconPrimary = Color(0xFFFFFFFF)
        val IconBar = Color(0xFFFFFFFF)
        val SeekGray = Color(0xFF9E9E9E)
        val IconDimmed = Color(0xFFC4C4C4)
        val CcActive = Color(0xFFA33937)
        val Scrim = Color(0x66000000)
    }

    object Dimens {
        val MenuWidth = 280.dp
        val MenuRadius = 6.dp
        val MenuPaddingH = 16.dp
        val MenuPaddingV = 8.dp
        val RowHeight = 36.dp
        val RowIcon = 24.dp
        val ControlBarGap = 12.dp
        val ControlIcon = 28.dp
        val TimelineHeight = 4.dp
        val TimelineThumb = 12.dp
        val FeedbackCircle = 64.dp
        val CenterGap = 24.dp
    }

    object Type {
        val Body14 = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
        val Body12 = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
        val Medium14 = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    }
}

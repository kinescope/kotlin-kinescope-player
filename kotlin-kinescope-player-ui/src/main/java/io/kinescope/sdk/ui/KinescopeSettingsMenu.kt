package io.kinescope.sdk.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kinescope.sdk.ui.R

private enum class SettingsScreen {
    Main, Speed, Quality, Audio, Subtitles,
    SubOptions, SubFontColor, SubFontSize, SubBgColor, SubBgOpacity,
}

private val ROW_HEIGHT = 36.dp

private val SUBMENU_CHROME = 52.dp
private val OPTIONS_HARD_MAX = 320.dp
private const val NAV_MS = 200
private const val FADE_MS = 140

@OptIn(UnstableApi::class)
@Composable
fun KinescopeSettingsMenu(
    controller: KinescopeComposePlayerController,
    modifier: Modifier = Modifier,
    maxHeight: Dp = OPTIONS_HARD_MAX + SUBMENU_CHROME,
    onDismiss: () -> Unit = {},
) {
    val state by controller.uiState.collectAsStateWithLifecycle()
    val subStyle by controller.subtitleStyleState
    val colors = playerColors()
    var screen by remember { mutableStateOf(SettingsScreen.Main) }
    val optionsMax = (maxHeight - SUBMENU_CHROME).coerceIn(ROW_HEIGHT * 2, OPTIONS_HARD_MAX)

    Box(
        modifier = modifier
            .width(280.dp)
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.menuBackground),
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                val slide = tween<IntOffset>(NAV_MS, easing = FastOutSlowInEasing)
                (slideInHorizontally(slide) { w -> dir * w } + fadeIn(tween(FADE_MS)))
                    .togetherWith(
                        slideOutHorizontally(slide) { w -> -dir * w } + fadeOut(tween(FADE_MS)),
                    )
                    .using(SizeTransform(clip = true) { _, _ -> tween(NAV_MS, easing = FastOutSlowInEasing) })
            },
            label = "settings-nav",
        ) { target ->
            when (target) {
                SettingsScreen.Main -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    MenuRow(
                        icon = R.drawable.ic_menu_cc,
                        label = "Субтитры",
                        value = state.subtitles.firstOrNull { it.id == state.selectedSubtitleId }?.label ?: "Выкл",
                        onClick = { screen = SettingsScreen.Subtitles },
                        colors = colors,
                    )
                    MenuRow(
                        icon = R.drawable.ic_menu_speed,
                        label = "Скорость",
                        value = speedLabel(state.speed),
                        onClick = { screen = SettingsScreen.Speed },
                        colors = colors,
                    )
                    MenuRow(
                        icon = R.drawable.ic_menu_quality,
                        label = "Качество",
                        value = state.qualities.firstOrNull { it.id == state.selectedQualityId }?.label ?: "Авто",
                        onClick = { screen = SettingsScreen.Quality },
                        colors = colors,
                    )
                    if (state.audioTracks.size >= 2) {
                        MenuRow(
                            icon = R.drawable.ic_menu_audio,
                            label = "Аудио",
                            value = state.audioTracks.firstOrNull { it.id == state.selectedAudioTrackId }?.label.orEmpty(),
                            onClick = { screen = SettingsScreen.Audio },
                            colors = colors,
                        )
                    }
                }

                SettingsScreen.Speed -> Submenu(
                    title = "Скорость",
                    onBack = { screen = SettingsScreen.Main },
                    selectedIndex = SPEED_OPTIONS.indexOfFirst { kotlin.math.abs(state.speed - it) < 0.01f },
                    optionsMax = optionsMax,
                    colors = colors,
                ) {
                    SPEED_OPTIONS.forEach { sp ->
                        CheckRow(
                            label = speedItemLabel(sp),
                            checked = kotlin.math.abs(state.speed - sp) < 0.01f,
                            onClick = { controller.setSpeed(sp); screen = SettingsScreen.Main },
                            colors = colors,
                        )
                    }
                }

                SettingsScreen.Quality -> Submenu(
                    title = "Качество",
                    onBack = { screen = SettingsScreen.Main },
                    selectedIndex = state.qualities.indexOfFirst { it.id == state.selectedQualityId },
                    optionsMax = optionsMax,
                    colors = colors,
                ) {
                    state.qualities.forEach { q ->
                        CheckRow(
                            label = q.label,
                            badge = q.badge,
                            checked = q.id == state.selectedQualityId,
                            onClick = { controller.setQuality(q.id); screen = SettingsScreen.Main },
                            colors = colors,
                        )
                    }
                }

                SettingsScreen.Audio -> Submenu(
                    title = "Аудио",
                    onBack = { screen = SettingsScreen.Main },
                    selectedIndex = state.audioTracks.indexOfFirst { it.id == state.selectedAudioTrackId },
                    optionsMax = optionsMax,
                    colors = colors,
                ) {
                    state.audioTracks.forEach { track ->
                        CheckRow(
                            label = track.label,
                            checked = track.id == state.selectedAudioTrackId,
                            onClick = { controller.selectAudioTrack(track.id); screen = SettingsScreen.Main },
                            colors = colors,
                        )
                    }
                }

                SettingsScreen.Subtitles -> Submenu(
                    title = "Субтитры",
                    onBack = { screen = SettingsScreen.Main },
                    selectedIndex = state.subtitles.indexOfFirst { it.id == state.selectedSubtitleId },
                    optionsMax = optionsMax,
                    colors = colors,
                    action = "Настройки" to { screen = SettingsScreen.SubOptions },
                ) {
                    state.subtitles.forEach { s ->
                        CheckRow(
                            label = s.label,
                            checked = s.id == state.selectedSubtitleId,
                            onClick = { controller.selectSubtitle(s.id); onDismiss() },
                            colors = colors,
                        )
                    }
                }
                SettingsScreen.SubOptions -> Submenu(
                    title = "Настройки",
                    onBack = { screen = SettingsScreen.Subtitles },
                    selectedIndex = -1,
                    optionsMax = optionsMax,
                    action = "Сбросить" to { controller.resetSubtitleStyle() },
                    colors = colors,
                ) {
                    OptionRow("Цвет шрифта", subtitleColorLabel(subStyle.fontColor), { screen = SettingsScreen.SubFontColor }, colors)
                    OptionRow("Размер шрифта", "${subStyle.fontSizePercent}%", { screen = SettingsScreen.SubFontSize }, colors)
                    OptionRow("Цвет фона", subtitleColorLabel(subStyle.bgColor), { screen = SettingsScreen.SubBgColor }, colors)
                    OptionRow("Прозрачность фона", "${subStyle.bgOpacityPercent}%", { screen = SettingsScreen.SubBgOpacity }, colors)
                }

                SettingsScreen.SubFontColor -> Submenu(
                    title = "Цвет шрифта",
                    onBack = { screen = SettingsScreen.SubOptions },
                    selectedIndex = SUBTITLE_COLORS.indexOfFirst { it.second == subStyle.fontColor },
                    optionsMax = optionsMax,
                    colors = colors,
                ) {
                    SUBTITLE_COLORS.forEach { (label, color) ->
                        CheckRow(
                            label = label,
                            checked = color == subStyle.fontColor,
                            onClick = { controller.setSubtitleFontColor(color); screen = SettingsScreen.SubOptions },
                            colors = colors,
                        )
                    }
                }

                SettingsScreen.SubFontSize -> Submenu(
                    title = "Размер шрифта",
                    onBack = { screen = SettingsScreen.SubOptions },
                    selectedIndex = SUBTITLE_FONT_SIZES.indexOfFirst { it == subStyle.fontSizePercent },
                    optionsMax = optionsMax,
                    colors = colors,
                ) {
                    SUBTITLE_FONT_SIZES.forEach { sz ->
                        CheckRow(
                            label = "$sz%",
                            checked = sz == subStyle.fontSizePercent,
                            onClick = { controller.setSubtitleFontSize(sz); screen = SettingsScreen.SubOptions },
                            colors = colors,
                        )
                    }
                }

                SettingsScreen.SubBgColor -> Submenu(
                    title = "Цвет фона",
                    onBack = { screen = SettingsScreen.SubOptions },
                    selectedIndex = SUBTITLE_COLORS.indexOfFirst { it.second == subStyle.bgColor },
                    optionsMax = optionsMax,
                    colors = colors,
                ) {
                    SUBTITLE_COLORS.forEach { (label, color) ->
                        CheckRow(
                            label = label,
                            checked = color == subStyle.bgColor,
                            onClick = { controller.setSubtitleBgColor(color); screen = SettingsScreen.SubOptions },
                            colors = colors,
                        )
                    }
                }

                SettingsScreen.SubBgOpacity -> Submenu(
                    title = "Прозрачность фона",
                    onBack = { screen = SettingsScreen.SubOptions },
                    selectedIndex = SUBTITLE_OPACITIES.indexOfFirst { it == subStyle.bgOpacityPercent },
                    optionsMax = optionsMax,
                    colors = colors,
                ) {
                    SUBTITLE_OPACITIES.forEach { op ->
                        CheckRow(
                            label = "$op%",
                            checked = op == subStyle.bgOpacityPercent,
                            onClick = { controller.setSubtitleBgOpacity(op); screen = SettingsScreen.SubOptions },
                            colors = colors,
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun MenuRow(
    icon: Int,
    label: String,
    value: String,
    onClick: () -> Unit,
    colors: PlayerThemeColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBox(icon, colors)
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 14.sp, lineHeight = 21.sp, fontFamily = RobotoFontFamily,
            maxLines = 1,
        )
        Text(
            text = value,
            color = colors.textSecondary,
            fontSize = 12.sp, lineHeight = 18.sp, fontFamily = RobotoFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        IconBox(R.drawable.ic_arrow_forward, colors)
    }
}

@Composable
private fun OptionRow(label: String, value: String, onClick: () -> Unit, colors: PlayerThemeColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 14.sp, lineHeight = 21.sp, fontFamily = RobotoFontFamily,
            maxLines = 1,
        )
        Text(
            text = value,
            color = colors.textSecondary,
            fontSize = 12.sp, lineHeight = 18.sp, fontFamily = RobotoFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        IconBox(R.drawable.ic_arrow_forward, colors)
    }
}

@Composable
private fun Submenu(
    title: String,
    onBack: () -> Unit,
    selectedIndex: Int,
    optionsMax: Dp,
    colors: PlayerThemeColors,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        if (selectedIndex > 0) {
            val target = with(density) { (selectedIndex * ROW_HEIGHT.toPx()).toInt() }
            scroll.scrollTo(target)
        }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .clickableNoRipple(onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Назад",
                modifier = Modifier.height(16.dp),
                colorFilter = ColorFilter.tint(colors.iconPrimary),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
                fontFamily = RobotoFontFamily,
            )
            if (action != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = action.first,
                    color = colors.textSecondary,
                    fontSize = 14.sp, lineHeight = 21.sp, fontFamily = RobotoFontFamily,
                    modifier = Modifier.clickableNoRipple(action.second),
                )
            }
        }
        Column(
            Modifier
                .heightIn(max = optionsMax)
                .verticalScroll(scroll),
        ) {
            content()
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    colors: PlayerThemeColors,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                append(label)
                if (badge != null) {
                    append(" ")
                    withStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        ),
                    ) { append(badge) }
                }
            },
            color = colors.textPrimary,
            fontSize = 14.sp, lineHeight = 21.sp, fontFamily = RobotoFontFamily,
            modifier = Modifier.weight(1f),
        )
        if (checked) IconBox(R.drawable.ic_check, colors)
    }
}

@Composable
private fun IconBox(icon: Int, colors: PlayerThemeColors) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.iconPrimary),
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

private fun speedLabel(speed: Float): String =
    if (kotlin.math.abs(speed - 1f) < 0.01f) "Обычная" else "${trimFloat(speed)}×"

private fun speedItemLabel(sp: Float): String =
    if (kotlin.math.abs(sp - 1f) < 0.01f) "Обычная" else trimFloat(sp)

private fun trimFloat(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()

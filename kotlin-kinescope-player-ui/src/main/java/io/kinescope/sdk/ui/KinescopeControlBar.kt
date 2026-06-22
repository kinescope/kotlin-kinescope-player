package io.kinescope.sdk.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kinescope.sdk.ui.R
import java.util.concurrent.TimeUnit

@Composable
fun KinescopeControlBar(
    controller: KinescopeComposePlayerController,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onFullscreenClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onScrubChange: (Boolean) -> Unit = {},
) {
    val state by controller.uiState.collectAsStateWithLifecycle()
    val colors = playerColors()
    val slots = playerSlots()
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showTotal by remember { mutableStateOf(false) }
    val selectedHeight = state.qualities.firstOrNull { it.id == state.selectedQualityId }?.height ?: 0
    val settingsIcon = when {
        selectedHeight >= 2160 -> R.drawable.ic_settings_4k
        selectedHeight >= 1080 -> R.drawable.ic_settings_hd
        else -> R.drawable.ic_settings
    }

    var expanded by remember { mutableStateOf(false) }
    val expandAnim by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "bar-expand",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shownMs = scrub?.let { (it * state.durationMs).toLong() } ?: state.positionMs
        Row(
            modifier = Modifier
                .animateContentSize(tween(100, easing = FastOutSlowInEasing))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showTotal = !showTotal },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(shownMs),
                color = colors.iconPrimary,
                fontSize = 14.sp, lineHeight = 21.sp,
                fontWeight = FontWeight.Normal, fontFamily = RobotoFontFamily,
                textAlign = TextAlign.Center,
            )
            if (showTotal) {
                Text(
                    text = " / ${formatTime(state.durationMs)}",
                    color = colors.textSecondary,
                    fontSize = 14.sp, lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal, fontFamily = RobotoFontFamily,
                    maxLines = 1, softWrap = false,
                )
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            val regionW = constraints.maxWidth.toFloat()
            if (expandAnim < 1f) {
                Timeline(
                    progress = state.progress,
                    buffered = state.bufferedProgress,
                    scrub = scrub,
                    accentColor = colors.timelinePlayed,
                    bufferedColor = colors.timelineBuffered,
                    trackColor = colors.timelineTrack,
                    onScrub = { f ->
                        if ((scrub != null) != (f != null)) onScrubChange(f != null)
                        scrub = f
                    },
                    onSeek = { frac -> controller.seekTo((frac * state.durationMs).toLong()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = -regionW * 0.55f * expandAnim
                            alpha = 1f - expandAnim
                        },
                )
            }
            if (expandAnim > 0f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            translationX = 56.dp.toPx() * (1f - expandAnim)
                            alpha = expandAnim
                        },
                ) {
                    slots.controlBarStart?.invoke(
                        PlayerUiStateSlot(state.isPlaying, state.positionMs, state.durationMs),
                    )
                    CastButton(modifier = Modifier.size(28.dp))
                    if (state.subtitles.any { it.id != SUBTITLES_OFF }) {
                        CcBarIcon(
                            active = state.subtitlesOn,
                            onClick = { controller.toggleSubtitles() },
                        )
                    }
                    BarIcon(settingsIcon, "Настройки", scale = 1.05f, onClick = onSettingsClick)
                    slots.controlBarEnd?.invoke(
                        PlayerUiStateSlot(state.isPlaying, state.positionMs, state.durationMs),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        BarIcon(
            if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
            if (fullscreen) "Свернуть" else "На весь экран",
            onClick = onFullscreenClick,
        )
        Spacer(Modifier.width(12.dp))
        BarIcon(R.drawable.ic_dots, "Ещё", onClick = { expanded = !expanded })
    }
}

@Composable
fun BarIcon(
    icon: Int,
    desc: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    val colors = playerColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 1.15f else 1f, label = "bar-press")
    Box(
        modifier = modifier
            .size(28.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = icon, animationSpec = tween(180), label = "bar-icon") { ic ->
            Image(
                painter = painterResource(ic),
                contentDescription = desc,
                colorFilter = ColorFilter.tint(colors.iconBar),
                modifier = Modifier.graphicsLayer {
                    scaleX = scale * pressScale
                    scaleY = scale * pressScale
                },
            )
        }
    }
}

@Composable
private fun Timeline(
    progress: Float,
    buffered: Float,
    scrub: Float?,
    accentColor: androidx.compose.ui.graphics.Color,
    bufferedColor: androidx.compose.ui.graphics.Color,
    trackColor: androidx.compose.ui.graphics.Color,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressed = scrub != null
    val shown = (scrub ?: progress).coerceIn(0f, 1f)
    val barHeightDp by animateFloatAsState(
        targetValue = if (pressed) 14f else 4f,
        animationSpec = tween(durationMillis = if (pressed) 110 else 200),
        label = "timeline-h",
    )

    BoxWithConstraints(modifier.height(24.dp)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var frac = (down.position.x / widthPx).coerceIn(0f, 1f)
                        onScrub(frac); down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { ch.consume(); break }
                            frac = (ch.position.x / widthPx).coerceIn(0f, 1f)
                            onScrub(frac); ch.consume()
                        }
                        onSeek(frac); onScrub(null)
                    }
                },
        ) {
            val cy = size.height / 2f
            val h = barHeightDp.dp.toPx()
            val top = cy - h / 2f
            val rad = CornerRadius(h / 4f, h / 4f)
            drawRoundRect(trackColor, Offset(0f, top), Size(size.width, h), rad)
            clipPath(Path().apply { addRoundRect(RoundRect(0f, top, size.width, top + h, rad)) }) {
                if (buffered > 0f) drawRect(bufferedColor, Offset(0f, top), Size(size.width * buffered, h))
                drawRect(accentColor, Offset(0f, top), Size(size.width * shown, h))
            }
        }
    }
}

@Composable
fun CcBarIcon(active: Boolean, onClick: () -> Unit) {
    val colors = playerColors()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_cc),
            contentDescription = "Субтитры",
            colorFilter = ColorFilter.tint(colors.iconBar),
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.3.dp)
                    .width(16.3.dp)
                    .height(2.3.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.ccActive),
            )
        }
    }
}

@Composable
fun PosterPlayButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = playerColors()
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.playButtonFill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_center_play),
            contentDescription = "Играть",
            modifier = Modifier.height(30.dp),
            colorFilter = ColorFilter.tint(colors.iconPrimary),
        )
    }
}

@Composable
fun ReplayButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = playerColors()
    Box(
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_replay),
            contentDescription = "Повторить",
            modifier = Modifier.size(44.dp),
            colorFilter = ColorFilter.tint(colors.iconPrimary),
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = TimeUnit.MILLISECONDS.toSeconds(ms)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

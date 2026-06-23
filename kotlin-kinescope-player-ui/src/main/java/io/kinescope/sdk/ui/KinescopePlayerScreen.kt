package io.kinescope.sdk.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import android.content.res.Configuration
import android.view.View
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.ui.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.view.KinescopePlayerView
import io.kinescope.sdk.settings.SubtitleStyle
import io.kinescope.sdk.utils.formatPlayerTime
import kotlinx.coroutines.delay

private const val DOUBLE_TAP_SEEK_STREAK_WINDOW_MS = 1500L
private const val DOUBLE_TAP_FEEDBACK_VISIBLE_MS = 500L
private const val DOUBLE_TAP_FEEDBACK_HIDE_MS = 150L
private const val CONTROLS_OVERLAY_FADE_MS = 220

private val controlsOverlayEnter = fadeIn(tween(CONTROLS_OVERLAY_FADE_MS, easing = FastOutSlowInEasing))
private val controlsOverlayExit = fadeOut(tween(CONTROLS_OVERLAY_FADE_MS, easing = FastOutSlowInEasing))

@OptIn(UnstableApi::class)
@Composable
fun KinescopePlayerScreen(
    player: KinescopeVideoPlayer,
    videoId: String,
    fullscreen: Boolean = false,
    modifier: Modifier = Modifier,
    onStopCast: () -> Unit = {},
    restoreQualityId: Int? = null,
    restoreSubtitleId: String? = null,
    restoreAudioTrackId: Int? = null,
    onTrackSelectionPersist: (qualityId: Int, subtitleId: String, audioTrackId: Int) -> Unit = { _, _, _ -> },
    onFullscreenToggle: () -> Unit = {},
    onPipClick: () -> Unit = {},
    onVideoLoaded: () -> Unit = {},
) {
    val controller = rememberComposePlayerController(player)
    val state by controller.uiState.collectAsStateWithLifecycle()
    val colors = playerColors()
    val slots = playerSlots()
    var controlsVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var seekSide by remember { mutableIntStateOf(0) }
    var seekTick by remember { mutableIntStateOf(0) }
    var overlaySeekSide by remember { mutableIntStateOf(0) }
    var overlaySeekSeconds by remember { mutableIntStateOf(0) }
    var seekStreakCount by remember { mutableIntStateOf(0) }
    var lastSeekTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastSeekTapSide by remember { mutableIntStateOf(0) }
    var seekHoldPlaying by remember { mutableStateOf(false) }
    val currentHasStarted by rememberUpdatedState(state.hasStarted)
    val currentControlsVisible by rememberUpdatedState(controlsVisible)

    DisposableEffect(controller, onTrackSelectionPersist) {
        controller.onTrackSelectionChanged = onTrackSelectionPersist
        onDispose { controller.onTrackSelectionChanged = null }
    }

    DisposableEffect(controller) {
        controller.attach()
        onDispose { controller.detach() }
    }

    var trackRestoreApplied by remember { mutableStateOf(false) }
    LaunchedEffect(
        state.isReady,
        restoreQualityId,
        restoreSubtitleId,
        restoreAudioTrackId,
    ) {
        if (trackRestoreApplied || !state.isReady) return@LaunchedEffect
        if (restoreQualityId == null && restoreSubtitleId == null && restoreAudioTrackId == null) {
            return@LaunchedEffect
        }
        restoreQualityId?.let(controller::setQuality)
        restoreSubtitleId?.let(controller::selectSubtitle)
        restoreAudioTrackId?.let(controller::selectAudioTrack)
        trackRestoreApplied = true
    }

    LaunchedEffect(videoId, loadAttempt) {
        controller.clearError()
        player.loadVideo(
            videoId,
            onSuccess = {
                controller.applySideloadedSubtitles()
                controller.refreshVideoMetadata()
                onVideoLoaded()
            },
            onFailed = { controller.setLoadError(it?.message) },
        )
    }

    LaunchedEffect(controlsVisible, state.isPlaying, showSettings) {
        if (controlsVisible && state.isPlaying && !showSettings) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) scrubbing = false
    }

    LaunchedEffect(seekTick) {
        if (seekSide != 0) {
            delay(DOUBLE_TAP_FEEDBACK_VISIBLE_MS)
            seekSide = 0
        }
    }

    LaunchedEffect(seekSide) {
        if (seekSide == 0 && overlaySeekSide != 0) {
            delay(DOUBLE_TAP_FEEDBACK_HIDE_MS)
            overlaySeekSide = 0
            overlaySeekSeconds = 0
            seekStreakCount = 0
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sizeModifier = if (fullscreen || landscape) Modifier.fillMaxSize()
    else Modifier.fillMaxWidth().aspectRatio(16f / 9f)

    BoxWithConstraints(modifier.then(sizeModifier).background(Color.Black)) {
        val density = LocalDensity.current
        val viewHeightPx = with(density) { maxHeight.roundToPx() }
        val targetSubtitleBottomPx = if (controlsVisible) {
            if (viewHeightPx > 1) {
                (0.2f * viewHeightPx + 4f * density.density).toInt()
            } else {
                (64f * density.density).toInt()
            }
        } else {
            (12f * density.density).toInt()
        }
        val animatedSubtitleBottomPx by animateIntAsState(
            targetValue = targetSubtitleBottomPx,
            animationSpec = tween(200),
            label = "subtitleBottomPadding",
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                KinescopePlayerView(ctx, null).apply {
                    setPlayer(player)
                    player.setShowOptions(false)
                    player.setShowFullscreen(false)
                    hideAllControls()
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                    findViewById<View>(io.kinescope.sdk.R.id.kinescope_buffering)?.visibility = View.GONE
                    findViewById<View>(io.kinescope.sdk.R.id.kinescope_time_container)?.visibility = View.GONE
                    findViewById<androidx.media3.ui.PlayerView>(io.kinescope.sdk.R.id.view_exoplayer)
                        ?.subtitleView?.visibility = View.GONE
                    findViewById<View>(io.kinescope.sdk.R.id.kinescope_seek_view)?.visibility = View.GONE
                }
            },
            update = { playerView ->
                playerView.syncSubtitleChromeForControls(controlsVisible)
            },
            onRelease = { it.setPlayer(null) },
        )
        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(state.hasStarted) {
                var controlsVisibleAtPress = false
                detectTapGestures(
                    onPress = {
                        controlsVisibleAtPress = currentControlsVisible
                        if (currentHasStarted && !currentControlsVisible) {
                            controlsVisible = true
                        }
                        tryAwaitRelease()
                    },
                    onTap = {
                        if (!currentHasStarted) {
                            controller.playPause()
                        } else if (controlsVisibleAtPress) {
                            controlsVisible = false
                        }
                    },
                    onDoubleTap = { offset ->
                        if (currentHasStarted) {
                            val side = if (offset.x > size.width / 2f) 1 else -1
                            val now = System.currentTimeMillis()
                            seekStreakCount = if (
                                side == lastSeekTapSide &&
                                now - lastSeekTapTimeMs <= DOUBLE_TAP_SEEK_STREAK_WINDOW_MS
                            ) {
                                seekStreakCount + 1
                            } else {
                                1
                            }
                            lastSeekTapSide = side
                            lastSeekTapTimeMs = now
                            val totalSeconds = seekStreakCount * 10

                            if (seekSide == 0) seekHoldPlaying = state.isPlaying
                            seekSide = side
                            overlaySeekSide = side
                            overlaySeekSeconds = totalSeconds
                            controller.seekBy(side * 10_000L)
                            seekTick++
                        }
                    },
                )
            }

        if (scrubbing) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        }
        val showMobileGradients = state.hasStarted && !scrubbing && (
            controlsVisible ||
                seekSide != 0 ||
                (!state.isPlaying && !state.hasEnded)
            )

        AnimatedVisibility(
            visible = showMobileGradients,
            enter = controlsOverlayEnter,
            exit = controlsOverlayExit,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xA3000000), Color(0x00222222)),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                if (state.videoTitle.isNotBlank()) {
                    Column(Modifier.align(Alignment.TopStart)) {
                        Text(
                            text = state.videoTitle,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoFontFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(
                                    color = Color(0xA3000000),
                                    offset = Offset(0.5f, 0.5f),
                                    blurRadius = 2f,
                                ),
                            ),
                        )
                        state.videoSubtitle?.let { subtitle ->
                            Text(
                                text = subtitle,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontFamily = RobotoFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = Color(0xA3000000),
                                        offset = Offset(0.5f, 0.5f),
                                        blurRadius = 2f,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showMobileGradients,
            enter = controlsOverlayEnter,
            exit = controlsOverlayExit,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x00222222), Color(0xA3000000)),
                        ),
                    ),
            )
        }

        val cues by controller.cuesState
        val subtitleStyle by controller.subtitleStyleState
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> androidx.media3.ui.SubtitleView(ctx) },
            update = { sv ->
                sv.isClickable = false
                sv.isFocusable = false
                sv.setOnTouchListener { _, _ -> false }
                applySubtitleStyle(sv, subtitleStyle, animatedSubtitleBottomPx)
                sv.setCues(cues)
            },
        )

        Box(gestureModifier)
        val slotState = PlayerUiStateSlot(state.isPlaying, state.positionMs, state.durationMs)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            slots.centerOverlay?.invoke(slotState)
            if (slots.centerOverlay == null) {
            when {
                state.error != null -> ErrorOverlay(
                    message = state.error?.message,
                    onRetry = { loadAttempt++ },
                )

                state.hasEnded -> ReplayButton { controller.replay() }
                state.isBuffering && seekSide == 0 -> CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = colors.iconPrimary,
                    strokeWidth = 3.dp,
                )

                !state.hasStarted -> PosterPlayButton { controller.playPause() }

                else -> Unit
            }
            AnimatedVisibility(
                visible = state.hasStarted &&
                    controlsVisible &&
                    state.error == null &&
                    !state.hasEnded &&
                    !(state.isBuffering && seekSide == 0),
                enter = controlsOverlayEnter,
                exit = controlsOverlayExit,
            ) {
                PlayPauseMorph(
                    isPlaying = if (seekSide != 0) seekHoldPlaying else state.isPlaying,
                    tint = if (scrubbing) colors.iconDimmed else colors.iconPrimary,
                    onClick = { controller.playPause() },
                )
            }
            }
        }
        AnimatedVisibility(
            visible = seekSide != 0,
            enter = EnterTransition.None,
            exit = fadeOut(tween(DOUBLE_TAP_FEEDBACK_HIDE_MS.toInt())),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (overlaySeekSide != 0) {
                DoubleTapSeekOverlay(
                    side = overlaySeekSide,
                    seconds = overlaySeekSeconds,
                    fullscreen = fullscreen || landscape,
                )
            }
        }

        AnimatedVisibility(
            visible = scrubbing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        ) {
            ScrubInformer()
        }

        AnimatedVisibility(
            visible = state.hasStarted && controlsVisible,
            enter = controlsOverlayEnter,
            exit = controlsOverlayExit,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            KinescopeControlBar(
                controller = controller,
                fullscreen = fullscreen,
                onFullscreenClick = onFullscreenToggle,
                onSettingsClick = { showSettings = true },
                onScrubChange = { active ->
                    scrubbing = active
                    if (active) player.pause() else player.play()
                },
            )
        }

        if (showSettings) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showSettings = false },
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                KinescopeSettingsMenu(
                    controller = controller,
                    maxHeight = this.maxHeight - 56.dp,
                    onDismiss = { showSettings = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 48.dp),
                )
            }
        }

        if (state.isCasting) {
            CastOverlay(
                deviceName = state.castDeviceName,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onPlayPause = { controller.playPause() },
                onSeek = { fraction ->
                    val duration = state.durationMs
                    if (duration > 0) {
                        controller.seekTo((fraction * duration).toLong())
                    }
                },
                onStop = onStopCast,
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun applySubtitleStyle(
    sub: androidx.media3.ui.SubtitleView,
    style: SubtitleStyle,
    bottomPaddingPx: Int,
) {
    val bg = (style.bgColor and 0x00FFFFFF) or ((style.bgOpacityPercent * 255 / 100) shl 24)
    sub.setApplyEmbeddedStyles(false)
    sub.setApplyEmbeddedFontSizes(false)
    sub.setStyle(
        androidx.media3.ui.CaptionStyleCompat(
            style.fontColor,
            bg,
            android.graphics.Color.TRANSPARENT,
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
            android.graphics.Color.TRANSPARENT,
            androidx.core.content.res.ResourcesCompat.getFont(sub.context, R.font.roboto),
        ),
    )

    val viewH = sub.height.toFloat()
    if (viewH > 1f) {
        sub.setFixedTextSize(
            android.util.TypedValue.COMPLEX_UNIT_PX,
            0.063f * viewH * style.fontSizePercent / 100f,
        )
    } else {
        sub.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f * style.fontSizePercent / 100f)
    }
    if (sub.paddingBottom != bottomPaddingPx) sub.setPadding(0, 0, 0, bottomPaddingPx)
    sub.setBottomPaddingFraction(0f)
}

@OptIn(UnstableApi::class)
@Composable
private fun CastOverlay(
    deviceName: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onStop: () -> Unit,
) {
    val colors = playerColors()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Трансляция на ${deviceName ?: "устройство"}",
                color = colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontFamily = RobotoFontFamily,
                textAlign = TextAlign.Center,
            )
            PlayPauseMorph(isPlaying = isPlaying, onClick = onPlayPause)
            CastSeekBar(positionMs, durationMs, onSeek)
            Text(
                text = "Остановить трансляцию",
                color = colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RobotoFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent)
                    .clickable(onClick = onStop)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CastSeekBar(positionMs: Long, durationMs: Long, onSeek: (Float) -> Unit) {
    val colors = playerColors()
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth(0.8f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatPlayerTime(positionMs),
            color = colors.textPrimary,
            fontSize = 14.sp, lineHeight = 21.sp, fontFamily = RobotoFontFamily,
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
        ) {
            val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.timelineTrack)
                    .pointerInput(w) {
                        detectTapGestures { o -> onSeek((o.x / w).coerceIn(0f, 1f)) }
                    },
            )
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent),
            )
        }
        Text(
            text = formatPlayerTime(durationMs),
            color = colors.textPrimary,
            fontSize = 14.sp, lineHeight = 21.sp, fontFamily = RobotoFontFamily,
        )
    }
}

@Composable
private fun ErrorOverlay(message: String?, onRetry: () -> Unit) {
    val colors = playerColors()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = message ?: "Не удалось загрузить видео",
            color = colors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontFamily = RobotoFontFamily,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Повторить",
            color = colors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = RobotoFontFamily,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accent)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

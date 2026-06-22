package io.kinescope.sdk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val SeekFeedbackCircleColor = Color(0x66000000)

private val SeekFeedbackCircleDiameterFullscreen = 360.dp
private const val SeekFeedbackContentOffsetInRadius = 0.42f
private const val SeekFeedbackScaleCollapsed = 0.88f
private const val SeekFeedbackShowMs = 180
@Composable
private fun SeekFeedbackHemisphere(
    left: Boolean,
    diameter: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    val diameterPx = with(LocalDensity.current) { diameter.roundToPx() }
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    Box(
        modifier
            .width(radius)
            .fillMaxHeight()
            .clip(RectangleShape),
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(diameterPx, diameterPx))
                    layout(radiusPx, constraints.maxHeight) {
                        val y = (constraints.maxHeight - diameterPx) / 2
                        val x = if (left) -radiusPx else 0
                        placeable.place(x, y)
                    }
                }
                .background(SeekFeedbackCircleColor, CircleShape),
        )
    }
}
private const val ARROW_VW = 30.1972f
private const val ARROW_VH = 10.2663f

private val CHEVRON_PATHS = listOf(
    "M0 9.26465C0 10.0633 0.890145 10.5397 1.5547 10.0967L7.75192 5.96522C8.34566 5.56939 8.34566 4.69694 7.75192 4.30112L1.5547 0.169632C0.890146 -0.273404 0 0.202986 0 1.00168Z",
    "M11 1.00168C11 0.202986 11.8901 -0.273405 12.5547 0.169631L18.7519 4.30111C19.3457 4.69694 19.3457 5.56939 18.7519 5.96521L12.5547 10.0967C11.8901 10.5397 11 10.0633 11 9.26465Z",
    "M22 1.00168C22 0.202986 22.8901 -0.273405 23.5547 0.169631L29.7519 4.30111C30.3457 4.69694 30.3457 5.56939 29.7519 5.96521L23.5547 10.0967C22.8901 10.5397 22 10.0633 22 9.26465Z",
)

@Composable
fun SeekChevrons(
    pointingLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = playerColors()
    val paths = remember { CHEVRON_PATHS.map { PathParser().parsePathString(it).toPath() } }
    val transition = rememberInfiniteTransition(label = "seek-chev")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "seek-chev-phase",
    )

    val canvasModifier = modifier
        .size(width = 31.dp, height = 12.dp)
        .then(if (pointingLeft) Modifier.graphicsLayer { scaleX = -1f } else Modifier)

    Canvas(canvasModifier) {
        scale(size.width / ARROW_VW, size.height / ARROW_VH, pivot = Offset.Zero) {
            paths.forEachIndexed { i, p ->
                val intensity = (1f - abs(phase - i)).coerceIn(0f, 1f)
                drawPath(p, lerp(colors.iconPrimary, colors.seekGray, intensity))
            }
        }
    }
}

@Composable
fun DoubleTapSeekOverlay(
    side: Int,
    seconds: Int,
    fullscreen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (side == 0) return
    val colors = playerColors()
    val left = side < 0
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scale = remember(side) { Animatable(SeekFeedbackScaleCollapsed) }
    val alpha = remember(side) { Animatable(0f) }
    LaunchedEffect(side) {
        if (alpha.value >= 1f && scale.value >= 1f) return@LaunchedEffect
        scale.snapTo(SeekFeedbackScaleCollapsed)
        alpha.snapTo(0f)
        coroutineScope {
            launch { scale.animateTo(1f, tween(SeekFeedbackShowMs)) }
            launch { alpha.animateTo(1f, tween(SeekFeedbackShowMs)) }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val diameter = when {
            fullscreen || landscape -> SeekFeedbackCircleDiameterFullscreen
            else -> maxWidth
        }.coerceAtMost(maxWidth)
        val radius = diameter / 2f
        val contentOffset = radius * SeekFeedbackContentOffsetInRadius
        val edgeAlign = if (left) Alignment.CenterStart else Alignment.CenterEnd

        Box(
            Modifier
                .fillMaxHeight()
                .align(edgeAlign)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    clip = false
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (left) 0f else 1f,
                        pivotFractionY = 0.5f,
                    )
                },
        ) {
            SeekFeedbackHemisphere(
                left = left,
                diameter = diameter,
                radius = radius,
                modifier = Modifier.align(edgeAlign),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(edgeAlign)
                    .padding(
                        start = if (left) contentOffset + 8.dp else 0.dp,
                        end = if (left) 0.dp else contentOffset + 8.dp,
                    ),
            ) {
                SeekChevrons(pointingLeft = left)
                Text(
                    text = "$seconds сек",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = RobotoFontFamily,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ScrubInformer(modifier: Modifier = Modifier) {
    val colors = playerColors()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeekChevrons(pointingLeft = true)
        Text(
            text = "Двойное касание ± 10 сек",
            color = colors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = RobotoFontFamily,
            textAlign = TextAlign.Center,
        )
        SeekChevrons(pointingLeft = false)
    }
}

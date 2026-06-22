package io.kinescope.sdk.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.kinescope.sdk.ui.R

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun PlayPauseMorph(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    glyphSize: androidx.compose.ui.unit.Dp = 41.dp,
    frameSize: androidx.compose.ui.unit.Dp = 56.dp,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val resolvedTint = tint ?: playerColors().iconPrimary
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.ic_play_pause_morph)
    val painter = rememberAnimatedVectorPainter(image, atEnd = isPlaying)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 1.15f else 1f, label = "press-scale")

    Box(
        modifier = modifier
            .size(frameSize)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = if (isPlaying) "Пауза" else "Играть",
            modifier = Modifier
                .size(glyphSize)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
            colorFilter = ColorFilter.tint(resolvedTint),
        )
    }
}

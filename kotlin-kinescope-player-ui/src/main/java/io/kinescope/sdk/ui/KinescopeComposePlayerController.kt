package io.kinescope.sdk.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import io.kinescope.sdk.cast.toCastData
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.player.state.KinescopePlayerStateController
import io.kinescope.sdk.settings.SubtitleStyle

@OptIn(UnstableApi::class)
class KinescopeComposePlayerController(
    private val stateController: KinescopePlayerStateController,
) {
    val uiState = stateController.uiState
    val positionState = stateController.positionState

    var onTrackSelectionChanged: ((qualityId: Int, subtitleId: String, audioTrackId: Int) -> Unit)? = null

    val subtitleStyleState = mutableStateOf(SubtitleStyle())
    val cuesState = mutableStateOf<List<Cue>>(emptyList())

    fun attach() = stateController.attach()

    fun detach() = stateController.detach()

    fun setLoadError(message: String?) = stateController.setLoadError(message)

    fun clearError() = stateController.clearError()

    fun playPause() = stateController.playPause()

    fun replay() = stateController.replay()

    fun seekTo(positionMs: Long) = stateController.seekTo(positionMs)

    fun seekBy(deltaMs: Long) = stateController.seekBy(deltaMs)

    fun setSpeed(speed: Float) = stateController.setSpeed(speed)

    fun setQuality(id: Int) {
        stateController.setQuality(id)
        notifyTrackSelectionChanged()
    }

    fun selectSubtitle(id: String) {
        stateController.selectSubtitle(id)
        notifyTrackSelectionChanged()
    }

    fun toggleSubtitles() = stateController.toggleSubtitles()

    fun selectAudioTrack(id: Int) {
        stateController.selectAudioTrack(id)
        notifyTrackSelectionChanged()
    }

    fun refreshPositions() = stateController.refreshPositions()

    fun refreshVideoMetadata() = stateController.refreshVideoMetadata()

    fun buildCastData() = stateController.player.getVideo()?.toCastData()

    fun applySideloadedSubtitles() = Unit

    fun setSubtitleFontColor(color: Int) {
        subtitleStyleState.value = subtitleStyleState.value.copy(fontColor = color)
    }

    fun setSubtitleFontSize(percent: Int) {
        subtitleStyleState.value = subtitleStyleState.value.copy(fontSizePercent = percent)
    }

    fun setSubtitleBgColor(color: Int) {
        subtitleStyleState.value = subtitleStyleState.value.copy(bgColor = color)
    }

    fun setSubtitleBgOpacity(percent: Int) {
        subtitleStyleState.value = subtitleStyleState.value.copy(bgOpacityPercent = percent)
    }

    fun resetSubtitleStyle() {
        subtitleStyleState.value = SubtitleStyle()
    }

    private fun notifyTrackSelectionChanged() {
        val state = stateController.uiState.value
        onTrackSelectionChanged?.invoke(
            state.selectedQualityId,
            state.selectedSubtitleId,
            state.selectedAudioTrackId,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberComposePlayerController(player: KinescopeVideoPlayer): KinescopeComposePlayerController {
    return remember(player) {
        KinescopeComposePlayerController(KinescopePlayerStateController(player))
    }
}

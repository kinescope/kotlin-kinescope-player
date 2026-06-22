package io.kinescope.sdk.player.state

import io.kinescope.sdk.player.quality.KinescopeQualityVariant

const val QUALITY_AUTO_ID = KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID
const val SUBTITLES_OFF_ID = "off"

data class QualityOption(
    val id: Int,
    val label: String,
    val height: Int = id,
    val badge: String? = null,
)

data class SubtitleOption(
    val id: String,
    val label: String,
)

data class AudioTrackOption(
    val id: Int,
    val label: String,
)

sealed interface PlayerError {
    val message: String?

    data class Load(override val message: String?) : PlayerError

    data class Playback(
        val errorCode: Int,
        override val message: String?,
    ) : PlayerError
}

data class PlayerUiState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasStarted: Boolean = false,
    val hasEnded: Boolean = false,
    val isCasting: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val speed: Float = 1f,
    val qualities: List<QualityOption> = emptyList(),
    val selectedQualityId: Int = QUALITY_AUTO_ID,
    val subtitlesOn: Boolean = false,
    val subtitles: List<SubtitleOption> = listOf(SubtitleOption(SUBTITLES_OFF_ID, "Off")),
    val selectedSubtitleId: String = SUBTITLES_OFF_ID,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val selectedAudioTrackId: Int = 0,
    val videoTitle: String = "",
    val videoSubtitle: String? = null,
    val error: PlayerError? = null,
) {
    val progress: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

    val bufferedProgress: Float
        get() = if (durationMs > 0) {
            (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
}

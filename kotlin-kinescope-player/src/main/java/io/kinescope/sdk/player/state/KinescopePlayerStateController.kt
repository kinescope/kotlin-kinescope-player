package io.kinescope.sdk.player.state

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.kinescope.sdk.player.KinescopeVideoPlayer
import io.kinescope.sdk.player.quality.KinescopeQualityVariant
import io.kinescope.sdk.player.quality.getQualityVariantsList
import io.kinescope.sdk.player.tracks.TrackController
import io.kinescope.sdk.settings.qualityBadgeForVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(UnstableApi::class)
class KinescopePlayerStateController(
    val player: KinescopeVideoPlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var trackController: TrackController? = null
    private var positionTickerJob: Job? = null
    private var hostListener: ((Player) -> Unit)? = null

    private val localExoPlayer: ExoPlayer?
        get() = player.exoPlayer

    private val activePlayer: Player?
        get() = player.playbackPlayer

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlaybackState(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { state ->
                state.copy(
                    isPlaying = isPlaying,
                    hasStarted = state.hasStarted || isPlaying,
                )
            }
            updatePositionTicker(isPlaying)
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshTracks(tracks)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.update {
                it.copy(
                    error = PlayerError.Playback(error.errorCode, error.localizedMessage),
                    isBuffering = false,
                )
            }
        }
    }

    fun attach() {
        val exo = localExoPlayer ?: return
        trackController = TrackController(player.context, exo.trackSelector as DefaultTrackSelector)
        exo.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        exo.setHandleAudioBecomingNoisy(true)
        bindActivePlayer(player.getOrCreatePlayerHost()?.activePlayer ?: exo)
        player.getOrCreatePlayerHost()?.let { host ->
            hostListener = { newPlayer ->
                bindActivePlayer(newPlayer)
            }
            host.onActivePlayerChanged = hostListener
        }
        refreshPositions()
    }

    fun detach() {
        positionTickerJob?.cancel()
        positionTickerJob = null
        player.getOrCreatePlayerHost()?.let { host ->
            if (host.onActivePlayerChanged === hostListener) {
                host.onActivePlayerChanged = null
            }
        }
        hostListener = null
        activePlayer?.removeListener(listener)
        scope.cancel()
    }

    fun setLoadError(message: String?) {
        _uiState.update {
            it.copy(error = PlayerError.Load(message), isBuffering = false)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun playPause() {
        if (_uiState.value.isPlaying) player.pause() else player.play()
    }

    fun replay() {
        activePlayer?.seekTo(0)
        _uiState.update { it.copy(hasEnded = false) }
        player.play()
        refreshPositions()
    }

    fun seekTo(positionMs: Long) {
        val duration = _uiState.value.durationMs
        activePlayer?.seekTo(positionMs.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        refreshPositions()
    }

    fun seekBy(deltaMs: Long) = seekTo(_uiState.value.positionMs + deltaMs)

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        refreshPositions()
    }

    fun setQuality(id: Int) {
        trackController?.setQualityVariant(id)
        _uiState.update { it.copy(selectedQualityId = id) }
    }

        fun selectSubtitle(id: String) {
        val controller = trackController ?: return
        if (id == SUBTITLES_OFF_ID) {
            controller.applySubtitleSelection(TrackController.SUBTITLES_OFF_ID)
            _uiState.update { it.copy(subtitlesOn = false, selectedSubtitleId = SUBTITLES_OFF_ID) }
        } else {
            val optionIndex = _uiState.value.subtitles.indexOfFirst { option -> option.id == id }
            if (optionIndex > 0) {
                controller.applySubtitleSelection(optionIndex - 1)
                _uiState.update { it.copy(subtitlesOn = true, selectedSubtitleId = id) }
            }
        }
    }

    fun toggleSubtitles() {
        if (_uiState.value.subtitlesOn) {
            selectSubtitle(SUBTITLES_OFF_ID)
        } else {
            _uiState.value.subtitles.firstOrNull { it.id != SUBTITLES_OFF_ID }?.let { selectSubtitle(it.id) }
        }
    }

    fun selectAudioTrack(id: Int) {
        trackController?.applyAudioSelection(id)
        _uiState.update { it.copy(selectedAudioTrackId = id) }
    }

    fun refreshPositions() {
        val playback = activePlayer ?: return
        _uiState.update {
            it.copy(
                positionMs = playback.currentPosition.coerceAtLeast(0L),
                bufferedMs = playback.bufferedPosition.coerceAtLeast(0L),
                durationMs = playback.duration.coerceAtLeast(0L),
                speed = playback.playbackParameters.speed,
                isCasting = player.isCasting,
            )
        }
    }

    fun refreshVideoMetadata() {
        val video = player.getVideo() ?: return
        _uiState.update {
            it.copy(
                videoTitle = video.title,
                videoSubtitle = video.subtitle?.takeIf { subtitle -> subtitle.isNotBlank() },
            )
        }
    }

    private fun bindActivePlayer(playback: Player) {
        activePlayer?.removeListener(listener)
        playback.addListener(listener)
        syncPlaybackState(playback.playbackState)
        refreshPositions()
    }

    private fun syncPlaybackState(playbackState: Int) {
        val ready = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
        _uiState.update { state ->
            state.copy(
                isReady = ready,
                isBuffering = playbackState == Player.STATE_BUFFERING,
                hasEnded = playbackState == Player.STATE_ENDED,
                durationMs = activePlayer?.duration?.coerceAtLeast(0L) ?: state.durationMs,
                error = if (playbackState == Player.STATE_READY) null else state.error,
            )
        }
    }

    private fun refreshTracks(tracks: Tracks) {
        val controller = trackController ?: return
        localExoPlayer?.let { exo ->
            with(exo.trackSelector as DefaultTrackSelector) {
                controller.updateQualityVariants(getQualityVariantsList())
            }
        }
        controller.updateTextTracks(tracks)
        controller.updateAudioTracks(tracks)

        val qualities = buildQualityOptions(controller)
        val subtitles = buildSubtitleOptions(tracks)
        val audioTracks = controller.buildAudioOptions().map { option ->
            AudioTrackOption(id = option.id, label = option.title)
        }

        _uiState.update { state ->
            state.copy(
                qualities = qualities,
                selectedQualityId = when {
                    controller.isAutoQuality -> QUALITY_AUTO_ID
                    controller.isAudioOnlyQuality -> KinescopeQualityVariant.QUALITY_VARIANT_AUDIO_ONLY_ID
                    else -> controller.selectedQualityVariant.id
                },
                subtitles = subtitles,
                selectedSubtitleId = subtitles.find { it.id != SUBTITLES_OFF_ID }
                    ?.takeIf { controller.selectedSubtitleIndex != TrackController.SUBTITLES_OFF_ID }
                    ?.id ?: SUBTITLES_OFF_ID,
                subtitlesOn = controller.selectedSubtitleIndex != TrackController.SUBTITLES_OFF_ID,
                audioTracks = audioTracks,
                selectedAudioTrackId = controller.selectedAudioIndex,
            )
        }
    }

    private fun buildQualityOptions(controller: TrackController): List<QualityOption> {
        val options = controller.qualityVariants.map { variant ->
            QualityOption(
                id = variant.id,
                label = variant.name,
                height = variant.id,
                badge = qualityBadgeForVariant(variant.id),
            )
        }
        return buildList {
            add(QualityOption(QUALITY_AUTO_ID, "Auto", 0))
            addAll(options)
        }
    }

    private fun buildSubtitleOptions(tracks: Tracks): List<SubtitleOption> {
        val options = mutableListOf(SubtitleOption(SUBTITLES_OFF_ID, "Off"))
        tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) continue
                val id = "${format.id ?: format.language ?: "txt"}#$index"
                options.add(SubtitleOption(id, subtitleLabel(format)))
            }
        }
        return options
    }

    private fun subtitleLabel(format: Format): String {
        format.label?.takeIf { it.isNotBlank() }?.let { return it }
        format.language?.takeIf { it.isNotBlank() && it != "und" }?.let { lang ->
            return Locale.forLanguageTag(lang).displayLanguage.ifBlank { lang }
        }
        return "Subtitles"
    }

    private fun updatePositionTicker(isPlaying: Boolean) {
        positionTickerJob?.cancel()
        if (!isPlaying) return
        positionTickerJob = scope.launch {
            while (isActive) {
                refreshPositions()
                val speed = _uiState.value.speed.coerceAtLeast(0.25f)
                delay((1000f / speed).toLong().coerceIn(200L, 1000L))
            }
        }
    }
}

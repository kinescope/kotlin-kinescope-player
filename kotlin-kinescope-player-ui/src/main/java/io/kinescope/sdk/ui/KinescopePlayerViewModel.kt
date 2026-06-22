package io.kinescope.sdk.ui



import androidx.annotation.OptIn

import androidx.lifecycle.SavedStateHandle

import androidx.lifecycle.ViewModel

import androidx.media3.common.util.UnstableApi

import io.kinescope.sdk.player.KinescopePlayerOptions

import io.kinescope.sdk.player.KinescopeVideoPlayer



private const val KEY_VIDEO_ID = "video_id"

private const val KEY_POSITION_MS = "position_ms"

private const val KEY_PLAY_WHEN_READY = "play_when_ready"

private const val KEY_SPEED = "speed"

private const val KEY_QUALITY_ID = "quality_id"

private const val KEY_SUBTITLE_ID = "subtitle_id"


@OptIn(UnstableApi::class)

class KinescopePlayerViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val playerFactory: (KinescopePlayerOptions) -> KinescopeVideoPlayer,
) : ViewModel() {
    val videoId: String?
        get() = savedStateHandle.get<String>(KEY_VIDEO_ID)
    private val _player: KinescopeVideoPlayer by lazy {

        playerFactory(
            KinescopePlayerOptions(
                controls = false,
                backgroundPlaybackAllowed = true,
                hdrToneMapping = true,
                showCastButton = true,
            ),
        )
    }

    val player: KinescopeVideoPlayer
        get() = _player

    fun attach() {
        restoreFromSavedState()
    }

    fun rememberVideoId(videoId: String) {
        savedStateHandle[KEY_VIDEO_ID] = videoId
    }

    fun persistToSavedState() {
        val playback = _player.playbackPlayer ?: return
        savedStateHandle[KEY_POSITION_MS] = playback.currentPosition.coerceAtLeast(0L)
        savedStateHandle[KEY_PLAY_WHEN_READY] = playback.playWhenReady
        savedStateHandle[KEY_SPEED] = playback.playbackParameters.speed
    }

    fun restorePlaybackIfNeeded() {
        val position = savedStateHandle.get<Long>(KEY_POSITION_MS) ?: return
        val play = savedStateHandle.get<Boolean>(KEY_PLAY_WHEN_READY) ?: false
        _player.playbackPlayer?.seekTo(position)
        if (play) _player.play()
    }

    fun savedQualityId(): Int? = savedStateHandle.get(KEY_QUALITY_ID)
    fun savedSubtitleId(): String? = savedStateHandle.get(KEY_SUBTITLE_ID)
    fun persistTrackSelection(qualityId: Int, subtitleId: String) {
        savedStateHandle[KEY_QUALITY_ID] = qualityId
        savedStateHandle[KEY_SUBTITLE_ID] = subtitleId
    }

    private fun restoreFromSavedState() {
        savedStateHandle.get<Float>(KEY_SPEED)?.let(_player::setPlaybackSpeed)
    }

    override fun onCleared() {
        persistToSavedState()
        _player.release()
        super.onCleared()
    }
}



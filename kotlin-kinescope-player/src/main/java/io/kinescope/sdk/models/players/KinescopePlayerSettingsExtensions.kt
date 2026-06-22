package io.kinescope.sdk.models.players

import io.kinescope.sdk.player.KinescopePlayerOptions

fun KinescopePlayerSettings.applyTo(options: KinescopePlayerOptions) {
    autoplay?.let { options.autoplay = it }
    muted?.let { options.muted = it }
    loop?.let { options.loop = it }
    controls?.let { options.controls = it }
    playsinline?.let { options.playsinline = it }
    preload?.let { options.preload = it }
    quality?.let { options.quality = it }
    keyboardShortcuts?.let { options.keyboardShortcuts = it }
    pictureInPicture?.let { options.pictureInPicture = it }
    fullscreen?.let { options.fullscreen = it }
    playbackRate?.let { options.playbackRate = it }
    color?.let { options.accentColor = it }
    options.syncLegacyChromeFlags()
}

fun KinescopePlayerOptions.toPlayerSettings(): KinescopePlayerSettings = KinescopePlayerSettings(
    autoplay = autoplay,
    muted = muted,
    loop = loop,
    controls = controls,
    playsinline = playsinline,
    preload = preload,
    quality = quality,
    keyboardShortcuts = keyboardShortcuts,
    pictureInPicture = pictureInPicture,
    fullscreen = fullscreen,
    playbackRate = playbackRate,
    color = accentColor,
)

fun KinescopePlayerOptions.syncLegacyChromeFlags() {
    showFullscreenButton = fullscreen
    showOptionsButton = controls
    showPlayPauseButton = controls
    showSeekBar = controls
    showDuration = controls
    showPlaybackSpeedInSettings = playbackRate && controls
    showAudioOnlyQualityInSettings = controls
    showAudioTracksInSettings = controls
}

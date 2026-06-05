package io.kinescope.sdk.player

import io.kinescope.sdk.utils.kinescopeReferer

data class KinescopePlayerOptions(
    var referer: String = kinescopeReferer,
    var autoplay: Boolean = false,
    var muted: Boolean = false,
    var loop: Boolean = false,
    var controls: Boolean = true,
    var playsinline: Boolean = true,
    var preload: String = "metadata",
    var quality: String = "auto",
    var keyboardShortcuts: Boolean = true,
    var pictureInPicture: Boolean = true,
    var fullscreen: Boolean = true,
    var playbackRate: Boolean = true,
    var accentColor: String = "#3B82F6",
    var showFullscreenButton: Boolean = true,
    var showOptionsButton: Boolean = true,
    var showSubtitlesButton: Boolean = false,
    var showSeekBar: Boolean = true,
    var showDuration: Boolean = true,
    var showAttachments: Boolean = false,
    var showPlayPauseButton: Boolean = true,
    var showPlaybackSpeedInSettings: Boolean = true,
    var showAudioOnlyQualityInSettings: Boolean = true,
)
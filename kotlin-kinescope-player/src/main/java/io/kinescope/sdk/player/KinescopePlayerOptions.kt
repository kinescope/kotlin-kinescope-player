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
    var accentColor: String = "#6161FC",
    var showFullscreenButton: Boolean = true,
    var showOptionsButton: Boolean = true,
    var showSubtitlesButton: Boolean = false,
    var showSeekBar: Boolean = true,
    var showDuration: Boolean = true,
    var showAttachments: Boolean = false,
    var showPlayPauseButton: Boolean = true,
    var showCastButton: Boolean = false,
    var showChaptersButton: Boolean = true,
    var showPlaylistButton: Boolean = false,
    var showPlaybackSpeedInSettings: Boolean = true,
    var showAudioOnlyQualityInSettings: Boolean = true,
    var showAudioTracksInSettings: Boolean = true,
    var videoScale: Boolean = true,
    var hdrToneMapping: Boolean = true,
    var backgroundPlaybackAllowed: Boolean = false,
    /** Default cover shown for live streams until the broadcast is ready. Set to false to skip it. */
    var showLiveAwaitingCover: Boolean = true,
)
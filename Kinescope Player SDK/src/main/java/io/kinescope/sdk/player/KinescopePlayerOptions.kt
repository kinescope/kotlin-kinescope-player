package io.kinescope.sdk.player

import io.kinescope.sdk.utils.kinescopeReferer

data class KinescopePlayerOptions(
    var referer: String = kinescopeReferer,
    var showFullscreenButton: Boolean = true,
    var showOptionsButton: Boolean = true,
    var showSubtitlesButton: Boolean = false,
    //var showQualityButton:Boolean = false,
    var showSeekBar: Boolean = true,
    var showDuration: Boolean = true,
    var showAttachments: Boolean = false
)
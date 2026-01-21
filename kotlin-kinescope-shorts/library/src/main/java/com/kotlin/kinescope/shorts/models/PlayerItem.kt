package com.kotlin.kinescope.shorts.models

import androidx.media3.common.Player

class PlayerItem(
    var exoPlayer: Player,
    var position: Int,
    var lastPosition: Long = 0
)



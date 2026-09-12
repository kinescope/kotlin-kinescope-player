package io.kinescope.sdk.view

/**
 * Where [KinescopePlayerView] puts the captions search panel while the player
 * is inline (not fullscreen). See [KinescopePlayerView.captionsSearchPlacement].
 */
enum class KinescopeCaptionsSearchPlacement {
    /** Above the control bar, list of a fixed height. Default. */
    BOTTOM,

    /** Docked to the top edge; the list fills down to the control bar. */
    TOP,
}

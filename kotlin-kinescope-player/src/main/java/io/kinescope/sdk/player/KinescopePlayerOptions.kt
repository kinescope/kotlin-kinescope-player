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
    /**
     * Built-in `default_poster` drawable when the video has no poster URL.
     * Set to false to leave the surface empty until playback (or your own poster call).
     */
    var showDefaultPoster: Boolean = true,
    /**
     * User authorization token for Kinescope Authorization Backend (`drmauthtoken`).
     * Passed as a query parameter when fetching `{videoId}.json` so DRM-protected
     * playback can use your access-check endpoint instead of leaving the video public.
     */
    var drmAuthToken: String? = null,
    /**
     * Your own viewer identifier (user ID, account ID), sent with every analytics event
     * as `Session.ExternalID` — the same as `externalid` in the web player embed.
     * Lets Kinescope analytics attribute views to your users. `null` or blank sends nothing.
     * Can be changed at any time; the next event uses the new value.
     */
    var externalId: String? = null,
    /**
     * Start position in seconds, applied each time [KinescopeVideoPlayer.loadVideo] loads a
     * video — `t` in the web embed. `null` or `≤ 0` starts from the beginning. Ignored for live.
     */
    var startTime: Double? = null,
    /**
     * Do not track — `dnt` in the web embed. When `true`, no analytics events are sent to
     * Kinescope (the [io.kinescope.sdk.view.KinescopePlayerView] analytics callback still fires).
     */
    var dnt: Boolean = false,
    /**
     * When another [KinescopeVideoPlayer] in the app starts playing, pause this one —
     * `autopause` in the web embed. Off by default.
     */
    var autopause: Boolean = false,
    /**
     * Show the video title and author over the player — `header` in the web embed.
     * Works together with [io.kinescope.sdk.view.KinescopePlayerView.titleChromeEnabled]:
     * the title shows only when both are `true`.
     */
    var header: Boolean = true,
    /**
     * Transparent player background instead of black — `transparent` in the web embed.
     * The area around the video, the poster backdrop and the loading backdrop become
     * transparent. For a transparent surface before the first frame use the TextureView
     * layout (`useTextureSurface`), a SurfaceView stays opaque.
     */
    var transparent: Boolean = false,
    /**
     * Request focus for the player view once the video is loaded — `autofocus` in the web
     * embed. Useful for Android TV / D-pad and hardware keyboards.
     */
    var autofocus: Boolean = false,
    /**
     * Base URL for video metadata (`{videoId}.json`) — `baseUrl` in the web embed, e.g. your
     * custom player domain. `null` uses `https://kinescope.io/`. A trailing `/` is added
     * if missing. Applied on the next [KinescopeVideoPlayer.loadVideo].
     */
    var baseUrl: String? = null,
    /**
     * `User-Agent` for video, subtitle and metadata requests. `null` keeps the SDK default
     * (`KinescopeAndroidVideoKotlin` for media). Applied on the next [KinescopeVideoPlayer.loadVideo].
     */
    var userAgent: String? = null,
) {
    /**
     * Background video — `background` in the web embed: autoplay, muted, looped, no controls.
     * Setting it to `true` switches those four options on; turning it off leaves them as they are.
     * Set it before [KinescopeVideoPlayer.loadVideo] and call
     * [io.kinescope.sdk.view.KinescopePlayerView.applyTemplateOptions] if the view is already bound.
     */
    var background: Boolean = false
        set(value) {
            field = value
            if (value) {
                autoplay = true
                muted = true
                loop = true
                controls = false
            }
        }

    internal companion object {
        const val DEFAULT_USER_AGENT = "KinescopeAndroidVideoKotlin"

        /** Start position in ms, or `null` when there is none. */
        fun startPositionMs(startTime: Double?): Long? =
            startTime?.takeIf { it.isFinite() && it > 0.0 }?.let { (it * 1000.0).toLong() }
    }
}
package io.kinescope.demo

/**
 * Demo app settings and sample content IDs.
 *
 * Replace values with your Kinescope API token and video IDs from your project
 * before running the demo screens.
 */
object KinescopeDemoConfig {

    /** Kinescope API token (Dashboard API, playlist, offline downloads). */
    const val API_KEY = "bc50167b-e868-47e4-a55c-07208ef15b22"

    /** Default VOD — Custom UI, Custom Player, Compose player screens. */
    const val DEFAULT_VIDEO_ID = "b138bf19-72fc-474b-901b-00f323899598"

    /** Widevine-protected VOD — DRM viewing screen. */
    const val DRM_VIDEO_ID = "eNWM8F6wbVTVa8fBeR66y6"

    /** Video with subtitles — Subtitles test screen. */
    const val SUBTITLES_VIDEO_ID = "4CCHqgs4MkL33akyL7jJtS"

    /** Default live stream ID when the Live test input is empty. */
    const val DEFAULT_LIVE_ID = "aLJgR9TJfe2EUBejpH5Fuo"

    /** Widevine license URL for a VOD video ID (`token=` suffix uses [API_KEY]). */
    fun widevineLicenseUrl(videoId: String): String =
        "https://license.kinescope.io/v1/vod/$videoId/acquire/widevine?token=$API_KEY"
}

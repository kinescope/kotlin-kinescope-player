package io.kinescope.sdk.models.players

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopePlayerSettings(
    @Json(name = "autoplay") val autoplay: Boolean? = null,
    @Json(name = "muted") val muted: Boolean? = null,
    @Json(name = "loop") val loop: Boolean? = null,
    @Json(name = "controls") val controls: Boolean? = null,
    @Json(name = "playsinline") val playsinline: Boolean? = null,
    @Json(name = "preload") val preload: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "keyboard_shortcuts") val keyboardShortcuts: Boolean? = null,
    @Json(name = "picture_in_picture") val pictureInPicture: Boolean? = null,
    @Json(name = "fullscreen") val fullscreen: Boolean? = null,
    @Json(name = "playback_rate") val playbackRate: Boolean? = null,
    @Json(name = "color") val color: String? = null,
) : Serializable

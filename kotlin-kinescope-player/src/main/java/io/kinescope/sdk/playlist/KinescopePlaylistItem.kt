package io.kinescope.sdk.playlist

data class KinescopePlaylistItem(
    val id: String,
    val title: String,
    val durationSeconds: Double? = null,
    val posterUrl: String? = null,
    val shareUrl: String? = null,
)

package io.kinescope.sdk.settings

data class KinescopeSettingsOption(
    val id: Int,
    val title: String,
    val isSelected: Boolean,
    val badge: String? = null,
)
package io.kinescope.sdk.player.quality

import androidx.media3.common.TrackSelectionOverride

data class KinescopeQualityVariant(
    val id: Int,
    val override: TrackSelectionOverride?,
) {
    companion object {
        const val QUALITY_VARIANT_AUTO_ID = -1
    }
}

data class KinescopeQualityVariantUi(
    val id: Int,
    val name: String,
    val isSelected: Boolean,
)
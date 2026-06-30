package io.kinescope.sdk.player.quality

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.kinescope.sdk.R
import io.kinescope.sdk.extensions.EMPTY

@OptIn(UnstableApi::class)
class KinescopeQualityManager(
    private val context: Context,
    private val trackSelector: DefaultTrackSelector,
) {

    var isAutoQuality: Boolean = true
        private set

    var isAudioOnlyQuality: Boolean = false
        private set

    var variants: List<KinescopeQualityVariantUi> = emptyList()
        private set

    private var selectedVariantId = 0

    private var variantOverrides = emptyList<KinescopeQualityVariant>()

    val selectedVariant: KinescopeQualityVariantUi
        get() = variants.find { variant -> variant.isSelected }
            ?: KinescopeQualityVariantUi(
                id = selectedVariantId,
                name = String.EMPTY,
                isSelected = true
            )

    fun updateVariants(variants: List<KinescopeQualityVariant>) {
        variantOverrides = variants
        updateUiVariants(variants)
    }

    fun setVariant(id: Int) {
        selectedVariantId = id
        when (id) {
            KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID -> {
                isAutoQuality = true
                isAudioOnlyQuality = false
                trackSelector.parameters =
                    trackSelector.parameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                        .build()
            }

            KinescopeQualityVariant.QUALITY_VARIANT_AUDIO_ONLY_ID -> {
                isAutoQuality = false
                isAudioOnlyQuality = true
                trackSelector.parameters =
                    trackSelector.parameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build()
            }

            else -> {
                isAutoQuality = false
                isAudioOnlyQuality = false
                variantOverrides.find { variant -> variant.id == id }
                    ?.let { variant ->
                        variant.override?.let { override ->
                            trackSelector.parameters =
                                trackSelector.parameters
                                    .buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                                    .addOverride(override)
                                    .build()
                        }
                    }
            }
        }

        updateUiVariants(variantOverrides)
    }

    private fun updateUiVariants(variants: List<KinescopeQualityVariant>) {
        this.variants = variants
            .sortedByDescending { variant -> variant.id }
            .map { variant ->
                KinescopeQualityVariantUi(
                    id = variant.id,
                    name = context.getString(
                        R.string.settings_video_quality_variant,
                        variant.id.toString()
                    ),
                    isSelected = variant.id == selectedVariantId,
                )
            }
    }
}
package io.kinescope.sdk.player.quality

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.kinescope.sdk.R
import io.kinescope.sdk.utils.EMPTY

@UnstableApi
class KinescopeQualityManager(
    private val context: Context,
    private val trackSelector: DefaultTrackSelector,
) {

    var isAutoQuality: Boolean = true
        private set

    var variants: List<KinescopeQualityVariantUi> = emptyList()
        private set

    private var videoHeight = 0

    private var variantOverrides = emptyList<KinescopeQualityVariant>()

    val selectedVariant: KinescopeQualityVariantUi
        get() = variants.find { variant -> variant.isSelected }
            ?: KinescopeQualityVariantUi(
                id = videoHeight,
                name = String.EMPTY,
                isSelected = true
            )

    fun updateVariants(variants: List<KinescopeQualityVariant>) {
        variantOverrides = variants
        this.variants = variants
            .sortedBy { variant -> variant.id }
            .map { variant ->
                KinescopeQualityVariantUi(
                    id = variant.id,
                    name = context.getString(R.string.settings_quality_variant, variant.id.toString()),
                    isSelected = variant.id == videoHeight,
                )
            }
    }

    fun updateVideoHeight(height: Int) {
        videoHeight = height
    }

    fun setVariant(id: Int) {
        isAutoQuality = if (id == KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID) {
            trackSelector.parameters =
                trackSelector.parameters
                    .buildUpon()
                    .clearOverrides()
                    .build()
            true
        } else {
            variantOverrides.find { variant -> variant.id == id }
                ?.let { variant ->
                    variant.override?.let { override ->
                        trackSelector.parameters =
                            trackSelector.parameters
                                .buildUpon()
                                .clearOverrides()
                                .addOverride(override)
                                .build()
                    }
                }
            false
        }
    }
}
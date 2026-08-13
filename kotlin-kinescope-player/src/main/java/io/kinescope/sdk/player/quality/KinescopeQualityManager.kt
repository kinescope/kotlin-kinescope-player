package io.kinescope.sdk.player.quality

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.kinescope.sdk.R

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

    /** Track [Format.height] → display name from embed `quality_map`. */
    private var qualityNamesByHeight: Map<Int, String> = emptyMap()

    val selectedVariant: KinescopeQualityVariantUi
        get() = variants.find { variant -> variant.isSelected }
            ?: KinescopeQualityVariantUi(
                id = selectedVariantId,
                name = qualityNamesByHeight[selectedVariantId]?.trim().orEmpty(),
                isSelected = true
            )

    /**
     * Overrides quality labels (stream [Format.height] → `quality_map.name`).
     * Prefer building the map with [resolveQualityMapName] / short-side matching.
     */
    fun setQualityNamesByHeight(namesByHeight: Map<Int, String>) {
        qualityNamesByHeight = namesByHeight
        updateUiVariants(variantOverrides)
    }

    fun updateVariants(variants: List<KinescopeQualityVariant>) {
        variantOverrides = variants
        // Re-bind a locked quality after tracks appear (id may be Format.height while
        // the app selected short-side / quality_map digits, e.g. 480 → 854x480).
        if (!isAutoQuality &&
            !isAudioOnlyQuality &&
            selectedVariantId != KinescopeQualityVariant.QUALITY_VARIANT_AUTO_ID &&
            selectedVariantId != KinescopeQualityVariant.QUALITY_VARIANT_AUDIO_ONLY_ID
        ) {
            findVariantForSelection(selectedVariantId)?.let { matched ->
                selectedVariantId = matched.id
                matched.override?.let { override ->
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
                val matched = findVariantForSelection(id)
                if (matched != null) {
                    selectedVariantId = matched.id
                    matched.override?.let { override ->
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

    /**
     * Matches by [Format.height] id, short side, or digits from a mapped quality name
     * (e.g. select `480` when the only track is `854x480` labeled `480p`).
     */
    private fun findVariantForSelection(id: Int): KinescopeQualityVariant? {
        variantOverrides.find { it.id == id }?.let { return it }
        for (variant in variantOverrides) {
            val format = variant.override?.let { override ->
                val idx = override.trackIndices.firstOrNull() ?: return@let null
                override.mediaTrackGroup.getFormat(idx)
            } ?: continue
            val shortSide = qualityDisplayHeightPx(format.width, format.height)
            if (shortSide == id) return variant
            val mapped = qualityNamesByHeight[variant.id] ?: qualityNamesByHeight[shortSide]
            if (digitsFromQualityName(mapped) == id) return variant
        }
        return null
    }

    private fun updateUiVariants(variants: List<KinescopeQualityVariant>) {
        this.variants = variants
            .sortedByDescending { variant -> variant.id }
            .map { variant ->
                val mappedName = resolveDisplayName(variant)
                KinescopeQualityVariantUi(
                    id = variant.id,
                    name = mappedName,
                    isSelected = variant.id == selectedVariantId,
                )
            }
    }

    /**
     * Prefer `quality_map.name` for [Format.height]; else short-side px (portrait-safe),
     * never the long edge alone when width is known.
     */
    private fun resolveDisplayName(variant: KinescopeQualityVariant): String {
        qualityNamesByHeight[variant.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val format = variant.override?.let { override ->
            val idx = override.trackIndices.firstOrNull() ?: return@let null
            override.mediaTrackGroup.getFormat(idx)
        }
        if (format != null) {
            // Also try map keys by short side / digits (portrait quality_map.height may differ).
            val shortSide = qualityDisplayHeightPx(format.width, format.height)
            qualityNamesByHeight[shortSide]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val displayPx = shortSide.takeIf { it > 0 } ?: variant.id
            return context.getString(
                R.string.settings_video_quality_variant,
                displayPx.toString(),
            )
        }

        return context.getString(
            R.string.settings_video_quality_variant,
            variant.id.toString(),
        )
    }
}

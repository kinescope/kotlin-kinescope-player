package io.kinescope.sdk.player.tracks

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.kinescope.sdk.models.videos.KinescopeVideoSubtitle
import io.kinescope.sdk.player.quality.KinescopeQualityManager
import io.kinescope.sdk.player.quality.KinescopeQualityVariant
import io.kinescope.sdk.player.quality.KinescopeQualityVariantUi
import io.kinescope.sdk.settings.KinescopeSettingsOption

@UnstableApi
class TrackController(
    private val context: Context,
    private val trackSelector: DefaultTrackSelector,
) {
    private val qualityManager = KinescopeQualityManager(context, trackSelector)

    val isAutoQuality: Boolean
        get() = qualityManager.isAutoQuality

    val isAudioOnlyQuality: Boolean
        get() = qualityManager.isAudioOnlyQuality

    val qualityVariants: List<KinescopeQualityVariantUi>
        get() = qualityManager.variants

    val selectedQualityVariant: KinescopeQualityVariantUi
        get() = qualityManager.selectedVariant

    var selectedSubtitleIndex: Int = SUBTITLES_OFF_ID
        private set

    var selectedAudioIndex: Int = 0
        private set

    private var subtitleTrackOverrides: List<Pair<Tracks.Group, Int>> = emptyList()
    private var audioTrackOverrides: List<Pair<Tracks.Group, Int>> = emptyList()

    val hasMultipleAudioTracks: Boolean
        get() = audioTrackOverrides.size > 1

    fun updateQualityVariants(variants: List<KinescopeQualityVariant>) {
        qualityManager.updateVariants(variants)
    }

    fun setQualityNamesByHeight(namesByHeight: Map<Int, String>) {
        qualityManager.setQualityNamesByHeight(namesByHeight)
    }

    fun updateTextTracks(tracks: Tracks) {
        subtitleTrackOverrides = tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    val format = group.getTrackFormat(index)
                    if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) {
                        null
                    } else {
                        group to index
                    }
                }
            }
    }

    fun updateAudioTracks(tracks: Tracks) {
        audioTrackOverrides = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                (0 until group.length).map { index -> group to index }
            }
        syncSelectedAudioIndex()
    }

    fun setQualityVariant(id: Int) {
        qualityManager.setVariant(id)
    }

    fun applySubtitleSelection(optionId: Int) {
        selectedSubtitleIndex = optionId
        val builder = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)

        if (optionId == SUBTITLES_OFF_ID) {
            trackSelector.parameters = builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        val override = subtitleTrackOverrides.getOrNull(optionId)
        trackSelector.parameters = if (override != null) {
            val (group, trackIndex) = override
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                .build()
        } else {
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    fun applyAudioSelection(optionId: Int) {
        if (audioTrackOverrides.isEmpty()) return
        selectedAudioIndex = optionId.coerceIn(0, audioTrackOverrides.lastIndex)
        val (group, trackIndex) = audioTrackOverrides[selectedAudioIndex]
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    fun ensureDefaultSubtitleEnabled(
        showSubtitles: Boolean,
        subtitles: List<KinescopeVideoSubtitle>,
    ) {
        if (selectedSubtitleIndex == SUBTITLES_OFF_ID && showSubtitles && subtitles.isNotEmpty()) {
            applySubtitleSelection(0)
        }
    }

    fun subtitleLabel(subtitle: KinescopeVideoSubtitle): String =
        subtitle.description.ifBlank { subtitle.language }.ifBlank { subtitle.id }

    fun currentSubtitleLabel(
        subtitles: List<KinescopeVideoSubtitle>,
        offLabel: String,
    ): String {
        if (selectedSubtitleIndex == SUBTITLES_OFF_ID) {
            return offLabel
        }
        return subtitles.getOrNull(selectedSubtitleIndex)?.let(::subtitleLabel).orEmpty()
    }

    fun buildSubtitleOptions(
        subtitles: List<KinescopeVideoSubtitle>,
        offLabel: String,
    ): List<KinescopeSettingsOption> = buildList {
        add(
            KinescopeSettingsOption(
                id = SUBTITLES_OFF_ID,
                title = offLabel,
                isSelected = selectedSubtitleIndex == SUBTITLES_OFF_ID,
            )
        )
        subtitles.forEachIndexed { index, subtitle ->
            add(
                KinescopeSettingsOption(
                    id = index,
                    title = subtitleLabel(subtitle),
                    isSelected = selectedSubtitleIndex == index,
                )
            )
        }
    }

    fun currentAudioLabel(): String {
        val override = audioTrackOverrides.getOrNull(selectedAudioIndex) ?: return ""
        val (_, trackIndex) = override
        return AudioTrackLabels.label(context, override.first.getTrackFormat(trackIndex), selectedAudioIndex)
    }

    fun buildAudioOptions(): List<KinescopeSettingsOption> {
        val labels = audioTrackOverrides.mapIndexed { index, (group, trackIndex) ->
            AudioTrackLabels.label(context, group.getTrackFormat(trackIndex), index)
        }
        val displayLabels = AudioTrackLabels.disambiguate(context, labels)
        return audioTrackOverrides.mapIndexed { index, _ ->
            KinescopeSettingsOption(
                id = index,
                title = displayLabels[index],
                isSelected = index == selectedAudioIndex,
            )
        }
    }

    private fun syncSelectedAudioIndex() {
        if (audioTrackOverrides.isEmpty()) {
            selectedAudioIndex = 0
            return
        }
        val activeIndex = audioTrackOverrides.indexOfFirst { (group, trackIndex) ->
            group.isTrackSelected(trackIndex)
        }
        selectedAudioIndex = when {
            activeIndex >= 0 -> activeIndex
            selectedAudioIndex in audioTrackOverrides.indices -> selectedAudioIndex
            else -> 0
        }
    }

    companion object {
        const val SUBTITLES_OFF_ID = -1
    }
}

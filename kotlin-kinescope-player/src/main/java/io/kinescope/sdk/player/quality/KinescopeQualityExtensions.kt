package io.kinescope.sdk.player.quality

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector

private val TRACK_SUPPORTED_FORMATS =
    listOf(
        C.TRACK_TYPE_VIDEO,
        C.TRACK_TYPE_AUDIO,
        C.TRACK_TYPE_TEXT,
    )

// Uses DefaultTrackSelector mapped tracks and TrackSelectionOverride for video quality variants.
@OptIn(UnstableApi::class)
fun DefaultTrackSelector.getQualityVariantsList(): List<KinescopeQualityVariant> {
    val trackOverrideList = mutableListOf<KinescopeQualityVariant>()

    val renderTrack = currentMappedTrackInfo
    val renderCount = renderTrack?.rendererCount ?: 0

    for (rendererIndex in 0 until renderCount) {
        if (isSupportedFormat(renderTrack, rendererIndex)) {
            val trackGroupType = renderTrack?.getRendererType(rendererIndex)
            val trackGroups = renderTrack?.getTrackGroups(rendererIndex)
            val trackGroupsCount = trackGroups?.length ?: break

            if (trackGroupType == C.TRACK_TYPE_VIDEO) {
                for (groupIndex in 0 until trackGroupsCount) {
                    val videoQualityTrackCount = trackGroups[groupIndex].length
                    for (trackIndex in 0 until videoQualityTrackCount) {
                        val isTrackSupported = renderTrack.getTrackSupport(
                            rendererIndex,
                            groupIndex,
                            trackIndex
                        ) == C.FORMAT_HANDLED
                        if (isTrackSupported) {
                            val track = trackGroups[groupIndex]
                            val trackFormat = track.getFormat(trackIndex)
                            val trackHeight = trackFormat.height
                            val trackOverride = TrackSelectionOverride(track, trackIndex)

                            trackOverrideList.add(
                                KinescopeQualityVariant(
                                    id = trackHeight,
                                    override = trackOverride,
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    return trackOverrideList
}

@UnstableApi
private fun isSupportedFormat(
    mappedTrackInfo: MappingTrackSelector.MappedTrackInfo?,
    rendererIndex: Int,
): Boolean {
    val trackGroupArray = mappedTrackInfo?.getTrackGroups(rendererIndex)
    return if (trackGroupArray?.length == 0) {
        false
    } else {
        mappedTrackInfo?.getRendererType(rendererIndex) in TRACK_SUPPORTED_FORMATS
    }
}
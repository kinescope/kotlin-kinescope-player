package io.kinescope.sdk.player.quality

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector

@UnstableApi
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
    rendererIndex: Int
): Boolean {
    val trackGroupArray = mappedTrackInfo?.getTrackGroups(rendererIndex)
    return if (trackGroupArray?.length == 0) {
        false
    } else mappedTrackInfo?.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO || mappedTrackInfo?.getRendererType(
        rendererIndex
    ) == C.TRACK_TYPE_AUDIO || mappedTrackInfo?.getRendererType(rendererIndex) == C.TRACK_TYPE_TEXT
}
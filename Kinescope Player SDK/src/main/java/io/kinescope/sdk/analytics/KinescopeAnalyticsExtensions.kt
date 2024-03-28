package io.kinescope.sdk.analytics

import com.bhavnathacker.jettasks.Native

fun Native.toStringData() =
    "Event: $event; Value: $value; " +
            "Video.source: ${video.source}; Video.duration: ${video.duration}; " +
            "Player.type: ${player.type}; Player.version: ${player.version}; " +

            "Device.os: ${device.os}; Device.osVersion: ${device.osVersion}; " +
            "Device.screenWidth: ${device.screenWidth}; Device.screenHeight: ${device.screenHeight}; " +

            "Session.id: ${session.id.toStringUtf8()}; Session.type: ${session.type}; " +
            "Session.viewId: ${session.viewID.toStringUtf8()}; Session.watchedDuration: ${session.watchedDuration}; " +

            "Playback.rate: ${playback.rate}; Playback.volume: ${playback.volume}; Playback.quality: ${playback.quality}; " +
            "Playback.isMuted: ${playback.isMuted}; Playback.isFullscreen: ${playback.isFullscreen}; " +
            "Playback.previewPosition: ${playback.previewPosition}; Playback.currentPosition: ${playback.currentPosition}"
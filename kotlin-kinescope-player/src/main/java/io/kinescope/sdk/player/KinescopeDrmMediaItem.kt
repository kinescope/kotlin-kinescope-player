package io.kinescope.sdk.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem

internal fun MediaItem.Builder.applyKinescopeWidevineDrm(): MediaItem.Builder =
    setDrmConfiguration(
        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build(),
    )

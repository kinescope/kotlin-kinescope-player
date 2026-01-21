package com.kotlin.kinescope.shorts.drm

import java.util.UUID

data class DrmContentProtection(
    val schemeUri: String,
    val licenseUrl: String?,
    val schemeUuid: UUID,
)






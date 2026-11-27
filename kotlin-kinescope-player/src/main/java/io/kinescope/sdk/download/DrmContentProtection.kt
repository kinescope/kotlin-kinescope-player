package io.kinescope.sdk.download

import java.util.UUID

data class DrmContentProtection(
    val schemeUri: String,
    val licenseUrl: String?,
    val schemeUuid: UUID,
)

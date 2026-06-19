package io.kinescope.sdk.view

import androidx.annotation.IdRes
import io.kinescope.sdk.R

/**
 * Internal SDK view ids that custom UI layers may need to reach into.
 * SDK 0.0.9 — verify ids when upgrading the library.
 */
internal object KinescopeSdkViewIds {
    @IdRes
    val bufferingSpinner: Int = R.id.kinescope_buffering
}

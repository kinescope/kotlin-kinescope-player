package io.kinescope.sdk.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

@UnstableApi
class KinescopeErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        RETRY_DELAY_MS

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = RETRY_COUNT

    companion object {
        private const val RETRY_DELAY_MS = 5000L
        private const val RETRY_COUNT = Int.MAX_VALUE
    }
}
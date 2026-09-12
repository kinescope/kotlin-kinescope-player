package io.kinescope.sdk.shorts.managers

/**
 * Adaptive Shorts preload policy based on current playback / network health.
 *
 * Tiers control how aggressively we prepare neighbors:
 * - [CRITICAL]: pause non-essential preload (keep ready +1 handoff if already prepared)
 * - [LOW]: +1 ExoPlayer only
 * - [MEDIUM]: +1 player + limited segment cache forward
 * - [HIGH]: +1 player + forward/back segment cache
 */
enum class PreloadTier {
    CRITICAL,
    LOW,
    MEDIUM,
    HIGH,
}

data class PreloadPlan(
    val tier: PreloadTier,
    val allowPlayerPreload: Boolean,
    val cacheForwardCount: Int,
    val cacheBackCount: Int,
    val maxPrefetchSegments: Int,
    val maxBytesPerResource: Long,
)

data class PlaybackHealthSnapshot(
    val isRebuffering: Boolean,
    /** Buffered media ahead of the playhead, milliseconds. */
    val bufferedDurationMs: Long,
    /** Estimated throughput from [androidx.media3.exoplayer.upstream.DefaultBandwidthMeter], bits/sec. */
    val estimatedBitrateBps: Long,
    val isMeteredNetwork: Boolean,
    val hasWifi: Boolean,
)

object PreloadHealth {

    private const val BUFFER_CRITICAL_MS = 800L
    private const val BUFFER_LOW_MS = 2_000L
    private const val BUFFER_HEALTHY_MS = 5_000L

    private const val BITRATE_LOW_BPS = 800_000L
    private const val BITRATE_FAIR_BPS = 2_000_000L

    fun evaluate(snapshot: PlaybackHealthSnapshot, weakDevice: Boolean): PreloadPlan {
        val tier = scoreTier(snapshot, weakDevice)
        return planFor(tier, weakDevice)
    }

    private fun scoreTier(snapshot: PlaybackHealthSnapshot, weakDevice: Boolean): PreloadTier {
        if (snapshot.isRebuffering || snapshot.bufferedDurationMs < BUFFER_CRITICAL_MS) {
            return PreloadTier.CRITICAL
        }

        var score = 0
        when {
            snapshot.bufferedDurationMs >= BUFFER_HEALTHY_MS -> score += 2
            snapshot.bufferedDurationMs >= BUFFER_LOW_MS -> score += 1
        }
        when {
            snapshot.estimatedBitrateBps >= BITRATE_FAIR_BPS -> score += 2
            snapshot.estimatedBitrateBps >= BITRATE_LOW_BPS -> score += 1
            snapshot.estimatedBitrateBps > 0L -> score += 0
            else -> score += 1 // unknown → neutral
        }
        if (snapshot.hasWifi && !snapshot.isMeteredNetwork) score += 1
        if (snapshot.isMeteredNetwork) score -= 1
        if (weakDevice) score -= 1

        return when {
            score <= 1 -> PreloadTier.LOW
            score <= 3 -> PreloadTier.MEDIUM
            else -> PreloadTier.HIGH
        }.let { tier ->
            if (weakDevice && tier == PreloadTier.HIGH) PreloadTier.MEDIUM else tier
        }
    }

    fun planFor(tier: PreloadTier, weakDevice: Boolean): PreloadPlan = when (tier) {
        PreloadTier.CRITICAL -> PreloadPlan(
            tier = tier,
            allowPlayerPreload = false,
            cacheForwardCount = 0,
            cacheBackCount = 0,
            maxPrefetchSegments = 0,
            maxBytesPerResource = 0L,
        )
        PreloadTier.LOW -> PreloadPlan(
            tier = tier,
            allowPlayerPreload = true,
            cacheForwardCount = 0,
            cacheBackCount = 0,
            maxPrefetchSegments = 1,
            maxBytesPerResource = 500_000L,
        )
        PreloadTier.MEDIUM -> PreloadPlan(
            tier = tier,
            allowPlayerPreload = true,
            cacheForwardCount = if (weakDevice) 0 else 1,
            cacheBackCount = 0,
            maxPrefetchSegments = 2,
            maxBytesPerResource = 1_000_000L,
        )
        PreloadTier.HIGH -> PreloadPlan(
            tier = tier,
            allowPlayerPreload = true,
            cacheForwardCount = 1,
            cacheBackCount = if (weakDevice) 0 else 1,
            maxPrefetchSegments = 3,
            maxBytesPerResource = 1_500_000L,
        )
    }
}

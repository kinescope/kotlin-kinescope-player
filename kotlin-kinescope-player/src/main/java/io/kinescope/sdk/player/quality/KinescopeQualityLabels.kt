package io.kinescope.sdk.player.quality

import androidx.media3.common.C
import androidx.media3.common.Format
import io.kinescope.sdk.models.videos.KinescopeQualityMapEntry

/**
 * Kinescope quality names (e.g. `"480p"`) use the shorter frame side.
 * Portrait variants often report [Format.height] as the long edge (e.g. 854)
 * while the marketing name is still `480p` — UI must follow [quality_map].
 */

fun qualityDisplayHeightPx(width: Int, height: Int): Int {
    val w = if (width == C.LENGTH_UNSET || width <= 0) 0 else width
    val h = if (height == C.LENGTH_UNSET || height <= 0) 0 else height
    return when {
        w > 0 && h > 0 -> minOf(w, h)
        h > 0 -> h
        w > 0 -> w
        else -> 0
    }
}

fun Format.qualityDisplayHeightPx(): Int = qualityDisplayHeightPx(width, height)

/** Digits from a quality name like `"480p"` / `"1080"` → `480` / `1080`. */
fun digitsFromQualityName(name: String?): Int? {
    if (name.isNullOrBlank()) return null
    val digits = Regex("""(\d+)""").find(name)?.groupValues?.getOrNull(1) ?: return null
    return digits.toIntOrNull()?.takeIf { it > 0 }
}

/**
 * Resolves the display name from [qualityMap] for a stream track.
 *
 * Match order:
 * 1. [KinescopeQualityMapEntry.height] == track [height]
 * 2. entry.height == short side of the frame
 * 3. digits from [KinescopeQualityMapEntry.name] == short side or track height
 */
fun resolveQualityMapName(
    qualityMap: List<KinescopeQualityMapEntry>?,
    width: Int,
    height: Int,
): String? {
    if (qualityMap.isNullOrEmpty()) return null
    val shortSide = qualityDisplayHeightPx(width, height)

    qualityMap.firstOrNull { it.height == height && height > 0 }?.name?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    if (shortSide > 0) {
        qualityMap.firstOrNull { it.height == shortSide }?.name?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }

    qualityMap.firstOrNull { entry ->
        val digits = digitsFromQualityName(entry.name) ?: return@firstOrNull false
        digits == shortSide || (height > 0 && digits == height)
    }?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    return null
}

fun resolveQualityMapName(
    qualityMap: List<KinescopeQualityMapEntry>?,
    format: Format,
): String? = resolveQualityMapName(qualityMap, format.width, format.height)

/**
 * User-facing label: `quality_map.name` when matched, otherwise `"${shortSide}p"`.
 */
fun formatQualityLabel(
    qualityMap: List<KinescopeQualityMapEntry>?,
    width: Int,
    height: Int,
): String {
    resolveQualityMapName(qualityMap, width, height)?.let { return it }
    val px = qualityDisplayHeightPx(width, height).takeIf { it > 0 } ?: height.takeIf { it > 0 } ?: 0
    return if (px > 0) "${px}p" else "Auto"
}

/**
 * Display height in px for badges / captions: prefer digits from matched name,
 * else short side, else track height.
 */
fun resolveQualityDisplayHeightPx(
    qualityMap: List<KinescopeQualityMapEntry>?,
    width: Int,
    height: Int,
): Int {
    val name = resolveQualityMapName(qualityMap, width, height)
    digitsFromQualityName(name)?.let { return it }
    return qualityDisplayHeightPx(width, height).takeIf { it > 0 } ?: height.coerceAtLeast(0)
}

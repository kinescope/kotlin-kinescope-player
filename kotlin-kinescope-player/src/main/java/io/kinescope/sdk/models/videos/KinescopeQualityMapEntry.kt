package io.kinescope.sdk.models.videos

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Entry from embed JSON `quality_map`.
 *
 * [height] is the stream frame height (often the long side for portrait);
 * [name] is the user-facing quality label (e.g. `"480p"`).
 */
@JsonClass(generateAdapter = true)
data class KinescopeQualityMapEntry(
    @Json(name = "label") val label: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "height") val height: Int,
) : Serializable

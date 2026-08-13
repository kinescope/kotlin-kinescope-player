package io.kinescope.sdk.shorts.models

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable as KSerializable
import java.io.Serializable as JSerializable

@InternalSerializationApi
@KSerializable
data class VideoData(
    val hlsLink: String,
    val drm: DrmInfo?,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val videoId: String? = null,
    val qualityMap: List<VideoQualityMapEntry>? = null,
    val selectedQualityLabel: String? = null,
    val posterUrl: String? = null,
) : JSerializable

@InternalSerializationApi
@KSerializable
data class VideoQualityMapEntry(
    val label: String? = null,
    val name: String,
    val height: Int,
) : JSerializable

@InternalSerializationApi
@KSerializable
data class DrmInfo(
    val widevine: WidevineInfo?
) : JSerializable

@InternalSerializationApi
@KSerializable
data class WidevineInfo(
    val licenseUrl: String
) : JSerializable

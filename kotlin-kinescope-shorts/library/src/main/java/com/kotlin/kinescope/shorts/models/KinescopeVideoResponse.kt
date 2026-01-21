package com.kotlin.kinescope.shorts.models

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@InternalSerializationApi
@Serializable
data class KinescopeVideoResponse(
    val data: KinescopeVideoData
)

@InternalSerializationApi
@Serializable
data class KinescopeVideoData(
    val id: String,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val status: String? = null,
    val project_id: String? = null,
    val folder_id: String? = null,
    @JsonNames("hls_link", "hlsLink")
    val hls_link: String? = null,
    val player: KinescopePlayer? = null,
    val drm: KinescopeDrm? = null,
    val quality_map: List<KinescopeQuality>? = null
)

@InternalSerializationApi
@Serializable
data class KinescopePlayer(
    val embed: String? = null,
    val hls: String? = null,
    val poster: String? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeDrm(
    val widevine: KinescopeWidevine? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeWidevine(
    @JsonNames("license_url", "licenseUrl", "licenseURI", "licenseUri")
    val licenseUrl: String? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeVideoListResponse(
    val data: List<KinescopeVideoData>,
    val meta: KinescopeMeta? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeMeta(
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeProjectsResponse(
    val data: List<KinescopeProject>
)

@InternalSerializationApi
@Serializable
data class KinescopeProject(
    val id: String,
    val name: String? = null,
    val description: String? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeProjectDetailResponse(
    val data: KinescopeProjectDetail
)

@InternalSerializationApi
@Serializable
data class KinescopeProjectDetail(
    val id: String,
    val name: String? = null,
    val items: List<KinescopeProjectItem>? = null,
    val items_count: Int? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeProjectItem(
    val id: String,
    val type: String? = null,
    val title: String? = null,
    val video: KinescopeVideoData? = null
)

@InternalSerializationApi
@Serializable
data class KinescopeQuality(
    val label: String? = null,
    val name: String? = null,
    val height: Int? = null
)


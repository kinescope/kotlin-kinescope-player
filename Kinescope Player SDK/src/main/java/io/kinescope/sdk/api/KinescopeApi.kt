package io.kinescope.sdk.api

import io.kinescope.sdk.models.common.KinescopeAllVideosResponse
import retrofit2.http.GET

interface KinescopeApi {
    @GET("v1/videos/")
    suspend fun getAll(): KinescopeAllVideosResponse
}

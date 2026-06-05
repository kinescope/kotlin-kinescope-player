package io.kinescope.sdk.api

import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.utils.SDK_TYPE
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface KinescopeFetch {
    @GET(KinescopeApiConfig.VIDEO_JSON)
    fun getVideo(
        @Path(KinescopeApiConfig.VIDEO_ID_PARAM) videoId: String,
        @Query("sdk") sdk: String = SDK_TYPE
    ): Call<KinescopeVideo>
}

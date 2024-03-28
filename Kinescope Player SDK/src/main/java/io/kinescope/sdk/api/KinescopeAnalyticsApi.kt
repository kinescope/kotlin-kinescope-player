package io.kinescope.sdk.api

import com.bhavnathacker.jettasks.Native
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface KinescopeAnalyticsApi {
    @POST("/player-native")
    fun sendEvent(@Body body: Native): Call<Void>
}
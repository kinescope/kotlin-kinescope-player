package io.kinescope.sdk.network

import com.squareup.moshi.Moshi
import io.kinescope.sdk.api.KinescopeAnalyticsApi
import io.kinescope.sdk.api.KinescopeApiConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory

object AnalyticsBuilder {
    private fun getOkhttpClient() =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BODY)
            ).build()

    private fun getBuilder() =
        Retrofit.Builder()
            .baseUrl(KinescopeApiConfig.ANALYTICS_BASE_URL)
            .addConverterFactory(ProtoConverterFactory.create())
            .client(getOkhttpClient())
            .build()

    fun getAnalyticsApi(): KinescopeAnalyticsApi =
        getBuilder().create(KinescopeAnalyticsApi::class.java)
}
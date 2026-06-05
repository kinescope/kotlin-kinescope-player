package io.kinescope.sdk.network

import com.squareup.moshi.Moshi
import io.kinescope.sdk.BuildConfig
import io.kinescope.sdk.api.KinescopeApi
import io.kinescope.sdk.api.KinescopeApiConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory


object RetrofitBuilder {
    private fun getMoshi(): Moshi {
        return  Moshi.Builder()
            .build()
    }

    private fun getOkhttpClient(kinescopeApikey:String): OkHttpClient.Builder  {
        val httpClientBulder: OkHttpClient.Builder = OkHttpClient.Builder()

        httpClientBulder.addInterceptor(Interceptor { chain ->
            var request: Request = chain.request()
            val builder = request.newBuilder()
                .header("Authorization", "${KinescopeApiConfig.TOKEN_TYPE} $kinescopeApikey")
                .header("Accept", "application/json")

            if (request.method == "DELETE" && request.body == null &&
                request.url.encodedPath.contains(KinescopeApiConfig.PLAYERS_SEGMENT)
            ) {
                val body = "{}".toRequestBody("application/json".toMediaType())
                builder.method("DELETE", body)
                    .header("Content-Type", "application/json")
            }

            chain.proceed(builder.build())
        })

        if (BuildConfig.DEBUG) {
            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            httpClientBulder.addInterceptor(interceptor)
        }

        return httpClientBulder
    }


    private fun getRetrofit(kinescopeApikey:String) : Retrofit = Retrofit.Builder()
        .client(getOkhttpClient(kinescopeApikey).build())
        .addConverterFactory(MoshiConverterFactory.create(getMoshi()))
        .baseUrl(KinescopeApiConfig.API_BASE_URL)
        .build()

    fun getKinescopeApi(kinescopeApikey:String) : KinescopeApi = getRetrofit(kinescopeApikey).create(KinescopeApi::class.java)
}
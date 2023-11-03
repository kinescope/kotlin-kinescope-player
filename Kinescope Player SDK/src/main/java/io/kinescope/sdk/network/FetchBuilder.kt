package io.kinescope.sdk.network

import com.squareup.moshi.Moshi
import io.kinescope.sdk.api.KinescopeFetch
import io.kinescope.sdk.utils.kinescopeFetchEndpoint
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object FetchBuilder {

    private fun getOkhttpClient(referer:String): OkHttpClient.Builder  {
        val httpClientBulder: OkHttpClient.Builder = OkHttpClient.Builder()

        httpClientBulder.addInterceptor (Interceptor { chain ->
            var request: Request = chain.request()
            request = request.newBuilder().header("Referer", referer).build()
            chain.proceed(request)
        })

        return httpClientBulder
    }

    private fun getMoshi(): Moshi {
        return  Moshi.Builder()
            .build()
    }

    private fun getBuilder(referer:String): Retrofit {
        return Retrofit.Builder().baseUrl(kinescopeFetchEndpoint)
            .addConverterFactory(MoshiConverterFactory.create(getMoshi()))
            .client(getOkhttpClient(referer).build()).build()
    }

    fun getKinescopeFetch(referer:String) : KinescopeFetch = getBuilder(referer).create(KinescopeFetch::class.java)
}

package io.kinescope.sdk.network

import com.squareup.moshi.Moshi
import io.kinescope.sdk.api.KinescopeFetch
import io.kinescope.sdk.api.KinescopeApiConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object FetchBuilder {

    private fun getOkhttpClient(referer: String, userAgent: String?) =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BODY)
            )
            .addInterceptor(Interceptor { chain ->
                val builder = chain.request().newBuilder().header("Referer", referer)
                userAgent?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it) }
                val request: Request = builder.build()
                chain.proceed(request)
            }).build()

    private fun getMoshi(): Moshi {
        return Moshi.Builder()
            .build()
    }

    private fun getBuilder(referer: String, baseUrl: String, userAgent: String?): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(getMoshi()))
            .client(getOkhttpClient(referer, userAgent))
            .build()
    }

    /**
     * @param baseUrl host for `{videoId}.json`; `null` or blank uses [KinescopeApiConfig.FETCH_BASE_URL].
     * @param userAgent `User-Agent` header; `null` keeps the OkHttp default.
     */
    fun getKinescopeFetch(
        referer: String,
        baseUrl: String? = null,
        userAgent: String? = null,
    ): KinescopeFetch =
        getBuilder(referer, normalizeBaseUrl(baseUrl), userAgent).create(KinescopeFetch::class.java)

    /** Blank → default host; adds `https://` when there is no scheme and a trailing `/`. */
    internal fun normalizeBaseUrl(baseUrl: String?): String {
        val trimmed = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return KinescopeApiConfig.FETCH_BASE_URL
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}

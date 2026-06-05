package io.kinescope.sdk.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import retrofit2.HttpException

@JsonClass(generateAdapter = true)
internal data class KinescopeApiErrorEnvelope(
    @Json(name = "message") val message: String? = null,
    @Json(name = "error") val error: KinescopeApiErrorBody? = null,
)

@JsonClass(generateAdapter = true)
internal data class KinescopeApiErrorBody(
    @Json(name = "message") val message: String? = null,
    @Json(name = "detail") val detail: String? = null,
    @Json(name = "code") val code: String? = null,
)

private val errorMoshi: Moshi = Moshi.Builder().build()

fun HttpException.readApiErrorMessage(): String? {
    val raw = response()?.errorBody()?.string()?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching {
        errorMoshi.adapter(KinescopeApiErrorEnvelope::class.java)
            .fromJson(raw)
            ?.let { envelope ->
                envelope.message
                    ?: envelope.error?.message
                    ?: envelope.error?.detail
                    ?: envelope.error?.code
            }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: raw
}

fun Throwable.readApiErrorMessage(): String? = (this as? HttpException)?.readApiErrorMessage()

/** Типичная 400 при DELETE плеера, созданного в веб-кабинете, а не через POST /v1/players. */
fun Throwable.isDashboardPlayerDeleteRestriction(): Boolean {
    val message = readApiErrorMessage()?.lowercase().orEmpty()
    if (message.isNotEmpty()) {
        if (message.contains("default") ||
            message.contains("dashboard") ||
            message.contains("builtin") ||
            message.contains("built-in") ||
            message.contains("cabinet") ||
            message.contains("in use") ||
            message.contains("по умолчанию")
        ) {
            return true
        }
    }
    return (this as? HttpException)?.code() == 400
}

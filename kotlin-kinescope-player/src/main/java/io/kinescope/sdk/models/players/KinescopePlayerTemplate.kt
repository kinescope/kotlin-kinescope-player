package io.kinescope.sdk.models.players

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class KinescopePlayerTemplate(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "is_default") val isDefault: Boolean = false,
    @Json(name = "default") val default: Boolean = false,
    /** `api` — через API; `dashboard` / `web` — в кабинете (если API отдаёт поле). */
    @Json(name = "source") val source: String? = null,
    @Json(name = "deletable") val deletable: Boolean? = null,
    @Json(name = "settings") val settings: KinescopePlayerSettings? = null,
    @Json(name = "ads") val ads: List<KinescopePlayerAd> = emptyList(),
    @Json(name = "logo") val logo: KinescopePlayerLogo? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
) : Serializable {

    val isDashboardManaged: Boolean
        get() = source.equals("dashboard", ignoreCase = true) ||
            source.equals("web", ignoreCase = true) ||
            source.equals("cabinet", ignoreCase = true)

    val canDelete: Boolean
        get() = when {
            deletable != null -> deletable
            isDefault || default -> false
            isDashboardManaged -> false
            source.equals("api", ignoreCase = true) -> true
            else -> true
        }
}

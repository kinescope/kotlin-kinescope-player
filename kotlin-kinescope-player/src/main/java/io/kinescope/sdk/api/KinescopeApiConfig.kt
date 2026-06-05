package io.kinescope.sdk.api

import io.kinescope.sdk.network.RetrofitBuilder

/**
 * Base URLs, relative paths and auth for Kinescope REST APIs.
 * API key is provided by the host app when creating [KinescopeApiHelper] (see [createApiHelper]).
 */
object KinescopeApiConfig {

    const val API_BASE_URL = "https://api.kinescope.io/"
    const val FETCH_BASE_URL = "https://kinescope.io/"
    const val ANALYTICS_BASE_URL = "https://metrics.kinescope.io/"
    const val TOKEN_TYPE = "Bearer"

    const val VIDEOS = "v1/videos/"

    const val PLAYERS = "v1/players"
    const val PLAYER_BY_ID = "v1/players/{player_id}"

    /** Used by [io.kinescope.sdk.network.RetrofitBuilder] DELETE interceptor. */
    const val PLAYERS_SEGMENT = "/players/"

    const val PLAYER_ID_PARAM = "player_id"

    const val VIDEO_JSON = "{video_id}.json"
    const val VIDEO_ID_PARAM = "video_id"

    fun createApiHelper(apiKey: String): KinescopeApiHelper =
        KinescopeApiHelperImpl(RetrofitBuilder.getKinescopeApi(apiKey))
}

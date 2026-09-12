package io.kinescope.sdk.shorts

/**
 * Client configuration for the Shorts feed sample / host app.
 *
 * Set values before loading the feed (e.g. in `Application.onCreate` or your
 * Shorts activity). With a JitPack AAR you assign at runtime — you cannot edit
 * constants inside the published artifact.
 *
 * ```kotlin
 * KinescopeShortsConfig.API_KEY = "your-dashboard-api-token"
 * KinescopeShortsConfig.PROJECT_ID = "optional-project-id"
 * KinescopeShortsConfig.FOLDER_ID = null
 * KinescopeShortsConfig.FEED_LIMIT = 50
 * ```
 *
 * Then pass into your provider / [io.kinescope.sdk.shorts.utils.KinescopeUrls]:
 *
 * ```kotlin
 * KinescopeUrls(
 *     videoProvider = MyProvider(KinescopeShortsConfig.API_KEY),
 *     projectId = KinescopeShortsConfig.PROJECT_ID,
 *     folderId = KinescopeShortsConfig.FOLDER_ID,
 *     limit = KinescopeShortsConfig.FEED_LIMIT,
 * )
 * ```
 */
object KinescopeShortsConfig {

    /**
     * Dashboard API token (Bearer).
     * Create/copy it in Kinescope Dashboard → Settings → API.
     */
    @JvmField
    var API_KEY: String = ""

    /**
     * Optional. Passed to `GET /v1/videos?project_id=`.
     * `null` = all projects available to the token.
     */
    @JvmField
    var PROJECT_ID: String? = null

    /**
     * Optional. Passed to `GET /v1/videos?folder_id=`.
     * `null` = any folder in the selected project scope.
     */
    @JvmField
    var FOLDER_ID: String? = null

    /**
     * Max videos loaded into the feed (`per_page` for the catalog request).
     * Default `50`; Dashboard allows up to `100`.
     */
    @JvmField
    var FEED_LIMIT: Int = 50
}

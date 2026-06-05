# Kinescope API Usage Guide

## Overview

The SDK uses **two separate API layers**. Choose the one that matches your use case:

| Use case | Client | You implement? | Documentation |
|----------|--------|----------------|---------------|
| **Shorts vertical feed** | `KinescopeVideoProvider` + `KinescopeUrls` | Yes — your HTTP client | This guide |
| **Player / demo / templates** | `KinescopeApiHelper` via `KinescopeApiConfig.createApiHelper(apiKey)` | No — built into the SDK | [README — Dashboard REST API](../README.md) |
| **Single video playback** | `KinescopeFetch` via `KinescopeVideoPlayer.loadVideo()` | No — built into the SDK | [README — Quick start](../README.md) |

> For **Playlist test**, **Custom Player test**, and player templates you only need `KinescopeApiHelper` and your API key in `KinescopeDemoConfig` — not `KinescopeVideoProvider`.

---

## Built-in Dashboard API (`KinescopeApiHelper`)

Included in `kotlin-kinescope-player`. No custom Retrofit setup required.

```kotlin
import io.kinescope.sdk.api.KinescopeApiConfig

// Application.onCreate
val apiHelper = KinescopeApiConfig.createApiHelper("your-api-key")

// Video catalog (Playlist test)
apiHelper.getAllVideos().collect { response ->
    val videos = response.data
}

// Player templates (Custom Player test)
apiHelper.getPlayers().collect { response ->
    val templates = response.data
}
```

| Method | Endpoint | Base URL |
|--------|----------|----------|
| `getAllVideos()` | `GET /v1/videos/` | `https://api.kinescope.io/` |
| `getPlayers()` | `GET /v1/players` | `https://api.kinescope.io/` |
| `getPlayer(id)` | `GET /v1/players/{id}` | `https://api.kinescope.io/` |
| `createPlayer(request)` | `POST /v1/players` | `https://api.kinescope.io/` |
| `updatePlayer(id, request)` | `PUT /v1/players/{id}` | `https://api.kinescope.io/` |
| `deletePlayer(id)` | `DELETE /v1/players/{id}` | `https://api.kinescope.io/` |

API key: [Kinescope dashboard](https://kinescope.io/) → project settings → API / Tokens.

Error helpers: `readApiErrorMessage()`, `isDashboardPlayerDeleteRestriction()`.

### HTTP response codes

| Code | Description | When it occurs |
|------|-------------|----------------|
| 200 | OK | Request completed successfully |
| 400 | Bad Request | Invalid request format or parameters |
| 401 | Unauthorized | Missing or invalid API token |
| 402 | Payment Required | Plan limits exceeded |
| 403 | Forbidden | No permission to perform the operation |
| 404 | Not Found | Resource not found |
| 422 | Unprocessable Entity | Data validation error |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server-side error |

### API error codes

Error responses may include a machine-readable code in the JSON body:

| Code | Name | Description |
|------|------|-------------|
| 1 | `IO_ERROR` | Empty request body |
| 100101 | `AUTH_HEADER_NOT_FOUND` | `Authorization` header is missing |
| 100102 | `UNAUTHORIZED` | Invalid token |
| 100103 | `ACCESS_DENIED` | Access denied |
| 100106 | `LIMIT_REACHED` | Limit reached |
| 400101 | `JSON_SYNTAX_ERROR` | JSON syntax error |
| 400201 | `ALREADY_EXISTS` | Resource already exists |
| 402402 | `VALIDATION_ERROR` | Validation error |
| 404404 | `NOT_FOUND` | Resource not found |
| 500500 | `INTERNAL_ERROR` | Internal server error |

```kotlin
import io.kinescope.sdk.api.readApiErrorMessage

apiHelper.getPlayers()
    .catch { error ->
        val message = error.readApiErrorMessage() // parses message / error.detail from body
        Log.e("API", "Error: $message")
    }
    .collect { /* ... */ }
```

### Pagination

List endpoints return paginated results. Use query parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | `1` | Page number |
| `per_page` | integer | `10` | Items per page (max `100`) |

Example:

```bash
curl "https://api.kinescope.io/v1/videos?page=2&per_page=25" \
  -H "Authorization: Bearer YOUR_API_TOKEN"
```

In the SDK, `getAllVideos()` parses pagination into `response.meta.pagination`:

```kotlin
apiHelper.getAllVideos().collect { response ->
    val videos = response.data
    val pagination = response.meta.pagination
    // pagination?.page, pagination?.perPage, pagination?.total
}
```

> The current `KinescopeApiHelper.getAllVideos()` does not accept `page` / `per_page` parameters — it requests the default first page. For custom pagination, extend `KinescopeApi` or call the REST API directly.

### API versions

| Version | Path | Status | SDK support |
|---------|------|--------|-------------|
| v1 | `/v1/*` | Stable | Yes — `KinescopeApiHelper` (videos, players) |
| v2 | `/v2/*` | Stable (Live API) | Not wrapped in the SDK yet |

Base URL for Dashboard API: `https://api.kinescope.io/` (`KinescopeApiConfig.API_BASE_URL`).

---

## Shorts feed: `KinescopeVideoProvider`

For the **Shorts vertical feed**, implement `KinescopeVideoProvider` yourself and pass it to `KinescopeUrls`.

 Endpoints:

| Task | Endpoint | Base URL | Used by |
|------|----------|----------|---------|
| Video catalog (ids, titles) | `GET /v1/videos/?page=&per_page=` | `https://api.kinescope.io/` | `KinescopeApiHelper.getAllVideos()` |
| Single video (HLS, metadata) | `GET /{video_id}.json?sdk=android` | `https://kinescope.io/` | `KinescopeFetch` / `KinescopeVideoPlayer.loadVideo()` |

`GET /v1/videos/` returns a lightweight catalog (`id`, `title`). For Shorts you need `hlsLink` — fetch each video via `/{video_id}.json` or call `loadVideo(videoId)` in your provider.

### Step 1: Recommended — delegate to built-in SDK clients

```kotlin
import io.kinescope.sdk.api.KinescopeApiConfig
import io.kinescope.sdk.api.KinescopeApiHelper
import io.kinescope.sdk.models.videos.KinescopeVideo
import io.kinescope.sdk.network.FetchBuilder
import io.kinescope.sdk.shorts.interfaces.KinescopeVideoProvider
import io.kinescope.sdk.shorts.models.VideoData
import kotlinx.coroutines.flow.first

class SdkKinescopeVideoProvider(
    apiKey: String,
    private val referer: String = "https://kinescope.io",
) : KinescopeVideoProvider {

    private val apiHelper: KinescopeApiHelper = KinescopeApiConfig.createApiHelper(apiKey)
    private val fetch = FetchBuilder.getKinescopeFetch(referer)

    override suspend fun loadVideo(videoId: String): VideoData? {
        return try {
            val response = fetch.getVideo(videoId).execute()
            if (!response.isSuccessful) return null
            response.body()?.let { convertToVideoData(it) }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadVideos(
        projectId: String?,
        folderId: String?,
        limit: Int,
        offset: Int
    ): List<VideoData> {
        return try {
            val catalog = apiHelper.getAllVideos().first()
            catalog.data
                .drop(offset)
                .take(limit)
                .mapNotNull { item -> loadVideo(item.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun convertToVideoData(video: KinescopeVideo): VideoData? {
        val hlsLink = video.hlsLink ?: return null
        return VideoData(
            hlsLink = hlsLink,
            drm = null, // add Widevine license URL if your videos use DRM
            title = video.title,
            subtitle = video.subtitle,
            description = video.description,
        )
    }
}
```

### Step 2: Example with Retrofit (custom client)

If you do not use `KinescopeApiHelper` / `FetchBuilder`, call the same endpoints directly. You need **two base URLs**:

```kotlin
import io.kinescope.sdk.models.common.KinescopeAllVideosResponse
import io.kinescope.sdk.models.videos.KinescopeVideo
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

// Catalog — api.kinescope.io
interface KinescopeDashboardApi {
    @GET("v1/videos/")
    suspend fun getVideos(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Header("Authorization") token: String,
    ): KinescopeAllVideosResponse
}

// Playback metadata — kinescope.io (same as KinescopeVideoPlayer)
interface KinescopePlaybackApi {
    @GET("{video_id}.json")
    suspend fun getVideo(
        @Path("video_id") videoId: String,
        @Query("sdk") sdk: String = "android",
    ): KinescopeVideo
}
```

> **Note.** `projectId` / `folderId` in `KinescopeVideoProvider.loadVideos()` are not used by `GET /v1/videos/` in the current SDK. Filter on the client side if needed, or extend the API client when the backend supports those query params.

> API errors (401, 404, …): see [API_TROUBLESHOOTING.md](API_TROUBLESHOOTING.md).

### Step 3: Using with KinescopeUrls

After implementing the provider, use it with `KinescopeUrls`:

```kotlin
import io.kinescope.sdk.shorts.utils.KinescopeUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun loadVideos() {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val videoProvider = MyKinescopeVideoProvider(apiToken = "your-token")

            val kinescopeVideo = KinescopeUrls(
                videoProvider = videoProvider,
                projectId = "your-project-id",
                folderId = "your-folder-id" // optional
            )

            val videos = kinescopeVideo.getVideosFromApi()

            if (videos.isEmpty()) {
                val fallbackVideos = kinescopeVideo.getNextVideoUrls()
                setupViewPager(fallbackVideos)
            } else {
                setupViewPager(videos)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading videos", e)
            val kinescopeVideo = KinescopeUrls()
            val fallbackVideos = kinescopeVideo.getNextVideoUrls()
            setupViewPager(fallbackVideos)
        }
    }
}
```

Without a provider, `KinescopeUrls()` falls back to a built-in hardcoded video list.

### Step 4: Loading a single video by ID

```kotlin
lifecycleScope.launch {
    val videoProvider = MyKinescopeVideoProvider(apiToken = "your-token")
    val kinescopeVideo = KinescopeUrls(videoProvider = videoProvider)

    val video = kinescopeVideo.getVideoById("b4081a51-e5a3-4586-8e6a-72e2f3eb3075")

    video?.let {
        player.loadVideo(it)
    }
}
```

---

## Where to get the parameters

### API Token
1. Go to the [Kinescope dashboard](https://kinescope.io/)
2. Open project settings
3. Find the "API" or "Tokens" section
4. Create a new token or use an existing one

Used by both `KinescopeApiHelper` and your `KinescopeVideoProvider`.

**Note:** The token may be optional if your videos are public (Shorts provider only).

### Project ID
1. In the Kinescope dashboard
2. Open your project
3. Project ID is usually in the URL or project settings
4. Format: `750f771f-92aa-433d-9b13-6979c38a5d6c`

### Video ID
1. Open the video in Kinescope
2. Video ID is in the video URL
3. Format: `b4081a51-e5a3-4586-8e6a-72e2f3eb3075`

---

## API endpoints reference

### Dashboard API (`KinescopeApiHelper` — built-in)

Base URL: `https://api.kinescope.io/`

- **Video catalog:** `GET /v1/videos/?page={page}&per_page={per_page}`
- **Player templates:** `GET/POST/PUT/DELETE /v1/players`

### Video playback (`KinescopeVideoPlayer` / Shorts `loadVideo`)

Base URL: `https://kinescope.io/`

- **Video metadata + HLS link:** `GET /{video_id}.json?sdk=android`

### DRM license (Widevine, not video metadata)

Base URL: `https://license.kinescope.io/`

- **License acquire:** `GET /v1/vod/{video_id}/acquire/widevine?token=`

### Shorts feed (`KinescopeVideoProvider`)

Combine catalog + playback:

1. `GET https://api.kinescope.io/v1/videos/` — list video ids
2. `GET https://kinescope.io/{video_id}.json?sdk=android` — HLS URL and metadata per video

---

## Error handling

### Shorts provider

```kotlin
lifecycleScope.launch {
    try {
        val videoProvider = MyKinescopeVideoProvider(apiToken)
        val kinescopeVideo = KinescopeUrls(videoProvider = videoProvider, projectId = "your-project-id")
        val videos = kinescopeVideo.getVideosFromApi()

        when {
            videos.isEmpty() -> showMessage("No videos found")
            else -> adapter.updateVideos(videos)
        }
    } catch (e: Exception) {
        Log.e("API", "Error loading videos", e)
        showError("Failed to load videos: ${e.message}")

        val fallback = KinescopeUrls().getNextVideoUrls()
        adapter.updateVideos(fallback)
    }
}
```

If list endpoints return **404**, see [API_TROUBLESHOOTING.md](API_TROUBLESHOOTING.md).

### Dashboard API

```kotlin
import io.kinescope.sdk.api.readApiErrorMessage

apiHelper.getPlayers()
    .catch { error ->
        val message = error.readApiErrorMessage() ?: error.message
        Log.e("API", "Failed to load templates: $message")
    }
    .collect { response -> /* ... */ }
```

---

## Dependencies

To implement `KinescopeVideoProvider` you need:

- HTTP client (Retrofit, Ktor, OkHttp, etc.)
- JSON parser (Kotlinx Serialization, Gson, Moshi, etc.)
- Coroutines for async work

`KinescopeApiHelper` already includes Retrofit + Moshi — no extra setup.

Example for a custom Shorts provider:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
}
```

---

## Debugging

Add logging in your provider for API debugging:

```kotlin
override suspend fun loadVideo(videoId: String): VideoData? {
    return try {
        Log.d("MyKinescopeVideoProvider", "Loading video: $videoId")
        val response = apiService.getVideo(videoId)
        Log.d("MyKinescopeVideoProvider", "Video loaded: ${response.data.title}")
        convertToVideoData(response.data)
    } catch (e: Exception) {
        Log.e("MyKinescopeVideoProvider", "Error loading video: $videoId", e)
        null
    }
}
```

---

## Related documentation

| Topic | File |
|-------|------|
| Shorts 404 errors | [API_TROUBLESHOOTING.md](API_TROUBLESHOOTING.md) |
| Shorts feed integration | [LIBRARY_USAGE_GUIDE.md](LIBRARY_USAGE_GUIDE.md) |
| Dashboard API, player options, PiP | [README.md](../README.md) |
